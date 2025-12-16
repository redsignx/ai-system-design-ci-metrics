package io.redsignx.jenkins.metrics;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.branch.MultiBranchProject;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionListener;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.StageStepExecution;

import javax.annotation.CheckForNull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Listens to Pipeline FlowGraph events and emits stage lifecycle metrics.
 */
@Extension
public class PipelineMetricsListener extends FlowExecutionListener {
    
    private static final Logger LOGGER = Logger.getLogger(PipelineMetricsListener.class.getName());
    
    // Track stage start times by node ID
    private final Map<String, Long> stageStartTimes = new ConcurrentHashMap<>();
    
    @Override
    public void onCreated(FlowExecution execution) {
        try {
            execution.addListener(new StageGraphListener(execution));
            LOGGER.fine("Added graph listener to flow execution");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to add graph listener", e);
        }
    }
    
    private class StageGraphListener implements GraphListener {
        
        private final FlowExecution execution;
        
        StageGraphListener(FlowExecution execution) {
            this.execution = execution;
        }
        
        @Override
        public void onNewHead(FlowNode node) {
            try {
                if (isStageNode(node)) {
                    handleStageNode(node);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error handling flow node", e);
            }
        }
        
        private boolean isStageNode(FlowNode node) {
            // Check if this is a stage node
            return node instanceof StepStartNode && 
                   "stage".equals(((StepStartNode) node).getDescriptor().getFunctionName());
        }
        
        private void handleStageNode(FlowNode node) {
            String nodeId = node.getId();
            String stageName = getStageName(node);
            
            if (stageName == null) {
                return;
            }
            
            // Check if stage is starting or ending
            if (node instanceof StepStartNode) {
                handleStageStart(node, nodeId, stageName);
            }
            
            // For stage end, we need to check the node's status
            if (node.isActive()) {
                // Stage is still running, record start time
                stageStartTimes.put(nodeId, System.currentTimeMillis());
            } else {
                // Stage has completed
                handleStageEnd(node, nodeId, stageName);
            }
        }
        
        private void handleStageStart(FlowNode node, String nodeId, String stageName) {
            try {
                long timestamp = System.currentTimeMillis();
                stageStartTimes.put(nodeId, timestamp);
                
                Run<?, ?> run = getRunFromExecution(execution);
                if (run == null) {
                    return;
                }
                
                BuildContext context = extractBuildContext(run);
                
                StageStartEvent event = new StageStartEvent(
                    generateStageId(run, nodeId),
                    stageName,
                    context.jobFullName,
                    context.buildNumber,
                    context.buildUrl,
                    context.branchName,
                    context.changeId,
                    context.changeTarget,
                    nodeId,
                    timestamp
                );
                
                MetricDeliveryService.getInstance().queueEvent(event);
                LOGGER.fine("Queued stage_start event for stage: " + stageName);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error handling stage start", e);
            }
        }
        
        private void handleStageEnd(FlowNode node, String nodeId, String stageName) {
            try {
                long endTime = System.currentTimeMillis();
                Long startTime = stageStartTimes.remove(nodeId);
                long durationMs = startTime != null ? endTime - startTime : 0;
                
                Run<?, ?> run = getRunFromExecution(execution);
                if (run == null) {
                    return;
                }
                
                BuildContext context = extractBuildContext(run);
                
                String status = "SUCCESS";
                String result = null;
                String errorMessage = null;
                
                if (node.getError() != null) {
                    status = "FAILURE";
                    errorMessage = node.getError().getMessage();
                    result = "FAILURE";
                } else {
                    result = "SUCCESS";
                }
                
                StageEndEvent event = new StageEndEvent(
                    generateStageId(run, nodeId),
                    stageName,
                    context.jobFullName,
                    context.buildNumber,
                    context.buildUrl,
                    context.branchName,
                    context.changeId,
                    context.changeTarget,
                    nodeId,
                    endTime,
                    status,
                    result,
                    durationMs,
                    errorMessage
                );
                
                MetricDeliveryService.getInstance().queueEvent(event);
                LOGGER.fine("Queued stage_end event for stage: " + stageName);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error handling stage end", e);
            }
        }
        
        @CheckForNull
        private String getStageName(FlowNode node) {
            String name = node.getDisplayName();
            if (name != null && !name.isEmpty()) {
                return name;
            }
            return null;
        }
        
        @CheckForNull
        private Run<?, ?> getRunFromExecution(FlowExecution execution) {
            try {
                return execution.getOwner().getExecutable();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to get run from execution", e);
                return null;
            }
        }
        
        private String generateStageId(Run<?, ?> run, String nodeId) {
            return run.getParent().getFullName() + "#" + run.getNumber() + ":" + nodeId;
        }
        
        private BuildContext extractBuildContext(Run<?, ?> run) {
            BuildContext context = new BuildContext();
            context.jobFullName = run.getParent().getFullName();
            context.buildNumber = run.getNumber();
            context.buildUrl = run.getUrl();
            
            // Extract branch and change information for Multibranch pipelines
            if (run instanceof WorkflowRun) {
                WorkflowRun workflowRun = (WorkflowRun) run;
                
                // Try to get branch name from environment
                try {
                    context.branchName = workflowRun.getEnvironment(TaskListener.NULL).get("BRANCH_NAME");
                    context.changeId = workflowRun.getEnvironment(TaskListener.NULL).get("CHANGE_ID");
                    context.changeTarget = workflowRun.getEnvironment(TaskListener.NULL).get("CHANGE_TARGET");
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "Could not extract branch/change info", e);
                }
            }
            
            return context;
        }
    }
    
    private static class BuildContext {
        String jobFullName;
        int buildNumber;
        String buildUrl;
        String branchName;
        String changeId;
        String changeTarget;
    }
}
