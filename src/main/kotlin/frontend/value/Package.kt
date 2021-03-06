package xyz.qwewqa.trebla.frontend.value

import xyz.qwewqa.trebla.frontend.context.*
import xyz.qwewqa.trebla.frontend.expression.Value
import xyz.qwewqa.trebla.grammar.trebla.TreblaNode

/**
 * Packages are the main method of scoping.
 */
class Package(
    val identifier: List<String>,
    override val parentContext: GlobalContext,
    val node: TreblaNode? = null,
) : GlobalAllocatorContext {
    override val globalContext: GlobalContext = parentContext.globalContext
    override val type = PackageType
    override val bindingContext: Context = parentContext
    override val scope = Scope(parentContext.scope)
    override val levelAllocator = parentContext.levelAllocator
    override val leveldataAllocator = parentContext.leveldataAllocator
    override val tempAllocator = parentContext.tempAllocator
    override val contextMetadata = ContextMetadata(parentContext.contextMetadata)

    override fun getMember(name: String, accessingContext: Context?): Value? =
        scope.get(name)

    /**
     * True if the same package or a subpackage
     */
    fun includedBy(other: Package) =
        identifier.size >= other.identifier.size && identifier.zip(other.identifier).all { (a, b) -> a == b }
}

object PackageType : BuiltinType("Package")
