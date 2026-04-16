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
