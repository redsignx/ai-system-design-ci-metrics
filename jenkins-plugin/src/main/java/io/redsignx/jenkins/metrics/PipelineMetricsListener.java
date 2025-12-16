package io.redsignx.jenkins.metrics;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionListener;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

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
    
    // Track stage start times and info by start node ID
    private final Map<String, StageStartInfo> stageStartInfoMap = new ConcurrentHashMap<>();
    
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
                if (isStageStartNode(node)) {
                    handleStageStart((StepStartNode) node);
                } else if (isStageEndNode(node)) {
                    handleStageEnd((StepEndNode) node);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error handling flow node", e);
            }
        }
        
        private boolean isStageStartNode(FlowNode node) {
            // Check if this is a stage start node
            return node instanceof StepStartNode && 
                   "stage".equals(((StepStartNode) node).getDescriptor().getFunctionName());
        }
        
        private boolean isStageEndNode(FlowNode node) {
            if (!(node instanceof StepEndNode)) {
                return false;
            }
            StepEndNode endNode = (StepEndNode) node;
            FlowNode startNode = endNode.getStartNode();
            return startNode instanceof StepStartNode && 
                   "stage".equals(((StepStartNode) startNode).getDescriptor().getFunctionName());
        }
        
        private void handleStageStart(StepStartNode startNode) {
            try {
                String nodeId = startNode.getId();
                String stageName = getStageName(startNode);
                
                if (stageName == null) {
                    return;
                }
                
                long timestamp = System.currentTimeMillis();
                
                Run<?, ?> run = getRunFromExecution(execution);
                if (run == null) {
                    return;
                }
                
                BuildContext context = extractBuildContext(run);
                
                // Store start info for later use in end event
                StageStartInfo startInfo = new StageStartInfo();
                startInfo.nodeId = nodeId;
                startInfo.stageName = stageName;
                startInfo.timestamp = timestamp;
                startInfo.context = context;
                stageStartInfoMap.put(nodeId, startInfo);
                
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
        
        private void handleStageEnd(StepEndNode endNode) {
            try {
                FlowNode startNode = endNode.getStartNode();
                String startNodeId = startNode.getId();
                
                StageStartInfo startInfo = stageStartInfoMap.remove(startNodeId);
                if (startInfo == null) {
                    LOGGER.fine("No start info found for stage end node: " + endNode.getId());
                    return;
                }
                
                long endTime = System.currentTimeMillis();
                long durationMs = endTime - startInfo.timestamp;
                
                Run<?, ?> run = getRunFromExecution(execution);
                if (run == null) {
                    return;
                }
                
                String status = "SUCCESS";
                String result = null;
                String errorMessage = null;
                
                if (endNode.getError() != null) {
                    status = "FAILURE";
                    errorMessage = endNode.getError().getMessage();
                    result = "FAILURE";
                } else {
                    result = "SUCCESS";
                }
                
                StageEndEvent event = new StageEndEvent(
                    generateStageId(run, startNodeId),
                    startInfo.stageName,
                    startInfo.context.jobFullName,
                    startInfo.context.buildNumber,
                    startInfo.context.buildUrl,
                    startInfo.context.branchName,
                    startInfo.context.changeId,
                    startInfo.context.changeTarget,
                    startNodeId,
                    endTime,
                    status,
                    result,
                    durationMs,
                    errorMessage
                );
                
                MetricDeliveryService.getInstance().queueEvent(event);
                LOGGER.fine("Queued stage_end event for stage: " + startInfo.stageName);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error handling stage end", e);
            }
        }
        
        @CheckForNull
        private String getStageName(FlowNode node) {
            // Try to get stage name from LabelAction
            LabelAction labelAction = node.getAction(LabelAction.class);
            if (labelAction != null && labelAction.getDisplayName() != null) {
                return labelAction.getDisplayName();
            }
            
            // Fallback to node display name
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
    
    private static class StageStartInfo {
        String nodeId;
        String stageName;
        long timestamp;
        BuildContext context;
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
