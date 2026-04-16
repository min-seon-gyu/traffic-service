package com.traffic.metric

data class AggregatedMetric(
    val stepName: String?,
    val currentVu: Int,
    val requestCount: Int,
    val errorCount: Int,
    val avgResponseTime: Double,
    val p95ResponseTime: Double,
    val tps: Double
)

class MetricAggregator {

    fun aggregate(records: List<MetricRecord>, currentVu: Int): List<AggregatedMetric> {
        if (records.isEmpty()) return emptyList()

        val result = mutableListOf<AggregatedMetric>()

        // Overall aggregate
        result.add(computeMetric(null, records, currentVu))

        // Per-step aggregates
        records.groupBy { it.stepName }.forEach { (stepName, stepRecords) ->
            result.add(computeMetric(stepName, stepRecords, currentVu))
        }

        return result
    }

    private fun computeMetric(stepName: String?, records: List<MetricRecord>, currentVu: Int): AggregatedMetric {
        val latencies = records.map { it.latencyMs }
        val sorted = latencies.sorted()
        val errorCount = records.count { !it.success }

        return AggregatedMetric(
            stepName = stepName,
            currentVu = currentVu,
            requestCount = records.size,
            errorCount = errorCount,
            avgResponseTime = latencies.average(),
            p95ResponseTime = percentile(sorted, 95.0),
            tps = records.size.toDouble()
        )
    }

    private fun percentile(sortedValues: List<Long>, percentile: Double): Double {
        if (sortedValues.isEmpty()) return 0.0
        val index = (percentile / 100.0 * (sortedValues.size - 1)).toInt()
        return sortedValues[index].toDouble()
    }
}
