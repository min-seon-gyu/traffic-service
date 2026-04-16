package com.traffic.domain.config

data class AdvancedConfig(
    val thinkTimeStrategy: ThinkTimeStrategy = ThinkTimeStrategy.CONSTANT,
    val thinkTimeMs: Int = 0,
    val thinkTimeMin: Int = 0,
    val thinkTimeMax: Int = 0,
    val timeoutMs: Int = 10000,
    val discardResponseBody: Boolean = false
)

enum class ThinkTimeStrategy {
    CONSTANT, RANDOM, PACING
}
