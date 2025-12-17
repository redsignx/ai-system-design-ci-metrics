# ai-system-design-ci-metrics

A collection of tools for collecting and emitting CI/CD metrics from Jenkins pipelines.

## Components

This repository contains two independent approaches for collecting pipeline metrics:

### 1. Jenkins Plugin (Recommended)

**Location**: [`jenkins-plugin/`](jenkins-plugin/)

A Jenkins controller-side plugin that automatically captures Pipeline stage lifecycle events and sends them to a remote endpoint via HTTP.

**Features:**
- ✅ Zero Jenkinsfile changes required
- ✅ Automatic stage tracking for all Pipeline jobs
- ✅ Multibranch Pipeline support with branch/PR metadata
- ✅ Handles parallel and nested stages automatically
- ✅ Asynchronous delivery with retry logic
- ✅ Secure token storage

**Quick Start:**
```bash
cd jenkins-plugin
mvn clean package
# Install target/pipeline-metrics.hpi via Jenkins Plugin Manager
```

See [jenkins-plugin/README.md](jenkins-plugin/README.md) for complete documentation.

### 2. Jenkins Shared Library (Legacy/Optional)

**Location**: `vars/`

Jenkins shared library helpers for emitting CI metrics from within Jenkinsfiles.

**Note**: The shared library is now **optional** and maintained for backward compatibility. New projects should use the Jenkins plugin instead.

## Comparison: Plugin vs Shared Library

| Feature | Plugin | Shared Library |
|---------|--------|----------------|
| Jenkinsfile changes required | ❌ No | ✅ Yes |
| Works with existing pipelines | ✅ Yes | ❌ No |
| Runs on | Controller | Agent |
| Multibranch metadata | ✅ Automatic | ⚠️ Manual |
| Parallel stage handling | ✅ Automatic | ⚠️ Manual |
| Retry logic | ✅ Built-in | ❌ Basic |
| Secure token storage | ✅ Jenkins Secret | ❌ Env var |

**Recommendation**: Use the Jenkins plugin for all new projects. The shared library is maintained for backward compatibility only.

---

## Migration Guide: Shared Library → Plugin

If you're currently using the shared library (`metricStage`, `metricEvent`), migrating to the plugin is straightforward:

### Step 1: Install the Plugin
```bash
cd jenkins-plugin
mvn clean package
# Install target/pipeline-metrics.hpi via Jenkins → Manage Jenkins → Manage Plugins → Advanced → Upload Plugin
```

### Step 2: Configure the Plugin
1. Go to Jenkins → Manage Jenkins → Configure System
2. Find "Pipeline Metrics Configuration"
3. Set your endpoint URL and optional bearer token
4. Click "Save"

### Step 3: Update Your Jenkinsfiles
You have two options:

**Option A: Remove the shared library entirely**
```groovy
// Before (with shared library)
@Library('ai-system-design-ci-metrics') _

metricStage('Build') {
  sh 'make build'
}

// After (with plugin - much simpler!)
stage('Build') {
  steps {
    sh 'make build'
  }
}
```

**Option B: Keep both (during transition)**
The plugin and shared library can coexist. They will emit separate events, which may be useful during migration for validation.

### Benefits of Migration
- ✅ No Jenkinsfile changes for new pipelines
- ✅ Automatic stage tracking in all existing pipelines
- ✅ Better Multibranch support
- ✅ Improved retry logic and error handling
- ✅ Secure token storage

---

## Shared Library Documentation

### Key changes / current behavior

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
