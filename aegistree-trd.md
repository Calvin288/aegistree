# AegisTree Architecture Blueprint & TRD

**Cloud-Native Hybrid Decision Engine** — JVM Core & High-Throughput Server

## 1. Executive Summary & Open-Source Vision

AegisTree is a cloud-native, domain-agnostic hybrid decision engine engineered for the JVM ecosystem (Kotlin and Java 21+). Modern enterprise production engines face an architectural split:

- **Traditional deterministic engines** (DRL, Regex, AST lookups, expression evaluators) provide sub-millisecond execution and total predictability, but fail under semantic ambiguity.
- **ML models and LLMs** provide deep semantic reasoning, but incur high latency, high compute costs, and non-deterministic jitter.

AegisTree bridges this gap by unifying deterministic evaluation algorithms with calibrated neural judgments in a single **Directed Acyclic Graph (DAG)** execution tree. Microsecond deterministic paths handle the vast majority of clear-cut evaluations. When a path encounters ambiguity — unstructured text, behavioral anomalies, or multi-factor intent classification — AegisTree dynamically routes execution to TypeSafe AI's Jev `/v1/systemone` inference pipeline.

```
INCOMING EVALUATION REQUEST
            │
            ▼
┌──────────────────────────────────┐
│ STAGE 1: Deterministic Fast Path │
│  (Regex, CEL, Redis Velocity)    │
└──────────────────────────────────┘
            │
     ┌──────┴──────┐
     │             │
[Clear Match]  [Ambiguous]
     │             │
     ▼             ▼
┌──────────┐  ┌──────────────────────────┐
│ Terminal │  │ STAGE 2: Jev System One  │
│ Verdict  │  │  (TypeSafe AI Jev)       │
└──────────┘  └──────────────────────────┘
                        │
                 ┌──────┴──────┐
                 │             │
           [High Conf]    [Low Conf]
                 │             │
                 ▼             ▼
         ┌────────────┐  ┌──────────────┐
         │  Verdict   │  │  Fallback /  │
         │  + Actions │  │  Escalation  │
         └────────────┘  └──────────────┘
```

### Key Architecture Principles

- **Sub-Millisecond Fast Path** — Regex, CEL predicates, and Redis velocity counters execute within 100 µs to 1 ms.
- **Calibrated Neural Escalation** — Deep neural checks occur strictly when deterministic rules return uncertain bounds or heuristics indicate high semantic ambiguity.
- **Generic Outcome & Action Model** — Strict separation between deterministic verdicts (`DecisionOutcome`) and side-effect dispatching (`ActionSpec`). No hardcoded binary outcomes; instead typed domain outcomes (`ALLOW`, `BLOCK`, `CHALLENGE_2FA`, `RECOMMEND_TIER_A`, `ROUTE_HUMAN_REVIEW`) plus asynchronous execution specs via Service Provider Interfaces (SPI).
- **Zero-Allocation Execution Core** — Avoids memory allocation churn on the critical path via object pooling, lock-free `AtomicReference` switches, and Kotlin coroutines.

### Open-Source Repository Layout

```
aegistree/
├── aegistree-core/        # Pure JVM core AST, Evaluator primitives, Execution Engine
├── aegistree-jev-client/  # TypeSafe AI /v1/systemone HTTP/gRPC resilient client
├── aegistree-dsl/         # Kotlin type-safe builder & JSON AST deserializers
├── aegistree-server/      # High-throughput Netty-based gRPC & REST stateless gateway
├── aegistree-spi-kafka/   # Async Action Dispatcher implementation for Apache Kafka
└── aegistree-cli/         # Static validator, strategy linter, and shadow evaluator
```

## 2. Universal Multi-Domain Use Cases

AegisTree abstracts domain execution patterns into standardized tree evaluations:

