package com.traffic.web

import com.traffic.domain.config.*
import com.traffic.domain.plan.TestPlan
import com.traffic.service.TestPlanService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*

private fun buildAdvancedConfig(
    timeoutMs: Int,
    thinkTimeStrategy: String?,
    thinkTimeMs: Int,
    thinkTimeMin: Int,
    thinkTimeMax: Int,
    discardResponseBody: Boolean
): AdvancedConfig {
    val strategy = try {
        thinkTimeStrategy?.let { ThinkTimeStrategy.valueOf(it) }
    } catch (_: Exception) { null } ?: ThinkTimeStrategy.CONSTANT
    return AdvancedConfig(strategy, thinkTimeMs, thinkTimeMin, thinkTimeMax, timeoutMs, discardResponseBody)
}

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
        @RequestParam(defaultValue = "10000") timeoutMs: Int,
        @RequestParam(required = false) thinkTimeStrategy: String?,
        @RequestParam(defaultValue = "0") thinkTimeMs: Int,
        @RequestParam(defaultValue = "0") thinkTimeMin: Int,
        @RequestParam(defaultValue = "0") thinkTimeMax: Int,
        @RequestParam(defaultValue = "false") discardResponseBody: Boolean,
        @RequestParam(required = false) authMode: String?,
        @RequestParam(required = false) authTokens: String?,
        @RequestParam(required = false) thresholdMetric: List<String>?,
        @RequestParam(required = false) thresholdOperator: List<String>?,
        @RequestParam(required = false) thresholdValue: List<String>?,
        @RequestParam(required = false) stageDuration: List<String>?,
        @RequestParam(required = false) stageTargetVu: List<String>?,
        @RequestParam(required = false) dsFilePath: String?,
        @RequestParam(required = false) dsDistribution: String?,
        @RequestParam(required = false) dsOnEof: String?
    ): String {
        val plan = TestPlan(
            name = name,
            targetBaseUrl = targetBaseUrl,
            description = description,
            loadConfig = LoadConfig(vuCount, requestsPerVu, rampUpSeconds, durationSeconds,
                stages = buildStages(stageDuration, stageTargetVu), maxDuration = maxDuration),
            safetyConfig = SafetyConfig(abortOnErrorRate, gracefulStopTimeout),
            advancedConfig = buildAdvancedConfig(timeoutMs, thinkTimeStrategy, thinkTimeMs, thinkTimeMin, thinkTimeMax, discardResponseBody),
            authConfig = buildAuthConfig(authMode, authTokens),
            thresholds = buildThresholds(thresholdMetric, thresholdOperator, thresholdValue),
            dataSourceConfig = buildDataSourceConfig(dsFilePath, dsDistribution, dsOnEof)
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
        @RequestParam(defaultValue = "10000") timeoutMs: Int,
        @RequestParam(required = false) thinkTimeStrategy: String?,
        @RequestParam(defaultValue = "0") thinkTimeMs: Int,
        @RequestParam(defaultValue = "0") thinkTimeMin: Int,
        @RequestParam(defaultValue = "0") thinkTimeMax: Int,
        @RequestParam(defaultValue = "false") discardResponseBody: Boolean,
        @RequestParam(required = false) authMode: String?,
        @RequestParam(required = false) authTokens: String?,
        @RequestParam(required = false) thresholdMetric: List<String>?,
        @RequestParam(required = false) thresholdOperator: List<String>?,
        @RequestParam(required = false) thresholdValue: List<String>?,
        @RequestParam(required = false) stageDuration: List<String>?,
        @RequestParam(required = false) stageTargetVu: List<String>?,
        @RequestParam(required = false) dsFilePath: String?,
        @RequestParam(required = false) dsDistribution: String?,
        @RequestParam(required = false) dsOnEof: String?
    ): String {
        val plan = planService.findById(id) ?: return "redirect:/plans"
        plan.name = name
        plan.targetBaseUrl = targetBaseUrl
        plan.description = description
        plan.loadConfig = LoadConfig(vuCount, requestsPerVu, rampUpSeconds, durationSeconds,
            stages = buildStages(stageDuration, stageTargetVu), maxDuration = maxDuration)
        plan.safetyConfig = SafetyConfig(abortOnErrorRate, gracefulStopTimeout)
        plan.advancedConfig = buildAdvancedConfig(timeoutMs, thinkTimeStrategy, thinkTimeMs, thinkTimeMin, thinkTimeMax, discardResponseBody)
        plan.authConfig = buildAuthConfig(authMode, authTokens)
        plan.thresholds = buildThresholds(thresholdMetric, thresholdOperator, thresholdValue)
        plan.dataSourceConfig = buildDataSourceConfig(dsFilePath, dsDistribution, dsOnEof)
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

    private fun buildAuthConfig(mode: String?, tokens: String?): AuthConfig {
        val authMode = try { mode?.let { AuthMode.valueOf(it) } } catch (_: Exception) { null } ?: AuthMode.LOGIN_STEP
        val tokenList = tokens?.split("\n")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        return AuthConfig(authMode, tokenList)
    }

    private fun buildThresholds(metrics: List<String>?, operators: List<String>?, values: List<String>?): List<Threshold> {
        if (metrics == null || operators == null || values == null) return emptyList()
        return metrics.indices
            .filter { i -> i < operators.size && i < values.size && values[i].isNotBlank() }
            .mapNotNull { i ->
                try {
                    Threshold(
                        metric = ThresholdMetric.valueOf(metrics[i]),
                        operator = ThresholdOperator.valueOf(operators[i]),
                        value = values[i].toDouble()
                    )
                } catch (_: Exception) { null }
            }
    }

    private fun buildStages(durations: List<String>?, targetVus: List<String>?): List<Stage> {
        if (durations == null || targetVus == null) return emptyList()
        return durations.indices
            .filter { i -> i < targetVus.size && durations[i].isNotBlank() && targetVus[i].isNotBlank() }
            .mapNotNull { i ->
                try { Stage(durations[i].toInt(), targetVus[i].toInt()) } catch (_: Exception) { null }
            }
    }

    private fun buildDataSourceConfig(filePath: String?, distribution: String?, onEof: String?): DataSourceConfig? {
        if (filePath.isNullOrBlank()) return null
        val dist = try { distribution?.let { DataDistribution.valueOf(it) } } catch (_: Exception) { null } ?: DataDistribution.SEQUENTIAL
        val eof = try { onEof?.let { EofStrategy.valueOf(it) } } catch (_: Exception) { null } ?: EofStrategy.RECYCLE
        return DataSourceConfig(filePath, dist, eof)
    }
}
