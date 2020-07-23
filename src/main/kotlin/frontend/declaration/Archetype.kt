package xyz.qwewqa.trebla.frontend.declaration

import xyz.qwewqa.trebla.backend.compile.Archetype
import xyz.qwewqa.trebla.backend.constexpr.tryConstexprEvaluate
import xyz.qwewqa.trebla.frontend.compileError
import xyz.qwewqa.trebla.frontend.context.Context
import xyz.qwewqa.trebla.frontend.context.ReadOnlyContext
import xyz.qwewqa.trebla.frontend.context.Visibility
import xyz.qwewqa.trebla.frontend.expression.parseAndApplyTo
import xyz.qwewqa.trebla.frontend.runWithErrorMessage
import xyz.qwewqa.trebla.grammar.trebla.ArchetypeDeclarationNode

class ArchetypeDeclaration(override val node: ArchetypeDeclarationNode, override val declaringContext: Context) :
    Declaration {
    override val identifier = node.name.value
    override val signature = Signature.Archetype
    override val visibility = Visibility.PUBLIC
    override val type = AnyType

    val isInput = node.isInput

    fun finalize() = runWithErrorMessage("Error in archetype declaration.") {
        val script = (declaringContext.scope.find(node.script.value) as? ScriptDeclaration)
            ?: compileError("Invalid script.")
        val defaults = node.defaults.value.associate {
            it.identifier.value to it.value.parseAndApplyTo(ReadOnlyContext(declaringContext)).let { value ->
                if (value.type != script.dataProperties[it.identifier.value]?.type)
                    compileError("Mismatched type on ${it.identifier.value}", it)
                (value as? RawStructValue)?.value?.toIR()
                    ?.tryConstexprEvaluate()
                    ?: compileError("Invalid archetype default. Must have a constant numerical or boolean value.")
            }
        }
        Archetype(identifier, script.index, isInput, defaults)
    }
}

data class ArchetypeData(val name: String, val script: ScriptDeclaration, val defaults: Map<String, Double>)