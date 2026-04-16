package com.traffic.metric

data class MetricRecord(
    val stepName: String,
    val latencyMs: Long,
    val statusCode: Int,
    val success: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
