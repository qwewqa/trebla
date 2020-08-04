package xyz.qwewqa.trebla.frontend.declaration

import xyz.qwewqa.trebla.frontend.compileError
import xyz.qwewqa.trebla.frontend.context.*
import xyz.qwewqa.trebla.frontend.expression.*
import xyz.qwewqa.trebla.grammar.trebla.StructDeclarationNode

class StructDeclaration(
    override val node: StructDeclarationNode,
    override val parentContext: Context,
) : Declaration,
    Callable,
    Type,
    Allocatable {
    override val identifier = node.identifier.value
    override val signature = Signature.Default
    override val visibility: Visibility
    override val type = TypeType

    // Eventually embedding a struct in another struct might be supported, like in Go
    // In this case, the binding hierarchy would include it.
    override val bindingHierarchy = listOf(listOf(StructType))

    override val loadEarly = true

    val isRaw: Boolean

    val fields by lazy { node.fields.value.parse(parentContext) }
    val fieldNames by lazy { fields.map { it.name }.toSet() }
    override val parameters by lazy { fields }

    override fun callWith(arguments: List<ValueArgument>, callingContext: Context?): Mutable {
        if (!isRaw) {
            return NormalStructValue(fields.pairedWithAndValidated(arguments).byParameterName(),
                callingContext,
                this)
        } else {
            if (arguments.size != 1) compileError("A raw struct constructor should get exactly one argument.")
            val arg = arguments.first()
            if (arg.name != null) compileError("A raw struct constructor does not take a named argument.")
            val value = arg.value
            if (value !is RawStructValue) compileError("A raw struct constructor only takes another raw struct.")
            return RawStructValue(value.raw, callingContext, this)
        }
    }

    override fun allocateOn(allocator: Allocator, context: Context?): Mutable {
        return if (isRaw) RawStructValue(AllocatedRawValue(allocator.allocate()), context, this)
        else callWith(parameters.map {
            ValueArgument(
                it.name,
                (it.type as? Allocatable)?.allocateOn(allocator, context)
                    ?: compileError("${type.identifier} is not allocatable.")
            )
        }, context)
    }

    override val allocationSize by lazy {
        fields.sumBy {
            (it.type as? Allocatable)?.allocationSize ?: compileError("Struct has unsized members.")
        }
    }

    init {
        node.modifiers.parse {
            visibility = selectFromMap(visibilityModifiers) ?: Visibility.PUBLIC
            isRaw = selectSingle("raw")
        }
    }
}

object StructType : BuiltinType("Struct")

/**
 * The value resulting from a struct construction.
 */
sealed class StructValue(
    override var bindingContext: Context?,
    override val type: StructDeclaration,
) : Value, Mutable

class NormalStructValue(
    val fields: Map<String, Value>,
    searchContext: Context?,
    type: StructDeclaration,
) : StructValue(searchContext, type), MemberAccessor {
    override fun copyOn(allocator: Allocator, context: ExecutionContext): StructValue {
        return NormalStructValue(fields.mapValues { (_, value) ->
            when (value) {
                is Mutable -> value.copyOn(allocator, context)
                !is Mutable -> value
                else -> compileError("Invalid copy.")
            }
        }, bindingContext, type)
    }

    override fun copyFrom(other: Value, context: ExecutionContext) {
        if (other !is NormalStructValue || type != other.type) compileError("Incompatible assigment.")
        fields.forEach { (name, value) ->
            if (value is Mutable) value.copyFrom(other.fields.getValue(name), context)
            else if (value != other.fields.getValue(name)) compileError("Invalid assigment.")
        }
    }

    override fun getMember(name: String, accessingContext: Context?): Value? = fields[name]

    override fun offsetReallocate(block: RawValue, index: RawValue): Mutable {
        return NormalStructValue(
            fields.mapValues { (_, v) ->
                (v as? Mutable)?.offsetReallocate(block, index) ?: v
            },
            bindingContext,
            type,
        )
    }
}

class RawStructValue(
    val raw: RawValue,
    searchContext: Context?,
    type: StructDeclaration,
) : StructValue(searchContext, type) {
    override fun copyOn(allocator: Allocator, context: ExecutionContext): StructValue {
        val newValue = AllocatedRawValue(allocator.allocate())
        context.statements += AllocatedValueAssignment(newValue, raw)
        return RawStructValue(newValue, bindingContext, type)
    }

    override fun copyFrom(other: Value, context: ExecutionContext) {
        if (other !is RawStructValue || type != other.type) compileError("Incompatible assigment.")
        context.statements += when (raw) {
            is AllocatedRawValue -> AllocatedValueAssignment(raw, other.raw)
            else -> compileError("Value is not mutable.")
        }
    }

    override fun offsetReallocate(block: RawValue, index: RawValue): Mutable {
        return when (raw) {
            is AllocatedRawValue -> when (raw.allocation) {
                is ConcreteAllocation -> RawStructValue(
                    AllocatedRawValue(DynamicAllocation(block, index, raw.allocation.index.toLiteralRawValue())),
                    bindingContext,
                    type
                )
                else -> compileError("Reallocation not available for dynamic or temporary allocated raw structs.")
            }
            else -> compileError("Reallocation not available for non-allocated raw structs.")
        }
    }
}