# Contributing to Pipeline Metrics Plugin

## Development Setup

### Prerequisites
- Java 11 or later
- Maven 3.6 or later
- Jenkins 2.361.4 or later (for testing)

### Building the Plugin

```bash
cd jenkins-plugin
mvn clean package
```

The built plugin will be available at `target/pipeline-metrics.hpi`.

### Running Tests

```bash
mvn test
```

### Running Jenkins Locally with the Plugin

```bash
mvn hpi:run
```

This starts a Jenkins instance at http://localhost:8080/jenkins with the plugin pre-installed.

## Project Structure

```
jenkins-plugin/
├── src/
│   ├── main/
│   │   ├── java/io/redsignx/jenkins/metrics/
│   │   │   ├── PipelineMetricsConfiguration.java  # Global config
│   │   │   ├── PipelineMetricsListener.java       # FlowGraph listener
│   │   │   ├── MetricDeliveryService.java         # HTTP delivery & retry
│   │   │   ├── MetricEvent.java                   # Base event class
│   │   │   ├── StageStartEvent.java               # Stage start event
│   │   │   └── StageEndEvent.java                 # Stage end event
│   │   └── resources/
│   │       ├── index.jelly                        # Plugin description
│   │       └── io/redsignx/jenkins/metrics/PipelineMetricsConfiguration/
│   │           ├── config.jelly                   # Config UI
│   │           └── help/*.html                    # Field help text
│   └── test/
│       └── java/io/redsignx/jenkins/metrics/
│           ├── MetricEventTest.java               # Payload tests
│           ├── PipelineMetricsConfigurationTest.java
│           └── MetricDeliveryServiceTest.java
├── pom.xml                                        # Maven build config
├── event-schema.json                              # JSON schema
└── README.md                                      # Plugin documentation
```

## Key Components

### PipelineMetricsListener
- Extends `FlowExecutionListener` to receive notifications about Pipeline executions
- Implements `GraphListener` to track FlowNode additions
- Detects stage start nodes (`StepStartNode` with function name "stage")
- Detects stage end nodes (`StepEndNode` whose start node is a stage)
- Extracts stage name, build context, branch/PR information
- Queues events for asynchronous delivery

### MetricDeliveryService
- Singleton service managing event queue and HTTP delivery
- Non-blocking queue with configurable size
- Worker threads for processing queue
- Retry logic with exponential backoff
- Never fails pipeline execution

### Event Classes
- `MetricEvent`: Base class with common fields
- `StageStartEvent`: Emitted when stage begins
- `StageEndEvent`: Emitted when stage completes (includes duration, status, error)

## Adding New Features

### Adding a New Event Field

1. Add the field to the appropriate event class:
```java
@SerializedName("new_field")
private final String newField;
```

2. Update the constructor to accept the new field
3. Update `PipelineMetricsListener` to extract and pass the field
4. Update tests in `MetricEventTest`
5. Update documentation in `README.md` and `event-schema.json`

### Adding a New Event Type

1. Create a new event class extending `MetricEvent`:
```java
public class NewEvent extends MetricEvent {
    // Add event-specific fields
}
```

2. Update `PipelineMetricsListener` to detect and emit the new event
3. Add tests for the new event
4. Update documentation

## Testing

### Unit Tests
Unit tests verify:
- Event payload structure and JSON serialization
- Configuration validation
- Queue behavior

Run with: `mvn test`

### Integration Tests
For integration testing with real Jenkins:
1. Run Jenkins locally: `mvn hpi:run`
2. Create a test pipeline job
3. Configure the plugin with a test endpoint
4. Run the pipeline and verify events are sent

### Manual Testing Checklist
- [ ] Plugin installs without errors
- [ ] Configuration page appears under Manage Jenkins → Configure System
- [ ] URL validation works correctly
- [ ] Events are sent for simple pipeline with one stage
- [ ] Events are sent for pipeline with parallel stages
- [ ] Events include correct branch name for Multibranch pipeline
- [ ] Events include PR information for pull requests
- [ ] Retry logic works when endpoint is temporarily down
- [ ] Plugin logs errors appropriately
- [ ] Pipeline builds succeed even when endpoint is unavailable

## Code Style

- Follow standard Java conventions
- Use meaningful variable and method names
- Add JavaDoc comments for public methods
- Keep methods focused and concise
- Handle exceptions gracefully (never fail the build)

## Pull Request Process

1. Fork the repository
2. Create a feature branch
3. Make your changes with appropriate tests
4. Update documentation as needed
5. Test thoroughly
6. Submit a pull request with a clear description

## Debugging

### Enable Debug Logging

1. Go to Jenkins → Manage Jenkins → System Log
2. Add a new logger:
   - Name: `io.redsignx.jenkins.metrics`
   - Level: `FINE` (or `FINEST` for more detail)
3. Check logs at Jenkins → Manage Jenkins → System Log

### Common Issues

**Events not being sent:**
- Check endpoint URL is configured
- Verify endpoint is reachable from Jenkins controller
- Check Jenkins logs for error messages
- Enable debug logging to see event queue activity

**Stage names incorrect:**
- Check `LabelAction` is being read correctly
- Verify FlowNode structure in Jenkins logs

**Memory issues:**
- Reduce max queue size
- Check for event queue backlog in logs
- Verify endpoint is processing events quickly

## License

MIT License - See repository root for details.
