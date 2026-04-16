# Traffic Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 운영 서비스에 HTTP 부하를 생성하는 웹 기반 부하 테스트 서비스를 구축한다.

**Architecture:** Kotlin + Spring Boot 4.x 단일 애플리케이션. Kotlin Coroutines로 가상 유저(VU)를 모델링하고 WebClient로 논블로킹 HTTP 부하를 생성한다. Thymeleaf SSR UI에서 테스트 계획을 관리하고, SSE로 실시간 메트릭을 스트리밍한다.

**Tech Stack:** Kotlin, Spring Boot 4.x, Spring WebFlux (WebClient), Spring Data JPA, MySQL, Thymeleaf, SSE, Gradle (Kotlin DSL)

---

## File Structure

```
traffic-service/
├── build.gradle.kts
├── settings.gradle.kts
├── src/main/kotlin/com/traffic/
│   ├── TrafficServiceApplication.kt
│   ├── domain/
│   │   ├── config/
│   │   │   ├── LoadConfig.kt          # 부하 설정 VO (vuCount, stages 등)
│   │   │   ├── SafetyConfig.kt        # 안전 설정 VO (abortOnErrorRate 등)
│   │   │   ├── AuthConfig.kt          # 인증 설정 VO (mode, tokens)
│   │   │   ├── AdvancedConfig.kt      # 부가 설정 VO (thinkTime, timeout 등)
│   │   │   ├── DataSourceConfig.kt    # 데이터 소스 설정 VO
│   │   │   ├── Threshold.kt           # 임계치 VO
│   │   │   └── Stage.kt               # 단계별 부하 프로필 VO
│   │   ├── plan/
│   │   │   ├── TestPlan.kt            # JPA 엔티티
│   │   │   ├── TestStep.kt            # JPA 엔티티
│   │   │   ├── Extractor.kt           # 변수 추출 규칙 VO
│   │   │   ├── Validator.kt           # 응답 검증 규칙 VO
│   │   │   ├── TestPlanRepository.kt
│   │   │   └── TestStepRepository.kt
│   │   └── execution/
│   │       ├── TestExecution.kt        # JPA 엔티티
│   │       ├── MetricSnapshot.kt       # JPA 엔티티
│   │       ├── TestExecutionRepository.kt
│   │       └── MetricSnapshotRepository.kt
│   ├── engine/
│   │   ├── LoadEngine.kt              # 부하 엔진 (VU 생명주기, ramp-up, stages)
│   │   ├── VirtualUser.kt             # 코루틴 기반 가상 유저
│   │   ├── StepExecutor.kt            # 단일 Step 실행 (HTTP + extract + validate)
│   │   ├── HttpRequestExecutor.kt     # WebClient 기반 HTTP 요청 실행
│   │   └── VariableResolver.kt        # {{변수}} 치환 엔진
│   ├── metric/
│   │   ├── MetricRecord.kt            # 요청 결과 레코드
│   │   ├── MetricBuffer.kt            # ConcurrentLinkedQueue 기반 버퍼
│   │   └── MetricAggregator.kt        # 1초 주기 집계
│   ├── feeder/
│   │   ├── DataFeeder.kt              # 데이터 피더 인터페이스
│   │   ├── CsvDataFeeder.kt           # CSV 파일 피더
│   │   └── JsonDataFeeder.kt          # JSON 파일 피더
│   ├── service/
│   │   ├── TestPlanService.kt         # TestPlan CRUD + 복제
│   │   ├── ExecutionService.kt        # 테스트 실행 오케스트레이션
│   │   └── MetricStreamService.kt     # SSE 스트리밍
│   ├── web/
│   │   ├── PlanController.kt          # /plans/** 라우트
│   │   ├── StepController.kt          # /plans/{id}/steps 라우트
│   │   ├── ExecutionController.kt     # /executions/** 라우트
│   │   └── DashboardController.kt     # /executions/{id}/live 라우트 + SSE
│   └── config/
│       └── WebClientConfig.kt         # WebClient 빈 설정
├── src/main/resources/
│   ├── application.yml
│   └── templates/
│       ├── layout.html                # 공통 레이아웃
│       ├── plans/
│       │   ├── list.html              # 테스트 계획 목록
│       │   ├── form.html              # 생성/수정 폼
│       │   └── steps.html             # Step 편집
│       └── executions/
│           ├── list.html              # 실행 이력 목록
│           ├── detail.html            # 결과 Summary
│           └── live.html              # 실시간 대시보드
└── src/test/kotlin/com/traffic/
    ├── engine/
    │   ├── VariableResolverTest.kt
    │   ├── StepExecutorTest.kt
    │   ├── VirtualUserTest.kt
    │   └── LoadEngineTest.kt
    ├── metric/
    │   ├── MetricBufferTest.kt
    │   └── MetricAggregatorTest.kt
    ├── feeder/
    │   ├── CsvDataFeederTest.kt
    │   └── JsonDataFeederTest.kt
    ├── service/
    │   ├── TestPlanServiceTest.kt
    │   └── ExecutionServiceTest.kt
    └── web/
        ├── PlanControllerTest.kt
        └── ExecutionControllerTest.kt
```

---

## Task 1: Project Scaffolding

**Files:**
- Create: `build.gradle.kts`
- Create: `settings.gradle.kts`
- Create: `src/main/kotlin/com/traffic/TrafficServiceApplication.kt`
- Create: `src/main/resources/application.yml`
- Create: `gradle/wrapper/gradle-wrapper.properties`

- [ ] **Step 1: Generate Gradle wrapper**

```bash
gradle wrapper --gradle-version 8.13
```

- [ ] **Step 2: Create settings.gradle.kts**

```kotlin
rootProject.name = "traffic-service"
```

- [ ] **Step 3: Create build.gradle.kts**

```kotlin
plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.spring") version "2.1.20"
    kotlin("plugin.jpa") version "2.1.20"
    id("org.springframework.boot") version "4.0.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.traffic"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")

    // Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // JSONPath
    implementation("com.jayway.jsonpath:json-path:2.9.0")

    // Database
    runtimeOnly("com.mysql:mysql-connector-j")
    runtimeOnly("com.h2database:h2")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("io.mockk:mockk:1.13.16")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

- [ ] **Step 4: Create application.yml**

```yaml
spring:
  application:
    name: traffic-service
  datasource:
    url: jdbc:h2:mem:trafficdb;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
    properties:
      hibernate:
        format_sql: true
  h2:
    console:
      enabled: true

server:
  port: 8080
```

참고: 개발 편의를 위해 H2 인메모리로 시작하고, MySQL 전환은 프로필로 분리한다.

- [ ] **Step 5: Create TrafficServiceApplication.kt**

```kotlin
package com.traffic

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TrafficServiceApplication

fun main(args: Array<String>) {
    runApplication<TrafficServiceApplication>(*args)
}
```

- [ ] **Step 6: Verify build**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git init
git add .
git commit -m "chore: initialize Spring Boot 4 project with Kotlin and Gradle"
```

---

## Task 2: Config Value Objects + Enums

**Files:**
- Create: `src/main/kotlin/com/traffic/domain/config/LoadConfig.kt`
- Create: `src/main/kotlin/com/traffic/domain/config/SafetyConfig.kt`
- Create: `src/main/kotlin/com/traffic/domain/config/AuthConfig.kt`
- Create: `src/main/kotlin/com/traffic/domain/config/AdvancedConfig.kt`
- Create: `src/main/kotlin/com/traffic/domain/config/DataSourceConfig.kt`
- Create: `src/main/kotlin/com/traffic/domain/config/Threshold.kt`
- Create: `src/main/kotlin/com/traffic/domain/config/Stage.kt`
- Create: `src/main/kotlin/com/traffic/domain/plan/Extractor.kt`
- Create: `src/main/kotlin/com/traffic/domain/plan/Validator.kt`

- [ ] **Step 1: Create Stage.kt**

```kotlin
package com.traffic.domain.config

data class Stage(
    val durationSeconds: Int,
    val targetVu: Int
)
```

- [ ] **Step 2: Create Threshold.kt**

```kotlin
package com.traffic.domain.config

data class Threshold(
    val metric: ThresholdMetric,
    val operator: ThresholdOperator,
    val value: Double
) {
    fun evaluate(actualValue: Double): Boolean = when (operator) {
        ThresholdOperator.LT -> actualValue < value
        ThresholdOperator.LTE -> actualValue <= value
        ThresholdOperator.GT -> actualValue > value
        ThresholdOperator.GTE -> actualValue >= value
    }
}

enum class ThresholdMetric {
    AVG, P50, P90, P95, P99, ERROR_RATE, TPS
}

enum class ThresholdOperator {
    LT, LTE, GT, GTE
}
```

- [ ] **Step 3: Create LoadConfig.kt**

```kotlin
package com.traffic.domain.config

data class LoadConfig(
    val vuCount: Int = 1,
    val requestsPerVu: Int? = null,
    val rampUpSeconds: Int = 0,
    val durationSeconds: Int? = null,
    val stages: List<Stage> = emptyList(),
    val maxDuration: Int = 600
) {
    fun isStagesMode(): Boolean = stages.isNotEmpty()
}
```

- [ ] **Step 4: Create SafetyConfig.kt**

```kotlin
package com.traffic.domain.config

data class SafetyConfig(
    val abortOnErrorRate: Int = 50,
    val gracefulStopTimeout: Int = 30
)
```

- [ ] **Step 5: Create AuthConfig.kt**

```kotlin
package com.traffic.domain.config

data class AuthConfig(
    val mode: AuthMode = AuthMode.LOGIN_STEP,
    val tokens: List<String> = emptyList()
)

enum class AuthMode {
    LOGIN_STEP, TOKEN_POOL
}
```

- [ ] **Step 6: Create AdvancedConfig.kt**

```kotlin
package com.traffic.domain.config

data class AdvancedConfig(
    val thinkTimeStrategy: ThinkTimeStrategy = ThinkTimeStrategy.CONSTANT,
    val thinkTimeMs: Int = 0,
    val thinkTimeMin: Int = 0,
    val thinkTimeMax: Int = 0,
    val timeoutMs: Int = 10000,
    val discardResponseBody: Boolean = false
)

enum class ThinkTimeStrategy {
    CONSTANT, RANDOM, PACING
}
```

- [ ] **Step 7: Create DataSourceConfig.kt**

```kotlin
package com.traffic.domain.config

data class DataSourceConfig(
    val filePath: String,
    val distribution: DataDistribution = DataDistribution.SEQUENTIAL,
    val onEof: EofStrategy = EofStrategy.RECYCLE
)

enum class DataDistribution {
    SEQUENTIAL, RANDOM, CIRCULAR
}

enum class EofStrategy {
    RECYCLE, STOP_VU
}
```

- [ ] **Step 8: Create Extractor.kt**

```kotlin
package com.traffic.domain.plan

data class Extractor(
    val source: ExtractorSource = ExtractorSource.BODY,
    val jsonPath: String,
    val variableName: String
)

enum class ExtractorSource {
    BODY, HEADER
}
```

- [ ] **Step 9: Create Validator.kt**

```kotlin
package com.traffic.domain.plan

data class StepValidator(
    val type: ValidatorType,
    val expected: String
)

enum class ValidatorType {
    STATUS, BODY_CONTAINS
}
```

