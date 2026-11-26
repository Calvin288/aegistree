package com.aegistree.spi

data class DecisionContext(
    val payload: Map<String, Any?>,
    val contextVariables: MutableMap<String, Any?> = mutableMapOf(),
    val traceEnabled: Boolean = true,
) {
    inline fun <reified T> getTyped(path: String): T? {
        var node: Any? = contextVariables
        for (key in path.split('.')) {
            node = when (key) {
                "payload" -> payload
                "context" -> contextVariables
                else -> (node as? Map<*, *>)?.get(key)
            }
        }
        return node as? T
    }
}
