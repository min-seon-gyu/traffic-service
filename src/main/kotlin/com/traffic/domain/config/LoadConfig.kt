package com.traffic.domain.config

data class LoadConfig(
    val vuCount: Int = 1,
    val requestsPerVu: Int? = null,
    val rampUpSeconds: Int = 0,
    val durationSeconds: Int? = null,
    val stages: List<Stage> = emptyList(),
    val maxDuration: Int = 600
) {
    fun isStagesMode(): Boolean = stages.isNotEmpty()
}
