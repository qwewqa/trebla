package xyz.qwewqa.trebla.frontend.value.intrinsics

import xyz.qwewqa.trebla.frontend.*
import xyz.qwewqa.trebla.frontend.context.Context
import xyz.qwewqa.trebla.frontend.context.DefaultSignature
import xyz.qwewqa.trebla.frontend.context.Signature
import xyz.qwewqa.trebla.frontend.context.Visibility
import xyz.qwewqa.trebla.frontend.value.Declaration
import xyz.qwewqa.trebla.frontend.value.Type
import xyz.qwewqa.trebla.frontend.expression.*

/**
 * Note that this should not be used as a delegate, or `this` will only include the delegate.
 */
open class SimpleDeclaration(
    override val parentContext: Context?,
    override val identifier: String,
    override val type: Type,
    override val signature: Signature = DefaultSignature,
    override val visibility: Visibility = Visibility.PUBLIC,
) : Declaration

class CallableDelegate(
    private val parameters: IntrinsicParameterDSLContext.() -> Unit,
    private val operation: IntrinsicFunctionDSLContext.() -> Value,
) : Callable {
    override fun callWith(arguments: List<ValueArgument>, callingContext: Context): Value =
        IntrinsicParameterDSLContext(callingContext).apply(parameters).get()?.map { p ->
            Parameter(p.name,
                p.type,
                p.default?.let { DefaultParameter(ValueExpression(it), callingContext) })
        }?.let {
            IntrinsicFunctionDSLContext(it.pairedWithAndValidated(arguments), null, callingContext).operation()
        } ?: IntrinsicFunctionDSLContext(null, arguments, callingContext).operation()
}

class SubscriptableDelegate(
    private val parameters: IntrinsicParameterDSLContext.() -> Unit,
    private val operation: IntrinsicFunctionDSLContext.() -> Value,
) : Subscriptable {
    override fun subscriptWith(arguments: List<ValueArgument>, callingContext: Context): Value =
        IntrinsicParameterDSLContext(callingContext).apply(parameters).get()?.map { p ->
            Parameter(p.name,
                p.type,
                p.default?.let { DefaultParameter(ValueExpression(it), callingContext) })
        }?.let {
            IntrinsicFunctionDSLContext(it.pairedWithAndValidated(arguments), null, callingContext).operation()
        } ?: IntrinsicFunctionDSLContext(null, arguments, callingContext).operation()
}

data class IntrinsicParameter(val name: String, val type: Type, val default: Value?)
class IntrinsicParameterDSLContext(val context: Context) {
    private val params = mutableListOf<IntrinsicParameter>()

    private var managed = true

    val unmanaged: Unit
        get() {
            managed = false
        }

    infix fun String.type(type: Type) =
        IntrinsicParameter(this, type, null).also { params += it }

    infix fun IntrinsicParameter.default(value: Value) =
        copy(default = value).also { new ->
            params.removeLast().let { if (it != this) error("Out of order default use in parameter DSL") }
            params += new
        }

    fun get() = if (managed) params else {
        if (params.isNotEmpty()) compileError("Unmanaged parameters requires none to be specified in DSL.")
        null
    }
}

class IntrinsicFunctionDSLContext(
    private val _parameters: Map<Parameter, Value>?,
    private val _arguments: List<ValueArgument>?,
    val callingContext: Context,
) {
    val String.number
        get() = cast<PrimitiveInstance>().value.tryConstexprEvaluate()
            ?: compileError("Argument must be a compile time constant.")

    val String.boolean
        get() = (cast<PrimitiveInstance>().value.tryConstexprEvaluate()
            ?: compileError("Argument must be a compile time constant.")) != 0.0

    inline fun <reified T : Any> String.cast(): T =
        (parameters[this] ?: error("Unknown parameter $this.")).let { (param, value) ->
            (value as? T) ?: compileError("Failed to cast parameter with type ${param.type.repr()}")
        }

    inline fun <reified T : Any> String.coerceTo(type: Type): T =
        (parameters[this] ?: error("Unknown parameter $this.")).let { (param, value) ->
            ((value as? Value)?.coerceTo(type) as? T)
                ?: compileError("Failed to cast parameter with type ${param.type.repr()}")
        }

    fun String.param() = parameters[this]?.first ?: error("Unknown parameter $this.")

    val arguments get() = _arguments ?: error("Parameters are managed; direct argument access is invalid.")
    val parameters by lazy {
        _parameters?.toList()?.associateBy { it.first.name }
            ?: error("Parameters are unmanaged; use the argument list instead.")
    }
}

fun Number.toPrimitive() = NumberType.fromRaw(this.toLiteralRawValue())
fun Boolean.toPrimitive() = BooleanType.fromRaw(this.toLiteralRawValue())

fun Double.isIntOrCompileError() = toInt().also {
    if (it.toDouble() != this) compileError("Value $this is not an integer.")
}
