package com.traffic.domain.config

data class AuthConfig(
    val mode: AuthMode = AuthMode.LOGIN_STEP,
    val tokens: List<String> = emptyList()
)

enum class AuthMode {
    LOGIN_STEP, TOKEN_POOL
}
