package com.traffic.feeder

import com.traffic.domain.config.DataDistribution
import com.traffic.domain.config.EofStrategy
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class JsonDataFeederTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `reads JSON array and returns rows sequentially`() {
        val file = tempDir.resolve("data.json")
        file.writeText("""[{"name":"alice","age":"30"},{"name":"bob","age":"25"}]""")

        val feeder = JsonDataFeeder(file.toString(), DataDistribution.SEQUENTIAL, EofStrategy.RECYCLE)

        assertEquals(mapOf("name" to "alice", "age" to "30"), feeder.next())
        assertEquals(mapOf("name" to "bob", "age" to "25"), feeder.next())
    }
}
