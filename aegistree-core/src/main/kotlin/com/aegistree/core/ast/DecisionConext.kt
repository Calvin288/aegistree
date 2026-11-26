package com.aegistree.core.ast

data class DecisionContext(
        val payload: Map<String, Any?>,
        val contextVariables: MutableMap<String, Any?> = mutableMapOf(),
        val traceEnabled: Boolean = true,
) {
  inline fun <reified T> getTyped(key: String): T? {
    val value =
            if (key.startsWith("payload.")) {
              payload[key.removePrefix("payload.")]
            } else {
              contextVariables[key]
            }
    return value as? T // checked at runtime — no warning
  }
}
