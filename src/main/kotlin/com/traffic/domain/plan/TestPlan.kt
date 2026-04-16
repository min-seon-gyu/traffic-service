package com.traffic.domain.plan

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.traffic.domain.config.*
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "test_plans")
class TestPlan(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    var name: String,

    var description: String? = null,

    @Column(nullable = false)
    var targetBaseUrl: String,

    @Column(columnDefinition = "TEXT")
    @Convert(converter = LoadConfigConverter::class)
    var loadConfig: LoadConfig = LoadConfig(),

    @Column(columnDefinition = "TEXT")
    @Convert(converter = SafetyConfigConverter::class)
    var safetyConfig: SafetyConfig = SafetyConfig(),

    @Column(columnDefinition = "TEXT")
    @Convert(converter = ThresholdListConverter::class)
    var thresholds: List<Threshold> = emptyList(),

    @Column(columnDefinition = "TEXT")
    @Convert(converter = AuthConfigConverter::class)
    var authConfig: AuthConfig = AuthConfig(),

    @Column(columnDefinition = "TEXT")
    @Convert(converter = AdvancedConfigConverter::class)
    var advancedConfig: AdvancedConfig = AdvancedConfig(),

    @Column(columnDefinition = "TEXT")
    @Convert(converter = DataSourceConfigConverter::class)
    var dataSourceConfig: DataSourceConfig? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @OneToMany(mappedBy = "testPlan", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    val steps: MutableList<TestStep> = mutableListOf()
)

// JSON Converters
private val mapper = jacksonObjectMapper()

@Converter(autoApply = false)
class LoadConfigConverter : AttributeConverter<LoadConfig, String> {
    override fun convertToDatabaseColumn(attr: LoadConfig?): String? = attr?.let { mapper.writeValueAsString(it) }
    override fun convertToEntityAttribute(data: String?): LoadConfig? = data?.let { mapper.readValue(it, LoadConfig::class.java) }
}

@Converter(autoApply = false)
class SafetyConfigConverter : AttributeConverter<SafetyConfig, String> {
    override fun convertToDatabaseColumn(attr: SafetyConfig?): String? = attr?.let { mapper.writeValueAsString(it) }
    override fun convertToEntityAttribute(data: String?): SafetyConfig? = data?.let { mapper.readValue(it, SafetyConfig::class.java) }
}

@Converter(autoApply = false)
class ThresholdListConverter : AttributeConverter<List<Threshold>, String> {
    override fun convertToDatabaseColumn(attr: List<Threshold>?): String? = attr?.let { mapper.writeValueAsString(it) }
    override fun convertToEntityAttribute(data: String?): List<Threshold>? = data?.let {
        mapper.readValue(it, mapper.typeFactory.constructCollectionType(List::class.java, Threshold::class.java))
    }
}

@Converter(autoApply = false)
class AuthConfigConverter : AttributeConverter<AuthConfig, String> {
    override fun convertToDatabaseColumn(attr: AuthConfig?): String? = attr?.let { mapper.writeValueAsString(it) }
    override fun convertToEntityAttribute(data: String?): AuthConfig? = data?.let { mapper.readValue(it, AuthConfig::class.java) }
}

@Converter(autoApply = false)
class AdvancedConfigConverter : AttributeConverter<AdvancedConfig, String> {
    override fun convertToDatabaseColumn(attr: AdvancedConfig?): String? = attr?.let { mapper.writeValueAsString(it) }
    override fun convertToEntityAttribute(data: String?): AdvancedConfig? = data?.let { mapper.readValue(it, AdvancedConfig::class.java) }
}

@Converter(autoApply = false)
class DataSourceConfigConverter : AttributeConverter<DataSourceConfig?, String> {
    override fun convertToDatabaseColumn(attr: DataSourceConfig?): String? = attr?.let { mapper.writeValueAsString(it) }
    override fun convertToEntityAttribute(data: String?): DataSourceConfig? = data?.let {
        if (it.isBlank() || it == "null") null
        else mapper.readValue(it, DataSourceConfig::class.java)
    }
}
