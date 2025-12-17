package io.redsignx.jenkins.metrics;

import com.google.gson.annotations.SerializedName;

/**
 * Event emitted when a pipeline stage starts.
 */
public class StageStartEvent extends MetricEvent {
    
    public StageStartEvent(String stageId, String stageName, 
                          String jobFullName, int buildNumber, String buildUrl,
                          String branchName, String changeId, String changeTarget,
                          String nodeId, long timestamp) {
        super("stage_start", stageId, stageName, jobFullName, buildNumber, buildUrl,
              branchName, changeId, changeTarget, nodeId, timestamp);
    }
}
