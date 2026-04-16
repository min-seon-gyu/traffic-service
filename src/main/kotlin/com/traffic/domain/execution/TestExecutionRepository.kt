package com.traffic.domain.execution

import org.springframework.data.jpa.repository.JpaRepository

interface TestExecutionRepository : JpaRepository<TestExecution, Long> {
    fun findByTestPlanIdOrderByStartedAtDesc(testPlanId: Long): List<TestExecution>
    fun findAllByOrderByStartedAtDesc(): List<TestExecution>
}