| Domain | Fast Deterministic Stage | Jev Neural Stage | DecisionOutcome | Async ActionSpec Side-Effects |
|---|---|---|---|---|
| Financial Risk & AML | IBAN regex + Redis 1-min velocity | Noul/Score behavioral anomaly | `BLOCK`, `STEP_UP_2FA` | EmitKafkaAlert, FreezeCard, TriggerAuditLog |
| Customer Likelihood & NBA | User tier check + inventory guardrail | Choice/Score affinity | `RECOMMEND_OFFER_A` | InjectBannerContext, TrackConversionEvent |
| LLM Agent Guardrails | PII masking regex + blocklist | Noul prompt-injection | `REDACT_AND_FORWARD` | SanitizePayload, MetricIncrement |
| Trust, Safety & Content | Keyword/link blocklist + karma | Choice toxicity rubric | `SHADOW_BAN`, `HUMAN_REVIEW` | HidePost, PublishToModeratorQueue |
| Support Triage & Routing | Order ID regex + SLA expression | Choice urgency/intent | `ROUTE_VIP_QUEUE` | AssignZendeskTicket, NotifySlackChannel |

### Multi-Domain Tree Topologies

**Financial Risk & AML Control Tree**

```
[Incoming Transaction] ──► (Regex: Sanction List Check)
            │
     ┌──────┴──────┐
  [Match]       [No Match]
     │              │
     ▼              ▼
[BLOCK]     (Velocity: Trans/Min > 5)
                   │
            ┌──────┴──────┐
          [True]       [False]
            │              │
            ▼              ▼
      (Jev Score)      [ALLOW]
      Anomaly
            │
     ┌──────┴──────┐
 [> 0.85]      [<= 0.85]
     │              │
     ▼              ▼
[FREEZE]        [ALLOW]
```

**LLM Guardrail & Prompt Safety Tree**

```
[Incoming Prompt] ──► (Regex: PII & Canary Patterns)
            │
     ┌──────┴──────┐
  [Match]       [No Match]
     │              │
     ▼              ▼
[REDACT]     (Jev Noul: Prompt Injection)
                   │
            ┌──────┴──────┐
         [UNSAFE]      [SAFE]
            │              │
            ▼              ▼
        [REJECT]     [ALLOW_LLM_CALL]
```

## 3. System Architecture & Runtime Topology

The stateless engine runs on Java 21+ virtual threads and Kotlin coroutines. Strategy configs are held in memory via zero-lock `AtomicReference` pointer swaps.

```
┌────────────────────────────────────────────────────────────────────┐
│                    AEGISTREE STATELESS GATEWAY                    │
│                                                                    │
│  INGRESS: gRPC Protobuf / HTTP/2 Netty Server (TLS, Auth)          │
│                              │                                     │
│                              ▼                                     │
│  LOCK-FREE STRATEGY REGISTRY                                        │
│  (AtomicReference<Map<StrategyId, ExecutableDAG>>)                 │
│                              │                                     │
│                              ▼                                     │
│  TREE EXECUTION ENGINE (Kotlin Coroutines / Virtual Threads)       │
│   ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────────┐       │
│   │ Regex    │ │ CEL      │ │ Velocity │ │ Jev System One │       │
│   │ Evaluator│ │ Evaluator│ │ Evaluator│ │ Client         │       │
│   └──────────┘ └──────────┘ └──────────┘ └────────────────┘       │
│     Synchronous Path │            │ Async Side-Effects             │
│                      ▼            ▼                                │
│  RESPONSE MARSHALLER   DECOUPLED ASYNC ACTION DISPATCHER           │
│  (DecisionOutcome)     (RingBuffer / LMAX Disruptor)               │
└───────────────────────────┬────────────────────────────────────────┘
                            │
              ┌─────────────┼─────────────┐
              ▼             ▼             ▼
         Kafka Topic   Webhook Disp.  Audit SPI
```

### Execution Lifecycle Sequence

