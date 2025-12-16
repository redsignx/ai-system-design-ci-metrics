#!/usr/bin/env groovy

import groovy.json.JsonOutput

/**
 * metricStage
 *
 * Simplified stage wrapper that emits a single stage_end event per invocation.
 *
 * - Generates a unique stage_id for each invocation.
 * - Runs the provided body.
 * - Emits stage_end with: stage_name, stage_id, status, duration_ms.
 * - On error, also includes: error_class, error_message.
 *
 * Notes:
 * - This library no longer tracks parent/stack context.
 * - No special handling is required for parallel; use Jenkins native parallel.
 */
def call(String stageName, Map metadata = [:], Closure body) {
  // Unique per invocation. Prefer UUID for uniqueness across nodes/executors.
  final String stageId = java.util.UUID.randomUUID().toString()

  long startMs = System.currentTimeMillis()
  String status = 'SUCCESS'
  Throwable err = null

  try {
    stage(stageName) {
      body.call()
    }
  } catch (Throwable t) {
    status = 'FAILURE'
    err = t
    throw t
  } finally {
    long durationMs = System.currentTimeMillis() - startMs

    Map payload = [:]
    payload.stage_name = stageName
    payload.stage_id = stageId
    payload.status = status
    payload.duration_ms = durationMs

    // Allow callers to attach custom fields (e.g., to disambiguate runs in parallel).
    if (metadata != null && !metadata.isEmpty()) {
      payload.putAll(metadata)
    }

    if (err != null) {
      payload.error_class = err.getClass().getName()
      payload.error_message = (err.getMessage() ?: err.toString())
    }

    // Emit via the generic metricEvent step if present, otherwise fall back to echo.
    try {
      metricEvent('stage_end', payload)
    } catch (MissingMethodException ignored) {
      echo(JsonOutput.toJson([event: 'stage_end', data: payload]))
    }
  }
}
