package com.aegistree.core.engine

import com.aegistree.core.ast.*
import com.aegistree.spi.*

/**
 * Walks a strategy DAG from its root to a terminal [Node] (an OutcomeLeaf), resolving each node's
 * `type` through the [EvaluatorRegistry].
 *
 * Synchronous for now: we'll make it a `suspend` function (and the evaluator's `evaluate` a
 * `suspend fun`) when we introduce the async Jev client.
 */
class TreeExecutionEngine(private val registry: EvaluatorRegistry) {
    fun execute(rootNode: Node, context: DecisionContext): EvaluationVerdict {
        val trace = ExecutionTrace(traceId = java.util.UUID.randomUUID().toString())
        val startTimeNanos = System.nanoTime()

        val leafNode = evaluateNode(rootNode, context, trace)
        val leaf =
                leafNode.leaf ?: throw IllegalStateException("Evaluation did not reach a leaf node")
        trace.totalLatencyMicros = (System.nanoTime() - startTimeNanos) / 1000

        return EvaluationVerdict(
                outcome = leaf.outcome,
                reasonCode = leaf.reasonCode,
                metadata = leaf.metadata,
                actions = leaf.actions,
                trace = if (context.traceEnabled) trace else null
        )
    }

    private fun evaluateNode(node: Node, context: DecisionContext, trace: ExecutionTrace): Node {
        val start = System.nanoTime()

        // Terminal node (OutcomeLeaf): stop and return it.
        val leaf = node.leaf
        if (leaf != null) {
            trace.steps.add(
                    NodeTraceStep(node.id, node.type, System.nanoTime() - start, leaf.outcome)
            )
            return node
        }

        // Delegate to the registered evaluator; it returns a branch label.
        val evaluator = registry.resolve(node.type)
        val branchLabel = evaluator.evaluate(node.config, context)

        trace.steps.add(NodeTraceStep(node.id, node.type, System.nanoTime() - start, branchLabel))

        val next =
                node.branches[branchLabel]
                        ?: throw IllegalStateException(
                                "Node '${node.id}' (${node.type}) returned unknown branch '$branchLabel'"
                        )

        return evaluateNode(next, context, trace)
    }
}
