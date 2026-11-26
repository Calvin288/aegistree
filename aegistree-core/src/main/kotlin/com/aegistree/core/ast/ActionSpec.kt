package com.aegistree.core.ast

data class ActionSpec(val actionType: String, val params: Map<String, Any?> = emptyMap())
