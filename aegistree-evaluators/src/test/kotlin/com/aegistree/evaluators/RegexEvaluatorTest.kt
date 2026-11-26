package com.aegistree.evaluators

import com.aegistree.spi.DecisionContext
import kotlin.test.Test
import kotlin.test.assertEquals

class RegexEvaluatorTest {

    private val evaluator = RegexEvaluator()

    @Test
    fun `returns match when the pattern matches`() {
        val context = DecisionContext(mapOf("accountNumber" to "IR1234567890123456789012"))

        val result =
                evaluator.evaluate(
                        mapOf(
                                "targetField" to "payload.accountNumber",
                                "pattern" to "^IR[0-9]{22}$"
                        ),
                        context,
                )

        assertEquals("match", result)
    }

    @Test
    fun `returns noMatch when the pattern does not match`() {
        val context = DecisionContext(mapOf("accountNumber" to "US123456"))

        val result =
                evaluator.evaluate(
                        mapOf(
                                "targetField" to "payload.accountNumber",
                                "pattern" to "^IR[0-9]{22}$"
                        ),
                        context,
                )

        assertEquals("noMatch", result)
    }

    @Test
    fun `captures groups into the context`() {
        val context = DecisionContext(mapOf("accountNumber" to "IR1234567890123456789012"))

        evaluator.evaluate(
                mapOf(
                        "targetField" to "payload.accountNumber",
                        "pattern" to "^(IR)[0-9]{22}$",
                        "captureVariables" to mapOf("countryCode" to "$1"),
                ),
                context,
        )

        assertEquals("IR", context.contextVariables["countryCode"])
    }
}
