package com.aegistree.core.ast

data class Node(
        val id: String,
        val type: String,
        val config: Map<String, Any?> = emptyMap(),
        val branches: Map<String, Node> = emptyMap(),
        val leaf: Leaf? = null, // Non-null only for terminal leaf nodes.
) {
  data class Leaf(
          val outcome: String,
          val reasonCode: String = "",
          val metadata: Map<String, Any?> = emptyMap(),
          val actions: List<ActionSpec> = emptyList(),
  )
}
