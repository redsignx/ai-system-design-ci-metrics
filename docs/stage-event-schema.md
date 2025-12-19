# Stage event schema

This document defines the **stage event** payload emitted by the Jenkins controller-side plugin.

- Two primary event types:
  - **`stage_start`**: emitted when a stage begins execution.
  - **`stage_end`**: emitted when a stage completes (success/failure/aborted/unstable/skipped).
- All events are **versioned** via `event_version`.
- Events are designed for **at-least-once delivery** with consumer-side **idempotent upserts** using `event_id`.

## Naming conventions

- JSON keys use **snake_case**.
- Common fields are grouped into top-level objects:
  - `event`, `pipeline`, `scm`, `build`, `stage`, `agent`, `parallel`, `matrix`, `ci`, `metrics`, `tags`.
- Stage-type-specific fields appear under `stage.custom`.
- Prefer enums over free-form strings where possible.

## Event envelope (common fields)

### Required fields (all events)

| Field | Type | Notes |
|------|------|------|
| `event_type` | string | `stage_start` or `stage_end` |
| `event_version` | integer | Schema version (e.g., `1`) |
| `event_id` | string | Unique per event occurrence; use for idempotency |
| `timestamp` | string (RFC3339) | Emission timestamp |
| `pipeline_run_id` | string | Stable ID for a single pipeline run |
| `job_full_name` | string | Jenkins full name (folder path + job) |
| `build.number` | integer | Jenkins build number |
| `stage_instance_id` | string | Unique stage execution instance ID |
| `stage.type` | string | Canonical stage type (see list below) |
| `stage.name` | string | Actual stage name from Jenkins (human-facing) |

### Recommended fields

| Field | Type | Notes |
|------|------|------|
| `ci.system` | string | e.g., `jenkins` |
| `ci.controller_url` | string | Optional; may be redacted |
| `pipeline.url` | string | Build URL |
| `pipeline.attempt` | integer | Run retry attempt (if supported) |
| `scm.repo` | string | `org/repo` slug |
| `scm.branch` | string | Branch name |
| `scm.revision` | string | Commit SHA |
| `scm.change_id` | string/int | PR number |
| `scm.change_target` | string | PR target branch |
| `agent.name` | string | Node/agent label/name |
| `agent.executor` | integer | Executor number |
| `parallel.branch_name` | string | If inside `parallel {}` |
| `matrix.cell` | object | If inside Declarative matrix |
| `tags` | object | Arbitrary string tags (team, service, etc.) |

### Stage timing fields

For `stage_start`:

- `stage.start_time` should be set.

For `stage_end`:

- `stage.end_time` must be set.
- `duration_ms` should be set when possible.

Fields:

| Field | Type | Notes |
|------|------|------|
| `stage.start_time` | string (RFC3339) | Stage start time |
| `stage.end_time` | string (RFC3339) | Stage end time |
| `duration_ms` | integer | `end - start` |

### Status fields (stage_end)

Only present on `stage_end`.

| Field | Type | Notes |
|------|------|------|
| `status` | string | `success`, `failure`, `unstable`, `aborted`, `skipped` |
| `error` | object | Present when `status != success` |

`error` object:

| Field | Type | Notes |
|------|------|------|
| `error.type` | string | e.g., `exception`, `timeout`, `test_failures`, `infrastructure` |
| `error.message` | string | Redacted; avoid secrets |
| `error.stacktrace` | string | Optional; usually omitted |

## Canonical stage types

- `checkout`
- `build`
- `unittest`
- `uitest`
- `sonarqube_scan`
- `jacoco_report`
- `upload`
- `custom` (fallback)

## Idempotency and event IDs

### Principles

- Delivery is **at-least-once**; duplicates are expected.
- Consumers should upsert/dedupe by `event_id`.
- `event_id` must be deterministic.

### Recommended IDs

