package com.traffic.domain.plan

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.persistence.*

@Entity
@Table(name = "test_steps")
class TestStep(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_plan_id", nullable = false)
    val testPlan: TestPlan,

    @Column(nullable = false)
    var orderIndex: Int,

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var httpMethod: HttpMethod = HttpMethod.GET,

    @Column(nullable = false)
    var path: String,

    @Column(columnDefinition = "TEXT")
    @Convert(converter = HeadersConverter::class)
    var headers: Map<String, String> = emptyMap(),

    @Column(columnDefinition = "TEXT")
    var body: String? = null,

    var thinkTimeMs: Int? = null,

    @Column(columnDefinition = "TEXT")
    @Convert(converter = ExtractorListConverter::class)
    var extractors: List<Extractor> = emptyList(),

    @Column(columnDefinition = "TEXT")
    @Convert(converter = ValidatorListConverter::class)
    var validators: List<StepValidator> = emptyList()
)

enum class HttpMethod {
    GET, POST, PUT, DELETE
}

private val stepMapper = jacksonObjectMapper()

@Converter(autoApply = false)
class HeadersConverter : AttributeConverter<Map<String, String>, String> {
    override fun convertToDatabaseColumn(attr: Map<String, String>?): String? = attr?.let { stepMapper.writeValueAsString(it) }
    override fun convertToEntityAttribute(data: String?): Map<String, String>? = data?.let {
        stepMapper.readValue(it, stepMapper.typeFactory.constructMapType(Map::class.java, String::class.java, String::class.java))
    }
}

@Converter(autoApply = false)
class ExtractorListConverter : AttributeConverter<List<Extractor>, String> {
    override fun convertToDatabaseColumn(attr: List<Extractor>?): String? = attr?.let { stepMapper.writeValueAsString(it) }
    override fun convertToEntityAttribute(data: String?): List<Extractor>? = data?.let {
        stepMapper.readValue(it, stepMapper.typeFactory.constructCollectionType(List::class.java, Extractor::class.java))
    }
}

@Converter(autoApply = false)
class ValidatorListConverter : AttributeConverter<List<StepValidator>, String> {
    override fun convertToDatabaseColumn(attr: List<StepValidator>?): String? = attr?.let { stepMapper.writeValueAsString(it) }
    override fun convertToEntityAttribute(data: String?): List<StepValidator>? = data?.let {
        stepMapper.readValue(it, stepMapper.typeFactory.constructCollectionType(List::class.java, StepValidator::class.java))
    }
}
