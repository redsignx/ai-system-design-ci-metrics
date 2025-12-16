package io.redsignx.jenkins.metrics;

import com.google.gson.annotations.SerializedName;

import javax.annotation.CheckForNull;
import java.util.HashMap;
import java.util.Map;

/**
 * Base class for pipeline metric events.
 */
public abstract class MetricEvent {
    
    @SerializedName("event_type")
    private final String eventType;
    
    @SerializedName("event_version")
    private final String eventVersion = "1.0";
    
    @SerializedName("timestamp")
    private final long timestamp;
    
    @SerializedName("stage_id")
    private final String stageId;
    
    @SerializedName("stage_name")
    private final String stageName;
    
    @SerializedName("job_full_name")
    private final String jobFullName;
    
    @SerializedName("build_number")
    private final int buildNumber;
    
    @SerializedName("build_url")
    private final String buildUrl;
    
    @SerializedName("branch_name")
    @CheckForNull
    private final String branchName;
    
    @SerializedName("change_id")
    @CheckForNull
    private final String changeId;
    
    @SerializedName("change_target")
    @CheckForNull
    private final String changeTarget;
    
    @SerializedName("node_id")
    private final String nodeId;
    
    protected MetricEvent(String eventType, String stageId, String stageName, 
                         String jobFullName, int buildNumber, String buildUrl,
                         String branchName, String changeId, String changeTarget,
                         String nodeId, long timestamp) {
        this.eventType = eventType;
        this.stageId = stageId;
        this.stageName = stageName;
        this.jobFullName = jobFullName;
        this.buildNumber = buildNumber;
        this.buildUrl = buildUrl;
        this.branchName = branchName;
        this.changeId = changeId;
        this.changeTarget = changeTarget;
        this.nodeId = nodeId;
        this.timestamp = timestamp;
    }
    
    public String getEventType() {
        return eventType;
    }
    
    public String getEventVersion() {
        return eventVersion;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public String getStageId() {
        return stageId;
    }
    
    public String getStageName() {
        return stageName;
    }
    
    public String getJobFullName() {
        return jobFullName;
    }
    
    public int getBuildNumber() {
        return buildNumber;
    }
    
    public String getBuildUrl() {
        return buildUrl;
    }
    
    @CheckForNull
    public String getBranchName() {
        return branchName;
    }
    
    @CheckForNull
    public String getChangeId() {
        return changeId;
    }
    
    @CheckForNull
    public String getChangeTarget() {
        return changeTarget;
    }
    
    public String getNodeId() {
        return nodeId;
    }
}
