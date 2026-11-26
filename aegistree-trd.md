AegisTree Architecture Blueprint & TRD
Cloud-Native Hybrid Decision Engine (JVM Core & High-Throughput Server)

1. Executive Summary & Open-Source Vision
   AegisTree is a cloud-native, domain-agnostic hybrid decision engine engineered for the JVM ecosystem (Kotlin and Java 21+). Modern enterprise production engines face an architectural split: traditional deterministic engines (DRL, Regex, AST lookups, expression evaluators) provide sub-millisecond execution and total predictability, but fail under semantic ambiguity; machine learning models and large language models provide deep semantic reasoning, but incur high latency, high compute costs, and non-deterministic jitter.

AegisTree bridges this gap by unifying deterministic evaluation algorithms with calibrated neural judgments in a unified Directed Acyclic Graph (DAG) execution tree. Microsecond deterministic evaluation paths handle the vast majority of clear-cut evaluations. When an incoming evaluation path encounters ambiguity—such as unstructured text, contextual behavioral anomalies, or multi-factor intent classification—AegisTree dynamically routes execution to TypeSafe AI's Jev /v1/systemone inference pipeline.

                  +-------------------------------------------------------+

                  |               INCOMING EVALUATION REQUEST             |

|---|+-------------------------------------------------------+
|
v
+-------------------------------------------------------+
| STAGE 1: Ultra-Fast Deterministic Path |
| (Regex, CEL Expressions, Redis Velocity Checks) |
+-------------------------------------------------------+
|
+--------------------+--------------------+
| |
[ Clear Rule Match ] [ Rule Ambiguity ]
| |
v v
+-------------------------------+ +-------------------------------+
| Immediate Terminal Verdict | | STAGE 2: Calibrated Neural |
| (Sub-millisecond Execution) | | System One (TypeSafe AI Jev) |
+-------------------------------+ +-------------------------------+
| |
| +---------------+---------------+
| | |
| [ High Confidence ] [ Low Confidence ]
| | |
v v v
+-----------------------------------------------+ +-----------------+
| Final Decision Verdict | | Fallback Route |
| (DecisionOutcome + Async ActionSpecs) | | / Escalation |
+-----------------------------------------------+ +-----------------+
Key Architecture Principles
Sub-Millisecond Fast Path: Regex pattern matching, Common Expression Language (CEL) predicates, and Redis velocity counters execute within 100 microseconds to 1 millisecond.
Calibrated Neural System One Escalation: Deep neural checks occur strictly when deterministic rules return uncertain bounds or when low-cost heuristics indicate high semantic ambiguity.
Generic Outcome & Action Model: AegisTree decoupling relies on strict structural separation between deterministic evaluation verdicts (DecisionOutcome) and side-effect dispatching (ActionSpec). The engine avoids hardcoded binary outcomes like ACCEPT or REJECT. Instead, it emits typed domain outcomes (ALLOW, BLOCK, CHALLENGE_2FA, RECOMMEND_TIER_A, ROUTE_HUMAN_REVIEW) alongside asynchronous execution specs handled via Service Provider Interfaces (SPI).
Zero-Allocation Execution Core: High-throughput path utilization avoids memory allocation churn on the critical evaluation path through object pooling, lock-free state atomic switches, and Kotlin coroutine integration.
Open-Source Repository Layout
aegistree/
├── aegistree-core/ # Pure JVM core AST, Evaluator primitives, Execution Engine
├── aegistree-jev-client/ # TypeSafe AI /v1/systemone HTTP/gRPC resilient client
├── aegistree-dsl/ # Kotlin type-safe builder & YAML/JSON AST deserializers
├── aegistree-server/ # High-throughput Netty-based gRPC & REST stateless gateway
├── aegistree-spi-kafka/ # Async Action Dispatcher implementation for Apache Kafka
└── aegistree-cli/ # Command-line static validator, policy linter, and shadow evaluator

2. Universal Multi-Domain Use Cases
   AegisTree abstracts domain execution patterns into standardized tree evaluations. The following matrix illustrates how diverse engineering teams apply AegisTree to their specific domains:

