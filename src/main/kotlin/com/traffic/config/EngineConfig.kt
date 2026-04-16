package com.traffic.config

import com.traffic.engine.HttpRequestExecutor
import com.traffic.engine.StepExecutor
import com.traffic.engine.VariableResolver
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class EngineConfig {

    @Bean
    fun variableResolver() = VariableResolver()

    @Bean
    fun httpRequestExecutor(webClient: WebClient) = HttpRequestExecutor(webClient)

    @Bean
    fun stepExecutor(httpRequestExecutor: HttpRequestExecutor, variableResolver: VariableResolver) =
        StepExecutor(httpRequestExecutor, variableResolver)
}
