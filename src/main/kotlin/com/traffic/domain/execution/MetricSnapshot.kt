package com.traffic.domain.execution

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "metric_snapshots")
class MetricSnapshot(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    val executionId: Long,

    val stepName: String? = null,

    @Column(nullable = false)
    val timestamp: LocalDateTime = LocalDateTime.now(),

    val currentVu: Int = 0,
    val requestCount: Int = 0,
    val errorCount: Int = 0,
    val avgResponseTime: Double = 0.0,
    val p95ResponseTime: Double = 0.0,
    val tps: Double = 0.0
)