- [ ] **Step 10: Build 확인**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 11: Commit**

```bash
git add src/main/kotlin/com/traffic/domain/config/ src/main/kotlin/com/traffic/domain/plan/Extractor.kt src/main/kotlin/com/traffic/domain/plan/Validator.kt
git commit -m "feat: add config value objects and enums"
```

---

## Task 3: JPA Entities + Repositories

**Files:**
- Create: `src/main/kotlin/com/traffic/domain/plan/TestPlan.kt`
- Create: `src/main/kotlin/com/traffic/domain/plan/TestStep.kt`
- Create: `src/main/kotlin/com/traffic/domain/plan/TestPlanRepository.kt`
- Create: `src/main/kotlin/com/traffic/domain/plan/TestStepRepository.kt`
- Create: `src/main/kotlin/com/traffic/domain/execution/TestExecution.kt`
- Create: `src/main/kotlin/com/traffic/domain/execution/MetricSnapshot.kt`
- Create: `src/main/kotlin/com/traffic/domain/execution/TestExecutionRepository.kt`
- Create: `src/main/kotlin/com/traffic/domain/execution/MetricSnapshotRepository.kt`
- Create: `src/main/kotlin/com/traffic/config/JpaConfig.kt`

- [ ] **Step 1: Create JpaConfig.kt with JSON converter**

```kotlin
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
```

- [ ] **Step 2: Create TestPlan.kt**

```kotlin
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

    @Column(columnDefinition = "JSON")
    @Convert(converter = LoadConfigConverter::class)
    var loadConfig: LoadConfig = LoadConfig(),

    @Column(columnDefinition = "JSON")
    @Convert(converter = SafetyConfigConverter::class)
    var safetyConfig: SafetyConfig = SafetyConfig(),

    @Column(columnDefinition = "JSON")
    @Convert(converter = ThresholdListConverter::class)
    var thresholds: List<Threshold> = emptyList(),

    @Column(columnDefinition = "JSON")
    @Convert(converter = AuthConfigConverter::class)
    var authConfig: AuthConfig = AuthConfig(),

    @Column(columnDefinition = "JSON")
    @Convert(converter = AdvancedConfigConverter::class)
    var advancedConfig: AdvancedConfig = AdvancedConfig(),

    @Column(columnDefinition = "JSON")
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
```

- [ ] **Step 3: Create TestStep.kt**

```kotlin
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

    @Column(columnDefinition = "JSON")
    @Convert(converter = HeadersConverter::class)
    var headers: Map<String, String> = emptyMap(),

    @Column(columnDefinition = "TEXT")
    var body: String? = null,

    var thinkTimeMs: Int? = null,

    @Column(columnDefinition = "JSON")
    @Convert(converter = ExtractorListConverter::class)
    var extractors: List<Extractor> = emptyList(),

    @Column(columnDefinition = "JSON")
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
```

- [ ] **Step 4: Create TestExecution.kt**

```kotlin
package com.traffic.domain.execution

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "test_executions")
class TestExecution(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    val testPlanId: Long,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var status: ExecutionStatus = ExecutionStatus.RUNNING,

    @Enumerated(EnumType.STRING)
    var result: ExecutionResult? = null,

    @Column(nullable = false)
    val startedAt: LocalDateTime = LocalDateTime.now(),

    var finishedAt: LocalDateTime? = null,

    var totalRequests: Long = 0,
    var successCount: Long = 0,
    var failCount: Long = 0,
    var avgResponseTime: Double = 0.0,
    var p50: Double = 0.0,
    var p90: Double = 0.0,
    var p95: Double = 0.0,
    var p99: Double = 0.0,
    var tps: Double = 0.0,
    var errorRate: Double = 0.0
)

enum class ExecutionStatus {
    RUNNING, COMPLETED, ABORTED
}

enum class ExecutionResult {
    PASS, FAIL
}
```

- [ ] **Step 5: Create MetricSnapshot.kt**

```kotlin
package com.traffic.domain.execution

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "metric_snapshots")
class MetricSnapshot(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    val executionId: Long,

    val stepName: String? = null,

    @Column(nullable = false)
    val timestamp: LocalDateTime = LocalDateTime.now(),

    val currentVu: Int = 0,
    val requestCount: Int = 0,
    val errorCount: Int = 0,
    val avgResponseTime: Double = 0.0,
    val p95ResponseTime: Double = 0.0,
    val tps: Double = 0.0
)
```

- [ ] **Step 6: Create Repositories**

TestPlanRepository.kt:
```kotlin
package com.traffic.domain.plan

import org.springframework.data.jpa.repository.JpaRepository

interface TestPlanRepository : JpaRepository<TestPlan, Long>
```

TestStepRepository.kt:
```kotlin
package com.traffic.domain.plan

import org.springframework.data.jpa.repository.JpaRepository

interface TestStepRepository : JpaRepository<TestStep, Long> {
    fun findByTestPlanIdOrderByOrderIndexAsc(testPlanId: Long): List<TestStep>
    fun deleteByTestPlanId(testPlanId: Long)
}
```

TestExecutionRepository.kt:
```kotlin
package com.traffic.domain.execution

import org.springframework.data.jpa.repository.JpaRepository

interface TestExecutionRepository : JpaRepository<TestExecution, Long> {
    fun findByTestPlanIdOrderByStartedAtDesc(testPlanId: Long): List<TestExecution>
    fun findAllByOrderByStartedAtDesc(): List<TestExecution>
}
```

MetricSnapshotRepository.kt:
```kotlin
package com.traffic.domain.execution

import org.springframework.data.jpa.repository.JpaRepository

interface MetricSnapshotRepository : JpaRepository<MetricSnapshot, Long> {
    fun findByExecutionIdAndStepNameIsNullOrderByTimestampAsc(executionId: Long): List<MetricSnapshot>
    fun findByExecutionIdAndStepNameOrderByTimestampAsc(executionId: Long, stepName: String): List<MetricSnapshot>
}
```

