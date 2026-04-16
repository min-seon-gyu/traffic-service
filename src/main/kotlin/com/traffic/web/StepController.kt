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
        @RequestParam(required = false) thinkTimeMs: Int?,
        @RequestParam(required = false) extractorSource: List<String>?,
        @RequestParam(required = false) extractorJsonPath: List<String>?,
        @RequestParam(required = false) extractorVariable: List<String>?,
        @RequestParam(required = false) validatorType: List<String>?,
        @RequestParam(required = false) validatorExpected: List<String>?
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
                headers = parseHeaders(headers),
                body = body,
                thinkTimeMs = thinkTimeMs,
                extractors = buildExtractors(extractorSource, extractorJsonPath, extractorVariable),
                validators = buildValidators(validatorType, validatorExpected)
            )
        )

        return "redirect:/plans/$planId"
    }

    private fun buildExtractors(
        sources: List<String>?,
        jsonPaths: List<String>?,
        variables: List<String>?
    ): List<Extractor> {
        if (sources == null || jsonPaths == null || variables == null) return emptyList()
        return sources.indices
            .filter { i -> i < jsonPaths.size && i < variables.size }
            .filter { i -> jsonPaths[i].isNotBlank() && variables[i].isNotBlank() }
            .map { i ->
                Extractor(
                    source = try { ExtractorSource.valueOf(sources[i]) } catch (_: Exception) { ExtractorSource.BODY },
                    jsonPath = jsonPaths[i].trim(),
                    variableName = variables[i].trim()
                )
            }
    }

    private fun buildValidators(
        types: List<String>?,
        expecteds: List<String>?
    ): List<StepValidator> {
        if (types == null || expecteds == null) return emptyList()
        return types.indices
            .filter { i -> i < expecteds.size && expecteds[i].isNotBlank() }
            .map { i ->
                StepValidator(
                    type = try { ValidatorType.valueOf(types[i]) } catch (_: Exception) { ValidatorType.STATUS },
                    expected = expecteds[i].trim()
                )
            }
    }

    @PostMapping("/{stepId}")
    fun updateStep(
        @PathVariable planId: Long,
        @PathVariable stepId: Long,
        @RequestParam name: String,
        @RequestParam httpMethod: HttpMethod,
        @RequestParam path: String,
        @RequestParam(required = false) headers: String?,
        @RequestParam(required = false) body: String?,
        @RequestParam(required = false) thinkTimeMs: Int?,
        @RequestParam(required = false) extractorSource: List<String>?,
        @RequestParam(required = false) extractorJsonPath: List<String>?,
        @RequestParam(required = false) extractorVariable: List<String>?,
        @RequestParam(required = false) validatorType: List<String>?,
        @RequestParam(required = false) validatorExpected: List<String>?
    ): String {
        val step = stepRepository.findById(stepId).orElse(null) ?: return "redirect:/plans/$planId"
        step.name = name
        step.httpMethod = httpMethod
        step.path = path
        step.headers = parseHeaders(headers)
        step.body = body
        step.thinkTimeMs = thinkTimeMs
        step.extractors = buildExtractors(extractorSource, extractorJsonPath, extractorVariable)
        step.validators = buildValidators(validatorType, validatorExpected)
        stepRepository.save(step)
        return "redirect:/plans/$planId"
    }

    @PostMapping("/{stepId}/delete")
    fun deleteStep(@PathVariable planId: Long, @PathVariable stepId: Long): String {
        stepRepository.deleteById(stepId)
        return "redirect:/plans/$planId"
    }

    private fun parseHeaders(headers: String?): Map<String, String> {
        if (headers.isNullOrBlank()) return emptyMap()
        return try {
            mapper.readValue(headers, mapper.typeFactory.constructMapType(Map::class.java, String::class.java, String::class.java))
        } catch (_: Exception) { emptyMap() }
    }
}
