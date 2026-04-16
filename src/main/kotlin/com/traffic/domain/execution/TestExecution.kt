package com.traffic.domain.execution

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "test_executions")
class TestExecution(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    val testPlanId: Long,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var status: ExecutionStatus = ExecutionStatus.RUNNING,

    @Enumerated(EnumType.STRING)
    var result: ExecutionResult? = null,

    @Column(nullable = false)
    val startedAt: LocalDateTime = LocalDateTime.now(),

    var finishedAt: LocalDateTime? = null,

    var totalRequests: Long = 0,
    var successCount: Long = 0,
    var failCount: Long = 0,
    var avgResponseTime: Double = 0.0,
    var p50: Double = 0.0,
    var p90: Double = 0.0,
    var p95: Double = 0.0,
    var p99: Double = 0.0,
    var tps: Double = 0.0,
    var errorRate: Double = 0.0
)

enum class ExecutionStatus {
    RUNNING, COMPLETED, ABORTED
}

enum class ExecutionResult {
    PASS, FAIL
}
