package xyz.qwewqa.trebla.frontend.context

import xyz.qwewqa.trebla.backend.compile.CallbackName
import xyz.qwewqa.trebla.frontend.compileError
import xyz.qwewqa.trebla.frontend.value.BuiltinType
import xyz.qwewqa.trebla.frontend.value.Type
import xyz.qwewqa.trebla.frontend.expression.UnitValue
import xyz.qwewqa.trebla.frontend.expression.parseAndApplyTo
import xyz.qwewqa.trebla.grammar.trebla.BlockNode
import xyz.qwewqa.trebla.grammar.trebla.StatementNode
import xyz.qwewqa.trebla.frontend.expression.BlockStatement
import xyz.qwewqa.trebla.frontend.expression.Value

/**
 * A context contains declarations, which are stored in its scope.
 */
interface Context : Value {
    val parentContext: Context?

    val globalContext: GlobalContext

    val scope: Scope

    val contextMetadata: ContextMetadata

    override val type: Type get() = ContextType

    override fun getMember(name: String, accessingContext: Context?) = scope.find(name)

    val bindingContext: Context? get() = parentContext
}

const val CONTEXT_DEPTH_LIMIT = 100

class ContextMetadata(
    parent: ContextMetadata?,
    callback: CallbackName? = null,
) {
    val depth: Int = parent?.depth?.plus(1) ?: 0
    val callback: CallbackName? = callback ?: parent?.callback

    init {
        if (depth >= CONTEXT_DEPTH_LIMIT) {
            compileError("Maximum depth reached.")
        }
    }
}

fun Context.getFullyQualified(name: List<String>) =
    globalContext.getPackage(name.dropLast(1))?.getMember(name.last(), null)
        ?: compileError("${name.joinToString(".")} does not exist")

fun Context.getFullyQualified(vararg name: String) = getFullyQualified(name.toList())

object ContextType : BuiltinType("Context")

/**
 * A context under which a block of expressions may be evaluated.
 */
interface ExecutionContext : Context {
    val localAllocator: TemporaryAllocator
    val statements: BlockStatement
}

open class SimpleContext(final override val parentContext: Context) : Context {
    override val globalContext: GlobalContext = parentContext.globalContext
    override val scope = Scope(parentContext.scope)
    override val contextMetadata = ContextMetadata(parentContext.contextMetadata)
}

open class SimpleExecutionContext(final override val parentContext: ExecutionContext) : ExecutionContext {
    override val scope = EagerScope(parentContext.scope)
    override val globalContext: GlobalContext = parentContext.globalContext
    override val localAllocator = parentContext.localAllocator
    override val statements = BlockStatement()
    override val contextMetadata = ContextMetadata(parentContext.contextMetadata)
}

class AlternateScopeContext(parentContext: Context, scopeContext: Context) : SimpleContext(parentContext) {
    override val scope = Scope(scopeContext.scope)
    // Note: Metadata also should reflect both parents eventually
}

class AlternateScopeExecutionContext(parentContext: ExecutionContext, scopeContext: Context) :
    SimpleExecutionContext(parentContext) {
    override val scope = EagerScope(scopeContext.scope)
}

fun Context.createAndAddChild() = when (this) {
    is ExecutionContext -> SimpleExecutionContext(this).also { this.statements += it.statements }
    else -> SimpleContext(this)
}

fun Context.createAndAddChild(scopeContext: Context) = when (this) {
    is ExecutionContext -> AlternateScopeExecutionContext(this, scopeContext).also { this.statements += it.statements }
    else -> AlternateScopeContext(this, scopeContext)
}

fun Context.createChild() = when (this) {
    is ExecutionContext -> SimpleExecutionContext(this)
    else -> SimpleContext(this)
}

class InnerExecutionContext(override val parentContext: ExecutionContext) : ExecutionContext {
    override val scope = EagerScope(parentContext.scope)
    override val globalContext: GlobalContext = parentContext.globalContext
    override val localAllocator = parentContext.localAllocator
    override val statements = parentContext.statements
    override val contextMetadata = ContextMetadata(parentContext.contextMetadata)
}

class ReadOnlyContext(override val parentContext: Context) : Context {
    override val scope = ReadOnlyScope(parentContext.scope)
    override val globalContext: GlobalContext = parentContext.globalContext
    override val contextMetadata = ContextMetadata(parentContext.contextMetadata)
}

fun List<StatementNode>.evaluateIn(context: Context): Value =
    this
        .asSequence()
        .map { it.parseAndApplyTo(context) }
        .lastOrNull() ?: UnitValue

fun List<StatementNode>.evaluateInChildOf(context: Context): Value = evaluateIn(context.createAndAddChild())

fun BlockNode.evaluateIn(context: Context) = value.evaluateIn(context)
fun BlockNode.evaluateInChildOf(context: Context): Value = evaluateIn(context.createAndAddChild())
