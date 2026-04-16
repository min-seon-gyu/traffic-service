package com.traffic.metric

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MetricAggregatorTest {

    private val aggregator = MetricAggregator()

    @Test
    fun `aggregate empty list returns empty result`() {
        val result = aggregator.aggregate(emptyList(), currentVu = 0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `aggregate produces overall snapshot with null stepName`() {
        val records = listOf(
            MetricRecord("login", 100, 200, true),
            MetricRecord("login", 200, 200, true),
            MetricRecord("search", 150, 200, true)
        )
        val result = aggregator.aggregate(records, currentVu = 5)
        val overall = result.find { it.stepName == null }

        assertNotNull(overall)
        assertEquals(3, overall!!.requestCount)
        assertEquals(0, overall.errorCount)
        assertEquals(5, overall.currentVu)
        assertEquals(150.0, overall.avgResponseTime, 0.1)
    }

    @Test
    fun `aggregate produces per-step snapshots`() {
        val records = listOf(
            MetricRecord("login", 100, 200, true),
            MetricRecord("login", 200, 500, false),
            MetricRecord("search", 150, 200, true)
        )
        val result = aggregator.aggregate(records, currentVu = 3)

        val login = result.find { it.stepName == "login" }
        assertNotNull(login)
        assertEquals(2, login!!.requestCount)
        assertEquals(1, login.errorCount)
        assertEquals(150.0, login.avgResponseTime, 0.1)

        val search = result.find { it.stepName == "search" }
        assertNotNull(search)
        assertEquals(1, search!!.requestCount)
        assertEquals(0, search.errorCount)
    }

    @Test
    fun `aggregate calculates p95 correctly`() {
        val records = (1..100).map { i ->
            MetricRecord("step", i.toLong(), 200, true)
        }
        val result = aggregator.aggregate(records, currentVu = 1)
        val overall = result.find { it.stepName == null }!!

        assertEquals(95.0, overall.p95ResponseTime, 1.0)
    }
}