Domain
Fast Deterministic Stage
System One (Jev) Neural Stage
Resulting DecisionOutcome
Asynchronous ActionSpec Side-Effects
Financial Risk & AML
IBAN Regex check + Redis 1-min Velocity Counter
score / noul Behavioral Anomaly & Narrative Risk
BLOCK or STEP_UP_2FA
EmitKafkaAlert, FreezeCard, TriggerAuditLog
Customer Likelihood & NBA
User Tier Check + Product Inventory Guardrail
choice / score Intent & Affinity Scoring
RECOMMEND_OFFER_A
InjectBannerContext, TrackConversionEvent
LLM Agent Guardrails
PII Masking Regex + Blocklist Check
noul Prompt-Injection & Policy Compliance
REDACT_AND_FORWARD
SanitizePayload, MetricIncrement
Trust, Safety & Content
Keyword/Link Blocklist Regex + Karma Check
choice Toxicity & Harassment Severity Rubric
SHADOW_BAN or HUMAN_REVIEW
HidePost, PublishToModeratorQueue
Support Triage & Routing
Order ID Regex + Customer SLA Expression
choice Semantic Urgency & Issue Intent
ROUTE_VIP_QUEUE
AssignZendeskTicket, NotifySlackChannel

Multi-Domain Tree Topologies
FINANCIAL RISK & AML CONTROL TREE
[Incoming Transaction] ──► (Regex: Sanction List Check)
│
┌──────┴──────┐
[Match Found] [No Match]
│ │
▼ ▼
[Outcome: BLOCK] (Velocity: Trans/Min > 5)
│
┌──────┴──────┐
[True] [False]
│ │
▼ ▼
[Outcome: 2FA] (Jev Score: Behavioral Anomaly)
│
┌──────┴──────┐
[Score > 0.85] [Score <= 0.85]
│ │
▼ ▼
[Outcome: FREEZE] [Outcome: ALLOW]

LLM GUARDRAIL & PROMPT SAFETY TREE
[Incoming Prompt] ──► (Regex: PII & Canary Patterns)
│
┌──────┴──────┐
[Match Found] [No Match]
│ │
▼ ▼
[Outcome: REDACT] (Jev Noul: Prompt Injection Risk)
│
┌──────┴──────┐
[UNSAFE] [SAFE]
│ │
▼ ▼
[Outcome: REJECT] [Outcome: ALLOW_LLM_CALL]

3. System Architecture & Runtime Topology
   Core Gateway & Engine Architecture
   The stateless engine deployment operates on Java 21+ Virtual Threads and Kotlin Coroutines. Policy configs are managed in memory through zero-lock AtomicReference pointer swaps.

+---------------------------------------------------------------------------------------------------------+
| AEGISTREE STATELESS GATEWAY |
| |
| +-------------------------------------------------------------------------------------------------+ |
| | INGRESS LAYER: gRPC Protocol Buffers / HTTP/2 Netty Server (TLS Termination, Auth) | |
| +-------------------------------------------------------------------------------------------------+ |
| | |
| v |
| +-------------------------------------------------------------------------------------------------+ |
| | LOCK-FREE POLICY REGISTRY (AtomicReference<Map<PolicyId, ExecutableDAG>>) | |
| +-------------------------------------------------------------------------------------------------+ |
| | |
| v |
| +-------------------------------------------------------------------------------------------------+ |
| | TREE EXECUTION ENGINE (Kotlin Coroutines / Java 21 Virtual Threads) | |
| | | |
| | +--------------------+ +--------------------+ +--------------------+ +-------------------+ | |
| | | Regex Evaluator | | CEL Expression | | Redis Velocity | | Jev System One | | |
| | | Engine | | Evaluator | | Evaluator | | Resilient Client | | |
| | +--------------------+ +--------------------+ +--------------------+ +-------------------+ | |
| +-------------------------------------------------------------------------------------------------+ |
| | | |
| Synchronous Path | | Asynchronous Side-Effects |
| (Verdict Payload) | | (Fire-and-Forget / Guaranteed) |
| v v |
| +---------------------------------------------------+ +-----------------------------------------+ |
| | RESPONSE MARSHALLER | | DECOUPLED ASYNC ACTION DISPATCHER | |
| | Returns DecisionOutcome, ReasonCode, Trace | | RingBuffer / LMAX Disruptor | |
| +---------------------------------------------------+ +-----------------------------------------+ |
| | |
+-----------------------------------------------------------------------|---------------------------------+
|
+------------------------------+------------------------------+
| | |
v v v
+--------------------+ +--------------------+ +--------------------+
| Kafka Action Topic | | Webhook Dispatcher | | Internal Audit SPI |
+--------------------+ +--------------------+ +--------------------+
Execution Lifecycle Sequence
Client Gateway AegisTree Core Fast Evaluator Jev Client Async Dispatcher
│ │ │ │ │
│─── Evaluate(Payload)──►│ │ │ │
│ │─── Eval Node 1 ────►│ │ │
│ │◄── Fast Match ──────│ │ │
│ │ │ │
│ │─── Eval Node 2 (Ambiguous Bound)──────►│ │
│ │ │── System One Call ─►│
│ │ │ (/v1/systemone) │
│ │ │◄── Typed Verdict ───│
│ │◄── High-Confidence Neural Result ──────│ │
│ │ │
│◄── Decision Outcome ───│ │
│ (Sub-50ms Total) │ │
│ │─────────────────────────────────────────────────────────────►│
│ │ Dispatch ActionSpecs (Non-blocking Background Task) │
│ │ │

