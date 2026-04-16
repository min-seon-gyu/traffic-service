package com.traffic.engine

import com.traffic.domain.plan.*
import com.traffic.metric.MetricRecord
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StepExecutorTest {

    private val httpExecutor = mockk<HttpRequestExecutor>()
    private val variableResolver = VariableResolver()
    private val stepExecutor = StepExecutor(httpExecutor, variableResolver)

    @Test
    fun `execute step produces metric record`() {
        every { httpExecutor.execute(any()) } returns HttpResponseResult(
            statusCode = 200, body = """{"token":"abc"}""",
            headers = emptyMap(), latencyMs = 50
        )

        val step = createStep("login", HttpMethod.POST, "/api/login")
        val result = stepExecutor.execute(step, "https://api.test.com", emptyMap(), 10000, false)

        assertEquals(200, result.metricRecord.statusCode)
        assertEquals(50, result.metricRecord.latencyMs)
        assertTrue(result.metricRecord.success)
        assertEquals("login", result.metricRecord.stepName)
    }

    @Test
    fun `execute step extracts variables from response body`() {
        every { httpExecutor.execute(any()) } returns HttpResponseResult(
            statusCode = 200, body = """{"data":{"token":"jwt123"}}""",
            headers = emptyMap(), latencyMs = 30
        )

        val step = createStep("login", HttpMethod.POST, "/api/login",
            extractors = listOf(Extractor(ExtractorSource.BODY, "$.data.token", "token"))
        )
        val result = stepExecutor.execute(step, "https://api.test.com", emptyMap(), 10000, false)

        assertEquals("jwt123", result.extractedVariables["token"])
    }

    @Test
    fun `execute step resolves variables in path and headers`() {
        every { httpExecutor.execute(any()) } returns HttpResponseResult(
            statusCode = 200, body = "ok", headers = emptyMap(), latencyMs = 10
        )

        val step = createStep("get-item", HttpMethod.GET, "/api/items/{{id}}",
            headers = mapOf("Authorization" to "Bearer {{token}}")
        )
        val variables = mapOf("id" to "42", "token" to "jwt123")
        stepExecutor.execute(step, "https://api.test.com", variables, 10000, false)

        io.mockk.verify {
            httpExecutor.execute(match {
                it.path == "/api/items/42" &&
                it.headers["Authorization"] == "Bearer jwt123"
            })
        }
    }

    @Test
    fun `execute step validates status code`() {
        every { httpExecutor.execute(any()) } returns HttpResponseResult(
            statusCode = 500, body = "error", headers = emptyMap(), latencyMs = 10
        )

        val step = createStep("test", HttpMethod.GET, "/api/test",
            validators = listOf(StepValidator(ValidatorType.STATUS, "200"))
        )
        val result = stepExecutor.execute(step, "https://api.test.com", emptyMap(), 10000, false)

        assertFalse(result.metricRecord.success)
    }

    @Test
    fun `execute step validates body contains`() {
        every { httpExecutor.execute(any()) } returns HttpResponseResult(
            statusCode = 200, body = """{"status":"error"}""",
            headers = emptyMap(), latencyMs = 10
        )

        val step = createStep("test", HttpMethod.GET, "/api/test",
            validators = listOf(StepValidator(ValidatorType.BODY_CONTAINS, "success"))
        )
        val result = stepExecutor.execute(step, "https://api.test.com", emptyMap(), 10000, false)

        assertFalse(result.metricRecord.success)
    }

    private fun createStep(
        name: String,
        method: HttpMethod,
        path: String,
        headers: Map<String, String> = emptyMap(),
        extractors: List<Extractor> = emptyList(),
        validators: List<StepValidator> = emptyList()
    ): StepSpec {
        return StepSpec(
            name = name,
            httpMethod = method,
            path = path,
            headers = headers,
            body = null,
            thinkTimeMs = null,
            extractors = extractors,
            validators = validators
        )
    }
}