```
Client ──Evaluate(Payload)──► Gateway ──Eval Node 1──► Fast Evaluator ──Match──►
Client ◄──DecisionOutcome──  Gateway ◄──Result──        ...
                              Gateway ──Eval Node 2 (ambiguous)──► Jev Client
                              Jev Client ──/v1/systemone──► (remote)
                              Jev Client ◄──Typed Verdict──
                              Gateway ◄──High-Confidence Result──
Client ◄──DecisionOutcome (sub-50ms)──
                              Gateway ──Dispatch ActionSpecs (non-blocking)──► Dispatcher
```

## 4. Core Domain Model & Tree AST Specification

AegisTree models decision logic as a directed tree. Execution starts at the root and traverses branches until an `OutcomeLeaf`.

### Multi-Way & Confidence Branching

```
┌───────────────────────────────┐
│ ExpressionNode                │
│ (payload.amount > 10000 USD)  │
└───────────────────────────────┘
        │
   ┌────┴────┐
 [True]   [False]
   │         │
   ▼         ▼
JevJudgmentNode   OutcomeLeaf: ALLOW
   │
   ├── ON_MATCH      (high conf fraud)
   ├── ON_NO_MATCH   (high conf safe)
   └── ON_UNCERTAIN  (low conf / circuit open)
```

### Node Evaluation Semantics

- **RegexNode** — Evaluates text against compiled linear-time-safe (RE2-compliant) regex. Captured groups populate the downstream `DecisionContext`.
- **ExpressionNode** — Executes sandboxed Google CEL boolean expressions over payload fields and context variables.
- **VelocityNode** — Queries distributed sliding-window counters (Redis) or local caches (Caffeine).
- **JevJudgmentNode** — Sends structured prompts to `/v1/systemone`. Branches on returned probability:
  - **ON_MATCH** — probability ≥ `tauHigh`
  - **ON_NO_MATCH** — inverse probability meets the non-match threshold
  - **ON_UNCERTAIN** — probability in the entropy band, or timeout/circuit-breaker
- **CompositeNode** — Boolean combinations (`ALL`, `ANY`, `NOT`) with cost-aware short-circuiting.
- **SwitchRouterNode** — Multi-way routing on equality, ranges, or string enums.
- **OutcomeLeaf** — Terminal node producing a `DecisionOutcome` with audit context, reason codes, metadata, and a list of `ActionSpec` descriptors.

## 5. Non-Functional Requirements, Performance & Scale SLAs

| Requirement | Deterministic Path (Regex/CEL) | Hybrid Path (Jev) | Hot-Reload & Admin Ops |
|---|---|---|---|
| Throughput (per 8-vCPU node) | ≥ 50,000 req/s | ≥ 5,000 req/s (gRPC pool) | 1,000 reloads/s without loss |
| Latency p50 | ≤ 0.4 ms | ≤ 45 ms (network dependent) | Immediate atomic pointer swap |
| Latency p99 | ≤ 1.8 ms | ≤ 180 ms | ≤ 50 ms max reload latch |
| Latency p99.9 | ≤ 4.5 ms | ≤ 400 ms | ≤ 150 ms |
| Memory allocation | Zero heap churn on evaluation | Max 2 KB per coroutine frame | Non-blocking garbage sweep |
| Availability SLA | 99.999% | 99.99% (fallback via ON_UNCERTAIN) | 100% zero-downtime hot swap |

### Static Optimization & Cache Strategies

- **AST Hoisting** — hoists invariant deterministic sub-expressions ahead of variable expressions; high-cost regex runs after low-cost scalar checks.
- **System One Decision Cache** — two-tier:
  - **L1**: local Caffeine cache of exact payload hash results (TTL 10–300 s).
  - **L2**: distributed Redis cache of semantic intent hashes.
- **Resilience** — Resilience4j-style circuit breakers; if error rate > 5% or p99 > 350 ms, divert to `ON_UNCERTAIN`.

## 6. Declarative Strategy Schema (JSON)

A strategy is a JSON document persisted as a PostgreSQL `jsonb` column. `rootNode` is the DAG entry point.

