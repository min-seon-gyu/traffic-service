package com.traffic.engine

import com.traffic.domain.plan.HttpMethod
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.time.Duration

data class HttpRequestSpec(
    val baseUrl: String,
    val path: String,
    val method: HttpMethod,
    val headers: Map<String, String>,
    val body: String?,
    val timeoutMs: Int
)

data class HttpResponseResult(
    val statusCode: Int,
    val body: String?,
    val headers: Map<String, String>,
    val latencyMs: Long
)

class HttpRequestExecutor(private val webClient: WebClient) {

    fun execute(spec: HttpRequestSpec): HttpResponseResult {
        val startTime = System.currentTimeMillis()

        val requestSpec = webClient
            .method(spec.method.toSpringMethod())
            .uri(spec.baseUrl + spec.path)

        spec.headers.forEach { (key, value) ->
            requestSpec.header(key, value)
        }

        if (spec.body != null) {
            requestSpec.bodyValue(spec.body)
        }

        return try {
            val response = requestSpec
                .exchangeToMono { clientResponse ->
                    clientResponse.bodyToMono<String>().defaultIfEmpty("").map { body ->
                        HttpResponseResult(
                            statusCode = clientResponse.statusCode().value(),
                            body = body,
                            headers = clientResponse.headers().asHttpHeaders().toSingleValueMap(),
                            latencyMs = System.currentTimeMillis() - startTime
                        )
                    }
                }
                .block(Duration.ofMillis(spec.timeoutMs.toLong()))!!

            response
        } catch (e: Exception) {
            HttpResponseResult(
                statusCode = 0,
                body = e.message,
                headers = emptyMap(),
                latencyMs = System.currentTimeMillis() - startTime
            )
        }
    }

    private fun HttpMethod.toSpringMethod(): org.springframework.http.HttpMethod = when (this) {
        HttpMethod.GET -> org.springframework.http.HttpMethod.GET
        HttpMethod.POST -> org.springframework.http.HttpMethod.POST
        HttpMethod.PUT -> org.springframework.http.HttpMethod.PUT
        HttpMethod.DELETE -> org.springframework.http.HttpMethod.DELETE
    }
}
