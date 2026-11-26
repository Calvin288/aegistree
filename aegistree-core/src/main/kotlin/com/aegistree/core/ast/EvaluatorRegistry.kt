package com.aegistree.core.ast

import java.util.concurrent.ConcurrentHashMap

class EvaluatorRegistry {
  private val evaluators = ConcurrentHashMap<String, NodeEvaluator>()

  fun register(evaluator: NodeEvaluator) {
    evaluators[evaluator.type] = evaluator
  }

  fun resolve(type: String): NodeEvaluator =
          evaluators[type]
                  ?: throw IllegalArgumentException("No evaluator registered for type: $type")
}