```json
{
  "schemaVersion": "aegistree/v1",
  "id": "risk_and_recommendation_v1",
  "description": "Universal Hybrid Strategy combining Fast Rules with Jev System One Ambiguity Resolution",
  "metadata": {
    "owner": "risk-engineering-team",
    "environment": "production"
  },
  "rootNode": {
    "id": "node_01_sanction_check",
    "type": "RegexNode",
    "description": "Check IBAN/Account against high-risk blocklist",
    "targetField": "payload.accountNumber",
    "pattern": "^(IR|KP|SY|CU)[0-9]{2}[A-Z0-9]{12,30}$",
    "captureVariables": { "countryCode": "$1" },
    "onMatch": {
      "id": "leaf_block_sanction",
      "type": "OutcomeLeaf",
      "outcome": "BLOCK",
      "reasonCode": "ERR_SANCTIONED_COUNTRY_PATTERN",
      "metadata": { "riskLevel": "CRITICAL", "actionSource": "DETERMINISTIC_REGEX" },
      "actions": [
        { "actionType": "KAFKA_DISPATCH", "params": { "topic": "risk.compliance.alerts", "priority": "HIGH" } },
        { "actionType": "FREEZE_ACCOUNT", "params": { "lockType": "TOTAL_LOCK" } }
      ]
    },
    "onNoMatch": {
      "id": "node_02_velocity_and_amount",
      "type": "ExpressionNode",
      "description": "Evaluate transaction amount thresholds and fast context parameters",
      "expression": "payload.amount > 10000.00 || context.userRiskTier == 'HIGH_RISK'",
      "onFalse": {
        "id": "leaf_allow_deterministic",
        "type": "OutcomeLeaf",
        "outcome": "ALLOW",
        "reasonCode": "OK_FAST_PASS",
        "metadata": { "confidenceScore": 1.0 },
        "actions": []
      },
      "onTrue": {
        "id": "node_03_jev_system_one_check",
        "type": "JevJudgmentNode",
        "description": "Escalate to Jev /v1/systemone on high-value/high-risk context",
        "promptTemplate": "Analyze this transaction for fraud patterns... ${context.userHistorySummary} ${payload.rawJson}",
        "judgmentType": "Noul",
        "tauHigh": 0.85,
        "tauLow": 0.15,
        "timeoutMs": 250,
        "failurePolicy": "FALLBACK_TO_UNCERTAIN",
        "onMatch": {
          "id": "leaf_challenge_fraud",
          "type": "OutcomeLeaf",
          "outcome": "CHALLENGE",
          "reasonCode": "RISK_JEV_HIGH_PROBABILITY_FRAUD",
          "metadata": { "riskLevel": "HIGH", "actionSource": "NEURAL_SYSTEM_ONE" },
          "actions": [
            { "actionType": "STEP_UP_AUTHENTICATION", "params": { "authType": "HARDWARE_TOKEN_OR_BIOMETRIC" } }
          ]
        },
        "onNoMatch": {
          "id": "leaf_recommend_vip",
          "type": "OutcomeLeaf",
          "outcome": "RECOMMEND_TIER_A",
          "reasonCode": "OK_VERIFIED_HIGH_VALUE",
          "metadata": { "vipEligible": true },
          "actions": [
            { "actionType": "APPLY_PROMO_CREDIT", "params": { "creditAmount": 50.0 } }
          ]
        },
        "onUncertain": {
          "id": "leaf_escalate_human_review",
          "type": "OutcomeLeaf",
          "outcome": "ESCALATE",
          "reasonCode": "WARN_JEV_UNCERTAIN_AMBIGUITY",
          "metadata": { "requiresManualAudit": true },
          "actions": [
            { "actionType": "CREATE_WORKFLOW_TICKET", "params": { "queue": "MANUAL_FRAUD_REVIEW" } }
          ]
        }
      }
    }
  }
}
```

## 7. Core Kotlin AST & Jev Client Implementation

### 7.1 AST & Context Data Models

