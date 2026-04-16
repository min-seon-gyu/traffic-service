package com.traffic.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VariableResolverTest {

    private val resolver = VariableResolver()

    @Test
    fun `resolve replaces single variable`() {
        val variables = mapOf("token" to "abc123")
        val result = resolver.resolve("Bearer {{token}}", variables)
        assertEquals("Bearer abc123", result)
    }

    @Test
    fun `resolve replaces multiple variables`() {
        val variables = mapOf("host" to "api.example.com", "id" to "42")
        val result = resolver.resolve("https://{{host}}/items/{{id}}", variables)
        assertEquals("https://api.example.com/items/42", result)
    }

    @Test
    fun `resolve leaves unknown variables unchanged`() {
        val variables = mapOf("token" to "abc")
        val result = resolver.resolve("{{token}} and {{unknown}}", variables)
        assertEquals("abc and {{unknown}}", result)
    }

    @Test
    fun `resolve handles empty variables map`() {
        val result = resolver.resolve("no variables here", emptyMap())
        assertEquals("no variables here", result)
    }

    @Test
    fun `resolve handles null input`() {
        val result = resolver.resolve(null, mapOf("a" to "b"))
        assertEquals(null, result)
    }

    @Test
    fun `resolveMap replaces variables in map values`() {
        val variables = mapOf("token" to "abc123")
        val headers = mapOf("Authorization" to "Bearer {{token}}", "Content-Type" to "application/json")
        val result = resolver.resolveMap(headers, variables)
        assertEquals(mapOf("Authorization" to "Bearer abc123", "Content-Type" to "application/json"), result)
    }
}
