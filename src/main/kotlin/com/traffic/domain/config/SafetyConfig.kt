package com.traffic.domain.config

data class SafetyConfig(
    val abortOnErrorRate: Int = 50,
    val gracefulStopTimeout: Int = 30
)
