package com.modeldoctor.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class InvestigationProvenanceDto {

    private String targetKey;
    private String runId;
    private List<String> sourceModules;
    private Map<String, Long> sourceResultIds;
    private List<String> rulesEvaluated;
    private Instant dataFreshness;
    private boolean isImmutableResult = true;

    public InvestigationProvenanceDto() {}

    public String getTargetKey() { return targetKey; }
    public void setTargetKey(String targetKey) { this.targetKey = targetKey; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public List<String> getSourceModules() { return sourceModules; }
    public void setSourceModules(List<String> sourceModules) { this.sourceModules = sourceModules; }

    public Map<String, Long> getSourceResultIds() { return sourceResultIds; }
    public void setSourceResultIds(Map<String, Long> sourceResultIds) { this.sourceResultIds = sourceResultIds; }

    public List<String> getRulesEvaluated() { return rulesEvaluated; }
    public void setRulesEvaluated(List<String> rulesEvaluated) { this.rulesEvaluated = rulesEvaluated; }

    public Instant getDataFreshness() { return dataFreshness; }
    public void setDataFreshness(Instant dataFreshness) { this.dataFreshness = dataFreshness; }

    public boolean isImmutableResult() { return isImmutableResult; }
    public void setImmutableResult(boolean immutableResult) { isImmutableResult = immutableResult; }
}
