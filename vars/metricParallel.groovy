#!/usr/bin/groovy

import java.util.UUID

/**
 * metricParallel
 * --------------
 * Wrapper around Jenkins `parallel` that propagates context and
 * sets parallel_group / parallel_branch for stage_end emissions.
 *
 * Usage:
 *   metricParallel([
 *     'a': { ... },
 *     'b': { ... },
 *   ])
 */

def call(Map branches) {
  String buildKey = (env.BUILD_TAG ?: "${env.JOB_NAME ?: 'job'}#${env.BUILD_NUMBER ?: '0'}")

  if (!binding.hasVariable('_metricCtx')) {
    binding.setVariable('_metricCtx', [:])
  }
  Map allCtx = (Map) binding.getVariable('_metricCtx')
  if (!allCtx.containsKey(buildKey)) {
    allCtx[buildKey] = [ stack: [], parallel_group: null, parallel_branch: null ]
  }
  Map ctx = (Map) allCtx[buildKey]

  String groupId = UUID.randomUUID().toString()
  def wrapped = [:]

  branches.each { String name, Closure cl ->
    wrapped[name] = {
      def prevGroup = ctx.parallel_group
      def prevBranch = ctx.parallel_branch
      ctx.parallel_group = groupId
      ctx.parallel_branch = name
      try {
        cl.call()
      } finally {
        ctx.parallel_group = prevGroup
        ctx.parallel_branch = prevBranch
      }
    }
  }

  parallel(wrapped)
}
