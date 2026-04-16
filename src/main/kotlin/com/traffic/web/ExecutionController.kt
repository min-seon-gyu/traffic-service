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
        val executions = executionService.getAllExecutions()
        val planNames = executions.map { it.testPlanId }.distinct()
            .associateWith { planService.findById(it)?.name ?: "(삭제된 플랜)" }
        model.addAttribute("executions", executions)
        model.addAttribute("planNames", planNames)
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
        val allSnapshots = executionService.getSnapshots(id)
        val overallSnapshots = allSnapshots.filter { it.stepName == null }
        val stepSnapshots = allSnapshots.filter { it.stepName != null }

        // Step별 집계
        val stepSummary = stepSnapshots.groupBy { it.stepName!! }.map { (name, snaps) ->
            mapOf(
                "name" to name,
                "requestCount" to snaps.sumOf { it.requestCount },
                "errorCount" to snaps.sumOf { it.errorCount },
                "avgResponseTime" to if (snaps.isNotEmpty()) snaps.map { it.avgResponseTime }.average() else 0.0,
                "p95ResponseTime" to if (snaps.isNotEmpty()) snaps.map { it.p95ResponseTime }.average() else 0.0
            )
        }

        model.addAttribute("execution", execution)
        model.addAttribute("plan", plan)
        model.addAttribute("stepSummary", stepSummary)
        model.addAttribute("chartAvg", overallSnapshots.map { it.avgResponseTime })
        model.addAttribute("chartP95", overallSnapshots.map { it.p95ResponseTime })
        model.addAttribute("chartTps", overallSnapshots.map { it.tps })
        model.addAttribute("hasSnapshots", overallSnapshots.isNotEmpty())
        return "executions/detail"
    }

    @PostMapping("/{id}/abort")
    fun abort(@PathVariable id: Long): String {
        executionService.abortExecution(id)
        return "redirect:/executions/$id/live"
    }

    @PostMapping("/{id}/delete")
    fun delete(@PathVariable id: Long): String {
        executionService.deleteExecution(id)
        return "redirect:/executions"
    }
}
