package com.traffic.domain.plan

import org.springframework.data.jpa.repository.JpaRepository

interface TestStepRepository : JpaRepository<TestStep, Long> {
    fun findByTestPlanIdOrderByOrderIndexAsc(testPlanId: Long): List<TestStep>
    fun deleteByTestPlanId(testPlanId: Long)
}
