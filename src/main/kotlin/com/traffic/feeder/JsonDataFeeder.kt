package com.traffic.feeder

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.traffic.domain.config.DataDistribution
import com.traffic.domain.config.EofStrategy
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class JsonDataFeeder(
    filePath: String,
    private val distribution: DataDistribution,
    private val onEof: EofStrategy
) : DataFeeder {

    private val rows: List<Map<String, String>>
    private val index = AtomicInteger(0)

    init {
        val mapper = jacksonObjectMapper()
        val rawList: List<Map<String, Any>> = mapper.readValue(File(filePath).readText())
        rows = rawList.map { row -> row.mapValues { it.value.toString() } }
    }

    override fun next(): Map<String, String>? {
        if (rows.isEmpty()) return null

        return when (distribution) {
            DataDistribution.RANDOM -> rows[Random.nextInt(rows.size)]
            DataDistribution.SEQUENTIAL, DataDistribution.CIRCULAR -> {
                val currentIndex = index.getAndIncrement()
                if (currentIndex >= rows.size) {
                    when (onEof) {
                        EofStrategy.STOP_VU -> null
                        EofStrategy.RECYCLE -> {
                            index.set(1)
                            rows[0]
                        }
                    }
                } else {
                    rows[currentIndex]
                }
            }
        }
    }
}
