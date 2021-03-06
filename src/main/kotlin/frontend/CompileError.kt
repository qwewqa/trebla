package xyz.qwewqa.trebla.frontend

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.misc.Interval
import org.antlr.v4.runtime.tree.TerminalNode
import xyz.qwewqa.trebla.frontend.value.Declaration
import xyz.qwewqa.trebla.frontend.expression.Value
import xyz.qwewqa.trebla.grammar.trebla.TreblaNode
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Represents an error during compilation due to some error in the code being compiled.
 * This does not represent a bug in the compiler itself
 * (unless, of course, it's being thrown when compiling valid code).
 */
class CompileError(message: String, val node: TreblaNode? = null, cause: CompileError? = null) :
    IllegalStateException(message, cause) {
    val filename = node?.filename
    val startLineIndex = when (val ctx = node?.context) {
        is ParserRuleContext -> ctx.start.line
        is TerminalNode -> ctx.symbol.line
        else -> null
    }
    val startCharIndex = when (val ctx = node?.context) {
        is ParserRuleContext -> ctx.start.charPositionInLine
        is TerminalNode -> ctx.symbol.charPositionInLine
        else -> null
    }

    val filenamePrefix = " --> "
    val codePrefix = "  | "
    val startPrefix = " >>>"
    val endPrefix = " <<<"
    val skipIndicator = " ..."
    val fileStartIndicator = " BEG"
    val fileEndIndicator = " END"
    val lookbackLines = 1
    val maxLookaheadLines = 1

    override val message: String
        get() = if (node != null) "CompileError: ${super.message}\n$filenamePrefix$filename: ($startLineIndex, $startCharIndex):\n${
            node.context.let {
                val startToken = when (it) {
                    is ParserRuleContext -> it.start
                    is TerminalNode -> it.symbol
                    else -> error("You should not be seeing this.")
                }
                val stopToken = when (it) {
                    is ParserRuleContext -> it.stop
                    is TerminalNode -> it.symbol
                    else -> error("You should not be seeing this.")
                }
                val startPosition = startToken.charPositionInLine
                val stopOffset = stopToken.stopIndex - startToken.startIndex
                // ANTLR doesn't seem to let us get things line-by-line, so just get the full text
                // and split it ourselves
                val text = startToken.inputStream
                    .getText(Interval(0, startToken.inputStream.size() - 1))
                    .split("\\r?\\n".toRegex())

                // lines are 1-indexed so we subtract
                val startLine = startToken.line - 1
                val endLine = stopToken.line - 1

                val prefixStartLine = (startLine - lookbackLines).coerceAtLeast(0)
                val bodyEndLine = endLine.coerceAtMost(startLine + maxLookaheadLines)
                val suffixEndLine = (startLine + maxLookaheadLines).coerceAtMost(text.size - 1)

                val location = if (startLine == endLine) {
                    if (stopOffset > 1) {
                        "${" ".repeat(startPosition)}v${"-".repeat(stopOffset - 1)}v"
                    } else {
                        "${" ".repeat(startPosition)}v"
                    }
                } else ""

                val prefix =
                    "${
                        if (startLine > 0) skipIndicator else fileStartIndicator
                    }\n${
                        if (prefixStartLine < startLine)
                            "${text[prefixStartLine until startLine].formatCode()}\n$startPrefix$location\n"
                        else "$startPrefix\n"
                    }"
                val body = text[startLine..bodyEndLine].formatCode()
                val suffix =
                    "${
                        when {
                            suffixEndLine > bodyEndLine -> "\n$endPrefix\n" + text[(bodyEndLine + 1)..suffixEndLine].formatCode()
                            bodyEndLine == endLine -> "\n$endPrefix"
                            else -> ""
                        }
                    }\n${
                        if (suffixEndLine != text.size - 1) skipIndicator else fileEndIndicator
                    }"

                "$prefix$body$suffix"
            }
        }"
        else "CompileError: " + super.message

    private fun List<String>.formatCode() = joinToString("\n").prependIndent(codePrefix)
}

private operator fun <E> List<E>.get(intRange: IntRange) = this.subList(intRange.first, intRange.last + 1)

fun compileError(message: String, node: TreblaNode? = null): Nothing = throw CompileError(message, node)
fun Declaration.compileError(message: String): Nothing = node?.compileError(message) ?: compileError(message, null)
fun TreblaNode.compileError(message: String): Nothing = compileError(message, this)

/**
 * Runs the given [block], returning the result.
 * If a [CompileError] is thrown within the block, rethrows a compile error with the given [message]
 * with the inner error as the cause and with this node.
 * Otherwise, is equivalent to [run].
 */
@OptIn(ExperimentalContracts::class)
inline fun <T> TreblaNode?.runWithErrorMessage(message: String, block: () -> T): T {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    try {
        return block()
    } catch (e: CompileError) {
        throw CompileError(message, this, e)
    }
}
