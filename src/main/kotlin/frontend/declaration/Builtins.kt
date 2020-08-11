package xyz.qwewqa.trebla.frontend.declaration

import xyz.qwewqa.trebla.backend.compile.IRFunction
import xyz.qwewqa.trebla.frontend.compileError
import xyz.qwewqa.trebla.frontend.context.*
import xyz.qwewqa.trebla.frontend.expression.*

/**
 * A special entity which provides access to builtin functions (Sonolus node function).
 * A member access yields [BuiltinFunction] callable which may be called with an arbitrary number of arguments,
 * which must be of a raw type.
 */
object Builtins : MemberAccessor, Declaration {
    override val parentContext: Context? = null
    override val type = AnyType

    override val identifier = "builtins"
    override val signature = Signature.Default
    override val visibility = Visibility.PUBLIC

    override fun getMember(name: String, accessingContext: Context?): Value? = try {
        BuiltinFunction(IRFunction.valueOf(name))
    } catch (e: IllegalArgumentException) {
        null
    }
}

/**
 * A builtin function.
 * Builtin functions are part of Sonolus.
 */
class BuiltinFunction(val function: IRFunction) : Callable, Value {
    override val type = CallableType
    override val bindingContext: Context? = null

    override fun callWith(arguments: List<ValueArgument>, callingContext: Context?): Value {
        val argumentValues = arguments.map {
            val parameterValue = it.value
            if (parameterValue !is RawStructValue) compileError("A builtin function must be called with only raw struct arguments.")
            parameterValue.raw
        }
        if (callingContext == null) compileError("Builtin function requires a context.")
        val rawType = callingContext.scope.getFullyQualified(listOf("std", "Raw")) as StructDeclaration
        return RawStructValue(BuiltinCallRawValue(function, argumentValues), callingContext, rawType)
    }
}