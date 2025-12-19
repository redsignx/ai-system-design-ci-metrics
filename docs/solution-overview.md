# Solution overview (Jenkins controller-side plugin)

This document describes the **controller-side Jenkins plugin** solution for emitting CI “stage events” (e.g., checkout, build, unit tests, UI tests, scans, reports, uploads) and forwarding them to downstream storage/analytics (e.g., OpenSearch/Elasticsearch, Kafka, HTTP collector).

> Scope: Jenkins **controller-side** plugin instrumentation for Pipeline / Multibranch Pipelines, with **stage_start**/**stage_end** events, versioned schema, idempotency, and recommended indexing keys.

## Goals

- **Observability**: produce consistent, structured events for pipeline stages across many repos and branches.
- **Low friction**: minimal changes to Jenkinsfiles; controlled centrally via plugin configuration.
- **Reliability**: at-least-once delivery with deduplication keys and replay-safe event IDs.
- **Security**: never leak secrets; enforce allowlists/redaction.
- **Scalability**: handle multibranch pipelines, parallel stages, matrix runs, and high event volumes.

## High-level architecture

### Components

1. **Jenkins controller-side plugin**
   - Hooks into Pipeline execution to observe stage lifecycle and extracts metadata.
   - Emits **stage_start** and **stage_end** events.
   - Adds stable correlation identifiers (run, branch, stage, parallel branch, attempt).
   - Supports retries/backoff and local buffering (if configured).

2. **Event transport / sink** (configurable)
   - HTTP(S) collector endpoint, Kafka producer, or file/spool-to-shipper pattern.
   - Auth via tokens/mTLS (depending on sink).

3. **Storage / indexing**
   - OpenSearch/Elasticsearch index templates / ILM; or data lake.

4. **Dashboards / analytics**
   - Duration, flakiness, failure causes by repo/branch/stage, SLOs.

### Data flow

- Pipeline starts → plugin observes stage entry → emits **stage_start**.
- Stage completes (success/unstable/failure/aborted) → plugin emits **stage_end**.
- Events are shipped to sink and indexed.

## Jenkins integration model

### Where the plugin runs

- Runs on the **controller** (master) to observe Pipeline execution and stage boundaries.
- Collects metadata that is available from:
  - `Run`/`Job`/`WorkflowRun`/`WorkflowJob`
  - Pipeline graph / FlowNodes
  - Multibranch SCM metadata (org/repo, branch, PR)
  - Agent/node information when available

### What it instruments

- **Stage boundaries** from Declarative / Scripted Pipeline stages
- Parallel branch boundaries (child stages) and matrix combinations (if used)

The plugin should treat stages as *logical units* of work and standardize them into a set of stage types (see [`docs/stage-event-schema.md`](./stage-event-schema.md)).

## Plugin configuration

> The exact UI and class names depend on the implementation; the configuration below is the recommended shape.

### Global configuration (Manage Jenkins → Configure System)

Recommended configuration sections:

1. **Event sink**
   - Sink type: `HTTP`, `Kafka`, `File/Spool`
   - Endpoint(s) / brokers
   - Authentication:
     - `Authorization: Bearer <token>` header, or
     - mTLS client certs
   - Request timeouts

2. **Reliability / buffering**
   - Max in-memory queue size
   - Optional on-disk spool directory
   - Retry policy: exponential backoff, max retries
   - Circuit breaker (pause sending when sink is down)

3. **Filtering / allowlists**
   - Allowed job name patterns (glob/regex)
   - Allowed folders
   - Stage type mapping rules (by stage name regex)

4. **Security / redaction**
   - Redact env vars by pattern (e.g., `.*TOKEN.*`, `.*PASSWORD.*`)
   - Allowlist for safe environment keys (recommended)
   - Control whether to include SCM URLs or only repo slug

5. **Schema / versioning**
   - `event_version` default (e.g., `1`)
   - Compatibility mode / migration toggles

### Pipeline-level configuration (optional)

If you need per-repo overrides without changing the plugin globally, support:

- A lightweight `ci-metrics.yml` repository file
- Or Pipeline library step options

When present, overrides can include:

- Stage mapping overrides
- Extra tags (team, service)
- Disable/enable metrics emission for that repo

## Stage typing and mapping

In practice, Jenkins stage names vary widely. The plugin should map arbitrary stage names into canonical `stage_type` values using:

- Regex rules ordered by precedence (e.g., `(?i)^checkout$|^scm` → `checkout`)
- Fallback: `custom` with `custom_stage_name`

Recommended canonical stage types:

- `checkout`
- `build`
- `unittest`
- `uitest`
- `sonarqube_scan`
- `jacoco_report`
- `upload`

## Multibranch, PRs, and parallel stages

### Multibranch handling

For multibranch jobs, the plugin should emit branch/PR metadata consistently:

- `scm.branch`: branch name (e.g., `main`, `feature/x`)
- `scm.change_id`: PR number (if applicable)
- `scm.change_target`: PR target branch
- `scm.revision`: commit SHA at build time (or best-effort)

### Parallel stages

Parallel branches require additional correlation:

- A stable `stage_id` for the logical stage node
- `parallel.branch_name` when stage is inside `parallel {}`
- `parallel.thread_id` or FlowNode ID to disambiguate duplicates

The plugin should treat each parallel branch execution as its own stage instance, while maintaining a shared root context:

- Same `pipeline_run_id`
- Same `job_full_name`
- Distinct `stage_instance_id`

### Matrix builds

If using Declarative matrix:

- Emit `matrix.axes` and `matrix.cell` fields on events
- Include axes in the recommended indexing keys (see below)

## Reliability and delivery semantics

### Stage lifecycle events

For each stage instance:

- Emit **stage_start** when stage is entered.
- Emit **stage_end** when the stage completes.

If a stage is skipped, you may either:

- Emit only `stage_end` with `status=skipped`, or
- Emit both with `stage_start` immediately followed by `stage_end`.

### Idempotency

Events must be replay-safe. The plugin should generate deterministic IDs so consumers can safely upsert:

- `event_id` must be unique per *event occurrence*.
- `stage_instance_id` must be unique per stage execution *instance*.

Recommended approach:

- `pipeline_run_id`: stable run identifier (e.g., `job_full_name + build_number + run_uuid`).
- `stage_instance_id`: `pipeline_run_id + stage_path + parallel_branch_name + attempt`.
- `event_id`: `stage_instance_id + event_type`.

Consumers can upsert on `event_id` (or `event_id` + `event_version`).

### At-least-once delivery

Assume the sink can fail mid-run. To avoid data loss:

- Buffer events in an in-memory queue.
- Retry with exponential backoff.
- Optionally spool to disk for controller restarts.
- Ensure events are **deduplicatable** by `event_id`.

### Clock and durations

Capture:

- `timestamp` (RFC3339/ISO-8601) at emission time
- `stage.start_time` and `stage.end_time` from stage execution (preferred)
- `duration_ms` computed from stage times (not wall-clock when possible)

## Security considerations

- **Do not emit secrets** (credentials, tokens, private keys, full env dumps).
- Prefer allowlisting over blocklisting for environment variables.
- If capturing command lines or logs, apply aggressive redaction; in most cases, do **not** capture logs.
- Use TLS for transport; validate certificates.
- Consider signing events (HMAC) if events traverse untrusted networks.
- Ensure the plugin does not require elevated script approvals; keep surface area minimal.

## Recommended indexing keys

To support common queries (trend by repo/branch, failure analysis, p95 durations), index these fields:

- `event_type` (`stage_start` / `stage_end`)
- `event_version`
- `pipeline_run_id`
- `job_full_name`
- `job_base_name`
- `scm.repo` (org/repo slug)
- `scm.branch`
- `scm.change_id` (PR)
- `build.number`
- `stage.type` and `stage.name`
- `stage_instance_id`
- `parallel.branch_name` (if any)
- `matrix.cell` (if any)
- `status` (on stage_end)
- `duration_ms` (on stage_end)

## Versioning and backwards compatibility

All events include:

- `event_version`: integer schema version

Guidelines:

- Additive changes (new optional fields) → keep same version.
- Breaking changes (rename/remove/semantic changes) → bump version.
- Consumers should accept unknown fields.

## Operational guidance

- Monitor queue depth, retry counts, and sink latency.
- Provide a “dry run” / logging-only mode.
- Provide a self-test endpoint in plugin config page (send sample event).

## References

- Stage event schema: [`docs/stage-event-schema.md`](./stage-event-schema.md)
- Jenkins configuration entry point: *Manage Jenkins → Configure System → CI Metrics / Stage Events* (recommended)
