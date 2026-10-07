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
    <aside className="w-80 bg-[#0c0e14] border-l border-[#1f2533] flex flex-col shrink-0 overflow-y-auto select-none font-mono text-xs">
      {/* 1. Model Health Summary Panel */}
      <div className="p-3 border-b border-[#1f2533]">
        <div className="text-[10px] text-[#64748b] uppercase tracking-wider mb-2">
          MODEL HEALTH INDEX
        </div>

        <div className="flex items-baseline justify-between mb-2">
          <div className="flex items-baseline gap-1">
            <span className="text-3xl font-bold font-sans text-white tabular-nums">
              {summary.healthScore}
            </span>
            <span className="text-xs text-[#64748b]">/ 100</span>
          </div>

          <span className="px-2 py-0.5 text-[10px] font-bold bg-[#ef4444] text-black">
            CRITICAL FINDINGS
          </span>
        </div>

        {/* Breakdown Counters */}
        <div className="grid grid-cols-3 gap-1 py-2 border-y border-[#1f2533] text-center">
          <div className="bg-[#141822] p-1.5 border border-[#222633]">
            <div className="text-[#ef4444] text-base font-bold tabular-nums">
              {summary.criticalFindingsCount}
            </div>
            <div className="text-[9px] text-[#64748b] uppercase">CRITICAL</div>
          </div>
          <div className="bg-[#141822] p-1.5 border border-[#222633]">
            <div className="text-[#f59e0b] text-base font-bold tabular-nums">
              {summary.warningsCount}
            </div>
            <div className="text-[9px] text-[#64748b] uppercase">WARNINGS</div>
          </div>
          <div className="bg-[#141822] p-1.5 border border-[#222633]">
            <div className="text-[#94a3b8] text-base font-bold tabular-nums">
              {summary.observationsCount}
            </div>
            <div className="text-[9px] text-[#64748b] uppercase">OBSERVATIONS</div>
          </div>
        </div>

        {/* Dimension Health Checks */}
        <div className="mt-3 space-y-1.5 text-[11px]">
          <div className="flex justify-between items-center">
            <span className="text-[#94a3b8]">Leakage MI Risk:</span>
            <span className="text-[#ef4444] font-bold tabular-nums">
              {summary.maxMutualInfoLeakage.toFixed(3)} (FAIL)
            </span>
          </div>
          <div className="flex justify-between items-center">
            <span className="text-[#94a3b8]">Max PSI Drift:</span>
            <span className="text-[#ef4444] font-bold tabular-nums">
              {summary.maxPsiDrift.toFixed(3)} (FAIL)
            </span>
          </div>
          <div className="flex justify-between items-center">
            <span className="text-[#94a3b8]">Calibration ECE:</span>
            <span className="text-[#f59e0b] font-bold tabular-nums">
              {summary.expectedCalibrationError.toFixed(3)} (WARN)
            </span>
          </div>
          <div className="flex justify-between items-center">
            <span className="text-[#94a3b8]">Disparate Impact:</span>
            <span className="text-[#f59e0b] font-bold tabular-nums">
              {summary.disparateImpactRatio.toFixed(3)} (WARN)
            </span>
          </div>
          <div className="flex justify-between items-center">
            <span className="text-[#94a3b8]">Gaussian Jitter Flip:</span>
            <span className="text-[#f59e0b] font-bold tabular-nums">
              {(summary.gaussianJitterFlipRate * 100).toFixed(1)}% (WARN)
            </span>
          </div>
        </div>
      </div>

      {/* 2. Artifact Provenance Panel */}
      <div className="p-3 border-b border-[#1f2533] space-y-2">
        <div className="flex items-center justify-between">
          <div className="text-[10px] text-[#64748b] uppercase tracking-wider">
            ARTIFACT PROVENANCE
          </div>
          <span className="px-1.5 py-0.5 text-[9px] font-bold bg-[#141822] text-[#3b82f6] border border-[#222633]">
            {summary.executionMode || (summary.modelArtifact ? "REAL" : "BENCHMARK")}
          </span>
        </div>

        {summary.modelArtifact ? (
          <div className="bg-[#141822] p-2 border border-[#222633] space-y-1">
            <div className="flex items-center justify-between text-[11px]">
              <span className="text-[#94a3b8] uppercase font-bold">MODEL</span>
              <span className="text-[#3b82f6] font-bold text-[10px]">{summary.modelArtifact.framework.toUpperCase()}</span>
            </div>
            <div className="text-white font-bold text-[11px] truncate" title={summary.modelArtifact.originalFilename}>
              {summary.modelArtifact.originalFilename}
            </div>
            <div className="text-[10px] text-[#64748b] font-mono flex items-center justify-between">
              <span>{(summary.modelArtifact.fileSize / 1024).toFixed(1)} KB</span>
              <span title={summary.modelArtifact.sha256}>
                SHA: {summary.modelArtifact.sha256.substring(0, 8)}...
              </span>
            </div>
          </div>
        ) : (
          <div className="bg-[#141822] p-2 border border-[#222633] space-y-0.5">
            <div className="text-[10px] text-[#94a3b8] uppercase font-bold">MODEL</div>
            <div className="text-white font-bold text-[11px] truncate">{summary.modelId}</div>
            <div className="text-[10px] text-[#64748b]">{summary.framework} ({summary.taskType})</div>
          </div>
        )}

        {summary.evaluationDatasetArtifact ? (
          <div className="bg-[#141822] p-2 border border-[#222633] space-y-1">
            <div className="flex items-center justify-between text-[11px]">
              <span className="text-[#94a3b8] uppercase font-bold">EVALUATION DATASET</span>
              <span className="text-[#10b981] font-bold text-[10px]">{summary.evaluationDatasetArtifact.datasetFormat.toUpperCase()}</span>
            </div>
            <div className="text-white font-bold text-[11px] truncate" title={summary.evaluationDatasetArtifact.originalFilename}>
              {summary.evaluationDatasetArtifact.originalFilename}
            </div>
            <div className="text-[10px] text-[#64748b] font-mono flex items-center justify-between">
              <span>{summary.evaluationDatasetArtifact.rowCount.toLocaleString()} rows &bull; {summary.evaluationDatasetArtifact.columnCount} cols</span>
            </div>
            <div className="text-[9px] text-[#64748b] font-mono truncate" title={summary.evaluationDatasetArtifact.sha256}>
              SHA: {summary.evaluationDatasetArtifact.sha256.substring(0, 10)}...
            </div>
          </div>
        ) : (
          <div className="bg-[#141822] p-2 border border-[#222633] space-y-0.5">
            <div className="text-[10px] text-[#94a3b8] uppercase font-bold">EVALUATION DATASET</div>
            <div className="text-white font-bold text-[11px] truncate">{summary.datasetName}</div>
            <div className="text-[10px] text-[#64748b]">{summary.sampleCount?.toLocaleString()} samples &bull; {summary.featureCount} features</div>
          </div>
        )}

        {summary.baselineDatasetArtifact && (
          <div className="bg-[#141822] p-2 border border-[#222633] space-y-1">
            <div className="flex items-center justify-between text-[11px]">
              <span className="text-[#94a3b8] uppercase font-bold">BASELINE DATASET</span>
              <span className="text-[#10b981] font-bold text-[10px]">{summary.baselineDatasetArtifact.datasetFormat.toUpperCase()}</span>
            </div>
            <div className="text-white font-bold text-[11px] truncate" title={summary.baselineDatasetArtifact.originalFilename}>
              {summary.baselineDatasetArtifact.originalFilename}
            </div>
            <div className="text-[10px] text-[#64748b] font-mono flex items-center justify-between">
              <span>{summary.baselineDatasetArtifact.rowCount.toLocaleString()} rows</span>
              <span title={summary.baselineDatasetArtifact.sha256}>
                SHA: {summary.baselineDatasetArtifact.sha256.substring(0, 8)}...
              </span>
            </div>
          </div>
        )}
      </div>

      {/* 2. Selected Feature / Finding Inspector */}
      <div className="p-3 flex-1 flex flex-col justify-between">
        <div>
          <div className="text-[10px] text-[#64748b] uppercase tracking-wider mb-2">
            INSPECTOR &bull; {selectedFeature ? "FEATURE DETAIL" : "GLOBAL SUMMARY"}
          </div>

          {selectedFeature ? (
            <div className="space-y-3">
              {/* Feature Header */}
              <div className="bg-[#141822] p-2 border border-[#222633]">
                <div className="flex items-center justify-between">
                  <span className="text-white font-bold text-xs truncate">
                    {selectedFeature.name}
                  </span>
                  <span
                    className={`px-1.5 py-0.2 text-[9px] font-bold ${
                      selectedFeature.severity === "CRITICAL"
                        ? "bg-[#ef4444] text-black"
                        : selectedFeature.severity === "WARNING"
                        ? "bg-[#f59e0b] text-black"
                        : "bg-[#3b82f6] text-white"
                    }`}
                  >
                    {selectedFeature.severity}
                  </span>
                </div>
                <div className="text-[10px] text-[#64748b] mt-0.5">
                  TYPE: {selectedFeature.dataType} &bull; ID: {selectedFeature.id}
                </div>
              </div>

              {/* Statistical Metrics Grid */}
              <div className="grid grid-cols-2 gap-1 text-[10px]">
                <div className="bg-[#141822] p-1.5 border border-[#222633]">
                  <div className="text-[#64748b]">MUTUAL INFO</div>
                  <div className="text-white font-bold tabular-nums">
                    {selectedFeature.mutualInfoTarget.toFixed(3)}
                  </div>
                </div>
                <div className="bg-[#141822] p-1.5 border border-[#222633]">
                  <div className="text-[#64748b]">CORRELATION</div>
                  <div className="text-white font-bold tabular-nums">
                    {selectedFeature.correlationTarget.toFixed(3)}
                  </div>
                </div>
                <div className="bg-[#141822] p-1.5 border border-[#222633]">
                  <div className="text-[#64748b]">PSI DRIFT</div>
                  <div className="text-white font-bold tabular-nums">
                    {selectedFeature.psiValue.toFixed(3)}
                  </div>
                </div>
                <div className="bg-[#141822] p-1.5 border border-[#222633]">
                  <div className="text-[#64748b]">SHAP IMPORTANCE</div>
                  <div className="text-white font-bold tabular-nums">
                    {(selectedFeature.shapImportance * 100).toFixed(1)}%
                  </div>
                </div>
              </div>

              {/* Anomaly & Root Cause Analysis */}
              {selectedFeature.anomalyType && (
                <div className="space-y-2 text-[11px]">
                  <div>
                    <div className="text-[#64748b] text-[10px] uppercase font-bold">
                      DETECTED ANOMALY
                    </div>
                    <div className="text-[#f1f3f8] mt-0.5 font-semibold">
                      {selectedFeature.anomalyType}
                    </div>
                  </div>

                  {selectedFeature.rootCause && (
                    <div>
                      <div className="text-[#64748b] text-[10px] uppercase font-bold">
                        ROOT CAUSE
                      </div>
                      <div className="text-[#94a3b8] mt-0.5 leading-snug">
                        {selectedFeature.rootCause}
                      </div>
                    </div>
                  )}

                  {selectedFeature.recommendation && (
                    <div>
                      <div className="text-[#64748b] text-[10px] uppercase font-bold">
                        RECOMMENDATION
                      </div>
                      <div className="text-[#10b981] mt-0.5 leading-snug bg-[#0e1614] p-1.5 border border-[#164e3f]">
                        {selectedFeature.recommendation}
                      </div>
                    </div>
                  )}
                </div>
              )}
            </div>
          ) : (
            <div className="text-[#64748b] text-[11px] py-4 text-center">
              Select a node in the topology graph to inspect statistical properties and causal recommendations.
            </div>
          )}
        </div>

        {/* Inspector Action Buttons */}
        {selectedFeature && (
          <div className="pt-3 border-t border-[#1f2533] space-y-1.5">
            <button
              onClick={() => onRunProbe && onRunProbe(selectedFeature)}
              className="w-full py-1.5 text-xs font-mono bg-[#141822] hover:bg-[#1a202c] border border-[#222633] text-[#f1f3f8] transition-colors cursor-pointer"
            >
              ISOLATE &amp; PROBE FEATURE
            </button>
            <button
              onClick={() => onExportFeatureEvidence && onExportFeatureEvidence(selectedFeature)}
              className="w-full py-1.5 text-xs font-mono bg-[#141822] hover:bg-[#1a202c] border border-[#222633] text-[#94a3b8] hover:text-[#f1f3f8] transition-colors cursor-pointer"
            >
              EXPORT FEATURE JSON
            </button>
          </div>
        )}
      </div>
    </aside>
  );
};
