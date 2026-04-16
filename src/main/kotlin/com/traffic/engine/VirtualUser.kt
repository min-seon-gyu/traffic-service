package com.traffic.engine

import com.traffic.domain.config.AdvancedConfig
import com.traffic.domain.config.ThinkTimeStrategy
import com.traffic.metric.MetricBuffer
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import kotlin.random.Random

class VirtualUser(
    val id: Int,
    private val steps: List<StepSpec>,
    private val baseUrl: String,
    private val advancedConfig: AdvancedConfig,
    private val stepExecutor: StepExecutor,
    private val metricBuffer: MetricBuffer,
    private val initialVariables: Map<String, String>
) {

    suspend fun run(requestCount: Int? = null, durationMs: Long? = null) {
        val startTime = System.currentTimeMillis()
        var iteration = 0

        while (true) {
            if (requestCount != null && iteration >= requestCount) break
            if (durationMs != null && System.currentTimeMillis() - startTime >= durationMs) break

            executeIteration()
            iteration++
            yield()
        }
    }

    suspend fun executeIteration() {
        val variables = initialVariables.toMutableMap()
        val iterationStart = System.currentTimeMillis()

        for (step in steps) {
            val result = stepExecutor.execute(
                step = step,
                baseUrl = baseUrl,
                variables = variables,
                timeoutMs = advancedConfig.timeoutMs,
                discardBody = advancedConfig.discardResponseBody
            )

            metricBuffer.add(result.metricRecord)
            variables.putAll(result.extractedVariables)

            applyThinkTime(step, iterationStart)
        }
    }

    private suspend fun applyThinkTime(step: StepSpec, iterationStart: Long) {
        val stepThinkTime = step.thinkTimeMs
        if (stepThinkTime != null) {
            delay(stepThinkTime.toLong())
            return
        }

        when (advancedConfig.thinkTimeStrategy) {
            ThinkTimeStrategy.CONSTANT -> {
                if (advancedConfig.thinkTimeMs > 0) delay(advancedConfig.thinkTimeMs.toLong())
            }
            ThinkTimeStrategy.RANDOM -> {
                val waitMs = Random.nextInt(advancedConfig.thinkTimeMin, advancedConfig.thinkTimeMax + 1)
                if (waitMs > 0) delay(waitMs.toLong())
            }
            ThinkTimeStrategy.PACING -> {
                val elapsed = System.currentTimeMillis() - iterationStart
                val remaining = advancedConfig.thinkTimeMs - elapsed
                if (remaining > 0) delay(remaining)
            }
        }
    }
}