```kotlin
package com.aegistree.core.ast

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.Instant

// Standardized typed decision outcomes emitted by AegisTree terminal leaves.
enum class CoreOutcome {
    ALLOW, BLOCK, CHALLENGE, RECOMMEND_TIER_A, RECOMMEND_TIER_B,
    ROUTE_QUEUE, ESCALATE, REDACT_AND_FORWARD, CUSTOM
}

// Side-effect descriptor queued for non-blocking async execution via SPI plugins.
data class ActionSpec(
    val actionType: String,
    val params: Map<String, Any?> = emptyMap()
)

// Thread-safe evaluation context: incoming payload, extracted variables, trace flags.
data class DecisionContext(
    val payload: Map<String, Any?>,
    val contextVariables: MutableMap<String, Any?> = mutableMapOf(),
    val traceEnabled: Boolean = true
) {
    @Suppress("UNCHECKED_CAST")
    fun <T> getTyped(key: String): T? {
        if (key.startsWith("payload.")) {
            return payload[key.removePrefix("payload.")] as? T
        }
        return contextVariables[key] as? T
    }
}

// Granular node-hop metric stored inside the execution trace.
data class NodeTraceStep(
    val nodeId: String,
    val nodeType: String,
    val executionTimeNanos: Long,
    val evaluationResult: String,
    val metadata: Map<String, Any?> = emptyMap()
)

// Fully deterministic trace metadata returned alongside verdicts for auditing.
data class ExecutionTrace(
    val traceId: String,
    val startTime: Instant = Instant.now(),
    var totalLatencyMicros: Long = 0,
    val steps: MutableList<NodeTraceStep> = mutableListOf()
)

// Sealed AST base node.
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = DecisionNode.RegexNode::class, name = "RegexNode"),
    JsonSubTypes.Type(value = DecisionNode.ExpressionNode::class, name = "ExpressionNode"),
    JsonSubTypes.Type(value = DecisionNode.JevJudgmentNode::class, name = "JevJudgmentNode"),
    JsonSubTypes.Type(value = DecisionNode.OutcomeLeaf::class, name = "OutcomeLeaf")
)
sealed interface DecisionNode {
    val id: String

    data class RegexNode(
        override val id: String,
        val targetField: String,
        val pattern: String,
        val captureVariables: Map<String, String> = emptyMap(),
        val onMatch: DecisionNode,
        val onNoMatch: DecisionNode
    ) : DecisionNode

    data class ExpressionNode(
        override val id: String,
        val expression: String,
        val onTrue: DecisionNode,
        val onFalse: DecisionNode
    ) : DecisionNode

    data class JevJudgmentNode(
        override val id: String,
        val promptTemplate: String,
        val judgmentType: String, // "Noul", "Choice", or "Score"
        val tauHigh: Double = 0.85,
        val tauLow: Double = 0.15,
        val onMatch: DecisionNode,
        val onNoMatch: DecisionNode,
        val onUncertain: DecisionNode
    ) : DecisionNode

    data class OutcomeLeaf(
        override val id: String,
        val outcome: String,
        val reasonCode: String,
        val metadata: Map<String, Any?> = emptyMap(),
        val actions: List<ActionSpec> = emptyList()
    ) : DecisionNode
}

// Evaluation response output wrapper.
data class EvaluationVerdict(
    val outcome: String,
    val reasonCode: String,
    val metadata: Map<String, Any?>,
    val actions: List<ActionSpec>,
    val trace: ExecutionTrace?
)
```

### 7.2 TypeSafe AI Jev Client

