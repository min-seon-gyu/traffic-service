package com.traffic.domain.config

import com.fasterxml.jackson.annotation.JsonIgnore

data class LoadConfig(
    val vuCount: Int = 1,
    val requestsPerVu: Int? = null,
    val rampUpSeconds: Int = 0,
    val durationSeconds: Int? = null,
    val stages: List<Stage> = emptyList(),
    val maxDuration: Int = 600
) {
    @JsonIgnore
    fun isStagesMode(): Boolean = stages.isNotEmpty()
}
