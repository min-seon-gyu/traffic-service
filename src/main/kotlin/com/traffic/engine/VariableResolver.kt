package com.traffic.engine

class VariableResolver {

    private val pattern = Regex("""\{\{(\w+)}}""")

    fun resolve(template: String?, variables: Map<String, String>): String? {
        if (template == null) return null
        return pattern.replace(template) { match ->
            val varName = match.groupValues[1]
            variables[varName] ?: match.value
        }
    }

    fun resolveMap(templates: Map<String, String>, variables: Map<String, String>): Map<String, String> =
        templates.mapValues { (_, value) -> resolve(value, variables) ?: value }
}
