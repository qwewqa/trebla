package xyz.qwewqa.sono.frontend.context

import xyz.qwewqa.sono.backend.compile.Archetype
import xyz.qwewqa.sono.frontend.CompilerConfiguration
import xyz.qwewqa.sono.frontend.SonoFile
import xyz.qwewqa.sono.frontend.compileError
import xyz.qwewqa.sono.frontend.declaration.*
import xyz.qwewqa.sono.frontend.expression.Expression
import xyz.qwewqa.sono.frontend.expression.UnitValue
import xyz.qwewqa.sono.frontend.expression.Value
import xyz.qwewqa.sono.grammar.sono.SonoFileNode

class GlobalContext(val compileConfiguration: CompilerConfiguration) : GlobalAllocatorContext {
    override val scope = Scope(null)
    override val levelAllocator = StandardAllocator(0, 256)
    override val tempAllocator = StandardAllocator(100, 16)

    private val files = mutableListOf<SonoFile>()
    var scriptIndex = 0

    fun process(): CompileData {
        with(files) {
            forEach(SonoFile::loadInitial)
            forEach(SonoFile::loadNormal)
            forEach(SonoFile::loadFinal)
        }
        return CompileData(
            files.flatMap { it.scripts }.map { it.finalize() },
            files.flatMap { it.archetypes }.map { it.finalize() }
        )
    }

    fun add(fileNode: SonoFileNode) {
        files += SonoFile(fileNode, this)
    }

    /**
     * Gets the package with the given [name], creating new packages as necessary.
     */
    fun getPackage(name: List<String>): Package {
        if (name.isEmpty()) error("Package name is empty")
        val base = (scope.get(name[0]) ?: Package(name[0], this).also { scope.add(it) }) as? Package ?: compileError(
            "${
                name.joinToString(
                    "."
                )
            } is not a package"
        )
        return name
            .drop(1)
            .fold(base) { a, v ->
                (a.scope.get(v) ?: Package(v, a).also { a.scope.add(it) }) as? Package
                    ?: compileError("${name.joinToString(".")} is not a package")
            }
    }

    fun getNestedValue(name: List<String>) = getNestedValueOrNull(name)
        ?: compileError("Value ${name.joinToString(".")} does not exist")

    fun getNestedValueOrNull(name: List<String>): Value? {
        if (name.isEmpty()) error("Value ${name.joinToString(".")} is empty")
        val base = scope.get(name[0]) ?: return null
        return name
            .drop(1)
            .fold(base) { a, v -> (a as? Context)?.scope?.get(v) ?: return null }
    }

    init {
        intrinsicObjects.forEach { (pkg, declaration) -> declaration.applyTo(getPackage(pkg)) }
        intrinsics.forEach { (pkg, declaration) ->
            val target = getPackage(pkg)
            declaration(target, compileConfiguration).applyTo(target)
        }
    }
}

data class CompileData(val scripts: List<ScriptData>, val archetypes: List<Archetype>)

interface GlobalAllocatorContext : Context {
    val levelAllocator: StandardAllocator

    // not to be confused with the local allocator for temporary variables
    // this is for the literal temporary memory block
    val tempAllocator: StandardAllocator
}

// completely stateless intrinsics
val intrinsicObjects: List<Pair<List<String>, Declaration>> = listOf(
    listOf("std") to Builtins,
    listOf("std") to TypeType,
    listOf("std") to AnyType,
    listOf("std") to UnitValue,
    listOf("std") to StructType,
    listOf("std") to FunctionType,
    listOf("std") to PackageType
)

// constructors for intrinsics that require the context
val intrinsics: List<Pair<List<String>, (Context, CompilerConfiguration) -> Expression>> = listOf(
    listOf("std") to ::Memref,
    listOf("std") to ::OptionsAccessor
)