# Jenkins Shared Library: CI Pipeline Metrics (No Plugin)

This repository provides a **Jenkins Shared Library** to emit **pipeline execution metrics** (stage-level) to a remote HTTP endpoint.

Designed for:
- Jenkins controller on VM
- Agents running in containers
- Many **parallel** and **nested parallel** stages
- Rich metadata needs for downstream analysis/visualization
- **No Jenkins plugin** development

## Key properties

- Emits **stage_end** events only (no stage_start), to reduce noise.
- Handles **parallel** and **nested parallel** by propagating context fields.
- Provides unique IDs so downstream systems can rebuild the execution tree:
  - `stage_id` (UUID)
  - `parent_stage_id` (UUID)
- Adds parallel grouping for better visualization:
  - `parallel_group` (UUID per parallel group)
  - `parallel_branch` (branch name)
- Best-effort delivery: HTTP failures do **not** fail the build.

## Configuration

Set these environment variables in your pipeline:

- `METRIC_ENDPOINT_URL` (required): HTTP endpoint URL to POST JSON events to
- `METRIC_CURL_TIMEOUT_SEC` (optional, default `3`): curl timeout
- `METRIC_EMIT` (optional, default `true`): set `false` to disable emission

Agent image requirements:
- `curl`
- `base64`

## Event schema

### stage_end event

The library emits JSON like:

- `event_type`: `stage_end`
- `ts`: ISO-8601 UTC timestamp
- `job_name`, `build_number`, `build_url`
- `stage_name`
- `stage_id`, `parent_stage_id`
- `parallel_group`, `parallel_branch`
- `status`: `SUCCESS` / `FAILURE` / `ABORTED` / `UNSTABLE` / `UNKNOWN`
- `duration_ms`
- Optional: `error_class`, `error_message`

Downstream apps can rebuild stage trees and parallel swimlanes using `stage_id` + `parent_stage_id` + parallel fields.

## Usage

### 1) Configure shared library in Jenkins

In Jenkins:
- **Manage Jenkins → System → Global Pipeline Libraries**
- Add this repo as a library, e.g. name: `ai-system-design-ci-metrics`

### 2) Use in Jenkinsfile

```groovy
@Library('ai-system-design-ci-metrics') _

pipeline {
  agent any
  environment {
    METRIC_ENDPOINT_URL = 'https://metrics.example.com/events'
  }

  stages {
    stage('Build') {
      steps {
        script {
          metricStage('Build') {
            sh 'make build'
          }
        }
      }
    }

    stage('Test') {
      steps {
        script {
          metricStage('Test') {
            metricParallel([
              'unit': {
                metricStage('Unit') { sh 'make test-unit' }
              },
              'integration': {
                metricStage('Integration') { sh 'make test-integration' }
              }
            ])
          }
        }
      }
    }
  }
}
```

## Files

- `vars/metricEvent.groovy`: generic HTTP event emitter
- `vars/metricStage.groovy`: stage wrapper emitting stage_end
- `vars/metricParallel.groovy`: parallel wrapper setting parallel context
- `examples/Jenkinsfile`: nested parallel demo

## Notes / best practices

- Do not put secrets into emitted metadata.
- Keep `error_message` short to avoid huge payloads.
- If you need auth headers, extend curl in `metricEvent.groovy`.

---

License: MIT (adjust as needed)
