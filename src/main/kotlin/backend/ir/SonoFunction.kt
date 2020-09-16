package xyz.qwewqa.trebla.backend.ir

enum class SonoFunction {
    Execute,
    If,
    Switch,
    SwitchWithDefault,
    SwitchInteger,
    SwitchIntegerWithDefault,
    While,
    Add,
    Subtract,
    Multiply,
    Divide,
    Mod,
    Power,
    Log,
    Equal,
    NotEqual,
    Greater,
    GreaterOr,
    Less,
    LessOr,
    And,
    Or,
    Not,
    Min,
    Max,
    Abs,
    Sign,
    Ceil,
    Floor,
    Round,
    Frac,
    Trunc,
    Degree,
    Radian,
    Sin,
    Cos,
    Tan,
    Sinh,
    Cosh,
    Tanh,
    Arcsin,
    Arccos,
    Arctan,
    Arctan2,
    Clamp,
    Lerp,
    LerpClamped,
    Unlerp,
    UnlerpClamped,
    Remap,
    RemapClamped,
    Smoothstep,
    Random,
    RandomInteger,
    ConstexprRandom,
    ConstexprRandomInteger,
    Get,
    GetShifted,
    Set,
    SetShifted,
    Draw,
    DrawCurvedL,
    DrawCurvedR,
    DrawCurvedLR,
    DrawCurvedB,
    DrawCurvedT,
    DrawCurvedBT,
    Play,
    PlayScheduled,
    Spawn,
    SpawnParticleEffect,
    MoveParticleEffect,
    DestroyParticleEffect,
    Judge,
    JudgeSimple,
    EaseInSine,
    EaseOutSine,
    EaseInOutSine,
    EaseOutInSine,
    EaseInQuad,
    EaseOutQuad,
    EaseInOutQuad,
    EaseOutInQuad,
    EaseInCubic,
    EaseOutCubic,
    EaseInOutCubic,
    EaseOutInCubic,
    EaseInQuart,
    EaseOutQuart,
    EaseInOutQuart,
    EaseOutInQuart,
    EaseInQuint,
    EaseOutQuint,
    EaseInOutQuint,
    EaseOutInQuint,
    EaseInExpo,
    EaseOutExpo,
    EaseInOutExpo,
    EaseOutInExpo,
    EaseInCirc,
    EaseOutCirc,
    EaseInOutCirc,
    EaseOutInCirc,
    EaseInBack,
    EaseOutBack,
    EaseInOutBack,
    EaseOutInBack,
    EaseInElastic,
    EaseOutElastic,
    EaseInOutElastic,
    EaseOutInElastic,
    ;

    fun calledWith(arguments: List<IRNode>) = IRFunctionCall(this, arguments)
    fun calledWith(vararg arguments: IRNode) = calledWith(arguments.toList())
}