- [ ] **Step 7: Build 확인**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/com/traffic/domain/ src/main/kotlin/com/traffic/config/JpaConfig.kt
git commit -m "feat: add JPA entities and repositories for TestPlan, TestStep, TestExecution, MetricSnapshot"
```

---

## Task 4: Variable Resolver (TDD)

**Files:**
- Create: `src/main/kotlin/com/traffic/engine/VariableResolver.kt`
- Create: `src/test/kotlin/com/traffic/engine/VariableResolverTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.traffic.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VariableResolverTest {

    private val resolver = VariableResolver()

    @Test
    fun `resolve replaces single variable`() {
        val variables = mapOf("token" to "abc123")
        val result = resolver.resolve("Bearer {{token}}", variables)
        assertEquals("Bearer abc123", result)
    }

    @Test
    fun `resolve replaces multiple variables`() {
        val variables = mapOf("host" to "api.example.com", "id" to "42")
        val result = resolver.resolve("https://{{host}}/items/{{id}}", variables)
        assertEquals("https://api.example.com/items/42", result)
    }

    @Test
    fun `resolve leaves unknown variables unchanged`() {
        val variables = mapOf("token" to "abc")
        val result = resolver.resolve("{{token}} and {{unknown}}", variables)
        assertEquals("abc and {{unknown}}", result)
    }

    @Test
    fun `resolve handles empty variables map`() {
        val result = resolver.resolve("no variables here", emptyMap())
        assertEquals("no variables here", result)
    }

    @Test
    fun `resolve handles null input`() {
        val result = resolver.resolve(null, mapOf("a" to "b"))
        assertEquals(null, result)
    }

    @Test
    fun `resolveMap replaces variables in map values`() {
        val variables = mapOf("token" to "abc123")
        val headers = mapOf("Authorization" to "Bearer {{token}}", "Content-Type" to "application/json")
        val result = resolver.resolveMap(headers, variables)
        assertEquals(mapOf("Authorization" to "Bearer abc123", "Content-Type" to "application/json"), result)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests "com.traffic.engine.VariableResolverTest" -v
```

Expected: FAIL (class not found)

- [ ] **Step 3: Implement VariableResolver**

```kotlin
package com.traffic.engine

class VariableResolver {

    private val pattern = Regex("""\{\{(\w+)}}""")

    fun resolve(template: String?, variables: Map<String, String>): String? {
        if (template == null) return null
        return pattern.replace(template) { match ->
            val varName = match.groupValues[1]
            variables[varName] ?: match.value
        }
    }

    fun resolveMap(templates: Map<String, String>, variables: Map<String, String>): Map<String, String> =
        templates.mapValues { (_, value) -> resolve(value, variables) ?: value }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "com.traffic.engine.VariableResolverTest" -v
```

Expected: All 6 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/traffic/engine/VariableResolver.kt src/test/kotlin/com/traffic/engine/VariableResolverTest.kt
git commit -m "feat: add VariableResolver for {{variable}} template substitution"
```

---

## Task 5: Metric Buffer + Record (TDD)

**Files:**
- Create: `src/main/kotlin/com/traffic/metric/MetricRecord.kt`
- Create: `src/main/kotlin/com/traffic/metric/MetricBuffer.kt`
- Create: `src/test/kotlin/com/traffic/metric/MetricBufferTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.traffic.metric

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MetricBufferTest {

    @Test
    fun `add and drain returns all records`() {
        val buffer = MetricBuffer()
        buffer.add(MetricRecord(stepName = "login", latencyMs = 100, statusCode = 200, success = true))
        buffer.add(MetricRecord(stepName = "login", latencyMs = 200, statusCode = 200, success = true))
        buffer.add(MetricRecord(stepName = "search", latencyMs = 150, statusCode = 200, success = true))

        val records = buffer.drain()
        assertEquals(3, records.size)
    }

    @Test
    fun `drain clears the buffer`() {
        val buffer = MetricBuffer()
        buffer.add(MetricRecord(stepName = "login", latencyMs = 100, statusCode = 200, success = true))

        buffer.drain()
        val second = buffer.drain()
        assertTrue(second.isEmpty())
    }

    @Test
    fun `drain on empty buffer returns empty list`() {
        val buffer = MetricBuffer()
        assertTrue(buffer.drain().isEmpty())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests "com.traffic.metric.MetricBufferTest" -v
```

Expected: FAIL

- [ ] **Step 3: Create MetricRecord.kt**

```kotlin
package com.traffic.metric

data class MetricRecord(
    val stepName: String,
    val latencyMs: Long,
    val statusCode: Int,
    val success: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
```

- [ ] **Step 4: Create MetricBuffer.kt**

```kotlin
package com.traffic.metric

import java.util.concurrent.ConcurrentLinkedQueue

class MetricBuffer {

    private val queue = ConcurrentLinkedQueue<MetricRecord>()

    fun add(record: MetricRecord) {
        queue.add(record)
    }

    fun drain(): List<MetricRecord> {
        val records = mutableListOf<MetricRecord>()
        while (true) {
            val record = queue.poll() ?: break
            records.add(record)
        }
        return records
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
./gradlew test --tests "com.traffic.metric.MetricBufferTest" -v
```

Expected: All 3 tests PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/traffic/metric/ src/test/kotlin/com/traffic/metric/
git commit -m "feat: add MetricBuffer and MetricRecord for request metric collection"
```

---

## Task 6: Metric Aggregator (TDD)

**Files:**
- Create: `src/main/kotlin/com/traffic/metric/MetricAggregator.kt`
- Create: `src/test/kotlin/com/traffic/metric/MetricAggregatorTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.traffic.metric

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MetricAggregatorTest {

    private val aggregator = MetricAggregator()

    @Test
    fun `aggregate empty list returns empty result`() {
        val result = aggregator.aggregate(emptyList(), currentVu = 0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `aggregate produces overall snapshot with null stepName`() {
        val records = listOf(
            MetricRecord("login", 100, 200, true),
            MetricRecord("login", 200, 200, true),
            MetricRecord("search", 150, 200, true)
        )
        val result = aggregator.aggregate(records, currentVu = 5)
        val overall = result.find { it.stepName == null }

        assertNotNull(overall)
        assertEquals(3, overall!!.requestCount)
        assertEquals(0, overall.errorCount)
        assertEquals(5, overall.currentVu)
        assertEquals(150.0, overall.avgResponseTime, 0.1)
    }

    @Test
    fun `aggregate produces per-step snapshots`() {
        val records = listOf(
            MetricRecord("login", 100, 200, true),
            MetricRecord("login", 200, 500, false),
            MetricRecord("search", 150, 200, true)
        )
        val result = aggregator.aggregate(records, currentVu = 3)

        val login = result.find { it.stepName == "login" }
        assertNotNull(login)
        assertEquals(2, login!!.requestCount)
        assertEquals(1, login.errorCount)
        assertEquals(150.0, login.avgResponseTime, 0.1)

        val search = result.find { it.stepName == "search" }
        assertNotNull(search)
        assertEquals(1, search!!.requestCount)
        assertEquals(0, search.errorCount)
    }

    @Test
    fun `aggregate calculates p95 correctly`() {
        val records = (1..100).map { i ->
            MetricRecord("step", i.toLong(), 200, true)
        }
        val result = aggregator.aggregate(records, currentVu = 1)
        val overall = result.find { it.stepName == null }!!

        assertEquals(95.0, overall.p95ResponseTime, 1.0)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests "com.traffic.metric.MetricAggregatorTest" -v
```

Expected: FAIL

- [ ] **Step 3: Implement MetricAggregator**

```kotlin
package com.traffic.metric

data class AggregatedMetric(
    val stepName: String?,
    val currentVu: Int,
    val requestCount: Int,
    val errorCount: Int,
    val avgResponseTime: Double,
    val p95ResponseTime: Double,
    val tps: Double
)

class MetricAggregator {

    fun aggregate(records: List<MetricRecord>, currentVu: Int): List<AggregatedMetric> {
        if (records.isEmpty()) return emptyList()

        val result = mutableListOf<AggregatedMetric>()

        // Overall aggregate
        result.add(computeMetric(null, records, currentVu))

        // Per-step aggregates
        records.groupBy { it.stepName }.forEach { (stepName, stepRecords) ->
            result.add(computeMetric(stepName, stepRecords, currentVu))
        }

        return result
    }

    private fun computeMetric(stepName: String?, records: List<MetricRecord>, currentVu: Int): AggregatedMetric {
        val latencies = records.map { it.latencyMs }
        val sorted = latencies.sorted()
        val errorCount = records.count { !it.success }

        return AggregatedMetric(
            stepName = stepName,
            currentVu = currentVu,
            requestCount = records.size,
            errorCount = errorCount,
            avgResponseTime = latencies.average(),
            p95ResponseTime = percentile(sorted, 95.0),
            tps = records.size.toDouble()
        )
    }

    private fun percentile(sortedValues: List<Long>, percentile: Double): Double {
        if (sortedValues.isEmpty()) return 0.0
        val index = (percentile / 100.0 * (sortedValues.size - 1)).toInt()
        return sortedValues[index].toDouble()
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "com.traffic.metric.MetricAggregatorTest" -v
```

Expected: All 4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/traffic/metric/MetricAggregator.kt src/test/kotlin/com/traffic/metric/MetricAggregatorTest.kt
git commit -m "feat: add MetricAggregator for overall and per-step metric aggregation"
```

---

## Task 7: WebClient Config + HTTP Request Executor (TDD)

**Files:**
- Create: `src/main/kotlin/com/traffic/config/WebClientConfig.kt`
- Create: `src/main/kotlin/com/traffic/engine/HttpRequestExecutor.kt`
- Create: `src/test/kotlin/com/traffic/engine/HttpRequestExecutorTest.kt`

- [ ] **Step 1: Create WebClientConfig.kt**

```kotlin
package com.traffic.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfig {

    @Bean
    fun webClient(): WebClient = WebClient.builder()
        .codecs { it.defaultCodecs().maxInMemorySize(2 * 1024 * 1024) }
        .build()
}
```

- [ ] **Step 2: Write failing tests**

```kotlin
package com.traffic.engine

import com.traffic.domain.plan.HttpMethod
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

class HttpRequestExecutorTest {

    private val webClient = WebClient.builder().build()
    private val executor = HttpRequestExecutor(webClient)

    @Test
    fun `execute returns response with status code and latency`() {
        // httpbin.org 대신 로컬에서 테스트하기 어려우므로, 구조만 검증
        val request = HttpRequestSpec(
            baseUrl = "https://httpbin.org",
            path = "/get",
            method = HttpMethod.GET,
            headers = mapOf("Accept" to "application/json"),
            body = null,
            timeoutMs = 10000
        )

        val response = executor.execute(request)
        assertTrue(response.statusCode in 100..599)
        assertTrue(response.latencyMs >= 0)
    }

    @Test
    fun `execute captures response body`() {
        val request = HttpRequestSpec(
            baseUrl = "https://httpbin.org",
            path = "/get",
            method = HttpMethod.GET,
            headers = emptyMap(),
            body = null,
            timeoutMs = 10000
        )

        val response = executor.execute(request)
        assertNotNull(response.body)
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

```bash
./gradlew test --tests "com.traffic.engine.HttpRequestExecutorTest" -v
```

Expected: FAIL (class not found)

- [ ] **Step 4: Implement HttpRequestExecutor**

```kotlin
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
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
./gradlew test --tests "com.traffic.engine.HttpRequestExecutorTest" -v
```

Expected: All 2 tests PASS (requires network access)

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/traffic/config/WebClientConfig.kt src/main/kotlin/com/traffic/engine/HttpRequestExecutor.kt src/test/kotlin/com/traffic/engine/HttpRequestExecutorTest.kt
git commit -m "feat: add HttpRequestExecutor with WebClient for non-blocking HTTP calls"
```

---

## Task 8: Step Executor (TDD)

**Files:**
- Create: `src/main/kotlin/com/traffic/engine/StepExecutor.kt`
- Create: `src/test/kotlin/com/traffic/engine/StepExecutorTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
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

        // Verify the HTTP request was made with resolved values
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
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests "com.traffic.engine.StepExecutorTest" -v
```

Expected: FAIL

- [ ] **Step 3: Implement StepExecutor**

```kotlin
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
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "com.traffic.engine.StepExecutorTest" -v
```

Expected: All 5 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/traffic/engine/StepExecutor.kt src/test/kotlin/com/traffic/engine/StepExecutorTest.kt
git commit -m "feat: add StepExecutor with variable resolution, extraction, and validation"
```

---

## Task 9: Virtual User (TDD)

**Files:**
- Create: `src/main/kotlin/com/traffic/engine/VirtualUser.kt`
- Create: `src/test/kotlin/com/traffic/engine/VirtualUserTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
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
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests "com.traffic.engine.VirtualUserTest" -v
```

Expected: FAIL

- [ ] **Step 3: Implement VirtualUser**

```kotlin
package com.traffic.engine

import com.traffic.domain.config.AdvancedConfig
import com.traffic.domain.config.ThinkTimeStrategy
import com.traffic.metric.MetricBuffer
import kotlinx.coroutines.delay
import kotlin.random.Random

class VirtualUser(
    val id: Int,
    private val steps: List<StepSpec>,
    private val baseUrl: String,
    private val advancedConfig: AdvancedConfig,
    private val stepExecutor: StepExecutor,
    private val metricBuffer: MetricBuffer,
    private val initialVariables: Map<String, String>
) {

    suspend fun run(requestCount: Int? = null, durationMs: Long? = null) {
        val startTime = System.currentTimeMillis()
        var iteration = 0

        while (true) {
            if (requestCount != null && iteration >= requestCount) break
            if (durationMs != null && System.currentTimeMillis() - startTime >= durationMs) break

            executeIteration()
            iteration++
        }
    }

    suspend fun executeIteration() {
        val variables = initialVariables.toMutableMap()
        val iterationStart = System.currentTimeMillis()

        for (step in steps) {
            val result = stepExecutor.execute(
                step = step,
                baseUrl = baseUrl,
                variables = variables,
                timeoutMs = advancedConfig.timeoutMs,
                discardBody = advancedConfig.discardResponseBody
            )

            metricBuffer.add(result.metricRecord)
            variables.putAll(result.extractedVariables)

            applyThinkTime(step, iterationStart)
        }
    }

    private suspend fun applyThinkTime(step: StepSpec, iterationStart: Long) {
        val stepThinkTime = step.thinkTimeMs
        if (stepThinkTime != null) {
            delay(stepThinkTime.toLong())
            return
        }

        when (advancedConfig.thinkTimeStrategy) {
            ThinkTimeStrategy.CONSTANT -> {
                if (advancedConfig.thinkTimeMs > 0) delay(advancedConfig.thinkTimeMs.toLong())
            }
            ThinkTimeStrategy.RANDOM -> {
                val waitMs = Random.nextInt(advancedConfig.thinkTimeMin, advancedConfig.thinkTimeMax + 1)
                if (waitMs > 0) delay(waitMs.toLong())
            }
            ThinkTimeStrategy.PACING -> {
                val elapsed = System.currentTimeMillis() - iterationStart
                val remaining = advancedConfig.thinkTimeMs - elapsed
                if (remaining > 0) delay(remaining)
            }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "com.traffic.engine.VirtualUserTest" -v
```

Expected: All 3 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/traffic/engine/VirtualUser.kt src/test/kotlin/com/traffic/engine/VirtualUserTest.kt
git commit -m "feat: add VirtualUser with coroutine-based iteration, variable passing, and think time"
```

---

## Task 10: Load Engine (TDD)

**Files:**
- Create: `src/main/kotlin/com/traffic/engine/LoadEngine.kt`
- Create: `src/test/kotlin/com/traffic/engine/LoadEngineTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.traffic.engine

import com.traffic.domain.config.*
import com.traffic.domain.plan.HttpMethod
import com.traffic.metric.MetricBuffer
import com.traffic.metric.MetricRecord
import io.mockk.coEvery
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
    fun `engine aborts when error rate exceeds threshold`() = runTest {
        val callCount = AtomicInteger(0)
        every { stepExecutor.execute(any(), any(), any(), any(), any()) } answers {
            callCount.incrementAndGet()
            StepResult(MetricRecord("step", 10, 500, false), emptyMap(), null)
        }

        val engine = LoadEngine(stepExecutor, metricBuffer)
        engine.run(
            steps = listOf(createStepSpec("step1")),
            baseUrl = "https://test.com",
            loadConfig = LoadConfig(vuCount = 1, requestsPerVu = 1000),
            safetyConfig = SafetyConfig(abortOnErrorRate = 50),
            advancedConfig = AdvancedConfig(),
            authConfig = AuthConfig(),
            onMetricSnapshot = {}
        )

        // Engine should have stopped before completing all 1000 requests
        assertTrue(callCount.get() < 1000)
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
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests "com.traffic.engine.LoadEngineTest" -v
```

Expected: FAIL

- [ ] **Step 3: Implement LoadEngine**

```kotlin
package com.traffic.engine

import com.traffic.domain.config.*
import com.traffic.metric.AggregatedMetric
import com.traffic.metric.MetricAggregator
import com.traffic.metric.MetricBuffer
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class LoadEngine(
    private val stepExecutor: StepExecutor,
    private val metricBuffer: MetricBuffer
) {
    private val aggregator = MetricAggregator()
    private val aborted = AtomicBoolean(false)
    private val activeVuCount = AtomicInteger(0)

    suspend fun run(
        steps: List<StepSpec>,
        baseUrl: String,
        loadConfig: LoadConfig,
        safetyConfig: SafetyConfig,
        advancedConfig: AdvancedConfig,
        authConfig: AuthConfig,
        onMetricSnapshot: (List<AggregatedMetric>) -> Unit
    ) {
        aborted.set(false)
        activeVuCount.set(0)

        coroutineScope {
            // Start metric collection job
            val collectorJob = launch {
                runMetricCollector(safetyConfig, onMetricSnapshot)
            }

            // Start VUs
            if (loadConfig.isStagesMode()) {
                runWithStages(this, steps, baseUrl, loadConfig, advancedConfig, authConfig)
            } else {
                runSimpleMode(this, steps, baseUrl, loadConfig, advancedConfig, authConfig)
            }

            // Stop collector
            collectorJob.cancel()
        }
    }

    fun abort() {
        aborted.set(true)
    }

    fun getCurrentVuCount(): Int = activeVuCount.get()

    private suspend fun runSimpleMode(
        scope: CoroutineScope,
        steps: List<StepSpec>,
        baseUrl: String,
        loadConfig: LoadConfig,
        advancedConfig: AdvancedConfig,
        authConfig: AuthConfig
    ) {
        val vuCount = loadConfig.vuCount
        val rampUpDelayMs = if (loadConfig.rampUpSeconds > 0 && vuCount > 1) {
            (loadConfig.rampUpSeconds * 1000L) / (vuCount - 1)
        } else 0L

        val durationMs = loadConfig.durationSeconds?.let { it * 1000L }

        val jobs = (0 until vuCount).map { index ->
            scope.launch {
                if (rampUpDelayMs > 0 && index > 0) {
                    delay(rampUpDelayMs * index)
                }
                if (aborted.get()) return@launch

                val initialVars = buildInitialVariables(authConfig, index)
                val vu = VirtualUser(
                    id = index,
                    steps = steps,
                    baseUrl = baseUrl,
                    advancedConfig = advancedConfig,
                    stepExecutor = stepExecutor,
                    metricBuffer = metricBuffer,
                    initialVariables = initialVars
                )

                activeVuCount.incrementAndGet()
                try {
                    vu.run(requestCount = loadConfig.requestsPerVu, durationMs = durationMs)
                } finally {
                    activeVuCount.decrementAndGet()
                }
            }
        }

        // maxDuration safety
        scope.launch {
            delay(loadConfig.maxDuration * 1000L)
            abort()
            jobs.forEach { it.cancel() }
        }

        jobs.joinAll()
    }

    private suspend fun runWithStages(
        scope: CoroutineScope,
        steps: List<StepSpec>,
        baseUrl: String,
        loadConfig: LoadConfig,
        advancedConfig: AdvancedConfig,
        authConfig: AuthConfig
    ) {
        var currentVuTarget = 0
        val activeJobs = mutableListOf<Job>()
        var vuIdCounter = 0

        for (stage in loadConfig.stages) {
            if (aborted.get()) break
            val targetVu = stage.targetVu
            val stageDurationMs = stage.durationSeconds * 1000L

            if (targetVu > currentVuTarget) {
                // Scale up
                val toAdd = targetVu - currentVuTarget
                val rampDelayMs = if (toAdd > 1) stageDurationMs / toAdd else 0

                for (i in 0 until toAdd) {
                    if (aborted.get()) break
                    val vuId = vuIdCounter++
                    val initialVars = buildInitialVariables(authConfig, vuId)
                    val job = scope.launch {
                        val vu = VirtualUser(vuId, steps, baseUrl, advancedConfig, stepExecutor, metricBuffer, initialVars)
                        activeVuCount.incrementAndGet()
                        try {
                            vu.run(durationMs = Long.MAX_VALUE)
                        } finally {
                            activeVuCount.decrementAndGet()
                        }
                    }
                    activeJobs.add(job)
                    if (rampDelayMs > 0) delay(rampDelayMs)
                }
            } else if (targetVu < currentVuTarget) {
                // Scale down
                val toRemove = currentVuTarget - targetVu
                repeat(toRemove.coerceAtMost(activeJobs.size)) {
                    activeJobs.removeFirstOrNull()?.cancel()
                }
            }

            currentVuTarget = targetVu
            if (!aborted.get()) delay(stageDurationMs)
        }

        // Cancel remaining VUs
        activeJobs.forEach { it.cancel() }
    }

    private suspend fun runMetricCollector(
        safetyConfig: SafetyConfig,
        onMetricSnapshot: (List<AggregatedMetric>) -> Unit
    ) {
        while (!aborted.get()) {
            delay(1000)
            val records = metricBuffer.drain()
            if (records.isNotEmpty()) {
                val aggregated = aggregator.aggregate(records, activeVuCount.get())
                onMetricSnapshot(aggregated)

                // Check abort condition
                val overall = aggregated.find { it.stepName == null }
                if (overall != null && overall.requestCount > 0) {
                    val errorRate = (overall.errorCount.toDouble() / overall.requestCount) * 100
                    if (errorRate > safetyConfig.abortOnErrorRate) {
                        abort()
                    }
                }
            }
        }
    }

    private fun buildInitialVariables(authConfig: AuthConfig, vuIndex: Int): Map<String, String> {
        if (authConfig.mode == AuthMode.TOKEN_POOL && authConfig.tokens.isNotEmpty()) {
            val token = authConfig.tokens[vuIndex % authConfig.tokens.size]
            return mapOf("token" to token)
        }
        return emptyMap()
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "com.traffic.engine.LoadEngineTest" -v
```

Expected: All 3 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/traffic/engine/LoadEngine.kt src/test/kotlin/com/traffic/engine/LoadEngineTest.kt
git commit -m "feat: add LoadEngine with VU lifecycle, ramp-up, stages, and abort on error rate"
```

---

## Task 11: Data Feeder (TDD)

**Files:**
- Create: `src/main/kotlin/com/traffic/feeder/DataFeeder.kt`
- Create: `src/main/kotlin/com/traffic/feeder/CsvDataFeeder.kt`
- Create: `src/main/kotlin/com/traffic/feeder/JsonDataFeeder.kt`
- Create: `src/test/kotlin/com/traffic/feeder/CsvDataFeederTest.kt`
- Create: `src/test/kotlin/com/traffic/feeder/JsonDataFeederTest.kt`

- [ ] **Step 1: Write CSV feeder tests**

```kotlin
package com.traffic.feeder

import com.traffic.domain.config.DataDistribution
import com.traffic.domain.config.EofStrategy
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class CsvDataFeederTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `sequential distribution returns rows in order`() {
        val file = tempDir.resolve("data.csv")
        file.writeText("username,password\nalice,pass1\nbob,pass2\ncharlie,pass3")

        val feeder = CsvDataFeeder(file.toString(), DataDistribution.SEQUENTIAL, EofStrategy.RECYCLE)

        assertEquals(mapOf("username" to "alice", "password" to "pass1"), feeder.next())
        assertEquals(mapOf("username" to "bob", "password" to "pass2"), feeder.next())
        assertEquals(mapOf("username" to "charlie", "password" to "pass3"), feeder.next())
    }

    @Test
    fun `recycle strategy wraps around`() {
        val file = tempDir.resolve("data.csv")
        file.writeText("name\nalice\nbob")

        val feeder = CsvDataFeeder(file.toString(), DataDistribution.SEQUENTIAL, EofStrategy.RECYCLE)

        feeder.next() // alice
        feeder.next() // bob
        val third = feeder.next()
        assertEquals(mapOf("name" to "alice"), third)
    }

    @Test
    fun `stop_vu strategy returns null at EOF`() {
        val file = tempDir.resolve("data.csv")
        file.writeText("name\nalice")

        val feeder = CsvDataFeeder(file.toString(), DataDistribution.SEQUENTIAL, EofStrategy.STOP_VU)

        assertNotNull(feeder.next())
        assertNull(feeder.next())
    }

    @Test
    fun `circular distribution returns rows in order then wraps`() {
        val file = tempDir.resolve("data.csv")
        file.writeText("name\nalice\nbob")

        val feeder = CsvDataFeeder(file.toString(), DataDistribution.CIRCULAR, EofStrategy.RECYCLE)

        assertEquals("alice", feeder.next()!!["name"])
        assertEquals("bob", feeder.next()!!["name"])
        assertEquals("alice", feeder.next()!!["name"])
    }
}
```

- [ ] **Step 2: Run CSV tests to verify they fail**

```bash
./gradlew test --tests "com.traffic.feeder.CsvDataFeederTest" -v
```

Expected: FAIL

- [ ] **Step 3: Implement DataFeeder interface and CsvDataFeeder**

DataFeeder.kt:
```kotlin
package com.traffic.feeder

interface DataFeeder {
    fun next(): Map<String, String>?
}
```

CsvDataFeeder.kt:
```kotlin
package com.traffic.feeder

import com.traffic.domain.config.DataDistribution
import com.traffic.domain.config.EofStrategy
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class CsvDataFeeder(
    filePath: String,
    private val distribution: DataDistribution,
    private val onEof: EofStrategy
) : DataFeeder {

    private val headers: List<String>
    private val rows: List<List<String>>
    private val index = AtomicInteger(0)

    init {
        val lines = File(filePath).readLines()
        headers = lines.first().split(",").map { it.trim() }
        rows = lines.drop(1).map { line -> line.split(",").map { it.trim() } }
    }

    override fun next(): Map<String, String>? {
        if (rows.isEmpty()) return null

        val row = when (distribution) {
            DataDistribution.RANDOM -> rows[Random.nextInt(rows.size)]
            DataDistribution.SEQUENTIAL, DataDistribution.CIRCULAR -> {
                val currentIndex = index.getAndIncrement()
                if (currentIndex >= rows.size) {
                    when (onEof) {
                        EofStrategy.STOP_VU -> return null
                        EofStrategy.RECYCLE -> {
                            index.set(1)
                            rows[0]
                        }
                    }
                } else {
                    rows[currentIndex]
                }
            }
        }

        return headers.zip(row).toMap()
    }
}
```

- [ ] **Step 4: Run CSV tests to verify they pass**

```bash
./gradlew test --tests "com.traffic.feeder.CsvDataFeederTest" -v
```

Expected: All 4 tests PASS

- [ ] **Step 5: Write JSON feeder tests**

```kotlin
package com.traffic.feeder

import com.traffic.domain.config.DataDistribution
import com.traffic.domain.config.EofStrategy
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class JsonDataFeederTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `reads JSON array and returns rows sequentially`() {
        val file = tempDir.resolve("data.json")
        file.writeText("""[{"name":"alice","age":"30"},{"name":"bob","age":"25"}]""")

        val feeder = JsonDataFeeder(file.toString(), DataDistribution.SEQUENTIAL, EofStrategy.RECYCLE)

        assertEquals(mapOf("name" to "alice", "age" to "30"), feeder.next())
        assertEquals(mapOf("name" to "bob", "age" to "25"), feeder.next())
    }
}
```

- [ ] **Step 6: Implement JsonDataFeeder**

```kotlin
package com.traffic.feeder

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.traffic.domain.config.DataDistribution
import com.traffic.domain.config.EofStrategy
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class JsonDataFeeder(
    filePath: String,
    private val distribution: DataDistribution,
    private val onEof: EofStrategy
) : DataFeeder {

    private val rows: List<Map<String, String>>
    private val index = AtomicInteger(0)

    init {
        val mapper = jacksonObjectMapper()
        val rawList: List<Map<String, Any>> = mapper.readValue(File(filePath).readText())
        rows = rawList.map { row -> row.mapValues { it.value.toString() } }
    }

    override fun next(): Map<String, String>? {
        if (rows.isEmpty()) return null

        return when (distribution) {
            DataDistribution.RANDOM -> rows[Random.nextInt(rows.size)]
            DataDistribution.SEQUENTIAL, DataDistribution.CIRCULAR -> {
                val currentIndex = index.getAndIncrement()
                if (currentIndex >= rows.size) {
                    when (onEof) {
                        EofStrategy.STOP_VU -> null
                        EofStrategy.RECYCLE -> {
                            index.set(1)
                            rows[0]
                        }
                    }
                } else {
                    rows[currentIndex]
                }
            }
        }
    }
}
```

- [ ] **Step 7: Run all feeder tests**

```bash
./gradlew test --tests "com.traffic.feeder.*" -v
```

Expected: All 5 tests PASS

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/com/traffic/feeder/ src/test/kotlin/com/traffic/feeder/
git commit -m "feat: add CSV and JSON data feeders with sequential, random, circular distribution"
```

---

## Task 12: TestPlan Service

**Files:**
- Create: `src/main/kotlin/com/traffic/service/TestPlanService.kt`
- Create: `src/test/kotlin/com/traffic/service/TestPlanServiceTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.traffic.service

import com.traffic.domain.config.LoadConfig
import com.traffic.domain.plan.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Optional

class TestPlanServiceTest {

    private val planRepo = mockk<TestPlanRepository>(relaxed = true)
    private val stepRepo = mockk<TestStepRepository>(relaxed = true)
    private val service = TestPlanService(planRepo, stepRepo)

    @Test
    fun `findAll returns all plans`() {
        val plans = listOf(
            TestPlan(id = 1, name = "Plan 1", targetBaseUrl = "https://a.com"),
            TestPlan(id = 2, name = "Plan 2", targetBaseUrl = "https://b.com")
        )
        every { planRepo.findAll() } returns plans

        val result = service.findAll()
        assertEquals(2, result.size)
    }

    @Test
    fun `findById returns plan with steps`() {
        val plan = TestPlan(id = 1, name = "Plan", targetBaseUrl = "https://a.com")
        every { planRepo.findById(1L) } returns Optional.of(plan)

        val result = service.findById(1L)
        assertNotNull(result)
        assertEquals("Plan", result!!.name)
    }

    @Test
    fun `findById returns null when not found`() {
        every { planRepo.findById(999L) } returns Optional.empty()

        val result = service.findById(999L)
        assertNull(result)
    }

    @Test
    fun `delete removes plan`() {
        service.delete(1L)
        verify { planRepo.deleteById(1L) }
    }

    @Test
    fun `duplicate creates copy with new name`() {
        val original = TestPlan(id = 1, name = "Original", targetBaseUrl = "https://a.com",
            loadConfig = LoadConfig(vuCount = 10))
        every { planRepo.findById(1L) } returns Optional.of(original)
        every { stepRepo.findByTestPlanIdOrderByOrderIndexAsc(1L) } returns emptyList()
        every { planRepo.save(any()) } answers { firstArg() }

        val result = service.duplicate(1L)
        assertNotNull(result)
        assertEquals("Original (copy)", result!!.name)
        assertEquals(10, result.loadConfig.vuCount)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests "com.traffic.service.TestPlanServiceTest" -v
```

Expected: FAIL

- [ ] **Step 3: Implement TestPlanService**

```kotlin
package com.traffic.service

import com.traffic.domain.plan.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TestPlanService(
    private val planRepository: TestPlanRepository,
    private val stepRepository: TestStepRepository
) {

    fun findAll(): List<TestPlan> = planRepository.findAll()

    fun findById(id: Long): TestPlan? = planRepository.findById(id).orElse(null)

    @Transactional
    fun save(plan: TestPlan): TestPlan = planRepository.save(plan)

    @Transactional
    fun delete(id: Long) = planRepository.deleteById(id)

    @Transactional
    fun duplicate(id: Long): TestPlan? {
        val original = findById(id) ?: return null
        val steps = stepRepository.findByTestPlanIdOrderByOrderIndexAsc(id)

        val copy = TestPlan(
            name = "${original.name} (copy)",
            targetBaseUrl = original.targetBaseUrl,
            description = original.description,
            loadConfig = original.loadConfig,
            safetyConfig = original.safetyConfig,
            thresholds = original.thresholds,
            authConfig = original.authConfig,
            advancedConfig = original.advancedConfig,
            dataSourceConfig = original.dataSourceConfig
        )
        val savedCopy = planRepository.save(copy)

        steps.forEach { step ->
            val stepCopy = TestStep(
                testPlan = savedCopy,
                orderIndex = step.orderIndex,
                name = step.name,
                httpMethod = step.httpMethod,
                path = step.path,
                headers = step.headers,
                body = step.body,
                thinkTimeMs = step.thinkTimeMs,
                extractors = step.extractors,
                validators = step.validators
            )
            stepRepository.save(stepCopy)
        }

        return savedCopy
    }

    fun getSteps(planId: Long): List<TestStep> =
        stepRepository.findByTestPlanIdOrderByOrderIndexAsc(planId)
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "com.traffic.service.TestPlanServiceTest" -v
```

Expected: All 5 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/traffic/service/TestPlanService.kt src/test/kotlin/com/traffic/service/TestPlanServiceTest.kt
git commit -m "feat: add TestPlanService with CRUD and duplicate operations"
```

---

## Task 13: Execution Service

**Files:**
- Create: `src/main/kotlin/com/traffic/service/ExecutionService.kt`
- Create: `src/main/kotlin/com/traffic/service/MetricStreamService.kt`

- [ ] **Step 1: Create MetricStreamService**

```kotlin
package com.traffic.service

import com.traffic.metric.AggregatedMetric
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Service
class MetricStreamService {

    private val emitters = ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>>()

    fun subscribe(executionId: Long): SseEmitter {
        val emitter = SseEmitter(Long.MAX_VALUE)
        val list = emitters.computeIfAbsent(executionId) { CopyOnWriteArrayList() }
        list.add(emitter)

        emitter.onCompletion { list.remove(emitter) }
        emitter.onTimeout { list.remove(emitter) }
        emitter.onError { list.remove(emitter) }

        return emitter
    }

    fun broadcast(executionId: Long, metrics: List<AggregatedMetric>) {
        val list = emitters[executionId] ?: return
        val deadEmitters = mutableListOf<SseEmitter>()

        for (emitter in list) {
            try {
                emitter.send(SseEmitter.event().name("metric").data(metrics))
            } catch (_: Exception) {
                deadEmitters.add(emitter)
            }
        }

        list.removeAll(deadEmitters.toSet())
    }

    fun complete(executionId: Long) {
        val list = emitters.remove(executionId) ?: return
        list.forEach { emitter ->
            try {
                emitter.send(SseEmitter.event().name("complete").data("done"))
                emitter.complete()
            } catch (_: Exception) {}
        }
    }
}
```

- [ ] **Step 2: Create ExecutionService**

```kotlin
package com.traffic.service

import com.traffic.domain.config.Threshold
import com.traffic.domain.config.ThresholdMetric
import com.traffic.domain.execution.*
import com.traffic.domain.plan.TestPlan
import com.traffic.domain.plan.TestStepRepository
import com.traffic.engine.LoadEngine
import com.traffic.engine.StepExecutor
import com.traffic.engine.StepSpec
import com.traffic.metric.AggregatedMetric
import com.traffic.metric.MetricBuffer
import kotlinx.coroutines.*
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

@Service
class ExecutionService(
    private val executionRepository: TestExecutionRepository,
    private val snapshotRepository: MetricSnapshotRepository,
    private val stepRepository: TestStepRepository,
    private val stepExecutor: StepExecutor,
    private val metricStreamService: MetricStreamService
) {
    private val runningEngines = ConcurrentHashMap<Long, LoadEngine>()
    private val allLatencies = ConcurrentHashMap<Long, MutableList<Long>>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun startExecution(plan: TestPlan): TestExecution {
        val execution = executionRepository.save(
            TestExecution(testPlanId = plan.id)
        )

        val steps = stepRepository.findByTestPlanIdOrderByOrderIndexAsc(plan.id)
        val stepSpecs = steps.map { step ->
            StepSpec(
                name = step.name,
                httpMethod = step.httpMethod,
                path = step.path,
                headers = step.headers,
                body = step.body,
                thinkTimeMs = step.thinkTimeMs,
                extractors = step.extractors,
                validators = step.validators
            )
        }

        val metricBuffer = MetricBuffer()
        val engine = LoadEngine(stepExecutor, metricBuffer)
        runningEngines[execution.id] = engine
        allLatencies[execution.id] = mutableListOf()

        scope.launch {
            try {
                engine.run(
                    steps = stepSpecs,
                    baseUrl = plan.targetBaseUrl,
                    loadConfig = plan.loadConfig,
                    safetyConfig = plan.safetyConfig,
                    advancedConfig = plan.advancedConfig,
                    authConfig = plan.authConfig,
                    onMetricSnapshot = { metrics ->
                        handleMetricSnapshot(execution.id, metrics)
                    }
                )
                finalizeExecution(execution.id, plan, ExecutionStatus.COMPLETED)
            } catch (e: CancellationException) {
                finalizeExecution(execution.id, plan, ExecutionStatus.ABORTED)
            } catch (e: Exception) {
                finalizeExecution(execution.id, plan, ExecutionStatus.ABORTED)
            } finally {
                runningEngines.remove(execution.id)
                metricStreamService.complete(execution.id)
            }
        }

        return execution
    }

    fun abortExecution(executionId: Long) {
        runningEngines[executionId]?.abort()
    }

    fun getExecution(id: Long): TestExecution? = executionRepository.findById(id).orElse(null)

    fun getAllExecutions(): List<TestExecution> = executionRepository.findAllByOrderByStartedAtDesc()

    private fun handleMetricSnapshot(executionId: Long, metrics: List<AggregatedMetric>) {
        // Save to DB
        metrics.forEach { metric ->
            snapshotRepository.save(
                MetricSnapshot(
                    executionId = executionId,
                    stepName = metric.stepName,
                    currentVu = metric.currentVu,
                    requestCount = metric.requestCount,
                    errorCount = metric.errorCount,
                    avgResponseTime = metric.avgResponseTime,
                    p95ResponseTime = metric.p95ResponseTime,
                    tps = metric.tps
                )
            )
        }

        // Broadcast via SSE
        metricStreamService.broadcast(executionId, metrics)
    }

    private fun finalizeExecution(executionId: Long, plan: TestPlan, status: ExecutionStatus) {
        val snapshots = snapshotRepository.findByExecutionIdAndStepNameIsNullOrderByTimestampAsc(executionId)
        val execution = executionRepository.findById(executionId).orElse(null) ?: return

        val totalRequests = snapshots.sumOf { it.requestCount }.toLong()
        val totalErrors = snapshots.sumOf { it.errorCount }.toLong()
        val totalSuccess = totalRequests - totalErrors
        val avgLatency = if (snapshots.isNotEmpty()) snapshots.map { it.avgResponseTime }.average() else 0.0
        val avgTps = if (snapshots.isNotEmpty()) snapshots.map { it.tps }.average() else 0.0
        val errorRate = if (totalRequests > 0) (totalErrors.toDouble() / totalRequests) * 100 else 0.0

        execution.status = status
        execution.finishedAt = LocalDateTime.now()
        execution.totalRequests = totalRequests
        execution.successCount = totalSuccess
        execution.failCount = totalErrors
        execution.avgResponseTime = avgLatency
        execution.tps = avgTps
        execution.errorRate = errorRate

        // Approximate percentiles from snapshot p95 values
        val p95Values = snapshots.map { it.p95ResponseTime }.sorted()
        execution.p50 = percentileFromList(p95Values, 50.0)
        execution.p90 = percentileFromList(p95Values, 90.0)
        execution.p95 = percentileFromList(p95Values, 95.0)
        execution.p99 = percentileFromList(p95Values, 99.0)

        // Threshold evaluation
        execution.result = evaluateThresholds(plan.thresholds, execution)

        executionRepository.save(execution)
    }

    private fun evaluateThresholds(thresholds: List<Threshold>, execution: TestExecution): ExecutionResult {
        if (thresholds.isEmpty()) return ExecutionResult.PASS

        return if (thresholds.all { threshold ->
            val actualValue = when (threshold.metric) {
                ThresholdMetric.AVG -> execution.avgResponseTime
                ThresholdMetric.P50 -> execution.p50
                ThresholdMetric.P90 -> execution.p90
                ThresholdMetric.P95 -> execution.p95
                ThresholdMetric.P99 -> execution.p99
                ThresholdMetric.ERROR_RATE -> execution.errorRate
                ThresholdMetric.TPS -> execution.tps
            }
            threshold.evaluate(actualValue)
        }) ExecutionResult.PASS else ExecutionResult.FAIL
    }

    private fun percentileFromList(sorted: List<Double>, percentile: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val index = ((percentile / 100.0) * (sorted.size - 1)).toInt()
        return sorted[index]
    }
}
```

- [ ] **Step 3: Create StepExecutor bean config**

`src/main/kotlin/com/traffic/config/EngineConfig.kt`:
```kotlin
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
```

- [ ] **Step 4: Build 확인**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/traffic/service/ src/main/kotlin/com/traffic/config/EngineConfig.kt
git commit -m "feat: add ExecutionService and MetricStreamService with SSE broadcasting"
```

---

## Task 14: Web Controllers

**Files:**
- Create: `src/main/kotlin/com/traffic/web/PlanController.kt`
- Create: `src/main/kotlin/com/traffic/web/StepController.kt`
- Create: `src/main/kotlin/com/traffic/web/ExecutionController.kt`
- Create: `src/main/kotlin/com/traffic/web/DashboardController.kt`

- [ ] **Step 1: Create PlanController**

```kotlin
package com.traffic.web

import com.traffic.domain.config.*
import com.traffic.domain.plan.TestPlan
import com.traffic.service.TestPlanService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*

@Controller
@RequestMapping("/plans")
class PlanController(private val planService: TestPlanService) {

    @GetMapping
    fun list(model: Model): String {
        model.addAttribute("plans", planService.findAll())
        return "plans/list"
    }

    @GetMapping("/new")
    fun newForm(model: Model): String {
        model.addAttribute("plan", TestPlan(name = "", targetBaseUrl = ""))
        model.addAttribute("isNew", true)
        return "plans/form"
    }

    @PostMapping
    fun create(
        @RequestParam name: String,
        @RequestParam targetBaseUrl: String,
        @RequestParam(required = false) description: String?,
        @RequestParam(defaultValue = "1") vuCount: Int,
        @RequestParam(required = false) requestsPerVu: Int?,
        @RequestParam(defaultValue = "0") rampUpSeconds: Int,
        @RequestParam(required = false) durationSeconds: Int?,
        @RequestParam(defaultValue = "600") maxDuration: Int,
        @RequestParam(defaultValue = "50") abortOnErrorRate: Int,
        @RequestParam(defaultValue = "30") gracefulStopTimeout: Int,
        @RequestParam(defaultValue = "10000") timeoutMs: Int
    ): String {
        val plan = TestPlan(
            name = name,
            targetBaseUrl = targetBaseUrl,
            description = description,
            loadConfig = LoadConfig(vuCount, requestsPerVu, rampUpSeconds, durationSeconds, maxDuration = maxDuration),
            safetyConfig = SafetyConfig(abortOnErrorRate, gracefulStopTimeout),
            advancedConfig = AdvancedConfig(timeoutMs = timeoutMs)
        )
        val saved = planService.save(plan)
        return "redirect:/plans/${saved.id}"
    }

    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long, model: Model): String {
        val plan = planService.findById(id) ?: return "redirect:/plans"
        model.addAttribute("plan", plan)
        model.addAttribute("steps", planService.getSteps(id))
        model.addAttribute("isNew", false)
        return "plans/form"
    }

    @PostMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestParam name: String,
        @RequestParam targetBaseUrl: String,
        @RequestParam(required = false) description: String?,
        @RequestParam(defaultValue = "1") vuCount: Int,
        @RequestParam(required = false) requestsPerVu: Int?,
        @RequestParam(defaultValue = "0") rampUpSeconds: Int,
        @RequestParam(required = false) durationSeconds: Int?,
        @RequestParam(defaultValue = "600") maxDuration: Int,
        @RequestParam(defaultValue = "50") abortOnErrorRate: Int,
        @RequestParam(defaultValue = "30") gracefulStopTimeout: Int,
        @RequestParam(defaultValue = "10000") timeoutMs: Int
    ): String {
        val plan = planService.findById(id) ?: return "redirect:/plans"
        plan.name = name
        plan.targetBaseUrl = targetBaseUrl
        plan.description = description
        plan.loadConfig = LoadConfig(vuCount, requestsPerVu, rampUpSeconds, durationSeconds, maxDuration = maxDuration)
        plan.safetyConfig = SafetyConfig(abortOnErrorRate, gracefulStopTimeout)
        plan.advancedConfig = plan.advancedConfig.copy(timeoutMs = timeoutMs)
        planService.save(plan)
        return "redirect:/plans/${id}"
    }

    @PostMapping("/{id}/delete")
    fun delete(@PathVariable id: Long): String {
        planService.delete(id)
        return "redirect:/plans"
    }

    @PostMapping("/{id}/duplicate")
    fun duplicate(@PathVariable id: Long): String {
        val copy = planService.duplicate(id)
        return "redirect:/plans/${copy?.id ?: ""}"
    }
}
```

- [ ] **Step 2: Create StepController**

```kotlin
package com.traffic.web

import com.traffic.domain.plan.*
import com.traffic.service.TestPlanService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*

@Controller
@RequestMapping("/plans/{planId}/steps")
class StepController(
    private val planService: TestPlanService,
    private val stepRepository: TestStepRepository
) {

    @GetMapping
    fun editSteps(@PathVariable planId: Long, model: Model): String {
        val plan = planService.findById(planId) ?: return "redirect:/plans"
        model.addAttribute("plan", plan)
        model.addAttribute("steps", planService.getSteps(planId))
        return "plans/steps"
    }

    @PostMapping
    fun addStep(
        @PathVariable planId: Long,
        @RequestParam name: String,
        @RequestParam httpMethod: HttpMethod,
        @RequestParam path: String,
        @RequestParam(required = false) body: String?,
        @RequestParam(required = false) thinkTimeMs: Int?
    ): String {
        val plan = planService.findById(planId) ?: return "redirect:/plans"
        val steps = planService.getSteps(planId)
        val nextIndex = (steps.maxOfOrNull { it.orderIndex } ?: -1) + 1

        stepRepository.save(
            TestStep(
                testPlan = plan,
                orderIndex = nextIndex,
                name = name,
                httpMethod = httpMethod,
                path = path,
                body = body,
                thinkTimeMs = thinkTimeMs
            )
        )

        return "redirect:/plans/$planId/steps"
    }

    @PostMapping("/{stepId}/delete")
    fun deleteStep(@PathVariable planId: Long, @PathVariable stepId: Long): String {
        stepRepository.deleteById(stepId)
        return "redirect:/plans/$planId/steps"
    }
}
```

- [ ] **Step 3: Create ExecutionController**

```kotlin
package com.traffic.web

import com.traffic.service.ExecutionService
import com.traffic.service.TestPlanService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*

@Controller
@RequestMapping("/executions")
class ExecutionController(
    private val executionService: ExecutionService,
    private val planService: TestPlanService
) {

    @GetMapping
    fun list(model: Model): String {
        model.addAttribute("executions", executionService.getAllExecutions())
        return "executions/list"
    }

    @PostMapping("/start/{planId}")
    fun start(@PathVariable planId: Long): String {
        val plan = planService.findById(planId) ?: return "redirect:/plans"
        val execution = executionService.startExecution(plan)
        return "redirect:/executions/${execution.id}/live"
    }

    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long, model: Model): String {
        val execution = executionService.getExecution(id) ?: return "redirect:/executions"
        val plan = planService.findById(execution.testPlanId)
        model.addAttribute("execution", execution)
        model.addAttribute("plan", plan)
        return "executions/detail"
    }

    @PostMapping("/{id}/abort")
    fun abort(@PathVariable id: Long): String {
        executionService.abortExecution(id)
        return "redirect:/executions/$id/live"
    }
}
```

- [ ] **Step 4: Create DashboardController**

```kotlin
package com.traffic.web

import com.traffic.service.ExecutionService
import com.traffic.service.MetricStreamService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Controller
class DashboardController(
    private val executionService: ExecutionService,
    private val metricStreamService: MetricStreamService
) {

    @GetMapping("/executions/{id}/live")
    fun liveDashboard(@PathVariable id: Long, model: Model): String {
        val execution = executionService.getExecution(id) ?: return "redirect:/executions"
        model.addAttribute("execution", execution)
        return "executions/live"
    }

    @GetMapping("/executions/{id}/stream")
    @ResponseBody
    fun stream(@PathVariable id: Long): SseEmitter {
        return metricStreamService.subscribe(id)
    }
}
```

- [ ] **Step 5: Build 확인**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/traffic/web/
git commit -m "feat: add web controllers for plans, steps, executions, and live dashboard"
```

---

## Task 15: Thymeleaf Templates

**Files:**
- Create: `src/main/resources/templates/layout.html`
- Create: `src/main/resources/templates/plans/list.html`
- Create: `src/main/resources/templates/plans/form.html`
- Create: `src/main/resources/templates/plans/steps.html`
- Create: `src/main/resources/templates/executions/list.html`
- Create: `src/main/resources/templates/executions/detail.html`
- Create: `src/main/resources/templates/executions/live.html`

- [ ] **Step 1: Create layout.html**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head th:fragment="head(title)">
    <meta charset="UTF-8"/>
    <title th:text="${title}">Traffic Service</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; background: #f5f5f5; color: #333; }
        nav { background: #1a1a2e; color: white; padding: 1rem 2rem; display: flex; gap: 2rem; align-items: center; }
        nav a { color: #e0e0e0; text-decoration: none; }
        nav a:hover { color: white; }
        nav .brand { font-size: 1.2rem; font-weight: bold; color: white; }
        .container { max-width: 1200px; margin: 2rem auto; padding: 0 1rem; }
        .card { background: white; border-radius: 8px; padding: 1.5rem; margin-bottom: 1rem; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
        table { width: 100%; border-collapse: collapse; }
        th, td { padding: 0.75rem; text-align: left; border-bottom: 1px solid #eee; }
        th { font-weight: 600; color: #666; }
        .btn { padding: 0.5rem 1rem; border: none; border-radius: 4px; cursor: pointer; text-decoration: none; display: inline-block; font-size: 0.9rem; }
        .btn-primary { background: #4361ee; color: white; }
        .btn-danger { background: #e63946; color: white; }
        .btn-secondary { background: #6c757d; color: white; }
        .btn-success { background: #2a9d8f; color: white; }
        .btn-sm { padding: 0.3rem 0.6rem; font-size: 0.8rem; }
        input, select, textarea { padding: 0.5rem; border: 1px solid #ddd; border-radius: 4px; width: 100%; margin-bottom: 0.5rem; }
        label { display: block; margin-bottom: 0.25rem; font-weight: 500; }
        .form-group { margin-bottom: 1rem; }
        .grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
        .badge { padding: 0.2rem 0.6rem; border-radius: 12px; font-size: 0.8rem; font-weight: 500; }
        .badge-running { background: #ffd60a; color: #333; }
        .badge-completed { background: #2a9d8f; color: white; }
        .badge-aborted { background: #e63946; color: white; }
        .badge-pass { background: #2a9d8f; color: white; }
        .badge-fail { background: #e63946; color: white; }
        .metric-cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 1rem; margin-bottom: 1rem; }
        .metric-card { text-align: center; padding: 1rem; }
        .metric-card .value { font-size: 2rem; font-weight: bold; }
        .metric-card .label { color: #666; font-size: 0.85rem; }
    </style>
</head>
<body>
    <nav th:fragment="nav">
        <span class="brand">Traffic Service</span>
        <a href="/plans">Plans</a>
        <a href="/executions">Executions</a>
    </nav>
</body>
</html>
```

- [ ] **Step 2: Create plans/list.html**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{layout :: head('Plans')}"></head>
<body>
<nav th:replace="~{layout :: nav}"></nav>
<div class="container">
    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:1rem;">
        <h2>Test Plans</h2>
        <a href="/plans/new" class="btn btn-primary">+ New Plan</a>
    </div>
    <div class="card">
        <table>
            <thead><tr><th>Name</th><th>Target URL</th><th>VU</th><th>Created</th><th>Actions</th></tr></thead>
            <tbody>
            <tr th:each="plan : ${plans}">
                <td><a th:href="@{/plans/{id}(id=${plan.id})}" th:text="${plan.name}"></a></td>
                <td th:text="${plan.targetBaseUrl}"></td>
                <td th:text="${plan.loadConfig.vuCount}"></td>
                <td th:text="${#temporals.format(plan.createdAt, 'yyyy-MM-dd HH:mm')}"></td>
                <td>
                    <form th:action="@{/executions/start/{id}(id=${plan.id})}" method="post" style="display:inline;">
                        <button type="submit" class="btn btn-success btn-sm">Run</button>
                    </form>
                    <form th:action="@{/plans/{id}/duplicate(id=${plan.id})}" method="post" style="display:inline;">
                        <button type="submit" class="btn btn-secondary btn-sm">Copy</button>
                    </form>
                    <form th:action="@{/plans/{id}/delete(id=${plan.id})}" method="post" style="display:inline;">
                        <button type="submit" class="btn btn-danger btn-sm">Delete</button>
                    </form>
                </td>
            </tr>
            </tbody>
        </table>
        <p th:if="${#lists.isEmpty(plans)}" style="text-align:center; padding:2rem; color:#999;">No plans yet. Create one to get started.</p>
    </div>
</div>
</body>
</html>
```

- [ ] **Step 3: Create plans/form.html**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{layout :: head('Plan')}"></head>
<body>
<nav th:replace="~{layout :: nav}"></nav>
<div class="container">
    <h2 th:text="${isNew} ? 'New Test Plan' : 'Edit: ' + ${plan.name}"></h2>
    <div class="card">
        <form th:action="${isNew} ? @{/plans} : @{/plans/{id}(id=${plan.id})}" method="post">
            <div class="form-group">
                <label>Name</label>
                <input type="text" name="name" th:value="${plan.name}" required/>
            </div>
            <div class="form-group">
                <label>Target Base URL</label>
                <input type="text" name="targetBaseUrl" th:value="${plan.targetBaseUrl}" placeholder="https://api.example.com" required/>
            </div>
            <div class="form-group">
                <label>Description</label>
                <textarea name="description" rows="2" th:text="${plan.description}"></textarea>
            </div>
            <h3 style="margin: 1.5rem 0 1rem;">Load Config</h3>
            <div class="grid-2">
                <div class="form-group">
                    <label>Virtual Users (VU)</label>
                    <input type="number" name="vuCount" th:value="${plan.loadConfig.vuCount}" min="1"/>
                </div>
                <div class="form-group">
                    <label>Requests per VU (empty = duration-based)</label>
                    <input type="number" name="requestsPerVu" th:value="${plan.loadConfig.requestsPerVu}" min="1"/>
                </div>
                <div class="form-group">
                    <label>Ramp-up (seconds)</label>
                    <input type="number" name="rampUpSeconds" th:value="${plan.loadConfig.rampUpSeconds}" min="0"/>
                </div>
                <div class="form-group">
                    <label>Duration (seconds, empty = request-based)</label>
                    <input type="number" name="durationSeconds" th:value="${plan.loadConfig.durationSeconds}" min="1"/>
                </div>
                <div class="form-group">
                    <label>Max Duration (seconds)</label>
                    <input type="number" name="maxDuration" th:value="${plan.loadConfig.maxDuration}" min="1"/>
                </div>
            </div>
            <h3 style="margin: 1.5rem 0 1rem;">Safety</h3>
            <div class="grid-2">
                <div class="form-group">
                    <label>Abort on Error Rate (%)</label>
                    <input type="number" name="abortOnErrorRate" th:value="${plan.safetyConfig.abortOnErrorRate}" min="1" max="100"/>
                </div>
                <div class="form-group">
                    <label>Graceful Stop Timeout (seconds)</label>
                    <input type="number" name="gracefulStopTimeout" th:value="${plan.safetyConfig.gracefulStopTimeout}" min="0"/>
                </div>
                <div class="form-group">
                    <label>Request Timeout (ms)</label>
                    <input type="number" name="timeoutMs" th:value="${plan.advancedConfig.timeoutMs}" min="100"/>
                </div>
            </div>
            <button type="submit" class="btn btn-primary" th:text="${isNew} ? 'Create' : 'Save'">Save</button>
        </form>
    </div>
    <div th:unless="${isNew}" class="card" style="margin-top:1rem;">
        <h3>Scenario Steps</h3>
        <p th:if="${steps != null && !steps.isEmpty()}" th:text="${#lists.size(steps)} + ' steps configured'"></p>
        <p th:if="${steps == null || steps.isEmpty()}">No steps yet.</p>
        <a th:href="@{/plans/{id}/steps(id=${plan.id})}" class="btn btn-secondary">Edit Steps</a>
    </div>
</div>
</body>
</html>
```

- [ ] **Step 4: Create plans/steps.html**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{layout :: head('Steps')}"></head>
<body>
<nav th:replace="~{layout :: nav}"></nav>
<div class="container">
    <h2>Steps: <span th:text="${plan.name}"></span></h2>
    <div class="card">
        <table th:if="${!steps.isEmpty()}">
            <thead><tr><th>#</th><th>Name</th><th>Method</th><th>Path</th><th>Think Time</th><th></th></tr></thead>
            <tbody>
            <tr th:each="step : ${steps}">
                <td th:text="${step.orderIndex}"></td>
                <td th:text="${step.name}"></td>
                <td th:text="${step.httpMethod}"></td>
                <td th:text="${step.path}"></td>
                <td th:text="${step.thinkTimeMs != null ? step.thinkTimeMs + 'ms' : 'default'}"></td>
                <td>
                    <form th:action="@{/plans/{pid}/steps/{sid}/delete(pid=${plan.id},sid=${step.id})}" method="post">
                        <button type="submit" class="btn btn-danger btn-sm">Delete</button>
                    </form>
                </td>
            </tr>
            </tbody>
        </table>
        <p th:if="${steps.isEmpty()}" style="padding:1rem; color:#999;">No steps yet. Add one below.</p>
    </div>
    <div class="card">
        <h3>Add Step</h3>
        <form th:action="@{/plans/{id}/steps(id=${plan.id})}" method="post">
            <div class="grid-2">
                <div class="form-group">
                    <label>Name</label>
                    <input type="text" name="name" placeholder="e.g. Login" required/>
                </div>
                <div class="form-group">
                    <label>HTTP Method</label>
                    <select name="httpMethod">
                        <option value="GET">GET</option>
                        <option value="POST">POST</option>
                        <option value="PUT">PUT</option>
                        <option value="DELETE">DELETE</option>
                    </select>
                </div>
                <div class="form-group">
                    <label>Path</label>
                    <input type="text" name="path" placeholder="/api/endpoint" required/>
                </div>
                <div class="form-group">
                    <label>Think Time (ms, optional)</label>
                    <input type="number" name="thinkTimeMs" min="0"/>
                </div>
            </div>
            <div class="form-group">
                <label>Body (optional, JSON)</label>
                <textarea name="body" rows="3" placeholder='{"key": "value"}'></textarea>
            </div>
            <button type="submit" class="btn btn-primary">Add Step</button>
        </form>
    </div>
    <a th:href="@{/plans/{id}(id=${plan.id})}" class="btn btn-secondary" style="margin-top:1rem;">Back to Plan</a>
</div>
</body>
</html>
```

- [ ] **Step 5: Create executions/list.html**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{layout :: head('Executions')}"></head>
<body>
<nav th:replace="~{layout :: nav}"></nav>
<div class="container">
    <h2>Execution History</h2>
    <div class="card">
        <table>
            <thead><tr><th>#</th><th>Status</th><th>Result</th><th>Requests</th><th>Avg (ms)</th><th>Error %</th><th>Started</th><th></th></tr></thead>
            <tbody>
            <tr th:each="exec : ${executions}">
                <td th:text="${exec.id}"></td>
                <td><span class="badge" th:classappend="'badge-' + ${exec.status.name().toLowerCase()}" th:text="${exec.status}"></span></td>
                <td><span th:if="${exec.result != null}" class="badge" th:classappend="'badge-' + ${exec.result.name().toLowerCase()}" th:text="${exec.result}"></span></td>
                <td th:text="${exec.totalRequests}"></td>
                <td th:text="${#numbers.formatDecimal(exec.avgResponseTime, 0, 1)}"></td>
                <td th:text="${#numbers.formatDecimal(exec.errorRate, 0, 1) + '%'}"></td>
                <td th:text="${#temporals.format(exec.startedAt, 'yyyy-MM-dd HH:mm:ss')}"></td>
                <td>
                    <a th:if="${exec.status.name() == 'RUNNING'}" th:href="@{/executions/{id}/live(id=${exec.id})}" class="btn btn-primary btn-sm">Live</a>
                    <a th:unless="${exec.status.name() == 'RUNNING'}" th:href="@{/executions/{id}(id=${exec.id})}" class="btn btn-secondary btn-sm">Detail</a>
                </td>
            </tr>
            </tbody>
        </table>
        <p th:if="${#lists.isEmpty(executions)}" style="text-align:center; padding:2rem; color:#999;">No executions yet.</p>
    </div>
</div>
</body>
</html>
```

- [ ] **Step 6: Create executions/detail.html**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{layout :: head('Result')}"></head>
<body>
<nav th:replace="~{layout :: nav}"></nav>
<div class="container">
    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:1rem;">
        <h2>Execution #<span th:text="${execution.id}"></span></h2>
        <span class="badge" th:if="${execution.result != null}"
              th:classappend="'badge-' + ${execution.result.name().toLowerCase()}"
              th:text="${execution.result}" style="font-size:1.2rem; padding: 0.4rem 1rem;"></span>
    </div>
    <div class="metric-cards">
        <div class="card metric-card">
            <div class="value" th:text="${execution.totalRequests}"></div>
            <div class="label">Total Requests</div>
        </div>
        <div class="card metric-card">
            <div class="value" th:text="${#numbers.formatDecimal(execution.avgResponseTime, 0, 1)} + 'ms'"></div>
            <div class="label">Avg Response Time</div>
        </div>
        <div class="card metric-card">
            <div class="value" th:text="${#numbers.formatDecimal(execution.tps, 0, 1)}"></div>
            <div class="label">TPS</div>
        </div>
        <div class="card metric-card">
            <div class="value" th:text="${#numbers.formatDecimal(execution.errorRate, 0, 1)} + '%'"></div>
            <div class="label">Error Rate</div>
        </div>
    </div>
    <div class="card">
        <h3>Response Time Distribution</h3>
        <table>
            <tr><th>p50</th><th>p90</th><th>p95</th><th>p99</th></tr>
            <tr>
                <td th:text="${#numbers.formatDecimal(execution.p50, 0, 1)} + 'ms'"></td>
                <td th:text="${#numbers.formatDecimal(execution.p90, 0, 1)} + 'ms'"></td>
                <td th:text="${#numbers.formatDecimal(execution.p95, 0, 1)} + 'ms'"></td>
                <td th:text="${#numbers.formatDecimal(execution.p99, 0, 1)} + 'ms'"></td>
            </tr>
        </table>
    </div>
    <div class="card">
        <h3>Summary</h3>
        <table>
            <tr><td>Status</td><td th:text="${execution.status}"></td></tr>
            <tr><td>Success</td><td th:text="${execution.successCount}"></td></tr>
            <tr><td>Failed</td><td th:text="${execution.failCount}"></td></tr>
            <tr><td>Started</td><td th:text="${#temporals.format(execution.startedAt, 'yyyy-MM-dd HH:mm:ss')}"></td></tr>
            <tr th:if="${execution.finishedAt != null}"><td>Finished</td><td th:text="${#temporals.format(execution.finishedAt, 'yyyy-MM-dd HH:mm:ss')}"></td></tr>
        </table>
    </div>
    <div style="margin-top:1rem;">
        <a th:if="${plan != null}" th:href="@{/executions/start/{id}(id=${plan.id})}" class="btn btn-success"
           onclick="event.preventDefault(); document.getElementById('rerun-form').submit();">Rerun</a>
        <form th:if="${plan != null}" id="rerun-form" th:action="@{/executions/start/{id}(id=${plan.id})}" method="post" style="display:none;"></form>
        <a href="/executions" class="btn btn-secondary">Back to List</a>
    </div>
</div>
</body>
</html>
```

- [ ] **Step 7: Create executions/live.html**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{layout :: head('Live Dashboard')}"></head>
<body>
<nav th:replace="~{layout :: nav}"></nav>
<div class="container">
    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:1rem;">
        <h2>Live Dashboard - Execution #<span th:text="${execution.id}"></span></h2>
        <form th:action="@{/executions/{id}/abort(id=${execution.id})}" method="post">
            <button type="submit" class="btn btn-danger" id="abort-btn">Abort</button>
        </form>
    </div>
    <div class="metric-cards">
        <div class="card metric-card"><div class="value" id="tps">-</div><div class="label">TPS</div></div>
        <div class="card metric-card"><div class="value" id="avg-latency">-</div><div class="label">Avg Latency</div></div>
        <div class="card metric-card"><div class="value" id="error-rate">-</div><div class="label">Error Rate</div></div>
        <div class="card metric-card"><div class="value" id="current-vu">-</div><div class="label">Active VU</div></div>
    </div>
    <div class="card">
        <h3>Response Time</h3>
        <canvas id="latency-chart" height="200"></canvas>
    </div>
    <div class="card">
        <h3>TPS</h3>
        <canvas id="tps-chart" height="200"></canvas>
    </div>
</div>

<script src="https://cdn.jsdelivr.net/npm/chart.js@4"></script>
<script th:inline="javascript">
    const executionId = [[${execution.id}]];
    const maxDataPoints = 120;

    const latencyData = { labels: [], datasets: [{ label: 'Avg (ms)', data: [], borderColor: '#4361ee', fill: false, tension: 0.3 }] };
    const tpsData = { labels: [], datasets: [{ label: 'TPS', data: [], borderColor: '#2a9d8f', fill: false, tension: 0.3 }] };

    const chartOpts = { responsive: true, animation: false, scales: { x: { display: false } } };
    const latencyChart = new Chart(document.getElementById('latency-chart'), { type: 'line', data: latencyData, options: chartOpts });
    const tpsChart = new Chart(document.getElementById('tps-chart'), { type: 'line', data: tpsData, options: chartOpts });

    const eventSource = new EventSource(`/executions/${executionId}/stream`);

    eventSource.addEventListener('metric', function(e) {
        const metrics = JSON.parse(e.data);
        const overall = metrics.find(m => m.stepName === null);
        if (!overall) return;

        const now = new Date().toLocaleTimeString();

        document.getElementById('tps').textContent = overall.tps.toFixed(1);
        document.getElementById('avg-latency').textContent = overall.avgResponseTime.toFixed(1) + 'ms';
        document.getElementById('current-vu').textContent = overall.currentVu;

        const errRate = overall.requestCount > 0 ? ((overall.errorCount / overall.requestCount) * 100).toFixed(1) : '0.0';
        document.getElementById('error-rate').textContent = errRate + '%';

        latencyData.labels.push(now);
        latencyData.datasets[0].data.push(overall.avgResponseTime);
        tpsData.labels.push(now);
        tpsData.datasets[0].data.push(overall.tps);

        if (latencyData.labels.length > maxDataPoints) { latencyData.labels.shift(); latencyData.datasets[0].data.shift(); }
        if (tpsData.labels.length > maxDataPoints) { tpsData.labels.shift(); tpsData.datasets[0].data.shift(); }

        latencyChart.update();
        tpsChart.update();
    });

    eventSource.addEventListener('complete', function() {
        eventSource.close();
        document.getElementById('abort-btn').disabled = true;
        document.getElementById('abort-btn').textContent = 'Completed';
        setTimeout(() => window.location.href = `/executions/${executionId}`, 2000);
    });

    eventSource.onerror = function() { eventSource.close(); };
</script>
</body>
</html>
```

- [ ] **Step 8: Build 확인**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add src/main/resources/templates/
git commit -m "feat: add Thymeleaf templates for plans, steps, executions, and live dashboard"
```

---

## Task 16: Integration Test + Smoke Test

**Files:**
- Create: `src/test/kotlin/com/traffic/web/PlanControllerTest.kt`
- Create: `src/test/kotlin/com/traffic/TrafficServiceApplicationTest.kt`

- [ ] **Step 1: Create application context test**

```kotlin
package com.traffic

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class TrafficServiceApplicationTest {

    @Test
    fun contextLoads() {
        // Verifies the entire application context starts successfully
    }
}
```

- [ ] **Step 2: Create PlanController integration test**

```kotlin
package com.traffic.web

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
class PlanControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET plans returns 200`() {
        mockMvc.perform(get("/plans"))
            .andExpect(status().isOk)
            .andExpect(view().name("plans/list"))
    }

    @Test
    fun `GET plans new returns 200`() {
        mockMvc.perform(get("/plans/new"))
            .andExpect(status().isOk)
            .andExpect(view().name("plans/form"))
    }

    @Test
    fun `POST plans creates and redirects`() {
        mockMvc.perform(post("/plans")
            .param("name", "Test Plan")
            .param("targetBaseUrl", "https://api.example.com"))
            .andExpect(status().is3xxRedirection)
    }

    @Test
    fun `GET executions returns 200`() {
        mockMvc.perform(get("/executions"))
            .andExpect(status().isOk)
            .andExpect(view().name("executions/list"))
    }
}
```

- [ ] **Step 3: Run all tests**

```bash
./gradlew test
```

Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add src/test/kotlin/com/traffic/
git commit -m "test: add integration tests for application context and plan controller"
```

---

## Task 17: Final Verification

- [ ] **Step 1: Run full build**

```bash
./gradlew clean build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Start application**

```bash
./gradlew bootRun
```

Expected: Application starts on port 8080

- [ ] **Step 3: Verify pages load**

Open browser:
- `http://localhost:8080/plans` — Plan list page
- `http://localhost:8080/plans/new` — New plan form
- `http://localhost:8080/executions` — Execution history

All pages should render without errors.

- [ ] **Step 4: Final commit**

```bash
git add .
git commit -m "chore: traffic service v0.1.0 - complete initial implementation"
```
