package com.traffic.domain.config

data class Threshold(
    val metric: ThresholdMetric,
    val operator: ThresholdOperator,
    val value: Double
) {
    fun evaluate(actualValue: Double): Boolean = when (operator) {
        ThresholdOperator.LT -> actualValue < value
        ThresholdOperator.LTE -> actualValue <= value
        ThresholdOperator.GT -> actualValue > value
        ThresholdOperator.GTE -> actualValue >= value
    }
}

enum class ThresholdMetric {
    AVG, P50, P90, P95, P99, ERROR_RATE, TPS
}

enum class ThresholdOperator {
    LT, LTE, GT, GTE
}
