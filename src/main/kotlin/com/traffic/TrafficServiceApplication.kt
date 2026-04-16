package com.traffic

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TrafficServiceApplication

fun main(args: Array<String>) {
    runApplication<TrafficServiceApplication>(*args)
}
