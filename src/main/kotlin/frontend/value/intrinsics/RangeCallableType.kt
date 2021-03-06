package xyz.qwewqa.trebla.frontend.value.intrinsics

import xyz.qwewqa.trebla.frontend.NumberType
import xyz.qwewqa.trebla.frontend.compileError
import xyz.qwewqa.trebla.frontend.context.Context
import xyz.qwewqa.trebla.frontend.context.getFullyQualified
import xyz.qwewqa.trebla.frontend.value.*
import xyz.qwewqa.trebla.frontend.expression.Callable
import xyz.qwewqa.trebla.frontend.expression.UnitValue
import xyz.qwewqa.trebla.frontend.expression.Value
import xyz.qwewqa.trebla.frontend.expression.ValueArgument

class RangeCallableType(context: Context) :
    BuiltinType("Range"),
    Callable by CallableDelegate(
        {
            "arg1" type NumberType
            "arg2" type UnionType(setOf(UnitValue, NumberType)) default UnitValue
            "step" type NumberType default 1.0.toPrimitive()
        },
        {
            val start: Double
            val stop: Double
            if ("arg2".cast<Value>() == UnitValue) {
                start = 0.0
                stop = "arg1".number
            } else {
                start = "arg1".number
                stop = "arg2".number
            }
            val step = "step".number
            if (step == 0.0) compileError("Range step must not be zero.")
            RangeValue(context, start.isIntOrCompileError(), stop.isIntOrCompileError(), step.isIntOrCompileError())
        }
    )

class RangeValue(
    val context: Context,
    val start: Int,
    val stop: Int,
    val step: Int,
) : Value,
    Callable by CallableDelegate(
        {
            "operation" type CallableType
        },
        {
            var index = start
            val operation = "operation".cast<Callable>()
            while (if (step > 0) index < stop else index > stop) {
                operation.callWith(
                    listOf(ValueArgument(null, index.toPrimitive())),
                    callingContext,
                )
                index += step
            }
            UnitValue
        }
    ) {
    override val type = context.getFullyQualified("std", "Range") as Type
    override fun getMember(name: String, accessingContext: Context?): Value? {
        return when (name) {
            "start" -> start.toPrimitive()
            "stop" -> stop.toPrimitive()
            "step" -> step.toPrimitive()
            else -> null
        }
    }
}
