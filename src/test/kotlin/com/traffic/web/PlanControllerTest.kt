package com.traffic.web

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
class PlanControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET plans returns 200`() {
        mockMvc.perform(get("/plans"))
            .andExpect(status().isOk)
            .andExpect(view().name("plans/list"))
    }

    @Test
    fun `GET plans new returns 200`() {
        mockMvc.perform(get("/plans/new"))
            .andExpect(status().isOk)
            .andExpect(view().name("plans/form"))
    }

    @Test
    fun `POST plans creates and redirects`() {
        mockMvc.perform(post("/plans")
            .param("name", "Test Plan")
            .param("targetBaseUrl", "https://api.example.com"))
            .andExpect(status().is3xxRedirection)
    }

    @Test
    fun `GET executions returns 200`() {
        mockMvc.perform(get("/executions"))
            .andExpect(status().isOk)
            .andExpect(view().name("executions/list"))
    }
}
