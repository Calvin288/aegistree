package com.aegistree.core.ast

data class NodeTraceStep(
    val nodeId: String,
    val nodeType: String,
    val executionTimeNanos: Long,
    val evaluationResult: String,
    val metadata: Map<String, Any?> = emptyMap()
)
