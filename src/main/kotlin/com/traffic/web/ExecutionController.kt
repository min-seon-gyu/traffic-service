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
