package io.redsignx.jenkins.metrics;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for PipelineMetricsConfiguration.
 */
public class PipelineMetricsConfigurationTest {
    
    @Test
    public void testDefaultValues() {
        PipelineMetricsConfiguration config = new PipelineMetricsConfiguration();
        
        assertEquals(10, config.getConnectionTimeoutSeconds());
        assertEquals(30, config.getReadTimeoutSeconds());
        assertEquals(1000, config.getMaxQueueSize());
        assertEquals(3, config.getMaxRetries());
        assertEquals(2, config.getInitialRetryDelaySeconds());
    }
    
    @Test
    public void testValidationAcceptsValidUrl() {
        PipelineMetricsConfiguration config = new PipelineMetricsConfiguration();
        
        assertEquals("OK", config.doCheckEndpointUrl("http://localhost:8080/metrics").kind.toString());
        assertEquals("OK", config.doCheckEndpointUrl("https://api.example.com/events").kind.toString());
    }
    
    @Test
    public void testValidationRejectsInvalidUrl() {
        PipelineMetricsConfiguration config = new PipelineMetricsConfiguration();
        
        assertEquals("ERROR", config.doCheckEndpointUrl("not-a-valid-url").kind.toString());
    }
    
    @Test
    public void testValidationAcceptsEmptyUrl() {
        PipelineMetricsConfiguration config = new PipelineMetricsConfiguration();
        
        assertEquals("OK", config.doCheckEndpointUrl("").kind.toString());
        assertEquals("OK", config.doCheckEndpointUrl(null).kind.toString());
    }
    
    @Test
    public void testQueueSizeValidation() {
        PipelineMetricsConfiguration config = new PipelineMetricsConfiguration();
        
        assertEquals("ERROR", config.doCheckMaxQueueSize(0).kind.toString());
        assertEquals("OK", config.doCheckMaxQueueSize(100).kind.toString());
        assertEquals("WARNING", config.doCheckMaxQueueSize(15000).kind.toString());
    }
    
    @Test
    public void testRetryValidation() {
        PipelineMetricsConfiguration config = new PipelineMetricsConfiguration();
        
        assertEquals("ERROR", config.doCheckMaxRetries(-1).kind.toString());
        assertEquals("OK", config.doCheckMaxRetries(3).kind.toString());
        assertEquals("WARNING", config.doCheckMaxRetries(15).kind.toString());
    }
}
