package io.redsignx.jenkins.metrics;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for MetricDeliveryService queue behavior.
 */
public class MetricDeliveryServiceTest {
    
    @Test
    public void testQueueEventWithNoConfiguration() {
        // When no configuration is set, events should be dropped gracefully
        StageStartEvent event = new StageStartEvent(
            "test-stage-id", "Test", "test/job", 1, "url", null, null, null, "node-1", 0L
        );
        
        MetricDeliveryService service = MetricDeliveryService.getInstance();
        // Should not throw exception, just return false
        boolean result = service.queueEvent(event);
        
        // Since no endpoint is configured, this should return false
        assertFalse(result);
    }
    
    @Test
    public void testQueueEventWithNullEvent() {
        MetricDeliveryService service = MetricDeliveryService.getInstance();
        boolean result = service.queueEvent(null);
        assertFalse(result);
    }
}
