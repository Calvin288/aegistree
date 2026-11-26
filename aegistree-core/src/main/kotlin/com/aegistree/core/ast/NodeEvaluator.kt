
package com.aegistree.core.ast

interface NodeEvaluator {
  val type: String
  fun evaluate(config: Map<String, Any?>, context: DecisionContext): String
}
