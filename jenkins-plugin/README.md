# Pipeline Metrics Jenkins Plugin

A Jenkins controller-side plugin that automatically collects Pipeline stage lifecycle events and sends them to a remote endpoint via HTTP.

## Features

- **Automatic Stage Tracking**: Captures stage start and end events from Pipeline/Workflow jobs
- **Multibranch Support**: Works seamlessly with Multibranch Pipeline projects, including branch and PR information
- **Parallel Stage Handling**: Correctly handles parallel and nested parallel stages without requiring Jenkinsfile changes
- **Asynchronous Delivery**: Non-blocking HTTP delivery that never fails your builds
- **Retry Logic**: Configurable retry with exponential backoff for transient failures
- **Secure Configuration**: Bearer token stored as Jenkins Secret

## Installation

1. Build the plugin:
   ```bash
   cd jenkins-plugin
   mvn clean package
   ```

2. Install the generated `.hpi` file:
   - Navigate to Jenkins → Manage Jenkins → Manage Plugins
   - Go to the "Advanced" tab
   - Under "Upload Plugin", choose the file `target/pipeline-metrics.hpi`
   - Click "Upload" and restart Jenkins

## Configuration

### Global Configuration

Navigate to **Manage Jenkins → Configure System** and find the "Pipeline Metrics Configuration" section.

Configure the following settings:

- **Endpoint URL** (required): The HTTP(S) endpoint to receive metrics (e.g., `https://metrics.example.com/api/events`)
- **Bearer Token** (optional): Authentication token for the endpoint, stored securely
- **Connection Timeout** (default: 10 seconds): Timeout for establishing HTTP connections
- **Read Timeout** (default: 30 seconds): Timeout for reading HTTP responses
- **Max Queue Size** (default: 1000): Maximum number of events to queue in memory
- **Max Retries** (default: 3): Maximum number of retry attempts for failed deliveries
- **Initial Retry Delay** (default: 2 seconds): Initial delay before first retry (doubles with each retry)

### Per-Pipeline Configuration

No per-pipeline configuration is needed! The plugin automatically monitors all Pipeline and Multibranch Pipeline jobs.

## Event Payload Schema

### Common Fields (All Events)

```json
{
  "event_type": "stage_start|stage_end",
  "event_version": "1.0",
  "timestamp": 1234567890000,
  "stage_id": "job/my-pipeline#42:node-123",
  "stage_name": "Build",
  "job_full_name": "folder/my-pipeline",
  "build_number": 42,
  "build_url": "job/folder/my-pipeline/42/",
  "branch_name": "main",
  "change_id": "PR-123",
  "change_target": "develop",
  "node_id": "node-123"
}
```

#### Field Descriptions

- `event_type`: Type of event (`stage_start` or `stage_end`)
- `event_version`: Schema version (currently `1.0`)
- `timestamp`: Unix timestamp in milliseconds when the event occurred
- `stage_id`: Unique identifier for this stage execution (format: `job_full_name#build_number:node_id`)
- `stage_name`: Display name of the stage
- `job_full_name`: Full name of the job including folder path
- `build_number`: Build number
- `build_url`: Relative URL to the build
- `branch_name`: Git branch name (for Multibranch pipelines, null otherwise)
- `change_id`: Pull request or change ID (e.g., "PR-123", null for regular branches)
- `change_target`: Target branch for pull requests (null otherwise)
- `node_id`: FlowNode ID from Jenkins Pipeline execution graph

### stage_start Event

Emitted when a stage begins execution.

```json
{
  "event_type": "stage_start",
  "event_version": "1.0",
  "timestamp": 1234567890000,
  "stage_id": "job/my-pipeline#42:node-123",
  "stage_name": "Build",
  "job_full_name": "folder/my-pipeline",
  "build_number": 42,
  "build_url": "job/folder/my-pipeline/42/",
  "branch_name": "main",
  "change_id": null,
  "change_target": null,
  "node_id": "node-123"
}
```

### stage_end Event

Emitted when a stage completes (successfully or with failure).

