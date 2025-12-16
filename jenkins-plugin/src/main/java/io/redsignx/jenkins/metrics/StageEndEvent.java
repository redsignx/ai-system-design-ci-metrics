package io.redsignx.jenkins.metrics;

import com.google.gson.annotations.SerializedName;

import javax.annotation.CheckForNull;

/**
 * Event emitted when a pipeline stage ends.
 */
public class StageEndEvent extends MetricEvent {
    
    @SerializedName("status")
    private final String status;
    
    @SerializedName("result")
    @CheckForNull
    private final String result;
    
    @SerializedName("duration_ms")
    private final long durationMs;
    
    @SerializedName("error_message")
    @CheckForNull
    private final String errorMessage;
    
    public StageEndEvent(String stageId, String stageName, 
                        String jobFullName, int buildNumber, String buildUrl,
                        String branchName, String changeId, String changeTarget,
                        String nodeId, long timestamp, String status, String result,
                        long durationMs, String errorMessage) {
        super("stage_end", stageId, stageName, jobFullName, buildNumber, buildUrl,
              branchName, changeId, changeTarget, nodeId, timestamp);
        this.status = status;
        this.result = result;
        this.durationMs = durationMs;
        this.errorMessage = errorMessage;
    }
    
    public String getStatus() {
        return status;
    }
    
    @CheckForNull
    public String getResult() {
        return result;
    }
    
    public long getDurationMs() {
        return durationMs;
    }
    
    @CheckForNull
    public String getErrorMessage() {
        return errorMessage;
    }
}
