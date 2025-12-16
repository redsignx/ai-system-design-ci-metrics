#!/usr/bin/groovy

import groovy.json.JsonOutput

/**
 * metricEvent
 * -----------
 * Generic best-effort event emitter.
 *
 * Usage:
 *   metricEvent(eventType: 'stage_end', data: [foo: 'bar'])
 *
 * Configuration:
 *   env.METRIC_ENDPOINT_URL (required)
 *   env.METRIC_CURL_TIMEOUT_SEC (optional, default 3)
 *   env.METRIC_EMIT (optional, default true)
 */

def call(Map args = [:]) {
  String endpoint = (env.METRIC_ENDPOINT_URL ?: '').trim()
  String emitFlag = (env.METRIC_EMIT ?: 'true').toString()
  if (!endpoint || emitFlag.equalsIgnoreCase('false')) {
    return
  }

  String eventType = (args.eventType ?: args.type ?: '').toString()
  Map data = (args.data instanceof Map) ? (Map) args.data : [:]

  Map payload = [
    event_type   : eventType,
    ts           : new Date().format("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", TimeZone.getTimeZone('UTC')),
    job_name     : env.JOB_NAME,
    build_number : env.BUILD_NUMBER,
    build_url    : env.BUILD_URL,
  ] + data

  String json = JsonOutput.toJson(payload)
  // Base64 to avoid shell quoting issues.
  String b64 = json.bytes.encodeBase64().toString()
  int timeoutSec = (env.METRIC_CURL_TIMEOUT_SEC ?: '3').toInteger()

  try {
    sh(
      label: 'metricEvent',
      returnStatus: true,
      script: """#!/usr/bin/env bash
set +euo pipefail
payload_b64='${b64}'
json_payload=\$(printf '%s' \"\$payload_b64\" | base64 --decode 2>/dev/null)
if [ -z \"\$json_payload\" ]; then
  exit 0
fi
curl -sS -m ${timeoutSec} \\
  -H 'Content-Type: application/json' \\
  -X POST \\
  --data \"\$json_payload\" \\
  '${endpoint}' \\
  >/dev/null 2>&1
exit 0
"""
    )
  } catch (ignored) {
    // never fail the pipeline because of metrics
  }
}
