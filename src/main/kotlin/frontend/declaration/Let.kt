package xyz.qwewqa.trebla.frontend.declaration

import xyz.qwewqa.trebla.frontend.compileError
import xyz.qwewqa.trebla.frontend.context.Context
import xyz.qwewqa.trebla.frontend.context.Visibility
import xyz.qwewqa.trebla.frontend.context.visibilityModifiers
import xyz.qwewqa.trebla.frontend.expression.UnitValue
import xyz.qwewqa.trebla.frontend.expression.Value
import xyz.qwewqa.trebla.frontend.expression.parseAndApplyTo
import xyz.qwewqa.trebla.grammar.trebla.LetDeclarationNode

class LetDeclaration(
    override val node: LetDeclarationNode,
    override val declaringContext: Context,
) : Declaration {
    override val identifier = node.identifier.value
    override val signature = Signature.Default
    override val visibility: Visibility
    override val type = UnitValue

    val typeConstraint = node.type?.applyIn(declaringContext) ?: AnyType

    init {
        node.modifiers.parse {
            visibility = selectFromMap(visibilityModifiers) ?: Visibility.PUBLIC
        }
    }

    override fun applyTo(context: Context): Value {
        context.scope.addDeferred(identifier, signature, visibility) {
            val value = node.expression.parseAndApplyTo(context)
            if (!typeConstraint.accepts(value)) compileError("Expression does not satisfy explicit type.")
            value
        }
        return UnitValue
    }

}