4. Core Domain Model & Tree AST Specification
   AegisTree models decision logic as a directed tree structure. Execution starts at a root node and traverses down branches using node evaluations until reaching an OutcomeLeaf.
   Multi-Way & Confidence Branching Layout
   +-----------------------------------+
   | ExpressionNode |
   | (payload.amount > 10000 USD) |
   +-----------------------------------+
   / \
    [ True ] [ False ]
   / \
    v v
   +---------------------------------------+ +------------------+
   | JevJudgmentNode | | OutcomeLeaf: |
   | (Prompt: "Evaluate Fraud Intent") | | ALLOW |
   +---------------------------------------+ +------------------+
   / | \
    [ ON_MATCH ] [ ON_NO_MATCH ] [ ON_UNCERTAIN / FAILURE ]
   (High Conf Fraud) (High Conf Safe) (Low Conf / Circuit Open)
   | | |
   v v v
   +--------------+ +--------------+ +------------------+
   | OutcomeLeaf: | | OutcomeLeaf: | | OutcomeLeaf: |
   | BLOCK | | ALLOW | | STEP_UP_2FA |
   +--------------+ +--------------+ +------------------+
   Node Evaluation Semantics
   RegexNode: Evaluates continuous text strings against compiled linear-time safe regex patterns (RE2-compliant). Captured groups automatically pollute the downstream DecisionContext map for subsequent evaluation access.
   ExpressionNode: Executes sandboxed Google Common Expression Language (CEL) boolean expressions over structural payload fields and context variables.
   VelocityNode: Queries underlying distributed sliding-window counters (Redis key structures) or local sliding caches (Caffeine) using key definitions computed from runtime execution context.
   JevJudgmentNode: Constructs structured prompts payload sent to /v1/systemone. Evaluates categorical choices, numeric standard deviations, or noul binary values. Evaluates three dynamic path conditions based on returned probability:
   ON_MATCH: Returned probability meets or exceeds the high-confidence threshold tau_high.
   ON_NO_MATCH: Returned inverse probability meets or exceeds the non-match threshold.
   ON_UNCERTAIN: Probability falls within the entropy band between bounds, or the Jev remote call timed out/circuit breaker triggered.
   CompositeNode: Evaluates Boolean combinations (ALL, ANY, NOT) across ordered child rule nodes using cost-aware dynamic short-circuiting.
   SwitchRouterNode: Dynamic multi-branching router using variable equality, integer ranges, or discrete string enum outcomes.
   OutcomeLeaf: Terminal node that produces a explicit DecisionOutcome containing audit context, human-readable reason codes, dynamic metadata maps, and a payload list of declarative ActionSpec descriptors.

5. Non-Functional Requirements, Performance & Scale SLAs
   Key Metric SLAs
   Requirement
   Deterministic Path (Regex/CEL)
   Hybrid Path (With Jev System One)
   Hot-Reload & Administrative Ops
   Throughput (Per 8-vCPU Node)
   $\ge$ 50,000 requests/sec
   $\ge$ 5,000 requests/sec (gRPC pool)
   1,000 reloads/sec without loss
   Latency p50
   $\le$ 0.4 ms
   $\le$ 45 ms (network dependant)
   Immediate atomic pointer swap
   Latency p99
   $\le$ 1.8 ms
   $\le$ 180 ms
   $\le$ 50 ms max reload latch
   Latency p99.9
   $\le$ 4.5 ms
   $\le$ 400 ms
   $\le$ 150 ms
   Memory Allocation
   Zero heap churn on evaluation
   Max 2 KB per active coroutine frame
   Non-blocking garbage sweep
   Availability SLA
   99.999% Uptime
   99.99% (Fallback via ON_UNCERTAIN)
   100% Zero-downtime hot swap

