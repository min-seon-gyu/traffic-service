package com.traffic.engine

import com.traffic.domain.config.*
import com.traffic.metric.AggregatedMetric
import com.traffic.metric.MetricAggregator
import com.traffic.metric.MetricBuffer
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class LoadEngine(
    private val stepExecutor: StepExecutor,
    private val metricBuffer: MetricBuffer
) {
    private val aggregator = MetricAggregator()
    private val aborted = AtomicBoolean(false)
    private val activeVuCount = AtomicInteger(0)
    private val vuJobs = mutableListOf<Job>()

    suspend fun run(
        steps: List<StepSpec>,
        baseUrl: String,
        loadConfig: LoadConfig,
        safetyConfig: SafetyConfig,
        advancedConfig: AdvancedConfig,
        authConfig: AuthConfig,
        onMetricSnapshot: (List<AggregatedMetric>) -> Unit
    ) {
        aborted.set(false)
        activeVuCount.set(0)
        vuJobs.clear()

        coroutineScope {
            val collectorJob = launch {
                runMetricCollector(safetyConfig, onMetricSnapshot)
            }

            if (loadConfig.isStagesMode()) {
                runWithStages(this, steps, baseUrl, loadConfig, advancedConfig, authConfig)
            } else {
                runSimpleMode(this, steps, baseUrl, loadConfig, advancedConfig, authConfig)
            }

            collectorJob.cancel()
        }
    }

    fun abort() {
        aborted.set(true)
        vuJobs.forEach { it.cancel() }
    }

    fun getCurrentVuCount(): Int = activeVuCount.get()

    private suspend fun runSimpleMode(
        scope: CoroutineScope,
        steps: List<StepSpec>,
        baseUrl: String,
        loadConfig: LoadConfig,
        advancedConfig: AdvancedConfig,
        authConfig: AuthConfig
    ) {
        val vuCount = loadConfig.vuCount
        val rampUpDelayMs = if (loadConfig.rampUpSeconds > 0 && vuCount > 1) {
            (loadConfig.rampUpSeconds * 1000L) / (vuCount - 1)
        } else 0L

        val durationMs = loadConfig.durationSeconds?.let { it * 1000L }

        val jobs = (0 until vuCount).map { index ->
            scope.launch {
                if (rampUpDelayMs > 0 && index > 0) {
                    delay(rampUpDelayMs * index)
                }
                if (aborted.get()) return@launch

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val initialVars = buildInitialVariables(authConfig, index)
                    val vu = VirtualUser(
                        id = index,
                        steps = steps,
                        baseUrl = baseUrl,
                        advancedConfig = advancedConfig,
                        stepExecutor = stepExecutor,
                        metricBuffer = metricBuffer,
                        initialVariables = initialVars
                    )

                    activeVuCount.incrementAndGet()
                    try {
                        vu.run(requestCount = loadConfig.requestsPerVu, durationMs = durationMs)
                    } finally {
                        activeVuCount.decrementAndGet()
                    }
                }
            }
        }
        vuJobs.addAll(jobs)

        val maxDurationJob = scope.launch {
            delay(loadConfig.maxDuration * 1000L)
            abort()
            jobs.forEach { it.cancel() }
        }

        jobs.joinAll()
        maxDurationJob.cancel()
    }

    private suspend fun runWithStages(
        scope: CoroutineScope,
        steps: List<StepSpec>,
        baseUrl: String,
        loadConfig: LoadConfig,
        advancedConfig: AdvancedConfig,
        authConfig: AuthConfig
    ) {
        var currentVuTarget = 0
        val activeJobs = mutableListOf<Job>()
        var vuIdCounter = 0

        for (stage in loadConfig.stages) {
            if (aborted.get()) break
            val targetVu = stage.targetVu
            val stageDurationMs = stage.durationSeconds * 1000L

            if (targetVu > currentVuTarget) {
                val toAdd = targetVu - currentVuTarget
                val rampDelayMs = if (toAdd > 1) stageDurationMs / toAdd else 0

                for (i in 0 until toAdd) {
                    if (aborted.get()) break
                    val vuId = vuIdCounter++
                    val initialVars = buildInitialVariables(authConfig, vuId)
                    val job = scope.launch {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val vu = VirtualUser(vuId, steps, baseUrl, advancedConfig, stepExecutor, metricBuffer, initialVars)
                            activeVuCount.incrementAndGet()
                            try {
                                vu.run(durationMs = Long.MAX_VALUE)
                            } finally {
                                activeVuCount.decrementAndGet()
                            }
                        }
                    }
                    activeJobs.add(job)
                    if (rampDelayMs > 0) delay(rampDelayMs)
                }
            } else if (targetVu < currentVuTarget) {
                val toRemove = currentVuTarget - targetVu
                repeat(toRemove.coerceAtMost(activeJobs.size)) {
                    activeJobs.removeFirstOrNull()?.cancel()
                }
            }

            currentVuTarget = targetVu
            if (!aborted.get()) delay(stageDurationMs)
        }

        activeJobs.forEach { it.cancel() }
    }

    private suspend fun runMetricCollector(
        safetyConfig: SafetyConfig,
        onMetricSnapshot: (List<AggregatedMetric>) -> Unit
    ) {
        while (!aborted.get()) {
            delay(1000)
            val records = metricBuffer.drain()
            if (records.isNotEmpty()) {
                val aggregated = aggregator.aggregate(records, activeVuCount.get())
                onMetricSnapshot(aggregated)

                val overall = aggregated.find { it.stepName == null }
                if (overall != null && overall.requestCount > 0) {
                    val errorRate = (overall.errorCount.toDouble() / overall.requestCount) * 100
                    if (errorRate > safetyConfig.abortOnErrorRate) {
                        abort()
                    }
                }
            }
        }
    }

    private fun buildInitialVariables(authConfig: AuthConfig, vuIndex: Int): Map<String, String> {
        if (authConfig.mode == AuthMode.TOKEN_POOL && authConfig.tokens.isNotEmpty()) {
            val token = authConfig.tokens[vuIndex % authConfig.tokens.size]
            return mapOf("token" to token)
        }
        return emptyMap()
    }
}