```kotlin
package com.aegistree.jev.client

import com.fasterxml.jackson.annotation.JsonProperty
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.future.await

// --- DTO schema matching TypeSafe AI /v1/systemone ---

data class JevSystemOneRequest(
    @JsonProperty("prompt") val prompt: String,
    @JsonProperty("judgment_type") val judgmentType: String,
    @JsonProperty("temperature") val temperature: Double = 0.0,
    @JsonProperty("context_payload") val contextPayload: Map<String, Any?> = emptyMap()
)

data class JevSystemOneResponse(
    @JsonProperty("status") val status: String,
    @JsonProperty("noul_verdict") val noulVerdict: Boolean?,
    @JsonProperty("selected_choice") val selectedChoice: String?,
    @JsonProperty("score_value") val scoreValue: Double?,
    @JsonProperty("probability") val probability: Double,
    @JsonProperty("confidence") val confidence: Double,
    @JsonProperty("latency_ms") val latencyMs: Long
)

interface JevClient {
    suspend fun evaluateSystemOne(request: JevSystemOneRequest): Result<JevSystemOneResponse>
}

// Non-blocking, resilient HTTP client targeting /v1/systemone.
class ResilientJevClient(
    private val baseUrl: String,
    private val apiKey: String,
    timeoutMs: Long = 250
) : JevClient {

    private val mapper = jacksonObjectMapper()
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(timeoutMs))
        .executor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor())
        .build()

    override suspend fun evaluateSystemOne(request: JevSystemOneRequest): Result<JevSystemOneResponse> =
        runCatching {
            val payloadJson = mapper.writeValueAsString(request)

            val httpRequest = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/v1/systemone"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $apiKey")
                .POST(HttpRequest.BodyPublishers.ofString(payloadJson))
                .timeout(Duration.ofMillis(300))
                .build()

            val response = httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString()).await()

            if (response.statusCode() != 200) {
                throw RuntimeException("Jev Endpoint returned HTTP ${response.statusCode()}: ${response.body()}")
            }

            mapper.readValue(response.body(), JevSystemOneResponse::class.java)
        }
}
```

### 7.3 Core Recursive Coroutine Evaluation Engine

