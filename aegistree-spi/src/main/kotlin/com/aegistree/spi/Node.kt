package com.aegistree.spi

data class Node(
        val id: String,
        val type: String,
        val config: Map<String, Any?> = emptyMap(),
        val branches: Map<String, Node> = emptyMap(),
        // Non-null only for terminal (OutcomeLeaf) nodes.
        val leaf: Leaf? = null,
) {
    data class Leaf(
            val outcome: String,
            val reasonCode: String = "",
            val metadata: Map<String, Any?> = emptyMap(),
            val actions: List<ActionSpec> = emptyList(),
    )
}
