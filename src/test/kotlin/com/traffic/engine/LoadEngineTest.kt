package com.traffic.engine

import com.traffic.domain.config.*
import com.traffic.domain.plan.HttpMethod
import com.traffic.metric.MetricBuffer
import com.traffic.metric.MetricRecord
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class LoadEngineTest {

    private val stepExecutor = mockk<StepExecutor>()
    private val metricBuffer = MetricBuffer()

    @Test
    fun `engine runs correct number of VUs and requests`() = runTest {
        val callCount = AtomicInteger(0)
        every { stepExecutor.execute(any(), any(), any(), any(), any()) } answers {
            callCount.incrementAndGet()
            StepResult(MetricRecord("step", 10, 200, true), emptyMap(), null)
        }

        val engine = LoadEngine(stepExecutor, metricBuffer)
        engine.run(
            steps = listOf(createStepSpec("step1")),
            baseUrl = "https://test.com",
            loadConfig = LoadConfig(vuCount = 3, requestsPerVu = 5),
            safetyConfig = SafetyConfig(),
            advancedConfig = AdvancedConfig(),
            authConfig = AuthConfig(),
            onMetricSnapshot = {}
        )

        assertEquals(15, callCount.get())
    }

    @Test
    fun `engine aborts when abort is called`() = runTest {
        val callCount = AtomicInteger(0)
        val engine = LoadEngine(stepExecutor, metricBuffer)

        every { stepExecutor.execute(any(), any(), any(), any(), any()) } answers {
            val count = callCount.incrementAndGet()
            if (count >= 10) engine.abort()
            StepResult(MetricRecord("step", 10, 500, false), emptyMap(), null)
        }

        engine.run(
            steps = listOf(createStepSpec("step1")),
            baseUrl = "https://test.com",
            loadConfig = LoadConfig(vuCount = 1, requestsPerVu = 1000),
            safetyConfig = SafetyConfig(abortOnErrorRate = 50),
            advancedConfig = AdvancedConfig(),
            authConfig = AuthConfig(),
            onMetricSnapshot = {}
        )

        assertTrue(callCount.get() < 1000, "Engine should have stopped early after abort, but ran ${callCount.get()} requests")
    }

    @Test
    fun `engine distributes tokens from pool to VUs`() = runTest {
        val receivedVariables = mutableListOf<Map<String, String>>()
        every { stepExecutor.execute(any(), any(), capture(receivedVariables), any(), any()) } returns
            StepResult(MetricRecord("step", 10, 200, true), emptyMap(), null)

        val engine = LoadEngine(stepExecutor, metricBuffer)
        engine.run(
            steps = listOf(createStepSpec("step1")),
            baseUrl = "https://test.com",
            loadConfig = LoadConfig(vuCount = 2, requestsPerVu = 1),
            safetyConfig = SafetyConfig(),
            advancedConfig = AdvancedConfig(),
            authConfig = AuthConfig(mode = AuthMode.TOKEN_POOL, tokens = listOf("tokenA", "tokenB")),
            onMetricSnapshot = {}
        )

        val tokens = receivedVariables.mapNotNull { it["token"] }.toSet()
        assertTrue(tokens.containsAll(setOf("tokenA", "tokenB")))
    }

    private fun createStepSpec(name: String) = StepSpec(
        name = name,
        httpMethod = HttpMethod.GET,
        path = "/api/$name",
        headers = emptyMap(),
        body = null,
        thinkTimeMs = null,
        extractors = emptyList(),
        validators = emptyList()
    )
}
