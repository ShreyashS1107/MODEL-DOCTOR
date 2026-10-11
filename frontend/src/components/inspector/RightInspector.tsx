"use client";

import React from "react";
import { FeatureNode, ModelDiagnosticSummary } from "@/types/diagnostics";

interface RightInspectorProps {
  summary: ModelDiagnosticSummary;
  selectedFeature: FeatureNode | null;
  onRunProbe?: (feature: FeatureNode) => void;
  onExportFeatureEvidence?: (feature: FeatureNode) => void;
}

export const RightInspector: React.FC<RightInspectorProps> = ({
  summary,
  selectedFeature,
  onRunProbe,
  onExportFeatureEvidence,
}) => {
  return (
    <aside className="w-80 bg-[#2A2432] border-l border-[#443849] flex flex-col shrink-0 overflow-y-auto select-none font-mono text-xs">
      {/* 1. Model Health Summary Panel */}
      <div className="p-3 border-b border-[#443849]">
        <div className="text-[10px] text-[#8E8295] uppercase tracking-wider mb-2">
          MODEL HEALTH INDEX
        </div>

        <div className="flex items-baseline justify-between mb-2">
          <div className="flex items-baseline gap-1">
            <span className="text-3xl font-bold font-sans text-[#F4EFF6] tabular-nums">
              {summary.healthScore}
            </span>
            <span className="text-xs text-[#8E8295]">/ 100</span>
          </div>

          <span className="px-2 py-0.5 text-[10px] font-bold bg-[#F18B78] text-[#211D29] rounded-xs">
            CRITICAL FINDINGS
          </span>
        </div>

        {/* Breakdown Counters */}
        <div className="grid grid-cols-3 gap-1 py-2 border-y border-[#443849] text-center">
          <div className="bg-[#332B3C] p-1.5 border border-[#443849] rounded-sm">
            <div className="text-[#F18B78] text-base font-bold tabular-nums">
              {summary.criticalFindingsCount}
            </div>
            <div className="text-[9px] text-[#8E8295] uppercase">CRITICAL</div>
          </div>
          <div className="bg-[#332B3C] p-1.5 border border-[#443849] rounded-sm">
            <div className="text-[#E8B66D] text-base font-bold tabular-nums">
              {summary.warningsCount}
            </div>
            <div className="text-[9px] text-[#8E8295] uppercase">WARNINGS</div>
          </div>
          <div className="bg-[#332B3C] p-1.5 border border-[#443849] rounded-sm">
            <div className="text-[#B7ACBD] text-base font-bold tabular-nums">
              {summary.observationsCount}
            </div>
            <div className="text-[9px] text-[#8E8295] uppercase">OBSERVATIONS</div>
          </div>
        </div>

        {/* Dimension Health Checks */}
        <div className="mt-3 space-y-1.5 text-[11px]">
          <div className="flex justify-between items-center">
            <span className="text-[#B7ACBD]">Leakage MI Risk:</span>
            <span className="text-[#F18B78] font-bold tabular-nums">
              {summary.maxMutualInfoLeakage.toFixed(3)} (FAIL)
            </span>
          </div>
          <div className="flex justify-between items-center">
            <span className="text-[#B7ACBD]">Max PSI Drift:</span>
            <span className="text-[#F18B78] font-bold tabular-nums">
              {summary.maxPsiDrift.toFixed(3)} (FAIL)
            </span>
          </div>
          <div className="flex justify-between items-center">
            <span className="text-[#B7ACBD]">Calibration ECE:</span>
            <span className="text-[#E8B66D] font-bold tabular-nums">
              {summary.expectedCalibrationError.toFixed(3)} (WARN)
            </span>
          </div>
          <div className="flex justify-between items-center">
            <span className="text-[#B7ACBD]">Disparate Impact:</span>
            <span className="text-[#E8B66D] font-bold tabular-nums">
              {summary.disparateImpactRatio.toFixed(3)} (WARN)
            </span>
          </div>
          <div className="flex justify-between items-center">
            <span className="text-[#B7ACBD]">Gaussian Jitter Flip:</span>
            <span className="text-[#E8B66D] font-bold tabular-nums">
              {(summary.gaussianJitterFlipRate * 100).toFixed(1)}% (WARN)
            </span>
          </div>
        </div>
      </div>

      {/* 2. Artifact Provenance Panel */}
      <div className="p-3 border-b border-[#443849] space-y-2">
        <div className="flex items-center justify-between">
          <div className="text-[10px] text-[#8E8295] uppercase tracking-wider">
            ARTIFACT PROVENANCE
          </div>
          <span className="px-1.5 py-0.5 text-[9px] font-bold bg-[#332B3C] text-[#F18B78] border border-[#443849] rounded-xs">
            {summary.executionMode || (summary.modelArtifact ? "REAL" : "BENCHMARK")}
          </span>
        </div>

        {summary.modelArtifact ? (
          <div className="bg-[#332B3C] p-2 border border-[#443849] space-y-1 rounded-sm">
            <div className="flex items-center justify-between text-[11px]">
              <span className="text-[#B7ACBD] uppercase font-bold">MODEL</span>
              <span className="text-[#F18B78] font-bold text-[10px]">{summary.modelArtifact.framework.toUpperCase()}</span>
            </div>
            <div className="text-[#F4EFF6] font-bold text-[11px] truncate" title={summary.modelArtifact.originalFilename}>
              {summary.modelArtifact.originalFilename}
            </div>
            <div className="text-[10px] text-[#8E8295] font-mono flex items-center justify-between">
              <span>{(summary.modelArtifact.fileSize / 1024).toFixed(1)} KB</span>
              <span title={summary.modelArtifact.sha256}>
                SHA: {summary.modelArtifact.sha256.substring(0, 8)}...
              </span>
            </div>
          </div>
        ) : (
          <div className="bg-[#332B3C] p-2 border border-[#443849] space-y-0.5 rounded-sm">
            <div className="text-[10px] text-[#B7ACBD] uppercase font-bold">MODEL</div>
            <div className="text-[#F4EFF6] font-bold text-[11px] truncate">{summary.modelId}</div>
            <div className="text-[10px] text-[#8E8295]">{summary.framework} ({summary.taskType})</div>
          </div>
        )}

        {summary.evaluationDatasetArtifact ? (
          <div className="bg-[#332B3C] p-2 border border-[#443849] space-y-1 rounded-sm">
            <div className="flex items-center justify-between text-[11px]">
              <span className="text-[#B7ACBD] uppercase font-bold">EVALUATION DATASET</span>
              <span className="text-[#B7D7A8] font-bold text-[10px]">{summary.evaluationDatasetArtifact.datasetFormat.toUpperCase()}</span>
            </div>
            <div className="text-[#F4EFF6] font-bold text-[11px] truncate" title={summary.evaluationDatasetArtifact.originalFilename}>
              {summary.evaluationDatasetArtifact.originalFilename}
            </div>
            <div className="text-[10px] text-[#8E8295] font-mono flex items-center justify-between">
              <span>{summary.evaluationDatasetArtifact.rowCount.toLocaleString()} rows &bull; {summary.evaluationDatasetArtifact.columnCount} cols</span>
            </div>
            <div className="text-[9px] text-[#8E8295] font-mono truncate" title={summary.evaluationDatasetArtifact.sha256}>
              SHA: {summary.evaluationDatasetArtifact.sha256.substring(0, 10)}...
            </div>
          </div>
        ) : (
          <div className="bg-[#332B3C] p-2 border border-[#443849] space-y-0.5 rounded-sm">
            <div className="text-[10px] text-[#B7ACBD] uppercase font-bold">EVALUATION DATASET</div>
            <div className="text-[#F4EFF6] font-bold text-[11px] truncate">{summary.datasetName}</div>
            <div className="text-[10px] text-[#8E8295]">{summary.sampleCount?.toLocaleString()} samples &bull; {summary.featureCount} features</div>
          </div>
        )}

        {summary.baselineDatasetArtifact && (
          <div className="bg-[#332B3C] p-2 border border-[#443849] space-y-1 rounded-sm">
            <div className="flex items-center justify-between text-[11px]">
              <span className="text-[#B7ACBD] uppercase font-bold">BASELINE DATASET</span>
              <span className="text-[#B7D7A8] font-bold text-[10px]">{summary.baselineDatasetArtifact.datasetFormat.toUpperCase()}</span>
            </div>
            <div className="text-[#F4EFF6] font-bold text-[11px] truncate" title={summary.baselineDatasetArtifact.originalFilename}>
              {summary.baselineDatasetArtifact.originalFilename}
            </div>
            <div className="text-[10px] text-[#8E8295] font-mono flex items-center justify-between">
              <span>{summary.baselineDatasetArtifact.rowCount.toLocaleString()} rows</span>
              <span title={summary.baselineDatasetArtifact.sha256}>
                SHA: {summary.baselineDatasetArtifact.sha256.substring(0, 8)}...
              </span>
            </div>
          </div>
        )}
      </div>

      {/* 3. Selected Feature / Finding Inspector */}
      <div className="p-3 flex-1 flex flex-col justify-between">
        <div>
          <div className="text-[10px] text-[#8E8295] uppercase tracking-wider mb-2">
            INSPECTOR &bull; {selectedFeature ? "FEATURE DETAIL" : "GLOBAL SUMMARY"}
          </div>

          {selectedFeature ? (
            <div className="space-y-3">
              {/* Feature Header */}
              <div className="bg-[#332B3C] p-2 border border-[#443849] rounded-sm">
                <div className="flex items-center justify-between">
                  <span className="text-[#F4EFF6] font-bold text-xs truncate">
                    {selectedFeature.name}
                  </span>
                  <span
                    className={`px-1.5 py-0.2 text-[9px] font-bold rounded-xs ${
                      selectedFeature.severity === "CRITICAL"
                        ? "bg-[#F18B78] text-[#211D29]"
                        : selectedFeature.severity === "WARNING"
                        ? "bg-[#E8B66D] text-[#211D29]"
                        : "bg-[#332B3C] text-[#B7D7A8] border border-[#B7D7A8]/40"
                    }`}
                  >
                    {selectedFeature.severity}
                  </span>
                </div>
                <div className="text-[10px] text-[#8E8295] mt-0.5">
                  TYPE: {selectedFeature.dataType} &bull; ID: {selectedFeature.id}
                </div>
              </div>

              {/* Statistical Metrics Grid */}
              <div className="grid grid-cols-2 gap-1 text-[10px]">
                <div className="bg-[#332B3C] p-1.5 border border-[#443849] rounded-sm">
                  <div className="text-[#8E8295]">MUTUAL INFO</div>
                  <div className="text-[#F4EFF6] font-bold tabular-nums">
                    {selectedFeature.mutualInfoTarget.toFixed(3)}
                  </div>
                </div>
                <div className="bg-[#332B3C] p-1.5 border border-[#443849] rounded-sm">
                  <div className="text-[#8E8295]">CORRELATION</div>
                  <div className="text-[#F4EFF6] font-bold tabular-nums">
                    {selectedFeature.correlationTarget.toFixed(3)}
                  </div>
                </div>
                <div className="bg-[#332B3C] p-1.5 border border-[#443849] rounded-sm">
                  <div className="text-[#8E8295]">PSI DRIFT</div>
                  <div className="text-[#F4EFF6] font-bold tabular-nums">
                    {selectedFeature.psiValue.toFixed(3)}
                  </div>
                </div>
                <div className="bg-[#332B3C] p-1.5 border border-[#443849] rounded-sm">
                  <div className="text-[#8E8295]">SHAP IMPORTANCE</div>
                  <div className="text-[#F4EFF6] font-bold tabular-nums">
                    {(selectedFeature.shapImportance * 100).toFixed(1)}%
                  </div>
                </div>
              </div>

              {/* Anomaly & Root Cause Analysis */}
              {selectedFeature.anomalyType && (
                <div className="space-y-2 text-[11px]">
                  <div>
                    <div className="text-[#8E8295] text-[10px] uppercase font-bold">
                      DETECTED ANOMALY
                    </div>
                    <div className="text-[#F4EFF6] mt-0.5 font-semibold">
                      {selectedFeature.anomalyType}
                    </div>
                  </div>

                  {selectedFeature.rootCause && (
                    <div>
                      <div className="text-[#8E8295] text-[10px] uppercase font-bold">
                        ROOT CAUSE
                      </div>
                      <div className="text-[#B7ACBD] mt-0.5 leading-snug">
                        {selectedFeature.rootCause}
                      </div>
                    </div>
                  )}

                  {selectedFeature.recommendation && (
                    <div>
                      <div className="text-[#8E8295] text-[10px] uppercase font-bold">
                        RECOMMENDATION
                      </div>
                      <div className="text-[#B7D7A8] mt-0.5 leading-snug bg-[#B7D7A8]/10 p-1.5 border border-[#B7D7A8]/30 rounded-sm">
                        {selectedFeature.recommendation}
                      </div>
                    </div>
                  )}
                </div>
              )}
            </div>
          ) : (
            <div className="text-[#8E8295] text-[11px] py-4 text-center">
              Select a node in the topology graph to inspect statistical properties and causal recommendations.
            </div>
          )}
        </div>

        {/* Inspector Action Buttons */}
        {selectedFeature && (
          <div className="pt-3 border-t border-[#443849] space-y-1.5">
            <button
              onClick={() => onRunProbe && onRunProbe(selectedFeature)}
              className="w-full py-1.5 text-xs font-mono bg-[#332B3C] hover:bg-[#3D3448] border border-[#443849] text-[#F4EFF6] transition-colors rounded-sm cursor-pointer"
            >
              ISOLATE &amp; PROBE FEATURE
            </button>
            <button
              onClick={() => onExportFeatureEvidence && onExportFeatureEvidence(selectedFeature)}
              className="w-full py-1.5 text-xs font-mono bg-[#332B3C] hover:bg-[#3D3448] border border-[#443849] text-[#B7ACBD] hover:text-[#F4EFF6] transition-colors rounded-sm cursor-pointer"
            >
              EXPORT FEATURE JSON
            </button>
          </div>
        )}
      </div>
    </aside>
  );
};
