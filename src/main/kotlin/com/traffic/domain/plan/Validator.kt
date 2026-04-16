package com.traffic.domain.plan

data class StepValidator(
    val type: ValidatorType,
    val expected: String
)

enum class ValidatorType {
    STATUS, BODY_CONTAINS
}
