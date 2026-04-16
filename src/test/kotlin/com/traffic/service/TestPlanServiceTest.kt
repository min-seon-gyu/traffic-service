package com.traffic.service

import com.traffic.domain.config.LoadConfig
import com.traffic.domain.plan.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Optional

class TestPlanServiceTest {

    private val planRepo = mockk<TestPlanRepository>(relaxed = true)
    private val stepRepo = mockk<TestStepRepository>(relaxed = true)
    private val service = TestPlanService(planRepo, stepRepo)

    @Test
    fun `findAll returns all plans`() {
        val plans = listOf(
            TestPlan(id = 1, name = "Plan 1", targetBaseUrl = "https://a.com"),
            TestPlan(id = 2, name = "Plan 2", targetBaseUrl = "https://b.com")
        )
        every { planRepo.findAll() } returns plans

        val result = service.findAll()
        assertEquals(2, result.size)
    }

    @Test
    fun `findById returns plan with steps`() {
        val plan = TestPlan(id = 1, name = "Plan", targetBaseUrl = "https://a.com")
        every { planRepo.findById(1L) } returns Optional.of(plan)

        val result = service.findById(1L)
        assertNotNull(result)
        assertEquals("Plan", result!!.name)
    }

    @Test
    fun `findById returns null when not found`() {
        every { planRepo.findById(999L) } returns Optional.empty()

        val result = service.findById(999L)
        assertNull(result)
    }

    @Test
    fun `delete removes plan`() {
        service.delete(1L)
        verify { planRepo.deleteById(1L) }
    }

    @Test
    fun `duplicate creates copy with new name`() {
        val original = TestPlan(id = 1, name = "Original", targetBaseUrl = "https://a.com",
            loadConfig = LoadConfig(vuCount = 10))
        every { planRepo.findById(1L) } returns Optional.of(original)
        every { stepRepo.findByTestPlanIdOrderByOrderIndexAsc(1L) } returns emptyList()
        every { planRepo.save(any()) } answers { firstArg() }

        val result = service.duplicate(1L)
        assertNotNull(result)
        assertEquals("Original (copy)", result!!.name)
        assertEquals(10, result.loadConfig.vuCount)
    }
}
