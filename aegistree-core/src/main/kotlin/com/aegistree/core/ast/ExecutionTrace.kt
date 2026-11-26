package com.aegistree.core.ast

import java.time.Instant

data class ExecutionTrace(
        val traceId: String,
        val startTime: Instant = Instant.now(),
        var totalLatencyMicros: Long = 0,
        val steps: MutableList<NodeTraceStep> = mutableListOf()
)
