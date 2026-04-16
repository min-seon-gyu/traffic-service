package com.traffic.web

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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

    private val mapper = jacksonObjectMapper()

    @PostMapping
    fun addStep(
        @PathVariable planId: Long,
        @RequestParam name: String,
        @RequestParam httpMethod: HttpMethod,
        @RequestParam path: String,
        @RequestParam(required = false) headers: String?,
        @RequestParam(required = false) body: String?,
        @RequestParam(required = false) thinkTimeMs: Int?
    ): String {
        val plan = planService.findById(planId) ?: return "redirect:/plans"
        val steps = planService.getSteps(planId)
        val nextIndex = (steps.maxOfOrNull { it.orderIndex } ?: -1) + 1

        val parsedHeaders: Map<String, String> = if (!headers.isNullOrBlank()) {
            try {
                mapper.readValue(headers, mapper.typeFactory.constructMapType(Map::class.java, String::class.java, String::class.java))
            } catch (e: Exception) {
                emptyMap()
            }
        } else emptyMap()

        stepRepository.save(
            TestStep(
                testPlan = plan,
                orderIndex = nextIndex,
                name = name,
                httpMethod = httpMethod,
                path = path,
                headers = parsedHeaders,
                body = body,
                thinkTimeMs = thinkTimeMs
            )
        )

        return "redirect:/plans/$planId"
    }

    @PostMapping("/{stepId}/delete")
    fun deleteStep(@PathVariable planId: Long, @PathVariable stepId: Long): String {
        stepRepository.deleteById(stepId)
        return "redirect:/plans/$planId"
    }
}