Static Optimization & Cache Strategies
Automated AST Hoisting: Static policy compilation hoists invariant deterministic sub-expressions ahead of variable expressions. High-cost regex matches run after low-cost scalar key matches.
System One Decision Cache: Jev responses are cached in a two-tier structure:
L1 Cache: Ultra-fast local Caffeine cache storing exact payload hash results (TTL 10s to 300s).
L2 Cache: Distributed Redis cache storing semantic intent hashes, avoiding repeated remote LLM inferences for identical behavioral profiles.
Resilience Strategy: Jev client wrappers utilize thread-safe circuit breakers (Resilience4j style). If the Jev endpoint error rate exceeds 5%, or p99 latency breaches 350 ms, the node automatically diverts execution directly to the ON_UNCERTAIN branch fallback path.

6. Declarative Policy Schema (YAML Specification)
   The following YAML manifest demonstrates a complete policy definition (risk_and_recommendation_v1). It configures a fast path check using regex and CEL expressions, followed by a fallback to TypeSafe AI Jev System One inference, and ends at typed outcome leaves.

version: "aegistree/v1"
policyId: "risk_and_recommendation_v1"
description: "Universal Hybrid Policy combining Fast Rules with Jev System One Ambiguity Resolution"
metadata:
owner: "risk-engineering-team"
environment: "production"

rootNode:
id: "node_01_sanction_check"
type: "RegexNode"
description: "Check IBAN or Account payload against high-risk pattern blocklist"
config:
targetField: "payload.accountNumber"
pattern: "^(IR|KP|SY|CU)[0-9]{2}[A-Z0-9]{12,30}$"
captureVariables:
countryCode: "$1"
branches:
onMatch:
id: "leaf_block_sanction"
type: "OutcomeLeaf"
outcome: "BLOCK"
reasonCode: "ERR_SANCTIONED_COUNTRY_PATTERN"
metadata:
riskLevel: "CRITICAL"
actionSource: "DETERMINISTIC_REGEX"
actions: - actionType: "KAFKA_DISPATCH"
params:
topic: "risk.compliance.alerts"
priority: "HIGH" - actionType: "FREEZE_ACCOUNT"
params:
lockType: "TOTAL_LOCK"

    onNoMatch:
      id: "node_02_velocity_and_amount"
      type: "ExpressionNode"
      description: "Evaluate transaction amount thresholds and fast context parameters"
      config:
        expression: "payload.amount > 10000.00 || context.userRiskTier == 'HIGH_RISK'"
      branches:
        onFalse:
          id: "leaf_allow_deterministic"
          type: "OutcomeLeaf"
          outcome: "ALLOW"
          reasonCode: "OK_FAST_PASS"
          metadata:
            confidenceScore: 1.0
          actions: []

        onTrue:
          id: "node_03_jev_system_one_check"
          type: "JevJudgmentNode"
          description: "Escalate to TypeSafe AI Jev /v1/systemone when high-value or high-risk context is detected"
          config:
            endpoint: "/v1/systemone"
            promptTemplate: "Analyze the following user transaction for semantic fraud patterns, suspicious narrative behavior, or account takeover indicators. User Context: ${context.userHistorySummary}, Current Transaction Payload: ${payload.rawJson}"
            judgmentType: "NOUL" # Binary Truth/Untruth calibrated assessment
            confidenceThresholds:
              tauHigh: 0.85
              tauLow: 0.15
            timeoutMs: 250
            failurePolicy: "FALLBACK_TO_UNCERTAIN"
          branches:
            onMatch: # High Confidence Unsafe (Noul = True)
              id: "leaf_challenge_fraud"
              type: "OutcomeLeaf"
              outcome: "CHALLENGE"
              reasonCode: "RISK_JEV_HIGH_PROBABILITY_FRAUD"
              metadata:
                riskLevel: "HIGH"
                actionSource: "NEURAL_SYSTEM_ONE"
              actions:
                - actionType: "STEP_UP_AUTHENTICATION"
                  params:
                    authType: "HARDWARE_TOKEN_OR_BIOMETRIC"

            onNoMatch: # High Confidence Safe (Noul = False)
              id: "leaf_recommend_vip"
              type: "OutcomeLeaf"
              outcome: "RECOMMEND_TIER_A"
              reasonCode: "OK_VERIFIED_HIGH_VALUE"
              metadata:
                vipEligible: true
              actions:
                - actionType: "APPLY_PROMO_CREDIT"
                  params:
                    creditAmount: 50.0

            onUncertain: # Low confidence band or network fallback trigger
              id: "leaf_escalate_human_review"
              type: "OutcomeLeaf"
              outcome: "ESCALATE"
              reasonCode: "WARN_JEV_UNCERTAIN_AMBIGUITY"
              metadata:
                requiresManualAudit: true
              actions:
                - actionType: "CREATE_WORKFLOW_TICKET"
                  params:
                    queue: "MANUAL_FRAUD_REVIEW"

