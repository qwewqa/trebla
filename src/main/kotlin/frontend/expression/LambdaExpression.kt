package xyz.qwewqa.trebla.frontend.expression

import xyz.qwewqa.trebla.frontend.context.Context
import xyz.qwewqa.trebla.frontend.context.createAndAddChild
import xyz.qwewqa.trebla.frontend.value.*
import xyz.qwewqa.trebla.grammar.trebla.LambdaNode

class LambdaExpression(override val node: LambdaNode, val parentContext: Context) : Expression, Callable, Value {
    override val type = LambdaType
    val parameters by lazy {
        node.parameters?.value?.map {
            Parameter(
                it,
                parentContext
            )
        } ?: defaultParameterList
    }
    private val defaultParameterList by lazy {
        listOf(Parameter("it", AnyType, node = node))
    }

    val hasDefaultParameters = node.parameters == null

    val statements = node.body

    override fun callWith(arguments: List<ValueArgument>, callingContext: Context): Value {
        val pairedArguments =
            if (parameters == defaultParameterList && arguments.isEmpty()) emptyMap()
            else parameters.pairedWithAndValidated(arguments)

        val functionContext = callingContext.createAndAddChild(parentContext)
        pairedArguments.forEach { (param, value) -> functionContext.scope.add(value, param.name) }

        statements.dropLast(1).forEach {
            it.parseAndApplyTo(functionContext)
        }

        return statements.lastOrNull()?.parseAndApplyTo(functionContext) ?: UnitValue
    }

    override fun applyTo(context: Context): Value {
        return this
    }
}

/**
 * The type of a lambda expression.
 * This is distinct from the function type because lambdas are allowed in places like
 * withContext, whereas a normal function is not.
 */
object LambdaType : BuiltinType("Lambda") {
    override val parentTypes: List<Type> = listOf(CallableType)
}
