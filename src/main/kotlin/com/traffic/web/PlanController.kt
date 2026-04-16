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
