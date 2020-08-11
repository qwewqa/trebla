package xyz.qwewqa.trebla.frontend.declaration.intrinsics

import xyz.qwewqa.trebla.backend.compile.IRFunction
import xyz.qwewqa.trebla.backend.compile.IRFunctionCall
import xyz.qwewqa.trebla.backend.constexpr.tryConstexprEvaluate
import xyz.qwewqa.trebla.frontend.CompilerConfiguration
import xyz.qwewqa.trebla.frontend.compileError
import xyz.qwewqa.trebla.frontend.context.*
import xyz.qwewqa.trebla.frontend.declaration.*
import xyz.qwewqa.trebla.frontend.expression.*
import kotlin.math.roundToInt

class TreblaList(parentContext: Context, val projectConfiguration: CompilerConfiguration) :
    SimpleDeclaration(
        parentContext,
        "List",
        TypeType
    ),
    Subscriptable by SubscriptableDSL(
        parentContext,
        {
            "type" type TypeType
            "size" type NumberType
        },
        {
            if (callingContext == null) compileError("Requires a context.")
            val type = ("type".cast<Type>() as? Allocatable)
                ?: compileError("List type must be allocatable")
            val size = "size".cast<RawStructValue>().raw.toIR().tryConstexprEvaluate()?.roundToInt()
                ?: compileError("List size must be a compile time constant.")
            SpecificListType(size, type, callingContext)
        }
    ),
    Type

class SpecificListType(val size: Int, val containedType: Allocatable, override val bindingContext: Context) : Type,
    Allocatable {
    override val type = TypeType
    override val bindingHierarchy = listOf(listOf(bindingContext.scope.getFullyQualified("std", "List") as Type))

    override fun allocateOn(allocator: Allocator, context: Context): Mutable {
        return ListValue(context, this, List(size) { containedType.allocateOn(allocator, context) })
    }

    override val allocationSize by lazy {
        size * containedType.allocationSize
    }

    override fun equals(other: Any?): Boolean =
        other is SpecificListType && size == other.size && containedType == other.containedType

    override fun hashCode(): Int {
        var result = size
        result = 31 * result + containedType.hashCode()
        return result
    }
}

class ListValue(val parentContext: Context, override val type: Allocatable, val values: List<Mutable>) : Mutable,
    MemberAccessor,
    Subscriptable {
    override val bindingContext = parentContext

    override val subscriptParameters by lazy {
        listOf(
            Parameter("index", parentContext.numberType),
        )
    }

    override fun subscriptWith(arguments: List<ValueArgument>, callingContext: Context?): Value {
        val index = (subscriptParameters.pairedWithAndValidated(arguments).values.first() as RawStructValue).raw.toIR()
            .tryConstexprEvaluate()?.roundToInt()
            ?: compileError("List index must be a compile time constant.")
        if (index !in values.indices) compileError("List index out of range.")
        return values[index]
    }

    override fun offsetReallocate(block: RawValue, index: RawValue): Mutable {
        return ListValue(parentContext, type, values.map { it.offsetReallocate(block, index) })
    }

    override fun copyTo(allocator: Allocator, context: ExecutionContext): Mutable {
        return ListValue(context, type, values.map { it.copyTo(allocator, context) })
    }

    override fun copyFrom(other: Value, context: ExecutionContext) {
        if (other is ListValue && other.type == type) {
            values.zip(other.values).forEach { (v, o) -> v.copyFrom(o, context) }
        } else {
            compileError("Incompatible assignment.")
        }
    }

    override fun getMember(name: String, accessingContext: Context?) = members[name]

    private val members: Map<String, Value> by lazy {
        mapOf(
            "forEach" to ListValueForEach(parentContext, this, false),
            "forEachIndexed" to ListValueForEach(parentContext, this, true),
            "get" to ListGet(parentContext, this)
        )
    }
}

class ListValueForEach(val context: Context, val listValue: ListValue, val indexed: Boolean) : Callable, Value {
    override val type = CallableType
    override val bindingContext = context

    override val parameters by lazy {
        listOf(
            Parameter("start",
                UnionType(listOf(UnitValue, context.numberType)),
                DefaultParameter(ValueExpression(UnitValue), context)),
            Parameter("stop",
                UnionType(listOf(UnitValue, context.numberType)),
                DefaultParameter(ValueExpression(UnitValue), context)),
            Parameter("operation", CallableType),
        )
    }

    override fun callWith(arguments: List<ValueArgument>, callingContext: Context?): Value {
        val args = parameters.pairedWithAndValidated(arguments).byParameterName()
        val operation = args["operation"] as Callable
        val start = (args["start"] as? RawStructValue)?.raw?.let {
            it.toIR().tryConstexprEvaluate() ?: compileError("Start index should be a compile time constant.")
        }?.roundToInt() ?: 0
        val stop = (args["stop"] as? RawStructValue)?.raw?.let {
            it.toIR().tryConstexprEvaluate() ?: compileError("Start index should be a compile time constant.")
        }?.roundToInt() ?: listValue.values.size
        if (callingContext !is ExecutionContext) compileError("List forEach requires an execution context.")
        var index = start
        listValue.values.subList(start, stop).forEach {
            if (indexed) operation.callWith(
                listOf(
                    ValueArgument(null, RawStructValue(LiteralRawValue(index.toDouble()), context, context.numberType)),
                    ValueArgument(null, it)
                ),
                callingContext,
            )
            else operation.callWith(listOf(ValueArgument(null, it)), callingContext)
            index++
        }
        return UnitValue
    }
}

class ListGet(val context: Context, val listValue: ListValue) : Callable, Value {
    override val type = CallableType
    override val bindingContext = context

    override val parameters by lazy {
        listOf(
            Parameter(
                "index",
                UnionType(listOf(UnitValue, context.numberType)),
                DefaultParameter(ValueExpression(UnitValue), context)
            )
        )
    }

    override fun callWith(arguments: List<ValueArgument>, callingContext: Context?): Value {
        val index = parameters.pairedWithAndValidated(arguments).byParameterName()["index"] as RawStructValue
        if (callingContext !is ExecutionContext) compileError("Dynamic list get requires an execution context.")
        val types = listValue.values.map { it.type }.toSet()
        if (types.isEmpty()) compileError("List is empty.")
        if (types.size > 1) compileError("Dynamic list get is only available for homogenous lists.")
        val type = types.first()
        if (type !is Allocatable) compileError("Dynamic list get is only available for lists with allocatable types.")
        val returns = type.allocateOn(callingContext.localAllocator, callingContext)
        callingContext.statements += Statement {
            IRFunctionCall(
                IRFunction.SwitchIntegerWithDefault,
                listValue.values.map { value ->
                    SimpleExecutionContext(callingContext).also {
                        returns.copyFrom(value, it)
                    }.toIR()
                }.let {
                    /*
                    Switch is one indexed but lists are zero indexed, so we can put the first value as the final, default value
                    rather than subtracting one.
                    Out of range access is undefined regardless.
                    */
                    listOf(index.raw.toIR()) + it.drop(1) + it.first()
                }
            )
        }
        return returns
    }
}