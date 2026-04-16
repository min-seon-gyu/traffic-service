package com.traffic.domain.config

data class DataSourceConfig(
    val filePath: String,
    val distribution: DataDistribution = DataDistribution.SEQUENTIAL,
    val onEof: EofStrategy = EofStrategy.RECYCLE
)

enum class DataDistribution {
    SEQUENTIAL, RANDOM, CIRCULAR
}

enum class EofStrategy {
    RECYCLE, STOP_VU
}
