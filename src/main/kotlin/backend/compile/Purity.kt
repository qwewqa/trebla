package xyz.qwewqa.trebla.backend.compile

import xyz.qwewqa.trebla.backend.compile.SonoFunction.*

val impureFunctions = setOf(
    Set,
    Get,
    SetShifted,
    GetShifted,
    Draw,
    Play,
    PlayScheduled,
    Spawn,
    SpawnParticleEffect,
    MoveParticleEffect,
    DestroyParticleEffect,
    Random,
    RandomInteger,
)

/**
 * Similar to impure functions, but excludes things like random or get,
 * which aren't technically pure, but if they're all that exist, the code has not effect anyways
 * unless it ends up as the argument of a function which does have an effect
 */
val undroppableFunctions = setOf(
    Set,
    SetShifted,
    Draw,
    Play,
    PlayScheduled,
    Spawn,
    SpawnParticleEffect,
    MoveParticleEffect,
    DestroyParticleEffect,
)

val orderedImpureFunctions = setOf(
    Set,
    Get,
    SetShifted,
    GetShifted,
)