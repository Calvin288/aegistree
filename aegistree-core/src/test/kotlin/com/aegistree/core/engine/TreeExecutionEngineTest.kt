package com.aegistree.core.engine

import com.aegistree.core.ast.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class TreeExecutionEngineTest {

    // A tiny in-memory evaluator used only for testing the engine.
    // Branches on whether a payload field equals an expected value.
    private class EqualsEvaluator : NodeEvaluator {
        override val type = "Equals"

        override fun evaluate(config: Map<String, Any?>, context: DecisionContext): String {
            val field = config["field"] as String
            val expected = config["expected"] as String
            val actual = context.getTyped<String>(field)
            return if (actual == expected) "equal" else "notEqual"
        }
    }

    private fun registry() = EvaluatorRegistry().apply { register(EqualsEvaluator()) }

    @Test
    fun `evaluates a simple DAG to a verdict`() {
        val engine = TreeExecutionEngine(registry())

        val root =
                Node(
                        id = "check_role",
                        type = "Equals",
                        config = mapOf("field" to "payload.role", "expected" to "admin"),
                        branches =
                                mapOf(
                                        "equal" to
                                                Node(
                                                        id = "block",
                                                        type = "OutcomeLeaf",
                                                        leaf =
                                                                Node.Leaf(
                                                                        outcome = "BLOCK",
                                                                        reasonCode = "ADMIN"
                                                                ),
                                                ),
                                        "notEqual" to
                                                Node(
                                                        id = "allow",
                                                        type = "OutcomeLeaf",
                                                        leaf =
                                                                Node.Leaf(
                                                                        outcome = "ALLOW",
                                                                        reasonCode = "OK"
                                                                ),
                                                ),
                                ),
                )

        val verdict = engine.execute(root, DecisionContext(mapOf("role" to "admin")))

        assertEquals("BLOCK", verdict.outcome)
        assertEquals("ADMIN", verdict.reasonCode)
        val trace = assertNotNull(verdict.trace)
        assertEquals(2, trace.steps.size) // check_role -> block leaf
    }

    @Test
    fun `follows the no-match branch when values differ`() {
        val engine = TreeExecutionEngine(registry())

        val root =
                Node(
                        id = "check_role",
                        type = "Equals",
                        config = mapOf("field" to "payload.role", "expected" to "admin"),
                        branches =
                                mapOf(
                                        "equal" to
                                                Node(
                                                        id = "block",
                                                        type = "OutcomeLeaf",
                                                        leaf = Node.Leaf(outcome = "BLOCK")
                                                ),
                                        "notEqual" to
                                                Node(
                                                        id = "allow",
                                                        type = "OutcomeLeaf",
                                                        leaf = Node.Leaf(outcome = "ALLOW")
                                                ),
                                ),
                )

        val verdict = engine.execute(root, DecisionContext(mapOf("role" to "guest")))

        assertEquals("ALLOW", verdict.outcome)
    }

    @Test
    fun `resolves nested dot-paths in the payload`() {
        val engine = TreeExecutionEngine(registry())

        val root =
                Node(
                        id = "check_city",
                        type = "Equals",
                        config =
                                mapOf(
                                        "field" to "payload.user.address.city",
                                        "expected" to "London"
                                ),
                        branches =
                                mapOf(
                                        "equal" to
                                                Node(
                                                        id = "vip",
                                                        type = "OutcomeLeaf",
                                                        leaf = Node.Leaf(outcome = "ROUTE_VIP")
                                                ),
                                        "notEqual" to
                                                Node(
                                                        id = "standard",
                                                        type = "OutcomeLeaf",
                                                        leaf =
                                                                Node.Leaf(
                                                                        outcome = "ROUTE_STANDARD"
                                                                ),
                                                ),
                                ),
                )

        val payload = mapOf("user" to mapOf("address" to mapOf("city" to "London")))
        val verdict = engine.execute(root, DecisionContext(payload))

        assertEquals("ROUTE_VIP", verdict.outcome)
    }

    @Test
    fun `throws when a node type is not registered`() {
        val engine = TreeExecutionEngine(EvaluatorRegistry())
        val root = Node(id = "x", type = "Unknown")

        assertFailsWith<IllegalArgumentException> { engine.execute(root, DecisionContext(mapOf())) }
    }
}