- `pipeline_run_id`: `${job_full_name}:${build.number}:${run_uuid}`
- `stage_instance_id`: `${pipeline_run_id}:${stage.path}:${parallel.branch_name}:${matrix.hash}:${stage.attempt}`
- `event_id`: `${stage_instance_id}:${event_type}:${event_version}`

`stage.path` refers to a stable representation of nested stages (e.g., `Build/Test/Unit`).

## Recommended indexing keys

For fast search, aggregations, and dashboards, index these keys (or map them as keyword fields):

- `event_type`, `event_version`
- `event_id`, `stage_instance_id`, `pipeline_run_id`
- `job_full_name`, `build.number`
- `scm.repo`, `scm.branch`, `scm.change_id`
- `stage.type`, `stage.name`, `parallel.branch_name`
- `status`, `duration_ms`

## Stage-type-specific fields (`stage.custom`)

All stage-type-specific fields must live under `stage.custom`.

- Use **snake_case**.
- Prefer stable names across tooling (e.g., `sonarqube_*`, `jacoco_*`).

Below are the recommended custom fields per canonical stage type.

### 1) checkout

`stage.type = "checkout"`

Custom fields:

| Field | Type | Notes |
|------|------|------|
| `stage.custom.scm_provider` | string | e.g., `github`, `gitlab` |
| `stage.custom.clone_depth` | integer | 0/absent means full clone |
| `stage.custom.submodules` | boolean | Whether submodules were updated |
| `stage.custom.checkout_strategy` | string | e.g., `clean`, `incremental` |
| `stage.custom.repo_url_hash` | string | Hash of repo URL if URL is sensitive |

#### Example (stage_start)

```json
{
  "event_type": "stage_start",
  "event_version": 1,
  "event_id": "job/a:12:uuid:Build/Checkout::stage_start:1",
  "timestamp": "2025-12-19T08:05:38Z",
  "pipeline_run_id": "job/a:12:uuid",
  "job_full_name": "folder/job-a",
  "build": { "number": 12 },
  "scm": {
    "repo": "org/repo",
    "branch": "main",
    "revision": "a1b2c3d4"
  },
  "stage": {
    "type": "checkout",
    "name": "Checkout",
    "path": "Build/Checkout",
    "start_time": "2025-12-19T08:05:10Z",
    "custom": {
      "scm_provider": "github",
      "clone_depth": 1,
      "submodules": false,
      "checkout_strategy": "clean",
      "repo_url_hash": "sha256:..."
    }
  }
}
```

#### Example (stage_end)

```json
{
  "event_type": "stage_end",
  "event_version": 1,
  "event_id": "job/a:12:uuid:Build/Checkout::stage_end:1",
  "timestamp": "2025-12-19T08:05:18Z",
  "pipeline_run_id": "job/a:12:uuid",
  "job_full_name": "folder/job-a",
  "build": { "number": 12 },
  "stage_instance_id": "job/a:12:uuid:Build/Checkout::0",
  "stage": {
    "type": "checkout",
    "name": "Checkout",
    "path": "Build/Checkout",
    "start_time": "2025-12-19T08:05:10Z",
    "end_time": "2025-12-19T08:05:18Z"
  },
  "duration_ms": 8000,
  "status": "success"
}
```

### 2) build

`stage.type = "build"`

Custom fields:

| Field | Type | Notes |
|------|------|------|
| `stage.custom.build_tool` | string | `maven`, `gradle`, `npm`, `bazel`, `make`, ... |
| `stage.custom.build_target` | string | Optional (e.g., `:app`, `all`) |
| `stage.custom.cache_hit` | boolean | Best-effort |
| `stage.custom.artifact_count` | integer | Number of produced artifacts |

#### Example