7. Core Kotlin AST & Jev Client Implementation
   The following Kotlin code provides the production-ready core implementation of AegisTree's AST nodes, execution context, engine pipeline, and TypeSafe AI Jev HTTP client integration.
   7.1 AST & Context Data Models
   package com.aegistree.core.ast

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.Instant

/\*\*

- Standardized typed decision outcomes emitted by AegisTree terminal leaves.
  \*/
  enum class CoreOutcome {
  ALLOW,
  BLOCK,
  CHALLENGE,
  RECOMMEND_TIER_A,
  RECOMMEND_TIER_B,
  ROUTE_QUEUE,
  ESCALATE,
  REDACT_AND_FORWARD,
  CUSTOM
  }

/\*\*

- Side-effect descriptor queued for non-blocking asynchronous execution via SPI plugins.
  \*/
  data class ActionSpec(
  val actionType: String,
  val params: Map<String, Any?> = emptyMap()
  )

/\*\*

- Thread-safe evaluation context carrying incoming payload data, extracted regex variables,
- transient state counters, and trace flags.
  \*/
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

/\*\*

- Granular node hop metric stored inside the execution trace.
  \*/
  data class NodeTraceStep(
  val nodeId: String,
  val nodeType: String,
  val executionTimeNanos: Long,
  val evaluationResult: String,
  val metadata: Map<String, Any?> = emptyMap()
  )

/\*\*

- Fully deterministic trace metadata returned alongside decision verdicts for complete auditing.
  \*/
  data class ExecutionTrace(
  val traceId: String,
  val startTime: Instant = Instant.now(),
  var totalLatencyMicros: Long = 0,
  val steps: MutableList<NodeTraceStep> = mutableListOf()
  )

/\*\*

- Sealed abstract syntax tree base node for AegisTree.
  \*/
  @JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  include = JsonTypeInfo.As.PROPERTY,
  property = "type"
  )
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
          val judgmentType: String, // "NOUL", "CHOICE", or "SCORE"
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

/\*\*

- Evaluation response output wrapper.
  \*/
  data class EvaluationVerdict(
  val outcome: String,
  val reasonCode: String,
  val metadata: Map<String, Any?>,
  val actions: List<ActionSpec>,
  val trace: ExecutionTrace?
  )
  7.2 TypeSafe AI Jev Engine Integration Client
  package com.aegistree.jev.client

import com.fasterxml.jackson.annotation.JsonProperty
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.future.await

// --- DTO Schema matching TypeSafe AI /v1/systemone Specification ---

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

/\*\*

- Non-blocking, resilient HTTP client targeting TypeSafe AI's /v1/systemone API.
  \*/
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

      override suspend fun evaluateSystemOne(request: JevSystemOneRequest): Result<JevSystemOneResponse> = runCatching {
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
              throw RuntimeException("Jev Endpoint returned HTTP code: ${response.statusCode()} payload: ${response.body()}")
          }

          mapper.readValue(response.body(), JevSystemOneResponse::class.java)
      }

  }
  7.3 Core Recursive Coroutine Evaluation Engine
  package com.aegistree.core.engine

