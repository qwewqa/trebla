package xyz.qwewqa.trebla.frontend

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.toList
import org.antlr.v4.runtime.*
import xyz.qwewqa.trebla.backend.BackendConfig
import xyz.qwewqa.trebla.backend.compile.*
import xyz.qwewqa.trebla.frontend.context.GlobalContext
import xyz.qwewqa.trebla.frontend.value.ScriptData
import xyz.qwewqa.trebla.grammar.generated.TreblaLexer
import xyz.qwewqa.trebla.grammar.generated.TreblaParser
import xyz.qwewqa.trebla.grammar.trebla.TreblaFileNode
import xyz.qwewqa.trebla.grammar.trebla.TreblaFileVisitor
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

class TreblaCompiler(val configuration: CompilerConfiguration) : CoroutineScope by CoroutineScope(Dispatchers.Default) {
    private val context = GlobalContext(configuration)
    private val loadChannel = Channel<TreblaFileNode>(Channel.BUFFERED)
    private val errorChannel = Channel<LogErrorListener.SyntaxError>(Channel.UNLIMITED)
    private val fileLoadJob = Job()
    private val loadJob = launch {
        for (fileNode in loadChannel) {
            context.add(fileNode)
        }
    }

    fun loadFile(stream: InputStream, name: String) {
        launch(fileLoadJob) {
            // Would think withContext would satisfy Intellij, but guess not
            // async + await actually does satisfy the IDE, except it (correctly) tells you to change this to withContext
            // which it then considers improper again.
            // Haven't bothered filing this as a bug yet.
            @Suppress("BlockingMethodInNonBlockingContext")
            val lexer = withContext(Dispatchers.IO) { TreblaLexer(CharStreams.fromStream(stream)) }
            val tokenStream = CommonTokenStream(lexer)
            val errorListener = LogErrorListener(name)
            val parser = TreblaParser(tokenStream).apply {
                removeErrorListeners()
                addErrorListener(errorListener)
            }
            val visitor = TreblaFileVisitor(name)
            val fileContext = parser.treblaFile()
            if (errorListener.errors.isNotEmpty()) {
                errorListener.errors.forEach { errorChannel.send(it) }
                return@launch
            }
            val fileNode = visitor.visit(fileContext) as TreblaFileNode
            loadChannel.send(fileNode)
        }
    }

    fun loadFile(file: File) = loadFile(file.inputStream(), file.canonicalPath)

    suspend fun process(): EngineData {
        fileLoadJob.complete()
        fileLoadJob.join()
        loadChannel.close()
        errorChannel.close()
        loadJob.join()
        val errors = errorChannel.toList()
        if (errors.isNotEmpty()) {
            throw CompileError("Syntax Error\n\n${errors.errorMessage()}")
        }
        val (scriptData, archetypes) = context.process()
        archetypes.map { it.name }.fold(mutableSetOf<String>()) { a, v ->
            a.add(v).also {
                if (!it) compileError("Duplicate archetypes with name '$v'.")
                archetypes.filter { it.name == v }
            }
            a
        }
        return processScripts(scriptData, archetypes, configuration)
    }

    private fun processScripts(
        scripts: List<ScriptData>,
        archetypes: List<Archetype>,
        configuration: CompilerConfiguration,
    ): EngineData {
        if (configuration.buildConfig.verbose) {
            scripts.forEach {
                println(it.name)
                println("Free: ${formatRuns(it.freeIndexes.toSet())}")
                it.callbacks.forEach { cb ->
                    println("${cb.name.compiledName} @${cb.order}: ${cb.toIR()}")
                }
                println()
            }
        }
        val irScripts = scripts.map { script ->
            IRScript(
                script.index,
                script.freeIndexes,
                script.callbacks.map { IRCallback(it.name, it.order, it.toIR()) },
                script.arguments,
            )
        }
        return compileEngine(irScripts,
            archetypes,
            configuration.projectConfig.options,
            configuration.projectConfig.buckets,
            BackendConfig())
    }

    init {
        ZipInputStream(javaClass.getResourceAsStream("/std.zip")).use {
            while (true) {
                val entry = it.nextEntry ?: break
                if (entry.isDirectory) {
                    it.closeEntry()
                    continue
                }
                loadFile(it.readAllBytes().inputStream(), "[Builtin] ${entry.name}")
                it.closeEntry()
            }
        }
    }
}

class LogErrorListener(private val filename: String) : BaseErrorListener() {
    val errors = mutableListOf<SyntaxError>()

    override fun syntaxError(
        recognizer: Recognizer<*, *>?,
        offendingSymbol: Any?,
        line: Int,
        charPositionInLine: Int,
        msg: String,
        e: RecognitionException?,
    ) {
        errors += SyntaxError(filename, line, charPositionInLine, msg)
    }

    data class SyntaxError(val filename: String, val line: Int, val position: Int, val message: String)
}

fun List<LogErrorListener.SyntaxError>.errorMessage() = this.sortedBy { it.filename }.joinToString("\n\n") {
    "${it.filename}: (${it.line}, ${it.position}):\n${it.message}"
}

fun formatRuns(n: Set<Int>): String = if (n.isEmpty()) "[]" else {
    val indexes = n.sorted()
    val ranges = mutableListOf<String>()
    var runStart = indexes.first()
    var runEnd = runStart
    indexes.drop(1).forEach {
        if (it - runEnd == 1) runEnd = it
        else {
            ranges += if (runStart == runEnd) runStart.toString() else "$runStart-$runEnd"
            runStart = it
            runEnd = it
        }
    }
    ranges += if (runStart == runEnd) runStart.toString() else "$runStart-$runEnd"
    ranges.joinToString(", ")
}
