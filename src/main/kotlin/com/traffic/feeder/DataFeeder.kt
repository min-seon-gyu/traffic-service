package com.traffic.feeder

interface DataFeeder {
    fun next(): Map<String, String>?
}
