package com.traffic.service

import com.traffic.metric.AggregatedMetric
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Service
class MetricStreamService {

    private val emitters = ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>>()

    fun subscribe(executionId: Long): SseEmitter {
        val emitter = SseEmitter(Long.MAX_VALUE)
        val list = emitters.computeIfAbsent(executionId) { CopyOnWriteArrayList() }
        list.add(emitter)

        emitter.onCompletion { list.remove(emitter) }
        emitter.onTimeout { list.remove(emitter) }
        emitter.onError { list.remove(emitter) }

        return emitter
    }

    fun broadcast(executionId: Long, metrics: List<AggregatedMetric>) {
        val list = emitters[executionId] ?: return
        val deadEmitters = mutableListOf<SseEmitter>()

        for (emitter in list) {
            try {
                emitter.send(SseEmitter.event().name("metric").data(metrics))
            } catch (_: Exception) {
                deadEmitters.add(emitter)
            }
        }

        list.removeAll(deadEmitters.toSet())
    }

    fun complete(executionId: Long) {
        val list = emitters.remove(executionId) ?: return
        list.forEach { emitter ->
            try {
                emitter.send(SseEmitter.event().name("complete").data("done"))
                emitter.complete()
            } catch (_: Exception) {}
        }
    }
}