```json
{
  "event_type": "stage_end",
  "event_version": "1.0",
  "timestamp": 1234567895000,
  "stage_id": "job/my-pipeline#42:node-123",
  "stage_name": "Build",
  "job_full_name": "folder/my-pipeline",
  "build_number": 42,
  "build_url": "job/folder/my-pipeline/42/",
  "branch_name": "main",
  "change_id": null,
  "change_target": null,
  "node_id": "node-123",
  "status": "SUCCESS",
  "result": "SUCCESS",
  "duration_ms": 5000,
  "error_message": null
}
```

#### Additional Fields for stage_end

- `status`: Stage status (`SUCCESS` or `FAILURE`)
- `result`: Stage result (`SUCCESS` or `FAILURE`)
- `duration_ms`: Duration in milliseconds
- `error_message`: Error message if the stage failed (null on success)

### Example: Failed Stage

```json
{
  "event_type": "stage_end",
  "event_version": "1.0",
  "timestamp": 1234567893000,
  "stage_id": "job/my-pipeline#42:node-124",
  "stage_name": "Test",
  "job_full_name": "folder/my-pipeline",
  "build_number": 42,
  "build_url": "job/folder/my-pipeline/42/",
  "branch_name": "feature/new-feature",
  "change_id": "PR-456",
  "change_target": "main",
  "node_id": "node-124",
  "status": "FAILURE",
  "result": "FAILURE",
  "duration_ms": 3000,
  "error_message": "Tests failed with 3 failures"
}
```

## How It Works

### With Regular Pipeline Jobs

For standard Pipeline jobs, the plugin captures:
- Job full name
- Build number and URL
- Stage names and lifecycle events

### With Multibranch Pipeline Jobs

For Multibranch Pipeline jobs, the plugin additionally captures:
- Branch name (e.g., `main`, `develop`, `feature/xyz`)
- Change ID for pull requests (e.g., `PR-123`)
- Change target (base branch for pull requests)

The plugin extracts this information from Jenkins environment variables (`BRANCH_NAME`, `CHANGE_ID`, `CHANGE_TARGET`).

### Parallel Stage Handling

The plugin correctly handles parallel stages without any special Jenkinsfile syntax:

```groovy
pipeline {
  stages {
    stage('Parallel Tests') {
      parallel {
        stage('Unit Tests') {
          steps { sh 'make test-unit' }
        }
        stage('Integration Tests') {
          steps { sh 'make test-integration' }
        }
      }
    }
  }
}
```

Each parallel stage will emit its own `stage_start` and `stage_end` events with unique `stage_id` values.

## Error Handling and Reliability

- **Non-blocking**: Event delivery runs asynchronously and never blocks Pipeline execution
- **No build failures**: Failed metric delivery will never cause your builds to fail
- **Retry logic**: Transient HTTP errors trigger automatic retries with exponential backoff
- **Queue management**: Events are queued in memory; if the queue fills, old events are dropped
- **Logging**: All delivery attempts and failures are logged for debugging

## Troubleshooting

### Events Not Being Sent

1. Check that the endpoint URL is configured in the global configuration
2. Verify the endpoint is reachable from the Jenkins controller
3. Check Jenkins logs for error messages from the plugin

### Authentication Failures

1. Ensure the bearer token is correctly configured
2. Verify the endpoint accepts the token format
3. Check endpoint logs for authentication errors

### Events Being Dropped

1. Increase the max queue size if events are being dropped frequently
2. Check that the endpoint is responding quickly enough
3. Verify network connectivity is stable

### Viewing Plugin Logs

Enable debug logging for the plugin:
1. Navigate to **Manage Jenkins → System Log**
2. Add a new logger for `io.redsignx.jenkins.metrics` with level `FINE`

## Relationship to Shared Library

This plugin is **independent** of the existing Jenkins shared library in this repository (`vars/metricStage.groovy`, etc.). 

- The **shared library** requires Jenkinsfile changes and runs on agents
- The **plugin** works automatically without Jenkinsfile changes and runs on the controller

You can:
- Use the plugin alone (recommended for new projects)
- Use both together (they will emit separate events)
- Migrate from the shared library to the plugin over time

## Development

### Building

```bash
cd jenkins-plugin
mvn clean package
```

### Testing

```bash
mvn test
```

### Running in Development

```bash
mvn hpi:run
```

This starts a Jenkins instance at http://localhost:8080/jenkins with the plugin installed.

## License

MIT License - See repository root for details.
