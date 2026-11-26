package com.aegistree.core.ast

import com.aegistree.spi.ActionSpec

data class EvaluationVerdict(
        val outcome: String,
        val reasonCode: String,
        val metadata: Map<String, Any?>,
        val actions: List<ActionSpec>,
        val trace: ExecutionTrace?
)
