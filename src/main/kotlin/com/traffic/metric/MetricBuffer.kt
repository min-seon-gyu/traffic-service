package com.traffic.metric

import java.util.concurrent.ConcurrentLinkedQueue

class MetricBuffer {

    private val queue = ConcurrentLinkedQueue<MetricRecord>()

    fun add(record: MetricRecord) {
        queue.add(record)
    }

    fun drain(): List<MetricRecord> {
        val records = mutableListOf<MetricRecord>()
        while (true) {
            val record = queue.poll() ?: break
            records.add(record)
        }
        return records
    }
}
