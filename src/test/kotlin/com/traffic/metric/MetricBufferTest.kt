package com.traffic.metric

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MetricBufferTest {

    @Test
    fun `add and drain returns all records`() {
        val buffer = MetricBuffer()
        buffer.add(MetricRecord(stepName = "login", latencyMs = 100, statusCode = 200, success = true))
        buffer.add(MetricRecord(stepName = "login", latencyMs = 200, statusCode = 200, success = true))
        buffer.add(MetricRecord(stepName = "search", latencyMs = 150, statusCode = 200, success = true))

        val records = buffer.drain()
        assertEquals(3, records.size)
    }

    @Test
    fun `drain clears the buffer`() {
        val buffer = MetricBuffer()
        buffer.add(MetricRecord(stepName = "login", latencyMs = 100, statusCode = 200, success = true))

        buffer.drain()
        val second = buffer.drain()
        assertTrue(second.isEmpty())
    }

    @Test
    fun `drain on empty buffer returns empty list`() {
        val buffer = MetricBuffer()
        assertTrue(buffer.drain().isEmpty())
    }
}