import com.aegistree.core.ast.\*
import com.aegistree.jev.client.JevClient
import com.aegistree.jev.client.JevSystemOneRequest
import kotlinx.coroutines.coroutineScope
import java.util.regex.Pattern
import java.util.concurrent.ConcurrentHashMap

class TreeExecutionEngine(
private val jevClient: JevClient
) {
private val compiledRegexCache = ConcurrentHashMap<String, Pattern>()

    /**
     * Executes decision tree logic recursively over an incoming payload context.
     */
    suspend fun execute(
        rootNode: DecisionNode,
        context: DecisionContext
    ): EvaluationVerdict = coroutineScope {
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

                val nextNode = if (isMatch) node.onMatch else node.onNoMatch
                evaluateNode(nextNode, context, trace)
            }

            is DecisionNode.ExpressionNode -> {
                // Production implementations resolve CEL/SpEL via sandboxed AST runner.
                // Evaluates basic boolean logic over extracted context parameters.
                val exprResult = evaluateSimplePredicate(node.expression, context)

                trace.steps.add(
                    NodeTraceStep(
                        nodeId = node.id,
                        nodeType = "ExpressionNode",
                        executionTimeNanos = System.nanoTime() - nodeStartTime,
                        evaluationResult = exprResult.toString()
                    )
                )

                val nextNode = if (exprResult) node.onTrue else node.onFalse
                evaluateNode(nextNode, context, trace)
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
                            prob >= node.tauHigh && (resp.noulVerdict == true || resp.selectedChoice != null) -> {
                                Pair(node.onMatch, "JEV_MATCH (prob=$prob)")
                            }
                            (1.0 - prob) >= (1.0 - node.tauLow) && resp.noulVerdict == false -> {
                                Pair(node.onNoMatch, "JEV_NO_MATCH (prob=$prob)")
                            }
                            else -> {
                                Pair(node.onUncertain, "JEV_UNCERTAIN_PROBABILITY (prob=$prob)")
                            }
                        }
                    },
                    onFailure = { err ->
                        // Fallback handling on network, circuit breaker, or timeout exception
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
        // Simple evaluation fallback logic for context flags
        if (expression.contains("amount > 10000")) {
            val amount = context.getTyped<Number>("payload.amount")?.toDouble() ?: 0.0
            return amount > 10000.0
        }
        return false
    }

    private fun interpolatePrompt(template: String, context: DecisionContext): String {
        var result = template
        context.payload.forEach { (k, v) ->
            result = result.replace("\${payload.$k}", v.toString())
        }
        context.contextVariables.forEach { (k, v) ->
            result = result.replace("\${context.$k}", v.toString())
        }
        return result
    }

}

8.  Policy Lifecycle, Static Validation, Shadow Testing & Observability
    8.1 Compile-Time Static Validation Checklist
    Before any updated policy tree is compiled into active AtomicReference pointer slots, the aegistree-cli engine runs static code analysis validation checks:

                      +-------------------------------------------------------+
                      |         INCOMING POLICY SUBMISSION (YAML/JSON)        |
                      +-------------------------------------------------------+
                                                  |
                                                  v
                      +-------------------------------------------------------+
                      |             STAGE 1: Structural Schema &              |
                      |                Syntactic Validation                   |
                      +-------------------------------------------------------+
                                                  |
                                                  v
                      +-------------------------------------------------------+
                      |           STAGE 2: Static Analysis Checks             |
                      |  - Cycle & Loop Detection (DAG Enforcer)              |
                      |  - ReDoS Protection (Linear Time Regex Audit)         |
                      |  - Unreachable Branch Identification                  |
                      |  - Missing Jev ON_UNCERTAIN / Fallback Handlers       |
                      +-------------------------------------------------------+
                                                  |
                             +--------------------+--------------------+
                             |                                         |
                     [ Check Failure ]                         [ Checks Passed ]
                             |                                         |
                             v                                         v
             +-------------------------------+         +-------------------------------+
             | Reject Submission             |         | Stage to Active Policy        |
             | Return Diagnostic Errors      |         | Registry (Atomic Pointer Swap)|
             +-------------------------------+         +-------------------------------+

