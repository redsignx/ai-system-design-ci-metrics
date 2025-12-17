package io.redsignx.jenkins.metrics;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for event payload structure.
 */
public class MetricEventTest {
    
    private final Gson gson = new Gson();
    
    @Test
    public void testStageStartEventPayload() {
        StageStartEvent event = new StageStartEvent(
            "stage-123",
            "Build",
            "test/job",
            42,
            "http://jenkins/job/test/42",
            "main",
            null,
            null,
            "node-456",
            1234567890000L
        );
        
        String json = gson.toJson(event);
        JsonObject obj = gson.fromJson(json, JsonObject.class);
        
        assertEquals("stage_start", obj.get("event_type").getAsString());
        assertEquals("1.0", obj.get("event_version").getAsString());
        assertEquals("stage-123", obj.get("stage_id").getAsString());
        assertEquals("Build", obj.get("stage_name").getAsString());
        assertEquals("test/job", obj.get("job_full_name").getAsString());
        assertEquals(42, obj.get("build_number").getAsInt());
        assertEquals("http://jenkins/job/test/42", obj.get("build_url").getAsString());
        assertEquals("main", obj.get("branch_name").getAsString());
        assertEquals("node-456", obj.get("node_id").getAsString());
        assertEquals(1234567890000L, obj.get("timestamp").getAsLong());
    }
    
    @Test
    public void testStageEndEventPayload() {
        StageEndEvent event = new StageEndEvent(
            "stage-123",
            "Build",
            "test/job",
            42,
            "http://jenkins/job/test/42",
            "main",
            "PR-123",
            "develop",
            "node-456",
            1234567890000L,
            "SUCCESS",
            "SUCCESS",
            5000L,
            null
        );
        
        String json = gson.toJson(event);
        JsonObject obj = gson.fromJson(json, JsonObject.class);
        
        assertEquals("stage_end", obj.get("event_type").getAsString());
        assertEquals("1.0", obj.get("event_version").getAsString());
        assertEquals("stage-123", obj.get("stage_id").getAsString());
        assertEquals("Build", obj.get("stage_name").getAsString());
        assertEquals("SUCCESS", obj.get("status").getAsString());
        assertEquals("SUCCESS", obj.get("result").getAsString());
        assertEquals(5000L, obj.get("duration_ms").getAsLong());
        assertEquals("PR-123", obj.get("change_id").getAsString());
        assertEquals("develop", obj.get("change_target").getAsString());
    }
    
    @Test
    public void testStageEndEventWithError() {
        StageEndEvent event = new StageEndEvent(
            "stage-123",
            "Build",
            "test/job",
            42,
            "http://jenkins/job/test/42",
            null,
            null,
            null,
            "node-456",
            1234567890000L,
            "FAILURE",
            "FAILURE",
            3000L,
            "Build failed: compilation error"
        );
        
        String json = gson.toJson(event);
        JsonObject obj = gson.fromJson(json, JsonObject.class);
        
        assertEquals("FAILURE", obj.get("status").getAsString());
        assertEquals("Build failed: compilation error", obj.get("error_message").getAsString());
    }
    
    @Test
    public void testEventVersionIsIncluded() {
        StageStartEvent event = new StageStartEvent(
            "stage-123", "Test", "job", 1, "url", null, null, null, "node", 0L
        );
        
        String json = gson.toJson(event);
        assertTrue(json.contains("\"event_version\":\"1.0\""));
    }
}
