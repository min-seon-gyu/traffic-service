package com.traffic.feeder

import com.traffic.domain.config.DataDistribution
import com.traffic.domain.config.EofStrategy
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class CsvDataFeeder(
    filePath: String,
    private val distribution: DataDistribution,
    private val onEof: EofStrategy
) : DataFeeder {

    private val headers: List<String>
    private val rows: List<List<String>>
    private val index = AtomicInteger(0)

    init {
        val lines = File(filePath).readLines()
        headers = lines.first().split(",").map { it.trim() }
        rows = lines.drop(1).map { line -> line.split(",").map { it.trim() } }
    }

    override fun next(): Map<String, String>? {
        if (rows.isEmpty()) return null

        val row = when (distribution) {
            DataDistribution.RANDOM -> rows[Random.nextInt(rows.size)]
            DataDistribution.SEQUENTIAL, DataDistribution.CIRCULAR -> {
                val currentIndex = index.getAndIncrement()
                if (currentIndex >= rows.size) {
                    when (onEof) {
                        EofStrategy.STOP_VU -> return null
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

        return headers.zip(row).toMap()
    }
}