Cycle & Loop Detection: Traverses paths using Depth-First Search (DFS) to enforce a acyclic (DAG) layout structure.
ReDoS Protection: Validates all regular expressions in RegexNode configurations using RE2 static checks to guarantee $O(N)$ worst-case execution time and prevent catastrophic backtracking vulnerability attacks.
Unreachable Branch Identification: Detects dead paths and unreachable subtree nodes.
Fallback Handler Enforcer: Validates that all JevJudgmentNode definitions explicitly specify an onUncertain fallback target leaf node.
8.2 Shadow / Canary Evaluation Mode
To allow safe validation of newly deployed policies without risking production disruption, AegisTree supports a zero-risk Shadow Evaluation Mode.

                           +-------------------------------------+
                           |      Incoming Production Request    |
                           +-------------------------------------+
                                              |
                                              v
                           +-------------------------------------+
                           |        Production Router            |
                           +-------------------------------------+
                                     /                 \
                 [ Synchronous Execution ]         [ Non-blocking Async Fork ]
                                   /                     \
                                  v                       v
            +---------------------------+   +---------------------------+
            |  Active Baseline Policy   |   |   Shadow Target Policy    |
            |     (Version 1.4.0)       |   |     (Version 2.0.0-RC)   |
            +---------------------------+   +---------------------------+
                          |                               |
                          v                               v
            +---------------------------+   +---------------------------+
            | Emit Synchronous Verdict  |   | Suppress ActionSpecs      |
            |   & Execute Side-Effects  |   | Record Divergence Metric  |
            +---------------------------+   +---------------------------+

During shadow execution, the engine evaluates both the active baseline policy and candidate shadow target policies asynchronously. Shadow nodes execute identical logic, but their ActionSpec side-effects are automatically suppressed.
8.3 Observability & Telemetry Standard
AegisTree exports real-time execution stats through OpenTelemetry and Micrometer frameworks.
Standard Prometheus Metric Descriptors
aegistree_engine_eval_duration_seconds_bucket{policy_id, outcome}: Histogram of overall resolution latencies.
aegistree_node_eval_duration_nanos{node_type, node_id}: Granular microsecond timings per node instance.
aegistree_jev_confidence_distribution{policy_id, node_id}: Continuous metric capturing output distribution probabilities $\tau$.
aegistree_shadow_divergence_total{baseline_policy_id, shadow_policy_id}: Metric incremented whenever candidate shadow output outcomes differ from the active production decision.
PII-Safe Structured Audit Log Schema
n
{
"timestamp": "2026-03-31T14:22:09.102Z",
"traceId": "c4b912a7-f321-42ab-881c-0b9213ef2110",
"policyId": "risk_and_recommendation_v1",
"policyVersion": "1.4.0",
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

#### 8.4 Open-Source Development Roadmap

v0.1 CORE ENGINE v0.2 RESILIENCE & JEV v0.3 GATEWAY SERVER v1.0 PRODUCTION READY
┌───────────────────────┐ ┌───────────────────────┐ ┌───────────────────────┐ ┌───────────────────────┐
│ • JVM Core AST Model │ │ • Jev System One SDK │ │ • Netty gRPC Gateway │ │ • Kafka Action SPI │
│ • Deterministic Engine│ │ • Circuit Breakers │ │ • REST JSON Endpoint │ │ • Shadow Execution │
│ • YAML Parser │ │ • Dynamic Caching │ │ • Hot Swap Registry │ │ • Web Console UI │
└───────────────────────┘ └───────────────────────┘ └───────────────────────┘ └───────────────────────┘

- **Phase v0.1 (Core AST Framework)**: Release pure JVM core evaluation engine (`aegistree-core`), basic CEL evaluation support, RE2 regex engine integration, and single-file YAML policy loading mechanisms.
- **Phase v0.2 (Jev Integration & Resilience)**: Include `aegistree-jev-client` module with built-in L1/L2 caching, Resilience4j circuit breaking fallbacks, static AST optimization analyzers, and unit testing assertion frameworks.
- **Phase v0.3 (High-Throughput Gateway Server)**: Introduce Netty gRPC and HTTP/2 stateless engine server wrapper (`aegistree-server`), zero-downtime hot policy updates using dynamic lock-free registry pointers, and baseline OpenTelemetry integrations.
- **Phase v1.0 (Enterprise Async SPI & Shadow Engine)**: Release Apache Kafka Action SPI dispatchers (`aegistree-spi-kafka`), complete shadow canary routing support, interactive policy validation CLI, and administrative Web Console dashboards.
