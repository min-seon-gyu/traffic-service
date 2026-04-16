package com.traffic.domain.execution

import org.springframework.data.jpa.repository.JpaRepository

interface MetricSnapshotRepository : JpaRepository<MetricSnapshot, Long> {
    fun findByExecutionIdAndStepNameIsNullOrderByTimestampAsc(executionId: Long): List<MetricSnapshot>
    fun findByExecutionIdAndStepNameOrderByTimestampAsc(executionId: Long, stepName: String): List<MetricSnapshot>
    fun findByExecutionIdOrderByTimestampAsc(executionId: Long): List<MetricSnapshot>
    fun deleteByExecutionId(executionId: Long)
}
