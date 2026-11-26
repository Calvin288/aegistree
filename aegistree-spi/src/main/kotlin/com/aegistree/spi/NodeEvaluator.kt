package com.aegistree.spi

interface NodeEvaluator {
    val type: String
    fun evaluate(config: Map<String, Any?>, context: DecisionContext): String
}
