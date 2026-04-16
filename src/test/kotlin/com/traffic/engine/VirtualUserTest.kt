package com.traffic.engine

import com.traffic.domain.config.AdvancedConfig
import com.traffic.domain.config.ThinkTimeStrategy
import com.traffic.domain.plan.HttpMethod
import com.traffic.metric.MetricBuffer
import com.traffic.metric.MetricRecord
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class VirtualUserTest {

    private val stepExecutor = mockk<StepExecutor>()
    private val metricBuffer = MetricBuffer()

    @Test
    fun `VU executes all steps in order`() = runTest {
        val steps = listOf(
            createStepSpec("step1"),
            createStepSpec("step2")
        )

        every { stepExecutor.execute(any(), any(), any(), any(), any()) } returns StepResult(
            metricRecord = MetricRecord("step", 100, 200, true),
            extractedVariables = emptyMap(),
            responseBody = "ok"
        )

        val vu = VirtualUser(
            id = 1,
            steps = steps,
            baseUrl = "https://test.com",
            advancedConfig = AdvancedConfig(),
            stepExecutor = stepExecutor,
            metricBuffer = metricBuffer,
            initialVariables = emptyMap()
        )

        vu.executeIteration()

        val records = metricBuffer.drain()
        assertEquals(2, records.size)
    }

    @Test
    fun `VU passes extracted variables to subsequent steps`() = runTest {
        val steps = listOf(
            createStepSpec("login"),
            createStepSpec("action")
        )

        every { stepExecutor.execute(match { it.name == "login" }, any(), any(), any(), any()) } returns StepResult(
            metricRecord = MetricRecord("login", 100, 200, true),
            extractedVariables = mapOf("token" to "jwt123"),
            responseBody = null
        )
        every { stepExecutor.execute(match { it.name == "action" }, any(), any(), any(), any()) } returns StepResult(
            metricRecord = MetricRecord("action", 50, 200, true),
            extractedVariables = emptyMap(),
            responseBody = null
        )

        val vu = VirtualUser(
            id = 1,
            steps = steps,
            baseUrl = "https://test.com",
            advancedConfig = AdvancedConfig(),
            stepExecutor = stepExecutor,
            metricBuffer = metricBuffer,
            initialVariables = emptyMap()
        )

        vu.executeIteration()

        io.mockk.verify {
            stepExecutor.execute(match { it.name == "action" }, any(), match { it["token"] == "jwt123" }, any(), any())
        }
    }

    @Test
    fun `VU runs multiple iterations`() = runTest {
        val steps = listOf(createStepSpec("step1"))

        every { stepExecutor.execute(any(), any(), any(), any(), any()) } returns StepResult(
            metricRecord = MetricRecord("step1", 10, 200, true),
            extractedVariables = emptyMap(),
            responseBody = null
        )

        val vu = VirtualUser(
            id = 1,
            steps = steps,
            baseUrl = "https://test.com",
            advancedConfig = AdvancedConfig(),
            stepExecutor = stepExecutor,
            metricBuffer = metricBuffer,
            initialVariables = emptyMap()
        )

        vu.run(requestCount = 3)

        val records = metricBuffer.drain()
        assertEquals(3, records.size)
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
