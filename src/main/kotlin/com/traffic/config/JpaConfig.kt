package com.traffic.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JpaConfig {
    @Bean
    fun objectMapper(): ObjectMapper = jacksonObjectMapper()
}

@Converter
class JsonConverter<T>(private val clazz: Class<T>) : AttributeConverter<T, String> {
    private val mapper = jacksonObjectMapper()

    override fun convertToDatabaseColumn(attribute: T?): String? =
        attribute?.let { mapper.writeValueAsString(it) }

    override fun convertToEntityAttribute(dbData: String?): T? =
        dbData?.let { mapper.readValue(it, clazz) }
}
