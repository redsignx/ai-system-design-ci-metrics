# ai-system-design-ci-metrics

Jenkins shared library helpers for emitting CI metrics.

## Key changes / current behavior

- **No parent/stack context tracking**: the library no longer tracks parent stages or a stage stack.
- **Parallel does not need special handling**: use Jenkins **native** `parallel(...)`.
- `metricParallel` is **optional / legacy**. It is kept as a thin wrapper around `parallel` for backward compatibility, but it does not set any context.

## Steps

### `metricStage(stageName, metadata = [:]) { ... }`

Wraps a Jenkins `stage(...)` and emits a single `stage_end` event per invocation.

Emitted fields:

- `stage_name`
- `stage_id` (unique per invocation)
- `status` (`SUCCESS` or `FAILURE`)
- `duration_ms`
- optionally `error_class`, `error_message` on failure

You may pass additional `metadata` fields to help disambiguate multiple invocations with the same `stage_name` (e.g., when running in parallel).

### `metricEvent(eventName, data)`

Use to emit custom events and/or attach additional information before/after stages.

## Parallel guidance

If you run the same `stage_name` in multiple parallel branches:

- `metricStage` will still generate a unique `stage_id` for each invocation.
- Add per-branch metadata (e.g. `branch: 'linux'`) via the `metadata` argument, **or**
- Emit a `metricEvent(...)` after the stage to add additional branch-specific information.

## Example

See [`examples/Jenkinsfile`](examples/Jenkinsfile).
