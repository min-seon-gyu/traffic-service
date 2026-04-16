package com.traffic.feeder

import com.traffic.domain.config.DataDistribution
import com.traffic.domain.config.EofStrategy
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class CsvDataFeederTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `sequential distribution returns rows in order`() {
        val file = tempDir.resolve("data.csv")
        file.writeText("username,password\nalice,pass1\nbob,pass2\ncharlie,pass3")

        val feeder = CsvDataFeeder(file.toString(), DataDistribution.SEQUENTIAL, EofStrategy.RECYCLE)

        assertEquals(mapOf("username" to "alice", "password" to "pass1"), feeder.next())
        assertEquals(mapOf("username" to "bob", "password" to "pass2"), feeder.next())
        assertEquals(mapOf("username" to "charlie", "password" to "pass3"), feeder.next())
    }

    @Test
    fun `recycle strategy wraps around`() {
        val file = tempDir.resolve("data.csv")
        file.writeText("name\nalice\nbob")

        val feeder = CsvDataFeeder(file.toString(), DataDistribution.SEQUENTIAL, EofStrategy.RECYCLE)

        feeder.next() // alice
        feeder.next() // bob
        val third = feeder.next()
        assertEquals(mapOf("name" to "alice"), third)
    }

    @Test
    fun `stop_vu strategy returns null at EOF`() {
        val file = tempDir.resolve("data.csv")
        file.writeText("name\nalice")

        val feeder = CsvDataFeeder(file.toString(), DataDistribution.SEQUENTIAL, EofStrategy.STOP_VU)

        assertNotNull(feeder.next())
        assertNull(feeder.next())
    }

    @Test
    fun `circular distribution returns rows in order then wraps`() {
        val file = tempDir.resolve("data.csv")
        file.writeText("name\nalice\nbob")

        val feeder = CsvDataFeeder(file.toString(), DataDistribution.CIRCULAR, EofStrategy.RECYCLE)

        assertEquals("alice", feeder.next()!!["name"])
        assertEquals("bob", feeder.next()!!["name"])
        assertEquals("alice", feeder.next()!!["name"])
    }
}
