package io.redsignx.jenkins.metrics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import hudson.util.Secret;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Asynchronous HTTP delivery service for metric events.
 * Implements retry logic with exponential backoff and queue management.
 */
public class MetricDeliveryService {
    
    private static final Logger LOGGER = Logger.getLogger(MetricDeliveryService.class.getName());
    private static MetricDeliveryService instance;
    
    private final BlockingQueue<MetricEvent> eventQueue;
    private final ExecutorService executorService;
    private final Gson gson;
    private volatile boolean running = false;
    
    private MetricDeliveryService() {
        PipelineMetricsConfiguration config = PipelineMetricsConfiguration.get();
        int maxQueueSize = config != null ? config.getMaxQueueSize() : 1000;
        this.eventQueue = new LinkedBlockingQueue<>(maxQueueSize);
        this.executorService = Executors.newFixedThreadPool(2);
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }
    
    public static synchronized MetricDeliveryService getInstance() {
        if (instance == null) {
            instance = new MetricDeliveryService();
            instance.start();
        }
        return instance;
    }
    
    public void start() {
        if (running) {
            return;
        }
        running = true;
        // Start worker thread to process queue
        executorService.submit(this::processQueue);
        LOGGER.info("MetricDeliveryService started");
    }
    
    public void shutdown() {
        running = false;
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.info("MetricDeliveryService shutdown");
    }
    
    /**
     * Queue an event for delivery. Non-blocking.
     * @param event The event to deliver
     * @return true if queued successfully, false if queue is full
     */
    public boolean queueEvent(MetricEvent event) {
        if (event == null) {
            return false;
        }
        
        PipelineMetricsConfiguration config = PipelineMetricsConfiguration.get();
        if (config == null || config.getEndpointUrl() == null || config.getEndpointUrl().trim().isEmpty()) {
            LOGGER.fine("Endpoint URL not configured, skipping event delivery");
            return false;
        }
        
        boolean queued = eventQueue.offer(event);
        if (!queued) {
            LOGGER.warning("Event queue is full, dropping event: " + event.getEventType());
        }
        return queued;
    }
    
    private void processQueue() {
        while (running) {
            try {
                MetricEvent event = eventQueue.poll(1, TimeUnit.SECONDS);
                if (event != null) {
                    // Submit delivery task to separate thread
                    executorService.submit(() -> deliverEventWithRetry(event));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    private void deliverEventWithRetry(MetricEvent event) {
        PipelineMetricsConfiguration config = PipelineMetricsConfiguration.get();
        if (config == null) {
            return;
        }
        
        int maxRetries = config.getMaxRetries();
        int retryDelaySeconds = config.getInitialRetryDelaySeconds();
        
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                deliverEvent(event, config);
                LOGGER.fine("Successfully delivered event: " + event.getEventType() + 
                           " for stage: " + event.getStageName());
                return; // Success
            } catch (IOException e) {
                if (attempt < maxRetries) {
                    LOGGER.log(Level.WARNING, "Failed to deliver event (attempt " + (attempt + 1) + 
                              "/" + (maxRetries + 1) + "), will retry: " + e.getMessage());
                    try {
                        Thread.sleep(retryDelaySeconds * 1000L);
                        // Exponential backoff
                        retryDelaySeconds *= 2;
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                } else {
                    LOGGER.log(Level.SEVERE, "Failed to deliver event after " + (maxRetries + 1) + 
                              " attempts, dropping event: " + event.getEventType(), e);
                }
            }
        }
    }
    
    private void deliverEvent(MetricEvent event, PipelineMetricsConfiguration config) throws IOException {
        String endpointUrl = config.getEndpointUrl();
        if (endpointUrl == null || endpointUrl.trim().isEmpty()) {
            return;
        }
        
        String json = gson.toJson(event);
        
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(config.getConnectionTimeoutSeconds() * 1000)
            .setSocketTimeout(config.getReadTimeoutSeconds() * 1000)
            .build();
        
        try (CloseableHttpClient httpClient = HttpClientBuilder.create()
                .setDefaultRequestConfig(requestConfig)
                .build()) {
            
            HttpPost post = new HttpPost(endpointUrl);
            post.setHeader("Content-Type", "application/json");
            
            Secret bearerToken = config.getBearerToken();
            if (bearerToken != null && bearerToken.getPlainText() != null && 
                !bearerToken.getPlainText().trim().isEmpty()) {
                post.setHeader("Authorization", "Bearer " + bearerToken.getPlainText());
            }
            
            post.setEntity(new StringEntity(json, "UTF-8"));
            
            HttpResponse response = httpClient.execute(post);
            int statusCode = response.getStatusLine().getStatusCode();
            
            // Consume response to release connection
            EntityUtils.consume(response.getEntity());
            
            if (statusCode < 200 || statusCode >= 300) {
                throw new IOException("HTTP request failed with status code: " + statusCode);
            }
        }
    }
}
