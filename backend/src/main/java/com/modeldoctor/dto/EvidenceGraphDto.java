package com.modeldoctor.dto;

import java.util.List;

public class EvidenceGraphDto {

    private String runId;
    private List<EvidenceGraphNodeDto> nodes;
    private List<EvidenceGraphEdgeDto> edges;
    private int nodeCount;
    private int edgeCount;
    private int targetCount;
    private double density;
    private boolean isAssociativeOnly = true;
    private String causalityDisclaimer = "INVESTIGATION RELATIONSHIPS ARE ASSOCIATIVE. THE SYSTEM DOES NOT ESTABLISH CAUSALITY.";

    public EvidenceGraphDto() {}

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public List<EvidenceGraphNodeDto> getNodes() { return nodes; }
    public void setNodes(List<EvidenceGraphNodeDto> nodes) { this.nodes = nodes; }

    public List<EvidenceGraphEdgeDto> getEdges() { return edges; }
    public void setEdges(List<EvidenceGraphEdgeDto> edges) { this.edges = edges; }

    public int getNodeCount() { return nodeCount; }
    public void setNodeCount(int nodeCount) { this.nodeCount = nodeCount; }

    public int getEdgeCount() { return edgeCount; }
    public void setEdgeCount(int edgeCount) { this.edgeCount = edgeCount; }

    public int getTargetCount() { return targetCount; }
    public void setTargetCount(int targetCount) { this.targetCount = targetCount; }

    public double getDensity() { return density; }
    public void setDensity(double density) { this.density = density; }

    public boolean isAssociativeOnly() { return isAssociativeOnly; }
    public void setAssociativeOnly(boolean associativeOnly) { isAssociativeOnly = associativeOnly; }

    public String getCausalityDisclaimer() { return causalityDisclaimer; }
    public void setCausalityDisclaimer(String causalityDisclaimer) { this.causalityDisclaimer = causalityDisclaimer; }
}
