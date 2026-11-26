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
│ type: "Cel"                   │
│ (payload.amount > 10000 USD)  │
└───────────────────────────────┘
        │
   ┌────┴────┐
 ["true"] ["false"]
   │         │
   ▼         ▼
type: "Jev"     OutcomeLeaf: ALLOW
   │
   ├── "match"      (high conf fraud)
   ├── "noMatch"    (high conf safe)
   └── "uncertain"  (low conf / circuit open)
```

### Node Evaluation Semantics

Nodes are **generic**: a node has a `type` string, a `config` map, and a `branches` map (branch label → child). The engine resolves `type` through the `NodeEvaluator` registry — there is no hardcoded node hierarchy. Built-in evaluators are just default-registered plugins; applications register their own.

**Built-in evaluators** (each returns a branch label):

| Evaluator `type` | Behavior | Branch labels |
|---|---|---|
| `Regex` | Linear-time-safe (RE2-compliant) regex match; captured groups populate `DecisionContext` | `match` / `noMatch` |
| `Cel` | Sandboxed Google CEL boolean expression over payload/context | `true` / `false` |
| `Velocity` | Distributed sliding-window counter (Redis) or local cache (Caffeine) | `exceed` / `below` |
| `Jev` | Sends a structured prompt to `/v1/systemone`; branches on returned probability | `match` / `noMatch` / `uncertain` |
| `Composite` | Boolean combination (`ALL`/`ANY`/`NOT`) with cost-aware short-circuiting | `true` / `false` |
| `Switch` | Multi-way routing on equality, ranges, or string enums | any configured labels |

**Terminal node** (`type: "OutcomeLeaf"`): a node with an `outcome` field — no evaluator, no branches. It produces the `DecisionOutcome` (reason code, metadata, `ActionSpec` list).

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

A strategy is a JSON document persisted as a PostgreSQL `jsonb` column. `rootNode` is the DAG entry point. Strategies are versioned using [Semantic Versioning](https://semver.org) (`MAJOR.MINOR.PATCH`, e.g. `0.0.0`, `0.0.1`) — each edit creates a new immutable version, and publishing promotes a version to active. Outcomes are declared in the `outcomes` array (never hardcoded); each node has a `type` (resolved through the evaluator registry), a `config` map, and a `branches` map of branch labels.

```json
{
  "schemaVersion": "aegistree/v1",
  "id": "risk_and_recommendation_v1",
  "version": "0.0.1",
  "description": "Universal Hybrid Strategy combining Fast Rules with Jev System One Ambiguity Resolution",
  "outcomes": ["ALLOW", "BLOCK", "CHALLENGE", "ESCALATE", "RECOMMEND_TIER_A"],
  "metadata": {
    "owner": "risk-engineering-team"
  },
  "rootNode": {
    "id": "node_01_sanction_check",
    "type": "Regex",
    "config": {
      "targetField": "payload.accountNumber",
      "pattern": "^(IR|KP|SY|CU)[0-9]{2}[A-Z0-9]{12,30}$",
      "captureVariables": { "countryCode": "$1" }
    },
    "branches": {
      "match": {
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
      "noMatch": {
        "id": "node_02_velocity_and_amount",
        "type": "Cel",
        "config": {
          "expression": "payload.amount > 10000.00 || context.userRiskTier == 'HIGH_RISK'"
        },
        "branches": {
          "false": {
            "id": "leaf_allow_deterministic",
            "type": "OutcomeLeaf",
            "outcome": "ALLOW",
            "reasonCode": "OK_FAST_PASS",
            "metadata": { "confidenceScore": 1.0 },
            "actions": []
          },
          "true": {
            "id": "node_03_jev_system_one_check",
            "type": "Jev",
            "config": {
              "promptTemplate": "Analyze this transaction for fraud patterns... ${context.userHistorySummary} ${payload.rawJson}",
              "judgmentType": "Noul",
              "tauHigh": 0.85,
              "tauLow": 0.15,
              "timeoutMs": 250,
              "failurePolicy": "FALLBACK_TO_UNCERTAIN"
            },
            "branches": {
              "match": {
                "id": "leaf_challenge_fraud",
                "type": "OutcomeLeaf",
                "outcome": "CHALLENGE",
                "reasonCode": "RISK_JEV_HIGH_PROBABILITY_FRAUD",
                "metadata": { "riskLevel": "HIGH", "actionSource": "NEURAL_SYSTEM_ONE" },
                "actions": [
                  { "actionType": "STEP_UP_AUTHENTICATION", "params": { "authType": "HARDWARE_TOKEN_OR_BIOMETRIC" } }
                ]
              },
              "noMatch": {
                "id": "leaf_recommend_vip",
                "type": "OutcomeLeaf",
                "outcome": "RECOMMEND_TIER_A",
                "reasonCode": "OK_VERIFIED_HIGH_VALUE",
                "metadata": { "vipEligible": true },
                "actions": [
                  { "actionType": "APPLY_PROMO_CREDIT", "params": { "creditAmount": 50.0 } }
                ]
              },
              "uncertain": {
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
    }
  }
}
```

## 7. PostgreSQL Schema

Strategies and their versions are persisted in PostgreSQL. The DAG itself is stored as `jsonb`; versions are immutable and use semantic versioning.

| Table | Purpose |
|---|---|
| `strategies` | One row per strategy (stable identity + metadata). |
| `strategy_versions` | Immutable point-in-time snapshots of a strategy's JSON DAG. |

### `strategies`

| Column | Type | Notes |
|---|---|---|
| `id` | `uuid` | Primary key |
| `slug` | `text` | Unique, human-readable handle for invocation |
| `name` | `text` | Display name |
| `description` | `text` | Optional |
| `owner` | `text` | Owning team/person |
| `status` | `text` | `DRAFT` \| `PUBLISHED` \| `ARCHIVED` |
| `active_version` | `text` | Semantic version currently published (e.g. `0.0.1`) |
| `created_at` | `timestamptz` | — |
| `updated_at` | `timestamptz` | — |

### `strategy_versions`

| Column | Type | Notes |
|---|---|---|
| `id` | `uuid` | Primary key |
| `strategy_id` | `uuid` | FK → `strategies.id` |
| `version` | `text` | Semantic version (`0.0.0`, `0.0.1`, …) |
| `schema_json` | `jsonb` | Full DAG definition |
| `checksum` | `text` | SHA-256 of `schema_json` for cache keying |
| `status` | `text` | `DRAFT` \| `VALIDATED` \| `PUBLISHED` \| `SUPERSEDED` \| `REJECTED` |
| `validation_report` | `jsonb` | Static validation errors/warnings |
| `published_at` | `timestamptz` | Set when published |
| `created_at` | `timestamptz` | — |
| `created_by` | `text` | — |

**Constraints:** `UNIQUE (strategy_id, version)` — versions are immutable and monotonic.

**Lifecycle:** creating a strategy writes a `0.0.0` draft; each edit creates the next patch/minor version as a new draft; publishing promotes a version to `PUBLISHED` and marks the previous active version `SUPERSEDED`.

```
DRAFT ──validate──► VALIDATED ──publish──► PUBLISHED ──new version──► SUPERSEDED
  │                      │                                          │
  └──────reject──────────┘                                          └──archive──► ARCHIVED
```

## 8. REST API Specification

Management endpoints are authenticated with OAuth 2.0 / OIDC; the evaluation endpoint uses an API key.

| Method | Path | Purpose | Auth |
|---|---|---|---|
| `POST` | `/api/v1/strategies` | Create a new strategy (draft `0.0.0`) | OAuth |
| `GET` | `/api/v1/strategies` | List strategies | OAuth |
| `GET` | `/api/v1/strategies/{id}` | Get strategy (latest or `?version=`) | OAuth |
| `PUT` | `/api/v1/strategies/{id}` | Edit strategy → new draft version | OAuth |
| `POST` | `/api/v1/strategies/{id}/validate` | Run static validation | OAuth |
| `POST` | `/api/v1/strategies/{id}/publish` | Publish a version (default latest draft) | OAuth |
| `GET` | `/api/v1/strategies/{id}/versions` | List versions | OAuth |
| `GET` | `/api/v1/strategies/{id}/versions/{version}` | Get a specific version | OAuth |
| `POST` | `/api/v1/strategies/{id}/archive` | Archive a strategy | OAuth |
| `POST` | `/api/v1/evaluate` | Evaluate a payload against a strategy | API key |

### Create strategy

`POST /api/v1/strategies`

**Request**

```json
{
  "name": "Risk & AML Screening",
  "description": "Optional description",
  "owner": "risk-engineering-team",
  "schema": { "schemaVersion": "aegistree/v1", "rootNode": {} }
}
```

**Response `201 Created`**

```json
{
  "strategyId": "6f8c…",
  "slug": "risk-aml-screening",
  "version": "0.0.0",
  "status": "DRAFT",
  "createdAt": "2026-11-26T10:00:00Z"
}
```

### Edit strategy (new draft version)

`PUT /api/v1/strategies/{id}`

**Request** — same shape as create (`schema` required; `name`/`description`/`owner` optional).

**Response `200 OK`**

```json
{
  "version": "0.0.1",
  "status": "DRAFT",
  "checksum": "sha256:…",
  "updatedAt": "2026-11-26T10:05:00Z"
}
```

### Validate strategy

`POST /api/v1/strategies/{id}/validate`

**Request** — optional `schema` override (defaults to latest draft).

**Response `200 OK`**

```json
{
  "valid": false,
  "errors": [
    { "code": "CYCLE_DETECTED", "message": "DAG contains a cycle", "nodeId": "node_x", "path": "rootNode.branches.match" },
    { "code": "MISSING_UNCERTAIN", "message": "Jev node lacks 'uncertain' branch", "nodeId": "node_jev" }
  ]
}
```

### Publish strategy

`POST /api/v1/strategies/{id}/publish`

**Request** — `{ "version": "0.0.1" }` (optional; defaults to latest validated draft).

**Response `200 OK`**

```json
{
  "publishedVersion": "0.0.1",
  "publishedAt": "2026-11-26T10:10:00Z",
  "status": "PUBLISHED"
}
```

### List versions

`GET /api/v1/strategies/{id}/versions`

**Response `200 OK`**

```json
{
  "items": [
    { "version": "0.0.1", "status": "PUBLISHED", "checksum": "sha256:…", "publishedAt": "…" },
    { "version": "0.0.0", "status": "SUPERSEDED", "checksum": "sha256:…", "publishedAt": "…" }
  ]
}
```

### Evaluate

`POST /api/v1/evaluate`

**Request**

```json
{
  "strategyRef": { "slug": "risk-aml-screening", "version": "0.0.1" },
  "payload": { "accountNumber": "IR1234567890123456789012", "amount": 15000.00 },
  "context": { "summary": "New account, high-value transfer" },
  "options": { "trace": true, "shadow": false }
}
```

**Response `200 OK`**

```json
{
  "traceId": "c4b912a7-f321-42ab-881c-0b9213ef2110",
  "strategyId": "risk-aml-screening",
  "version": "0.0.1",
  "verdict": {
    "outcome": "CHALLENGE",
    "reasonCode": "RISK_JEV_HIGH",
    "metadata": { "riskLevel": "HIGH" },
    "actions": [
      { "actionType": "STEP_UP_AUTHENTICATION", "params": { "authType": "HARDWARE_TOKEN_OR_BIOMETRIC" } }
    ]
  },
  "trace": []
}
```

## 9. Core Kotlin AST & Jev Client Implementation

### 9.1 AST & Context Data Models

```kotlin
package com.aegistree.core.ast

import java.time.Instant

// NOTE: There is no outcome enum. Outcomes are domain-defined strings,
// declared per-strategy in the `outcomes` list of the JSON document.

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

// Generic DAG node. No sealed hierarchy — `type` is an extension point resolved
// through the NodeEvaluator registry. `branches` maps a branch label to a child node.
data class Node(
    val id: String,
    val type: String,
    val config: Map<String, Any?> = emptyMap(),
    val branches: Map<String, Node> = emptyMap(),
    // Terminal (OutcomeLeaf) fields — set only when this node is a leaf:
    val outcome: String? = null,
    val reasonCode: String? = null,
    val metadata: Map<String, Any?> = emptyMap(),
    val actions: List<ActionSpec> = emptyList()
)

// The whole evaluator contract: (config, context) -> branch label.
interface NodeEvaluator {
    val type: String
    fun evaluate(config: Map<String, Any?>, context: DecisionContext): String
}

// Registry mapping node type -> evaluator. Built-ins are default-registered;
// third parties register custom evaluators here (e.g. via ServiceLoader).
class EvaluatorRegistry {
    private val evaluators = java.util.concurrent.ConcurrentHashMap<String, NodeEvaluator>()

    fun register(evaluator: NodeEvaluator) {
        evaluators[evaluator.type] = evaluator
    }

    fun resolve(type: String): NodeEvaluator =
        evaluators[type] ?: throw UnknownNodeType("No evaluator registered for type '$type'")
}

class UnknownNodeType(message: String) : RuntimeException(message)

// Evaluation response output wrapper.
data class EvaluationVerdict(
    val outcome: String,
    val reasonCode: String,
    val metadata: Map<String, Any?>,
    val actions: List<ActionSpec>,
    val trace: ExecutionTrace?
)
```

### 9.2 TypeSafe AI Jev Client

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

### 9.3 Core Recursive Coroutine Evaluation Engine

```kotlin
package com.aegistree.core.engine

import com.aegistree.core.ast.*
import kotlinx.coroutines.coroutineScope

class TreeExecutionEngine(
    private val registry: EvaluatorRegistry
) {
    suspend fun execute(rootNode: Node, context: DecisionContext): EvaluationVerdict =
        coroutineScope {
            val trace = ExecutionTrace(traceId = java.util.UUID.randomUUID().toString())
            val startTimeNanos = System.nanoTime()

            val leaf = evaluateNode(rootNode, context, trace)
            trace.totalLatencyMicros = (System.nanoTime() - startTimeNanos) / 1000

            EvaluationVerdict(
                outcome = leaf.outcome ?: "UNKNOWN",
                reasonCode = leaf.reasonCode ?: "",
                metadata = leaf.metadata,
                actions = leaf.actions,
                trace = if (context.traceEnabled) trace else null
            )
        }

    private suspend fun evaluateNode(node: Node, context: DecisionContext, trace: ExecutionTrace): Node {
        val start = System.nanoTime()

        // Terminal node (OutcomeLeaf): stop and return it.
        if (node.outcome != null) {
            trace.steps.add(NodeTraceStep(node.id, node.type, System.nanoTime() - start, node.outcome))
            return node
        }

        // Delegate to the registered evaluator; it returns a branch label.
        val evaluator = registry.resolve(node.type)
        val branchLabel = evaluator.evaluate(node.config, context)

        trace.steps.add(NodeTraceStep(node.id, node.type, System.nanoTime() - start, branchLabel))

        val next = node.branches[branchLabel]
            ?: throw MissingBranch("Node '${node.id}' (${node.type}) returned unknown branch '$branchLabel'")

        return evaluateNode(next, context, trace)
    }
}

class MissingBranch(message: String) : RuntimeException(message)
```

### 9.4 Evaluators (built-in & custom)

Built-ins are ordinary `NodeEvaluator` implementations registered by default. Third parties add new node types without touching the core.

```kotlin
package com.aegistree.evaluators

import com.aegistree.core.ast.NodeEvaluator
import com.aegistree.core.ast.DecisionContext
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

// Built-in: regex evaluator. Branch labels: "match" | "noMatch".
class RegexEvaluator : NodeEvaluator {
    override val type = "Regex"

    private val cache = ConcurrentHashMap<String, Pattern>()

    override fun evaluate(config: Map<String, Any?>, context: DecisionContext): String {
        val targetField = config["targetField"] as String
        val patternStr = config["pattern"] as String
        val fieldValue = context.getTyped<String>(targetField) ?: ""

        val pattern = cache.computeIfAbsent(patternStr) { Pattern.compile(it) }
        val matched = pattern.matcher(fieldValue).find()

        // Capture groups into the context for downstream nodes.
        val captures = config["captureVariables"] as? Map<String, String> ?: emptyMap()
        if (matched && captures.isNotEmpty()) {
            val matcher = pattern.matcher(fieldValue)
            captures.forEach { (name, groupRef) ->
                val idx = groupRef.removePrefix("$").toIntOrNull() ?: 0
                if (idx <= matcher.groupCount()) context.contextVariables[name] = matcher.group(idx)
            }
        }

        return if (matched) "match" else "noMatch"
    }
}

// A third-party evaluator — no core changes required.
class GeoFenceEvaluator(private val geo: GeoService) : NodeEvaluator {
    override val type = "GeoFence"

    override fun evaluate(config: Map<String, Any?>, context: DecisionContext): String {
        val inside = geo.isInside(
            config["polygonId"] as String,
            context.payload["lat"] as Double,
            context.payload["lon"] as Double
        )
        return if (inside) "inside" else "outside"
    }
}

// Default registry wired up at startup (and augmented via ServiceLoader).
val defaultRegistry = EvaluatorRegistry().apply {
    register(RegexEvaluator())
    register(CelEvaluator())
    register(VelocityEvaluator())
    register(JevEvaluator())
}
```

### 9.5 SDK SPI & Interoperability

AegisTree is shipped as an SDK that other services depend on. Everything domain-specific is injected, so the core has **zero** domain knowledge.

```
aegistree/
├── aegistree-spi/        # ONLY interfaces: NodeEvaluator, ActionDispatcher, JevClient, StrategyStore, CacheProvider
├── aegistree-core/       # engine + registry + generic Node model (depends on spi)
├── aegistree-evaluators/ # built-in Regex/CEL/Velocity/Jev plugins
├── aegistree-jev-client/ # Jev HTTP/gRPC client
├── aegistree-server/     # Spring Boot REST + gRPC gateway
└── aegistree-spi-kafka/  # Kafka ActionDispatcher implementation
```

| Concern | Extension point |
|---|---|
| **Outcomes** | `String` + per-strategy `outcomes[]` declaration — never an enum |
| **Node logic** | `NodeEvaluator` SPI + `EvaluatorRegistry`; `type` is the extension key |
| **Side effects** | `ActionDispatcher` SPI (Kafka / webhook / custom) |
| **Model backend** | `JevClient` interface — swap TypeSafe AI for any model |
| **Cache** | `CacheProvider` SPI — Caffeine / Redis / custom |
| **Storage** | `StrategyStore` SPI — PostgreSQL now, anything later |
| **Contract versioning** | `schemaVersion` (engine contract) vs `version` (per-strategy semver) |
| **Auto-discovery** | `java.util.ServiceLoader` — drop in a JAR, it self-registers |



## 10. Strategy Lifecycle, Static Validation, Shadow Testing & Observability

### 10.1 Compile-Time Static Validation

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
- **Fallback Handler Enforcer** — every `Jev` node must define an `uncertain` branch.

### 10.2 Shadow / Canary Evaluation Mode

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

### 10.3 Observability & Telemetry

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
    { "nodeId": "node_01_sanction_check", "type": "Regex", "latencyNanos": 18200, "result": "noMatch" },
    { "nodeId": "node_02_velocity_and_amount", "type": "Cel", "latencyNanos": 8400, "result": "true" },
    { "nodeId": "node_03_jev_system_one_check", "type": "Jev", "latencyNanos": 41100000, "result": "match (prob=0.92)" }
  ]
}
```

**How to view the telemetry:**

| Signal | Backend | How you look at it |
|---|---|---|
| **Traces** | OpenTelemetry Collector → **Jaeger** or **Grafana Tempo** | Open the backend UI and paste a `traceId` (returned in every evaluate response and audit log) to see the full DAG path with per-node latencies. |
| **Metrics** | **Prometheus** (scrapes) + **Grafana** (dashboards) | Query `aegistree_engine_eval_duration_seconds`, `aegistree_jev_confidence_distribution`, and `aegistree_shadow_divergence_total`. |
| **Logs** | **Loki** (or any log aggregator) | Structured JSON audit logs ship to stdout, are collected, and correlated with traces via `traceId`. |

### 10.4 Open-Source Development Roadmap

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
