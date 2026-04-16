package com.traffic.domain.plan

data class Extractor(
    val source: ExtractorSource = ExtractorSource.BODY,
    val jsonPath: String,
    val variableName: String
)

enum class ExtractorSource {
    BODY, HEADER
}
