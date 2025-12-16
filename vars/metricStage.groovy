#!/usr/bin/groovy

import java.util.UUID

/**
 * metricStage
 * -----------
 * Stage wrapper that emits ONLY a stage_end event.
 *
 * Features:
 * - stage_id: UUID per stage invocation
 * - parent_stage_id: maintained via a stack
 * - parallel_group / parallel_branch: supported via shared context, set by metricParallel
 * - best-effort emission (never fails pipeline)
 *
 * Usage:
 *   metricStage('Build') {
 *     sh 'make build'
 *   }
 */

def call(String stageName, Closure body) {
  if (stageName == null) {
    stageName = ''
  }

  String buildKey = (env.BUILD_TAG ?: "${env.JOB_NAME ?: 'job'}#${env.BUILD_NUMBER ?: '0'}")

  // Shared per-build context stored in the script binding.
  if (!binding.hasVariable('_metricCtx')) {
    binding.setVariable('_metricCtx', [:])
  }
  Map allCtx = (Map) binding.getVariable('_metricCtx')
  if (!allCtx.containsKey(buildKey)) {
    allCtx[buildKey] = [ stack: [], parallel_group: null, parallel_branch: null ]
  }
  Map ctx = (Map) allCtx[buildKey]
  List stack = (List) ctx.stack

  String stageId = UUID.randomUUID().toString()
  String parentStageId = stack ? stack[-1]?.toString() : null

  long startMs = System.currentTimeMillis()
  String status = 'SUCCESS'
  Throwable err = null

  stack.add(stageId)

  try {
    stage(stageName) {
      body.call()
    }
  } catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException fie) {
    status = 'ABORTED'
    err = fie
    throw fie
  } catch (Throwable t) {
    status = 'FAILURE'
    err = t
    throw t
  } finally {
    long durationMs = Math.max(0L, System.currentTimeMillis() - startMs)

    // Pop current stage id (defensive)
    if (stack && stack[-1]?.toString() == stageId) {
      stack.remove(stack.size() - 1)
    } else {
      // If stack got out of sync for any reason, remove first occurrence.
      stack.remove(stageId)
    }

    Map data = [
      stage_name      : stageName,
      stage_id        : stageId,
      parent_stage_id : parentStageId,
      parallel_group  : ctx.parallel_group,
      parallel_branch : ctx.parallel_branch,
      status          : status,
      duration_ms     : durationMs,
    ]

    if (err != null) {
      data.error_class = err.getClass().getName()
      data.error_message = (err.getMessage() ?: '').take(500)
    }

    try {
      metricEvent(eventType: 'stage_end', data: data)
    } catch (ignored) {
      // never fail pipeline
    }
  }
}