```json
{
  "event_type": "stage_end",
  "event_version": 1,
  "event_id": "job/a:12:uuid:Build/Compile::stage_end:1",
  "timestamp": "2025-12-19T08:12:00Z",
  "pipeline_run_id": "job/a:12:uuid",
  "job_full_name": "folder/job-a",
  "build": { "number": 12 },
  "stage_instance_id": "job/a:12:uuid:Build/Compile::0",
  "stage": {
    "type": "build",
    "name": "Compile",
    "path": "Build/Compile",
    "start_time": "2025-12-19T08:05:20Z",
    "end_time": "2025-12-19T08:12:00Z",
    "custom": {
      "build_tool": "maven",
      "build_target": "package",
      "cache_hit": false,
      "artifact_count": 3
    }
  },
  "duration_ms": 400000,
  "status": "success"
}
```

### 3) unittest

`stage.type = "unittest"`

Custom fields:

| Field | Type | Notes |
|------|------|------|
| `stage.custom.framework` | string | `junit`, `pytest`, `jest`, ... |
| `stage.custom.tests_total` | integer | Total tests |
| `stage.custom.tests_failed` | integer | Failed tests |
| `stage.custom.tests_skipped` | integer | Skipped tests |
| `stage.custom.report_paths` | array[string] | Optional paths or logical identifiers |

#### Example

```json
{
  "event_type": "stage_end",
  "event_version": 1,
  "event_id": "job/a:12:uuid:Test/Unit::stage_end:1",
  "timestamp": "2025-12-19T08:20:10Z",
  "pipeline_run_id": "job/a:12:uuid",
  "job_full_name": "folder/job-a",
  "build": { "number": 12 },
  "stage_instance_id": "job/a:12:uuid:Test/Unit::0",
  "stage": {
    "type": "unittest",
    "name": "Unit Tests",
    "path": "Test/Unit",
    "start_time": "2025-12-19T08:12:10Z",
    "end_time": "2025-12-19T08:20:10Z",
    "custom": {
      "framework": "junit",
      "tests_total": 540,
      "tests_failed": 2,
      "tests_skipped": 4,
      "report_paths": ["**/surefire-reports/*.xml"]
    }
  },
  "duration_ms": 480000,
  "status": "unstable",
  "error": {
    "type": "test_failures",
    "message": "2 unit tests failed"
  }
}
```

### 4) uitest

`stage.type = "uitest"`

Custom fields:

| Field | Type | Notes |
|------|------|------|
| `stage.custom.framework` | string | `cypress`, `playwright`, `selenium`, ... |
| `stage.custom.browser` | string | e.g., `chrome`, `firefox` |
| `stage.custom.tests_total` | integer | Total |
| `stage.custom.tests_failed` | integer | Failed |
| `stage.custom.video_artifacts` | integer | Count if recorded |

#### Example

```json
{
  "event_type": "stage_end",
  "event_version": 1,
  "event_id": "job/a:12:uuid:Test/UI::stage_end:1",
  "timestamp": "2025-12-19T08:40:00Z",
  "pipeline_run_id": "job/a:12:uuid",
  "job_full_name": "folder/job-a",
  "build": { "number": 12 },
  "stage_instance_id": "job/a:12:uuid:Test/UI::0",
  "stage": {
    "type": "uitest",
    "name": "UI Tests",
    "path": "Test/UI",
    "start_time": "2025-12-19T08:20:20Z",
    "end_time": "2025-12-19T08:40:00Z",
    "custom": {
      "framework": "playwright",
      "browser": "chrome",
      "tests_total": 120,
      "tests_failed": 0,
      "video_artifacts": 12
    }
  },
  "duration_ms": 1180000,
  "status": "success"
}
```

### 5) sonarqube_scan

`stage.type = "sonarqube_scan"`

Custom fields:

| Field | Type | Notes |
|------|------|------|
| `stage.custom.sonarqube_server` | string | Logical name, not full URL |
| `stage.custom.project_key` | string | Sonar project key |
| `stage.custom.quality_gate` | string | `passed`, `failed`, `unknown` |
| `stage.custom.analysis_id` | string | Sonar analysis/task id |

#### Example

