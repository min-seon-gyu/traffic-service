package com.traffic.web

import com.traffic.service.ExecutionService
import com.traffic.service.MetricStreamService
import com.traffic.service.TestPlanService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Controller
class DashboardController(
    private val executionService: ExecutionService,
    private val metricStreamService: MetricStreamService,
    private val planService: TestPlanService
) {

    @GetMapping("/executions/{id}/live")
    fun liveDashboard(@PathVariable id: Long, model: Model): String {
        val execution = executionService.getExecution(id) ?: return "redirect:/executions"
        val plan = planService.findById(execution.testPlanId)
        model.addAttribute("execution", execution)
        model.addAttribute("plan", plan)
        return "executions/live"
    }

    @GetMapping("/executions/{id}/stream")
    @ResponseBody
    fun stream(@PathVariable id: Long): SseEmitter {
        return metricStreamService.subscribe(id)
    }
}