```kotlin
package com.aegistree.core.engine

import com.aegistree.core.ast.*
import com.aegistree.jev.client.JevClient
import com.aegistree.jev.client.JevSystemOneRequest
import kotlinx.coroutines.coroutineScope
import java.util.regex.Pattern
import java.util.concurrent.ConcurrentHashMap

class TreeExecutionEngine(
    private val jevClient: JevClient
) {
    private val compiledRegexCache = ConcurrentHashMap<String, Pattern>()

    suspend fun execute(rootNode: DecisionNode, context: DecisionContext): EvaluationVerdict =
        coroutineScope {
            val trace = ExecutionTrace(traceId = java.util.UUID.randomUUID().toString())
            val startTimeNanos = System.nanoTime()

            val leafResult = evaluateNode(rootNode, context, trace)
            trace.totalLatencyMicros = (System.nanoTime() - startTimeNanos) / 1000

            EvaluationVerdict(
                outcome = leafResult.outcome,
                reasonCode = leafResult.reasonCode,
                metadata = leafResult.metadata,
                actions = leafResult.actions,
                trace = if (context.traceEnabled) trace else null
            )
        }

    private suspend fun evaluateNode(
        node: DecisionNode,
        context: DecisionContext,
        trace: ExecutionTrace
    ): DecisionNode.OutcomeLeaf {
        val nodeStartTime = System.nanoTime()

        return when (node) {
            is DecisionNode.OutcomeLeaf -> {
                trace.steps.add(
                    NodeTraceStep(
                        nodeId = node.id,
                        nodeType = "OutcomeLeaf",
                        executionTimeNanos = System.nanoTime() - nodeStartTime,
                        evaluationResult = node.outcome
                    )
                )
                node
            }

            is DecisionNode.RegexNode -> {
                val fieldValue = context.getTyped<String>(node.targetField) ?: ""
                val compiledPattern = compiledRegexCache.computeIfAbsent(node.pattern) { Pattern.compile(it) }
                val matcher = compiledPattern.matcher(fieldValue)
                val isMatch = matcher.find()

                if (isMatch && node.captureVariables.isNotEmpty()) {
                    node.captureVariables.forEach { (varName, captureGroup) ->
                        val groupIdx = captureGroup.removePrefix("$").toIntOrNull() ?: 0
                        if (groupIdx <= matcher.groupCount()) {
                            context.contextVariables[varName] = matcher.group(groupIdx)
                        }
                    }
                }

                trace.steps.add(
                    NodeTraceStep(
                        nodeId = node.id,
                        nodeType = "RegexNode",
                        executionTimeNanos = System.nanoTime() - nodeStartTime,
                        evaluationResult = if (isMatch) "MATCH" else "NO_MATCH"
                    )
                )

                evaluateNode(if (isMatch) node.onMatch else node.onNoMatch, context, trace)
            }

            is DecisionNode.ExpressionNode -> {
                val exprResult = evaluateSimplePredicate(node.expression, context)
                trace.steps.add(
                    NodeTraceStep(
                        nodeId = node.id,
                        nodeType = "ExpressionNode",
                        executionTimeNanos = System.nanoTime() - nodeStartTime,
                        evaluationResult = exprResult.toString()
                    )
                )
                evaluateNode(if (exprResult) node.onTrue else node.onFalse, context, trace)
            }

            is DecisionNode.JevJudgmentNode -> {
                val interpolatedPrompt = interpolatePrompt(node.promptTemplate, context)
                val jevReq = JevSystemOneRequest(
                    prompt = interpolatedPrompt,
                    judgmentType = node.judgmentType,
                    contextPayload = context.payload
                )

                val jevResponseResult = jevClient.evaluateSystemOne(jevReq)
                val (nextNode, evalCode) = jevResponseResult.fold(
                    onSuccess = { resp ->
                        val prob = resp.probability
                        when {
                            prob >= node.tauHigh && (resp.noulVerdict == true || resp.selectedChoice != null) ->
                                Pair(node.onMatch, "JEV_MATCH (prob=$prob)")
                            (1.0 - prob) >= (1.0 - node.tauLow) && resp.noulVerdict == false ->
                                Pair(node.onNoMatch, "JEV_NO_MATCH (prob=$prob)")
                            else ->
                                Pair(node.onUncertain, "JEV_UNCERTAIN_PROBABILITY (prob=$prob)")
                        }
                    },
                    onFailure = { err ->
                        Pair(node.onUncertain, "JEV_FALLBACK_FAILURE: ${err.message}")
                    }
                )

                trace.steps.add(
                    NodeTraceStep(
                        nodeId = node.id,
                        nodeType = "JevJudgmentNode",
                        executionTimeNanos = System.nanoTime() - nodeStartTime,
                        evaluationResult = evalCode
                    )
                )

                evaluateNode(nextNode, context, trace)
            }
        }
    }

    private fun evaluateSimplePredicate(expression: String, context: DecisionContext): Boolean {
        if (expression.contains("amount > 10000")) {
            val amount = context.getTyped<Number>("payload.amount")?.toDouble() ?: 0.0
            return amount > 10000.0
        }
        return false
    }

    private fun interpolatePrompt(template: String, context: DecisionContext): String {
        var result = template
        context.payload.forEach { (k, v) -> result = result.replace("\${payload.$k}", v.toString()) }
        context.contextVariables.forEach { (k, v) -> result = result.replace("\${context.$k}", v.toString()) }
        return result
    }
}
```

## 8. Strategy Lifecycle, Static Validation, Shadow Testing & Observability

### 8.1 Compile-Time Static Validation

Before a strategy tree is compiled into an active `AtomicReference` slot, the `aegistree-cli` engine runs:

```
INCOMING STRATEGY SUBMISSION (JSON)
        │
        ▼
STAGE 1: Structural Schema & Syntactic Validation
        │
        ▼
STAGE 2: Static Analysis Checks
   • Cycle & Loop Detection (DAG enforcer)
   • ReDoS Protection (linear-time regex audit)
   • Unreachable Branch Identification
   • Missing Jev ON_UNCERTAIN / Fallback Handlers
        │
   ┌────┴────┐
[Failure]  [Passed]
   │          │
   ▼          ▼
Reject     Stage to Active
Submission Strategy Registry
(+ errors) (atomic pointer swap)
```

