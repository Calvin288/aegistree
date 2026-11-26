package com.aegistree.evaluators

import com.aegistree.spi.DecisionContext
import com.aegistree.spi.NodeEvaluator
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * Built-in evaluator: matches a payload field against a regex pattern. Branch labels: "match" |
 * "noMatch".
 *
 * Compiled patterns are cached to avoid recompiling on every evaluation. Optional capture groups
 * are written into [DecisionContext.contextVariables].
 */
class RegexEvaluator : NodeEvaluator {
    override val type = "Regex"

    private val cache = ConcurrentHashMap<String, Pattern>()

    override fun evaluate(config: Map<String, Any?>, context: DecisionContext): String {
        val targetField = config["targetField"] as String
        val patternStr = config["pattern"] as String
        val fieldValue = context.getTyped<String>(targetField) ?: ""

        val pattern = cache.computeIfAbsent(patternStr) { Pattern.compile(it) }
        val matcher = pattern.matcher(fieldValue)
        val matched = matcher.find()

        val captures = config["captureVariables"] as? Map<String, String> ?: emptyMap()
        if (matched && captures.isNotEmpty()) {
            captures.forEach { (name, groupRef) ->
                val idx = groupRef.removePrefix("$").toIntOrNull() ?: 0
                if (idx <= matcher.groupCount()) {
                    context.contextVariables[name] = matcher.group(idx)
                }
            }
        }

        return if (matched) "match" else "noMatch"
    }
}
