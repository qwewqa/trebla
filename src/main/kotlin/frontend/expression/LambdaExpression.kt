package xyz.qwewqa.trebla.frontend.expression

import xyz.qwewqa.trebla.frontend.compileError
import xyz.qwewqa.trebla.frontend.context.Context
import xyz.qwewqa.trebla.frontend.context.ExecutionContext
import xyz.qwewqa.trebla.frontend.declaration.*
import xyz.qwewqa.trebla.grammar.trebla.LambdaNode

class LambdaExpression(override val node: LambdaNode, val declaringContext: Context) : Expression, Callable, Value {
    override val type = FunctionType
    override val parameters by lazy {
        node.parameters?.values?.map {
            Parameter(
                it,
                declaringContext
            )
        } ?: listOf(Parameter("it", AnyType, DefaultParameter(ValueExpression(UnitValue), declaringContext), node))
    }

    override fun callWith(arguments: List<ValueArgument>, callingContext: Context): Value {
        val statements = node.body
        val pairedArguments = parameters.pairedWithAndValidated(arguments)

        if (callingContext !is ExecutionContext) {
            // we allow single or zero expressions even in non-execution contexts
            return when (statements.size) {
                0 -> UnitValue
                1 -> statements.first().parseAndApplyTo(FunctionSimpleContext(declaringContext, pairedArguments))
                else -> compileError("Invalid location for multi-statement function call.")
            }
        }
        val functionContext = FunctionExecutionContext(
            callingContext,
            declaringContext,
            pairedArguments
        )

        statements.dropLast(1).forEach {
            it.parseAndApplyTo(functionContext)
        }
        val returnValue = statements.lastOrNull()?.parseAndApplyTo(functionContext) ?: UnitValue

        if (functionContext.statements.isNotEmpty()) callingContext.statements += functionContext

        return returnValue
    }

    override fun applyTo(context: Context): Value {
        return this
    }
}