- **Cycle & Loop Detection** — DFS to enforce an acyclic (DAG) layout.
- **ReDoS Protection** — validates regex with RE2 static checks for O(N) worst-case time.
- **Unreachable Branch Identification** — detects dead paths and unreachable subtrees.
- **Fallback Handler Enforcer** — every `JevJudgmentNode` must define an `onUncertain` target.

### 8.2 Shadow / Canary Evaluation Mode

```
Incoming Production Request
        │
        ▼
Production Router
        │
   ┌────┴─────────────┐
[Synchronous]   [Non-blocking Async Fork]
   │                  │
   ▼                  ▼
Active Baseline    Shadow Target
(Version 1.4.0)    (Version 2.0.0-RC)
   │                  │
   ▼                  ▼
Emit Verdict       Suppress ActionSpecs
& Execute Effects  Record Divergence
```

During shadow execution, the engine evaluates both the active baseline and the candidate shadow strategy. Shadow nodes run identical logic, but their `ActionSpec` side-effects are suppressed.

### 8.3 Observability & Telemetry

AegisTree exports real-time stats via OpenTelemetry and Micrometer.

**Prometheus metrics:**

- `aegistree_engine_eval_duration_seconds_bucket{strategy_id, outcome}` — histogram of resolution latencies.
- `aegistree_node_eval_duration_nanos{node_type, node_id}` — per-node timings.
- `aegistree_jev_confidence_distribution{strategy_id, node_id}` — output probability distribution (τ).
- `aegistree_shadow_divergence_total{baseline, shadow}` — incremented when shadow output differs.

**PII-safe structured audit log:**

```json
{
  "timestamp": "2026-03-31T14:22:09.102Z",
  "traceId": "c4b912a7-f321-42ab-881c-0b9213ef2110",
  "strategyId": "risk_and_recommendation_v1",
  "strategyVersion": "1.4.0",
  "latencyMicros": 41200,
  "verdict": {
    "outcome": "CHALLENGE",
    "reasonCode": "RISK_JEV_HIGH_PROBABILITY_FRAUD",
    "actionCount": 1
  },
  "executionGraphTrace": [
    { "nodeId": "node_01_sanction_check", "type": "RegexNode", "latencyNanos": 18200, "result": "NO_MATCH" },
    { "nodeId": "node_02_velocity_and_amount", "type": "ExpressionNode", "latencyNanos": 8400, "result": "true" },
    { "nodeId": "node_03_jev_system_one_check", "type": "JevJudgmentNode", "latencyNanos": 41100000, "result": "JEV_MATCH (prob=0.92)" }
  ]
}
```

### 8.4 Open-Source Development Roadmap

| Phase | Deliverables |
|---|---|
| **v0.1 Core Engine** | JVM core AST model, deterministic engine, JSON schema parser |
| **v0.2 Resilience & Jev** | Jev System One SDK, circuit breakers, dynamic caching |
| **v0.3 Gateway Server** | Netty gRPC gateway, REST JSON endpoint, hot-swap registry |
| **v1.0 Production Ready** | Kafka Action SPI, shadow execution, web console UI |

- **v0.1 (Core AST Framework)** — Pure JVM evaluation engine (`aegistree-core`), CEL evaluation, RE2 regex integration, JSON strategy loading.
- **v0.2 (Jev Integration & Resilience)** — `aegistree-jev-client` with L1/L2 caching, Resilience4j fallbacks, static AST optimizers, test frameworks.
- **v0.3 (High-Throughput Gateway)** — Netty gRPC + HTTP/2 server (`aegistree-server`), zero-downtime hot updates, OpenTelemetry.
- **v1.0 (Enterprise Async SPI & Shadow Engine)** — Kafka dispatchers (`aegistree-spi-kafka`), shadow canary routing, validation CLI, admin console.
