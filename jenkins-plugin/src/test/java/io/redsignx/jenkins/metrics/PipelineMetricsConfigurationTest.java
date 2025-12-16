package io.redsignx.jenkins.metrics;

import hudson.util.FormValidation;
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
        
        assertEquals(FormValidation.Kind.OK, config.doCheckEndpointUrl("http://localhost:8080/metrics").kind);
        assertEquals(FormValidation.Kind.OK, config.doCheckEndpointUrl("https://api.example.com/events").kind);
    }
    
    @Test
    public void testValidationRejectsInvalidUrl() {
        PipelineMetricsConfiguration config = new PipelineMetricsConfiguration();
        
        assertEquals(FormValidation.Kind.ERROR, config.doCheckEndpointUrl("not-a-valid-url").kind);
    }
    
    @Test
    public void testValidationAcceptsEmptyUrl() {
        PipelineMetricsConfiguration config = new PipelineMetricsConfiguration();
        
        assertEquals(FormValidation.Kind.OK, config.doCheckEndpointUrl("").kind);
        assertEquals(FormValidation.Kind.OK, config.doCheckEndpointUrl(null).kind);
    }
    
    @Test
    public void testQueueSizeValidation() {
        PipelineMetricsConfiguration config = new PipelineMetricsConfiguration();
        
        assertEquals(FormValidation.Kind.ERROR, config.doCheckMaxQueueSize(0).kind);
        assertEquals(FormValidation.Kind.OK, config.doCheckMaxQueueSize(100).kind);
        assertEquals(FormValidation.Kind.WARNING, config.doCheckMaxQueueSize(15000).kind);
    }
    
    @Test
    public void testRetryValidation() {
        PipelineMetricsConfiguration config = new PipelineMetricsConfiguration();
        
        assertEquals(FormValidation.Kind.ERROR, config.doCheckMaxRetries(-1).kind);
        assertEquals(FormValidation.Kind.OK, config.doCheckMaxRetries(3).kind);
        assertEquals(FormValidation.Kind.WARNING, config.doCheckMaxRetries(15).kind);
    }
}
