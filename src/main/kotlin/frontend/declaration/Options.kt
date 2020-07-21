package xyz.qwewqa.sono.frontend.declaration

import xyz.qwewqa.sono.backend.constexpr.ConstexprEvaluationException
import xyz.qwewqa.sono.backend.constexpr.constexprEvaluate
import xyz.qwewqa.sono.frontend.CompilerConfiguration
import xyz.qwewqa.sono.frontend.compileError
import xyz.qwewqa.sono.frontend.context.*
import xyz.qwewqa.sono.frontend.expression.*
import xyz.qwewqa.sono.project.ProjectConfiguration

class OptionsAccessor(override val declaringContext: Context, val projectConfiguration: CompilerConfiguration) : Declaration, MemberAccessor {
    override val identifier = "options"
    override val type = AnyType
    override val signature = Signature.Default
    override val visibility = Visibility.PUBLIC

    private val options = projectConfiguration.projectConfig.options.mapIndexed { i, option ->
        val name = option["name"]
        val type = option["type"]
        if (name !is String) compileError("Option name invalid or does not exist.")
        if (type !is String) compileError("Invalid option type '$type'.")
        name to OptionData(
            i,
            when (type) {
                "slider" -> OptionType.SLIDER
                "toggle" -> OptionType.TOGGLE
                else -> compileError("Unknown option type '$type'.")
            }
        )
    }.toMap()

    override fun getMember(name: String, accessingContext: Context?): Value {
        val option = options[name] ?: compileError("No option with name '$name'.")
        return RawStructValue(
            AllocatedValue(ConcreteAllocation(2, option.index)),
            accessingContext,
            declaringContext.scope.getFullyQualified("std", option.type.structName) as StructDeclaration
        )
    }
}

private data class OptionData(val index: Int, val type: OptionType)
private enum class OptionType(val structName: String) {
    SLIDER("Number"),
    TOGGLE("Boolean")
}