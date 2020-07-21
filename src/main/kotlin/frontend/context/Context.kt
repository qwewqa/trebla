package xyz.qwewqa.sono.frontend.context

import xyz.qwewqa.sono.backend.compile.FunctionIRNode
import xyz.qwewqa.sono.backend.compile.FunctionIRNodeVariant
import xyz.qwewqa.sono.backend.compile.IRNode
import xyz.qwewqa.sono.frontend.expression.Statement

/**
 * A context contains declarations, which are stored in its scope.
 */
interface Context {
    val scope: Scope
}

/**
 * A context under which a block of expressions may be evaluated.
 */
interface ExecutionContext : Context {
    val localAllocator: TemporaryAllocator
    val statements: MutableList<Statement>
}

/**
 * In some contexts (the file context) it's useful for some checks like type checks to be deferred until
 * later stages of compilation.
 */
interface DeferrableContext : Context {
    fun addDeferredCheck(block: () -> Unit)
}

fun Context.addDeferredOrRun(block: () -> Unit) {
    if (this is DeferrableContext) addDeferredCheck(block)
    else block()
}

class SimpleContext(parent: Context) : Context {
    override val scope = Scope(parent.scope)
}

class SimpleExecutionContext(parent: ExecutionContext) : ExecutionContext, Statement {
    override val scope = Scope(parent.scope)
    override val localAllocator = parent.localAllocator
    override val statements = mutableListOf<Statement>()

    override fun toIR(): IRNode {
        return FunctionIRNode(FunctionIRNodeVariant.Execute, statements.map { it.toIR() })
    }
}


class ReadOnlyContext(parent: Context) : Context {
    override val scope = ReadOnlyScope(parent.scope)
}

class EmptyContext : Context {
    override val scope = ReadOnlyScope()
}