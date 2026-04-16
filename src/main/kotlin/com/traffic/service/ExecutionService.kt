package com.traffic.service

import com.traffic.domain.config.Threshold
import com.traffic.domain.config.ThresholdMetric
import com.traffic.domain.execution.*
import com.traffic.domain.plan.TestPlan
import com.traffic.domain.plan.TestStepRepository
import com.traffic.engine.LoadEngine
import com.traffic.engine.StepExecutor
import com.traffic.engine.StepSpec
import com.traffic.metric.AggregatedMetric
import com.traffic.metric.MetricBuffer
import jakarta.annotation.PostConstruct
import jakarta.transaction.Transactional
import kotlinx.coroutines.*
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

@Service
class ExecutionService(
    private val executionRepository: TestExecutionRepository,
    private val snapshotRepository: MetricSnapshotRepository,
    private val stepRepository: TestStepRepository,
    private val stepExecutor: StepExecutor,
    private val metricStreamService: MetricStreamService
) {
    private val runningEngines = ConcurrentHashMap<Long, LoadEngine>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @PostConstruct
    fun cleanupOrphanedExecutions() {
        val orphaned = executionRepository.findAll().filter { it.status == ExecutionStatus.RUNNING }
        orphaned.forEach {
            it.status = ExecutionStatus.ABORTED
            it.finishedAt = it.finishedAt ?: LocalDateTime.now()
            executionRepository.save(it)
        }
    }

    fun startExecution(plan: TestPlan): TestExecution {
        val execution = executionRepository.save(
            TestExecution(testPlanId = plan.id)
        )

        val steps = stepRepository.findByTestPlanIdOrderByOrderIndexAsc(plan.id)
        val stepSpecs = steps.map { step ->
            StepSpec(
                name = step.name,
                httpMethod = step.httpMethod,
                path = step.path,
                headers = step.headers,
                body = step.body,
                thinkTimeMs = step.thinkTimeMs,
                extractors = step.extractors,
                validators = step.validators
            )
        }

        val metricBuffer = MetricBuffer()
        val engine = LoadEngine(stepExecutor, metricBuffer)
        runningEngines[execution.id] = engine

        scope.launch {
            try {
                engine.run(
                    steps = stepSpecs,
                    baseUrl = plan.targetBaseUrl,
                    loadConfig = plan.loadConfig,
                    safetyConfig = plan.safetyConfig,
                    advancedConfig = plan.advancedConfig,
                    authConfig = plan.authConfig,
                    onMetricSnapshot = { metrics ->
                        handleMetricSnapshot(execution.id, metrics)
                    }
                )
                finalizeExecution(execution.id, plan, ExecutionStatus.COMPLETED)
            } catch (e: CancellationException) {
                finalizeExecution(execution.id, plan, ExecutionStatus.ABORTED)
            } catch (e: Exception) {
                finalizeExecution(execution.id, plan, ExecutionStatus.ABORTED)
            } finally {
                runningEngines.remove(execution.id)
                metricStreamService.complete(execution.id)
            }
        }

        return execution
    }

    fun abortExecution(executionId: Long) {
        runningEngines[executionId]?.abort()
        val execution = executionRepository.findById(executionId).orElse(null) ?: return
        if (execution.status == ExecutionStatus.RUNNING) {
            execution.status = ExecutionStatus.ABORTED
            execution.finishedAt = LocalDateTime.now()
            executionRepository.save(execution)
        }
    }

    fun getExecution(id: Long): TestExecution? = executionRepository.findById(id).orElse(null)

    fun getAllExecutions(): List<TestExecution> = executionRepository.findAllByOrderByStartedAtDesc()

    fun getSnapshots(executionId: Long): List<MetricSnapshot> = snapshotRepository.findByExecutionIdOrderByTimestampAsc(executionId)

    fun getOverallSnapshots(executionId: Long): List<MetricSnapshot> = snapshotRepository.findByExecutionIdAndStepNameIsNullOrderByTimestampAsc(executionId)

    @Transactional
    fun deleteExecution(id: Long) {
        runningEngines[id]?.abort()
        runningEngines.remove(id)
        snapshotRepository.deleteByExecutionId(id)
        executionRepository.deleteById(id)
    }

    private fun handleMetricSnapshot(executionId: Long, metrics: List<AggregatedMetric>) {
        metrics.forEach { metric ->
            snapshotRepository.save(
                MetricSnapshot(
                    executionId = executionId,
                    stepName = metric.stepName,
                    currentVu = metric.currentVu,
                    requestCount = metric.requestCount,
                    errorCount = metric.errorCount,
                    avgResponseTime = metric.avgResponseTime,
                    p95ResponseTime = metric.p95ResponseTime,
                    tps = metric.tps
                )
            )
        }

        metricStreamService.broadcast(executionId, metrics)
    }

    private fun finalizeExecution(executionId: Long, plan: TestPlan, status: ExecutionStatus) {
        val snapshots = snapshotRepository.findByExecutionIdAndStepNameIsNullOrderByTimestampAsc(executionId)
        val execution = executionRepository.findById(executionId).orElse(null) ?: return

        val totalRequests = snapshots.sumOf { it.requestCount }.toLong()
        val totalErrors = snapshots.sumOf { it.errorCount }.toLong()
        val totalSuccess = totalRequests - totalErrors
        val avgLatency = if (snapshots.isNotEmpty()) snapshots.map { it.avgResponseTime }.average() else 0.0
        val avgTps = if (snapshots.isNotEmpty()) snapshots.map { it.tps }.average() else 0.0
        val errorRate = if (totalRequests > 0) (totalErrors.toDouble() / totalRequests) * 100 else 0.0

        execution.status = status
        execution.finishedAt = LocalDateTime.now()
        execution.totalRequests = totalRequests
        execution.successCount = totalSuccess
        execution.failCount = totalErrors
        execution.avgResponseTime = avgLatency
        execution.tps = avgTps
        execution.errorRate = errorRate

        val p95Values = snapshots.map { it.p95ResponseTime }.sorted()
        execution.p50 = percentileFromList(p95Values, 50.0)
        execution.p90 = percentileFromList(p95Values, 90.0)
        execution.p95 = percentileFromList(p95Values, 95.0)
        execution.p99 = percentileFromList(p95Values, 99.0)

        execution.result = evaluateThresholds(plan.thresholds, execution)

        executionRepository.save(execution)
    }

    private fun evaluateThresholds(thresholds: List<Threshold>, execution: TestExecution): ExecutionResult {
        if (thresholds.isEmpty()) return ExecutionResult.PASS

        return if (thresholds.all { threshold ->
            val actualValue = when (threshold.metric) {
                ThresholdMetric.AVG -> execution.avgResponseTime
                ThresholdMetric.P50 -> execution.p50
                ThresholdMetric.P90 -> execution.p90
                ThresholdMetric.P95 -> execution.p95
                ThresholdMetric.P99 -> execution.p99
                ThresholdMetric.ERROR_RATE -> execution.errorRate
                ThresholdMetric.TPS -> execution.tps
            }
            threshold.evaluate(actualValue)
        }) ExecutionResult.PASS else ExecutionResult.FAIL
    }

    private fun percentileFromList(sorted: List<Double>, percentile: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val index = ((percentile / 100.0) * (sorted.size - 1)).toInt()
        return sorted[index]
    }
}
