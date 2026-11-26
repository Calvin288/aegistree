package com.aegistree.spi

data class ActionSpec(val actionType: String, val params: Map<String, Any?> = emptyMap())