```json
{
  "event_type": "stage_end",
  "event_version": 1,
  "event_id": "job/a:12:uuid:Scan/SonarQube::stage_end:1",
  "timestamp": "2025-12-19T08:48:30Z",
  "pipeline_run_id": "job/a:12:uuid",
  "job_full_name": "folder/job-a",
  "build": { "number": 12 },
  "stage_instance_id": "job/a:12:uuid:Scan/SonarQube::0",
  "stage": {
    "type": "sonarqube_scan",
    "name": "SonarQube Scan",
    "path": "Scan/SonarQube",
    "start_time": "2025-12-19T08:40:10Z",
    "end_time": "2025-12-19T08:48:30Z",
    "custom": {
      "sonarqube_server": "sonar-prod",
      "project_key": "org-repo",
      "quality_gate": "passed",
      "analysis_id": "AYx..."
    }
  },
  "duration_ms": 500000,
  "status": "success"
}
```

### 6) jacoco_report

`stage.type = "jacoco_report"`

Custom fields:

| Field | Type | Notes |
|------|------|------|
| `stage.custom.coverage_line_pct` | number | 0..100 |
| `stage.custom.coverage_branch_pct` | number | 0..100 |
| `stage.custom.report_format` | string | e.g., `xml`, `html` |
| `stage.custom.report_paths` | array[string] | Glob/path identifiers |

#### Example

```json
{
  "event_type": "stage_end",
  "event_version": 1,
  "event_id": "job/a:12:uuid:Report/JaCoCo::stage_end:1",
  "timestamp": "2025-12-19T08:53:10Z",
  "pipeline_run_id": "job/a:12:uuid",
  "job_full_name": "folder/job-a",
  "build": { "number": 12 },
  "stage_instance_id": "job/a:12:uuid:Report/JaCoCo::0",
  "stage": {
    "type": "jacoco_report",
    "name": "JaCoCo Coverage",
    "path": "Report/JaCoCo",
    "start_time": "2025-12-19T08:48:40Z",
    "end_time": "2025-12-19T08:53:10Z",
    "custom": {
      "coverage_line_pct": 83.2,
      "coverage_branch_pct": 71.5,
      "report_format": "xml",
      "report_paths": ["target/site/jacoco/jacoco.xml"]
    }
  },
  "duration_ms": 270000,
  "status": "success"
}
```

### 7) upload

`stage.type = "upload"`

Custom fields:

| Field | Type | Notes |
|------|------|------|
| `stage.custom.destination_type` | string | `artifact_repo`, `container_registry`, `s3`, `gcs`, ... |
| `stage.custom.destination_name` | string | Logical name, not URL |
| `stage.custom.bytes_uploaded` | integer | Total bytes |
| `stage.custom.files_uploaded` | integer | Count |
| `stage.custom.checksum` | string | Optional overall checksum |

#### Example

```json
{
  "event_type": "stage_end",
  "event_version": 1,
  "event_id": "job/a:12:uuid:Publish/Upload::stage_end:1",
  "timestamp": "2025-12-19T08:58:00Z",
  "pipeline_run_id": "job/a:12:uuid",
  "job_full_name": "folder/job-a",
  "build": { "number": 12 },
  "stage_instance_id": "job/a:12:uuid:Publish/Upload::0",
  "stage": {
    "type": "upload",
    "name": "Upload Artifacts",
    "path": "Publish/Upload",
    "start_time": "2025-12-19T08:53:20Z",
    "end_time": "2025-12-19T08:58:00Z",
    "custom": {
      "destination_type": "artifact_repo",
      "destination_name": "nexus-prod",
      "bytes_uploaded": 73400320,
      "files_uploaded": 6,
      "checksum": "sha256:..."
    }
  },
  "duration_ms": 280000,
  "status": "success"
}
```

## Notes for implementers

- Keep payloads small; avoid logs.
- Ensure any URLs can be redacted or hashed.
- Emit partial data when some fields are unavailable (best-effort), but keep required fields.
- Consumers must tolerate missing optional fields and unknown new fields.
