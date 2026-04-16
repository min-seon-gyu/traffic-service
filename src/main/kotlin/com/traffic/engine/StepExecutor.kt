package com.traffic.engine

import com.jayway.jsonpath.JsonPath
import com.traffic.domain.plan.*
import com.traffic.metric.MetricRecord

data class StepSpec(
    val name: String,
    val httpMethod: HttpMethod,
    val path: String,
    val headers: Map<String, String>,
    val body: String?,
    val thinkTimeMs: Int?,
    val extractors: List<Extractor>,
    val validators: List<StepValidator>
)

data class StepResult(
    val metricRecord: MetricRecord,
    val extractedVariables: Map<String, String>,
    val responseBody: String?
)

class StepExecutor(
    private val httpExecutor: HttpRequestExecutor,
    private val variableResolver: VariableResolver
) {

    fun execute(
        step: StepSpec,
        baseUrl: String,
        variables: Map<String, String>,
        timeoutMs: Int,
        discardBody: Boolean
    ): StepResult {
        val resolvedPath = variableResolver.resolve(step.path, variables)!!
        val resolvedHeaders = variableResolver.resolveMap(step.headers, variables)
        val resolvedBody = variableResolver.resolve(step.body, variables)

        val spec = HttpRequestSpec(
            baseUrl = baseUrl,
            path = resolvedPath,
            method = step.httpMethod,
            headers = resolvedHeaders,
            body = resolvedBody,
            timeoutMs = timeoutMs
        )

        val response = httpExecutor.execute(spec)

        val validationPassed = validate(step.validators, response)
        val extracted = extract(step.extractors, response)

        val record = MetricRecord(
            stepName = step.name,
            latencyMs = response.latencyMs,
            statusCode = response.statusCode,
            success = response.statusCode in 200..399 && validationPassed
        )

        return StepResult(
            metricRecord = record,
            extractedVariables = extracted,
            responseBody = if (discardBody) null else response.body
        )
    }

    private fun validate(validators: List<StepValidator>, response: HttpResponseResult): Boolean {
        return validators.all { validator ->
            when (validator.type) {
                ValidatorType.STATUS -> response.statusCode.toString() == validator.expected
                ValidatorType.BODY_CONTAINS -> response.body?.contains(validator.expected) == true
            }
        }
    }

    private fun extract(extractors: List<Extractor>, response: HttpResponseResult): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (extractor in extractors) {
            try {
                when (extractor.source) {
                    ExtractorSource.BODY -> {
                        val value = JsonPath.read<Any>(response.body, extractor.jsonPath)
                        result[extractor.variableName] = value.toString()
                    }
                    ExtractorSource.HEADER -> {
                        response.headers[extractor.jsonPath]?.let {
                            result[extractor.variableName] = it
                        }
                    }
                }
            } catch (_: Exception) {
                // extraction failure is non-fatal
            }
        }
        return result
    }
}
