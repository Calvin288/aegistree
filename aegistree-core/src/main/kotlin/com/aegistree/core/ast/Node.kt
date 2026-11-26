package com.aegistree.core.ast

data class Node(
        val id: String,
        val type: String,
        val config: Map<String, Any?> = emptyMap(),
        val branches: Map<String, Node> = emptyMap(),
        // Terminal (OutcomeLeaf) fields — set only when this node is a leaf:
        val outcome: String? = null,
        val reasonCode: String? = null,
        val metadata: Map<String, Any?> = emptyMap(),
        val actions: List<ActionSpec> = emptyList()
)
