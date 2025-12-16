#!/usr/bin/env groovy

/**
 * metricParallel (legacy/optional)
 *
 * This step is kept for backward compatibility.
 *
 * The library no longer tracks parent/stack or parallel context. Jenkins native
 * `parallel(...)` works without any special handling.
 *
 * Prefer:
 *   parallel a: { ... }, b: { ... }
 */
def call(Map branches) {
  parallel branches
}
