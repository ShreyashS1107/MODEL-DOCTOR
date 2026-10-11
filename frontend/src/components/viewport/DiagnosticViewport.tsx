"use client";

import React, { useState } from "react";
import {
  CalibrationBin,
  DiagnosticRunRecord,
  DistributionBin,
  FeatureEdge,
  FeatureNode,
  ViewportTab,
} from "@/types/diagnostics";

interface DiagnosticViewportProps {
  nodes: FeatureNode[];
  edges: FeatureEdge[];
  distributionBins: DistributionBin[];
  calibrationBins: CalibrationBin[];
  runs: DiagnosticRunRecord[];
  selectedFeature: FeatureNode | null;
  onSelectFeature: (feature: FeatureNode) => void;
  onSelectRun?: (run: DiagnosticRunRecord) => void;
}

export const DiagnosticViewport: React.FC<DiagnosticViewportProps> = ({
  nodes,
  edges,
  distributionBins,
  calibrationBins,
  runs,
  selectedFeature,
  onSelectFeature,
  onSelectRun,
}) => {
  const [activeTab, setActiveTab] = useState<ViewportTab>("TOPOLOGY");
  const [anomalyFilter, setAnomalyFilter] = useState<"ALL" | "ANOMALIES" | "CRITICAL">("ALL");

  const filteredNodes = nodes.filter((n) => {
    if (anomalyFilter === "CRITICAL") return n.severity === "CRITICAL";
    if (anomalyFilter === "ANOMALIES") return n.severity === "CRITICAL" || n.severity === "WARNING";
    return true;
  });

  return (
    <div className="flex-1 bg-[#211D29] flex flex-col min-w-0 overflow-hidden">
      {/* Viewport Top Control Bar */}
      <div className="h-10 bg-[#2A2432] border-b border-[#443849] px-3 flex items-center justify-between select-none shrink-0">
        {/* Sub-tab Navigation */}
        <div className="flex items-center gap-1 font-mono text-xs">
          <button
            onClick={() => setActiveTab("TOPOLOGY")}
            className={`px-3 py-1.5 border-b-2 transition-colors cursor-pointer ${
              activeTab === "TOPOLOGY"
                ? "border-[#F18B78] text-[#F4EFF6] font-semibold bg-[#332B3C]"
                : "border-transparent text-[#B7ACBD] hover:text-[#F4EFF6]"
            }`}
          >
            TOPOLOGY GRAPH
          </button>
          <button
            onClick={() => setActiveTab("DISTRIBUTION")}
            className={`px-3 py-1.5 border-b-2 transition-colors cursor-pointer ${
              activeTab === "DISTRIBUTION"
                ? "border-[#F18B78] text-[#F4EFF6] font-semibold bg-[#332B3C]"
                : "border-transparent text-[#B7ACBD] hover:text-[#F4EFF6]"
            }`}
          >
            DISTRIBUTION SHIFT (KS / PSI)
          </button>
          <button
            onClick={() => setActiveTab("CALIBRATION")}
            className={`px-3 py-1.5 border-b-2 transition-colors cursor-pointer ${
              activeTab === "CALIBRATION"
                ? "border-[#F18B78] text-[#F4EFF6] font-semibold bg-[#332B3C]"
                : "border-transparent text-[#B7ACBD] hover:text-[#F4EFF6]"
            }`}
          >
            CALIBRATION &amp; ERROR (ECE)
          </button>
          <button
            onClick={() => setActiveTab("LEAKAGE_SHAP")}
            className={`px-3 py-1.5 border-b-2 transition-colors cursor-pointer ${
              activeTab === "LEAKAGE_SHAP"
                ? "border-[#F18B78] text-[#F4EFF6] font-semibold bg-[#332B3C]"
                : "border-transparent text-[#B7ACBD] hover:text-[#F4EFF6]"
            }`}
          >
            MUTUAL INFO &amp; LEAKAGE
          </button>
          <button
            onClick={() => setActiveTab("RUN_HISTORY")}
            className={`px-3 py-1.5 border-b-2 transition-colors cursor-pointer ${
              activeTab === "RUN_HISTORY"
                ? "border-[#F18B78] text-[#F4EFF6] font-semibold bg-[#332B3C]"
                : "border-transparent text-[#B7ACBD] hover:text-[#F4EFF6]"
            }`}
          >
            RUN HISTORY ({runs.length})
          </button>
        </div>

        {/* Viewport Filter / Legend Controls */}
        {activeTab === "TOPOLOGY" && (
          <div className="flex items-center gap-2 text-[11px] font-mono">
            <span className="text-[#8E8295]">FILTER:</span>
            <div className="flex items-center bg-[#332B3C] border border-[#443849] rounded-sm">
              <button
                onClick={() => setAnomalyFilter("ALL")}
                className={`px-2 py-0.5 cursor-pointer ${
                  anomalyFilter === "ALL" ? "bg-[#443849] text-[#F4EFF6] font-bold" : "text-[#8E8295] hover:text-[#B7ACBD]"
                }`}
              >
                ALL ({nodes.length})
              </button>
              <button
                onClick={() => setAnomalyFilter("ANOMALIES")}
                className={`px-2 py-0.5 cursor-pointer ${
                  anomalyFilter === "ANOMALIES" ? "bg-[#443849] text-[#E8B66D] font-bold" : "text-[#8E8295] hover:text-[#B7ACBD]"
                }`}
              >
                FLAGGED (6)
              </button>
              <button
                onClick={() => setAnomalyFilter("CRITICAL")}
                className={`px-2 py-0.5 cursor-pointer ${
                  anomalyFilter === "CRITICAL" ? "bg-[#443849] text-[#F18B78] font-bold" : "text-[#8E8295] hover:text-[#B7ACBD]"
                }`}
              >
                CRITICAL (2)
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Main Viewport Workspace Stage */}
      <div className="flex-1 overflow-auto p-4 relative">
        {/* ================================================================= */}
        {/* 1. TOPOLOGY & ANOMALY GRAPH                                       */}
        {/* ================================================================= */}
        {activeTab === "TOPOLOGY" && (
          <div className="h-full flex flex-col border border-[#443849] bg-[#2A2432] relative select-none rounded-sm">
            {/* Stage Header Info */}
            <div className="px-3 py-2 border-b border-[#443849] flex items-center justify-between text-xs font-mono text-[#8E8295]">
              <div>
                FEATURE RELATIONSHIP &amp; ANOMALY TOPOLOGY &bull; {filteredNodes.length} NODES &bull; {edges.length} CORRELATION EDGES
              </div>
              <div className="flex items-center gap-3 text-[10px]">
                <span className="flex items-center gap-1 text-[#B7ACBD]">
                  <span className="w-2 h-2 rounded-full bg-[#F18B78]" /> CRITICAL LEAK / DRIFT
                </span>
                <span className="flex items-center gap-1 text-[#B7ACBD]">
                  <span className="w-2 h-2 rounded-full bg-[#E8B66D]" /> WARNING
                </span>
                <span className="flex items-center gap-1 text-[#B7ACBD]">
                  <span className="w-2 h-2 rounded-full bg-[#B7D7A8]" /> NOMINAL
                </span>
              </div>
            </div>

            {/* SVG Feature Graph Stage */}
            <div className="flex-1 relative overflow-hidden flex items-center justify-center p-2">
              <svg className="w-full h-full min-h-[380px]" viewBox="0 0 800 520">
                {/* Background Grid Pattern */}
                <defs>
                  <pattern id="grid" width="40" height="40" patternUnits="userSpaceOnUse">
                    <path d="M 40 0 L 0 0 0 40" fill="none" stroke="#332B3C" strokeWidth="1" />
                  </pattern>
                </defs>
                <rect width="100%" height="100%" fill="url(#grid)" />

                {/* Edges */}
                {edges.map((edge, idx) => {
                  const src = nodes.find((n) => n.id === edge.source);
                  const tgt = nodes.find((n) => n.id === edge.target);
                  if (!src || !tgt) return null;

                  return (
                    <g key={idx}>
                      <line
                        x1={src.x}
                        y1={src.y}
                        x2={tgt.x}
                        y2={tgt.y}
                        stroke={edge.isAnomaly ? "#F18B78" : "#443849"}
                        strokeWidth={Math.max(1, edge.weight * 2.5)}
                        strokeDasharray={edge.isAnomaly ? "4,3" : "none"}
                        opacity={edge.isAnomaly ? 0.9 : 0.6}
                      />
                    </g>
                  );
                })}

                {/* Nodes */}
                {filteredNodes.map((node) => {
                  const isSelected = selectedFeature?.id === node.id;
                  const nodeColor =
                    node.severity === "CRITICAL"
                      ? "#F18B78"
                      : node.severity === "WARNING"
                      ? "#E8B66D"
                      : "#B7D7A8";

                  const radius = Math.max(14, node.shapImportance * 45);

                  return (
                    <g
                      key={node.id}
                      transform={`translate(${node.x}, ${node.y})`}
                      onClick={() => onSelectFeature(node)}
                      className="cursor-pointer group"
                    >
                      {/* Selection Ring */}
                      {isSelected && (
                        <circle
                          r={radius + 6}
                          fill="none"
                          stroke="#F4EFF6"
                          strokeWidth="1.5"
                          strokeDasharray="3,3"
                        />
                      )}

                      {/* Node Body */}
                      <circle
                        r={radius}
                        fill="#332B3C"
                        stroke={nodeColor}
                        strokeWidth={isSelected ? 2.5 : 1.5}
                      />

                      {/* Node Core Dot */}
                      <circle r={3} fill={nodeColor} />

                      {/* Node Label */}
                      <text
                        y={radius + 14}
                        textAnchor="middle"
                        fill={isSelected ? "#F4EFF6" : "#B7ACBD"}
                        fontSize="10"
                        fontFamily="monospace"
                        fontWeight={isSelected ? "bold" : "normal"}
                      >
                        {node.name}
                      </text>

                      {/* Primary Metric Tag */}
                      <text
                        y={radius + 25}
                        textAnchor="middle"
                        fill={nodeColor}
                        fontSize="9"
                        fontFamily="monospace"
                      >
                        {node.severity === "CRITICAL" && node.mutualInfoTarget > 0.85
                          ? `MI: ${node.mutualInfoTarget.toFixed(3)}`
                          : node.psiValue > 0.2
                          ? `PSI: ${node.psiValue.toFixed(3)}`
                          : `SHAP: ${(node.shapImportance * 100).toFixed(1)}%`}
                      </text>
                    </g>
                  );
                })}
              </svg>
            </div>

            {/* Stage Bottom Instruction */}
            <div className="px-3 py-1.5 bg-[#211D29] border-t border-[#443849] flex items-center justify-between text-[11px] font-mono text-[#8E8295]">
              <span>CLICK A FEATURE NODE TO INSPECT EXACT CAUSAL EVIDENCE &amp; METRICS</span>
              <span>ACTIVE SELECTION: <strong className="text-[#F4EFF6]">{selectedFeature ? selectedFeature.name : "NONE"}</strong></span>
            </div>
          </div>
        )}

        {/* ================================================================= */}
        {/* 2. DISTRIBUTION SHIFT (KS / PSI)                                  */}
        {/* ================================================================= */}
        {activeTab === "DISTRIBUTION" && (
          <div className="space-y-4 font-mono">
            <div className="p-3 border border-[#443849] bg-[#2A2432] flex items-center justify-between rounded-sm">
              <div>
                <div className="text-xs text-[#B7ACBD] uppercase font-bold">
                  COVARIATE DRIFT ANALYSIS: user_velocity_6h
                </div>
                <div className="text-[11px] text-[#8E8295]">
                  Kolmogorov-Smirnov two-sample test (D = 0.284, p-value &lt; 0.0001) &bull; Population Stability Index (PSI = 0.312)
                </div>
              </div>
              <span className="px-2 py-0.5 text-xs font-bold bg-[#F18B78] text-[#211D29] rounded-xs">
                CRITICAL DRIFT
              </span>
            </div>

            {/* Distribution Bins Table & Bar Chart */}
            <div className="border border-[#443849] bg-[#2A2432] p-3 rounded-sm">
              <div className="text-xs text-[#B7ACBD] mb-3 uppercase font-bold">
                QUANTILE DENSITY COMPARISON (BASELINE vs PRODUCTION)
              </div>

              <div className="space-y-2">
                {distributionBins.map((bin) => (
                  <div key={bin.binIndex} className="text-xs">
                    <div className="flex justify-between text-[11px] mb-1">
                      <span className="text-[#F4EFF6] w-20">{bin.binRange}</span>
                      <span className="text-[#8E8295]">
                        Baseline: <strong className="text-[#B7ACBD]">{(bin.baselineDensity * 100).toFixed(1)}%</strong> | Current: <strong className="text-[#F4EFF6]">{(bin.currentDensity * 100).toFixed(1)}%</strong>
                      </span>
                      <span className={bin.psiDelta > 0.03 ? "text-[#F18B78] font-bold" : "text-[#8E8295]"}>
                        PSI Delta: {bin.psiDelta.toFixed(3)}
                      </span>
                    </div>

                    <div className="grid grid-cols-2 gap-1 h-3 bg-[#1C1824] p-0.5 border border-[#443849] rounded-xs">
                      <div
                        className="bg-[#B7ACBD] h-full rounded-xs"
                        style={{ width: `${bin.baselineDensity * 100 * 2}%` }}
                        title={`Baseline: ${(bin.baselineDensity * 100).toFixed(1)}%`}
                      />
                      <div
                        className={bin.psiDelta > 0.03 ? "bg-[#F18B78] h-full rounded-xs" : "bg-[#B7D7A8] h-full rounded-xs"}
                        style={{ width: `${bin.currentDensity * 100 * 2}%` }}
                        title={`Current: ${(bin.currentDensity * 100).toFixed(1)}%`}
                      />
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}

        {/* ================================================================= */}
        {/* 3. CALIBRATION & ERROR (ECE)                                      */}
        {/* ================================================================= */}
        {activeTab === "CALIBRATION" && (
          <div className="space-y-4 font-mono">
            <div className="p-3 border border-[#443849] bg-[#2A2432] flex items-center justify-between rounded-sm">
              <div>
                <div className="text-xs text-[#B7ACBD] uppercase font-bold">
                  PROBABILITY CALIBRATION CURVE &amp; RELIABILITY DECOMPOSITION
                </div>
                <div className="text-[11px] text-[#8E8295]">
                  Expected Calibration Error (ECE = 0.084) &bull; Brier Score = 0.068 &bull; 10 Probability Deciles
                </div>
              </div>
              <span className="px-2 py-0.5 text-xs font-bold bg-[#E8B66D] text-[#211D29] rounded-xs">
                WARNING: OVERCONFIDENT IN [0.0 - 0.2]
              </span>
            </div>

            <div className="border border-[#443849] bg-[#2A2432] p-3 rounded-sm">
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead>
                    <tr className="border-b border-[#443849] text-[#8E8295] text-[10px] uppercase">
                      <th className="py-1.5 px-2">Decile Bin</th>
                      <th className="py-1.5 px-2">Mean Predicted Prob</th>
                      <th className="py-1.5 px-2">Observed Positive Rate</th>
                      <th className="py-1.5 px-2">Calibration Gap</th>
                      <th className="py-1.5 px-2">Sample Count</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#443849] tabular-nums">
                    {calibrationBins.map((bin) => {
                      const gap = bin.predictedProbability - bin.observedFrequency;
                      const isHighGap = Math.abs(gap) >= 0.03;

                      return (
                        <tr key={bin.binIndex} className="hover:bg-[#332B3C]">
                          <td className="py-2 px-2 text-[#F4EFF6]">Bin {bin.binIndex + 1}</td>
                          <td className="py-2 px-2 text-[#B7ACBD]">{bin.predictedProbability.toFixed(2)}</td>
                          <td className="py-2 px-2 text-[#F4EFF6]">{bin.observedFrequency.toFixed(2)}</td>
                          <td className={`py-2 px-2 font-bold ${isHighGap ? "text-[#E8B66D]" : "text-[#B7D7A8]"}`}>
                            {gap > 0 ? `+${gap.toFixed(3)}` : gap.toFixed(3)}
                          </td>
                          <td className="py-2 px-2 text-[#8E8295]">{bin.sampleCount.toLocaleString()}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {/* ================================================================= */}
        {/* 4. MUTUAL INFO & LEAKAGE                                          */}
        {/* ================================================================= */}
        {activeTab === "LEAKAGE_SHAP" && (
          <div className="space-y-4 font-mono">
            <div className="p-3 border border-[#443849] bg-[#2A2432] flex items-center justify-between rounded-sm">
              <div>
                <div className="text-xs text-[#B7ACBD] uppercase font-bold">
                  TARGET MUTUAL INFORMATION PROBE &bull; LABEL: is_fraud
                </div>
                <div className="text-[11px] text-[#8E8295]">
                  Identifies proxy identifiers, lookahead bias, and suspiciously predictive feature leakage
                </div>
              </div>
              <span className="px-2 py-0.5 text-xs font-bold bg-[#F18B78] text-[#211D29] rounded-xs">
                1 CRITICAL LEAK DETECTED
              </span>
            </div>

            <div className="border border-[#443849] bg-[#2A2432] p-3 space-y-3 rounded-sm">
              {nodes
                .slice()
                .sort((a, b) => b.mutualInfoTarget - a.mutualInfoTarget)
                .map((n) => (
                  <div key={n.id} className="text-xs">
                    <div className="flex justify-between text-[11px] mb-1">
                      <span className={n.mutualInfoTarget > 0.85 ? "text-[#F18B78] font-bold" : "text-[#F4EFF6]"}>
                        {n.name} ({n.dataType})
                      </span>
                      <span className={n.mutualInfoTarget > 0.85 ? "text-[#F18B78] font-bold" : "text-[#B7ACBD]"}>
                        MI = {n.mutualInfoTarget.toFixed(3)} &bull; Correlation = {n.correlationTarget.toFixed(3)}
                      </span>
                    </div>

                    <div className="w-full h-2 bg-[#1C1824] border border-[#443849] overflow-hidden rounded-xs">
                      <div
                        className={n.mutualInfoTarget > 0.85 ? "bg-[#F18B78] h-full" : "bg-[#B7D7A8] h-full"}
                        style={{ width: `${n.mutualInfoTarget * 100}%` }}
                      />
                    </div>
                  </div>
                ))}
            </div>
          </div>
        )}

        {/* ================================================================= */}
        {/* 5. RUN HISTORY                                                    */}
        {/* ================================================================= */}
        {activeTab === "RUN_HISTORY" && (
          <div className="space-y-4 font-mono">
            <div className="border border-[#443849] bg-[#2A2432] p-3 rounded-sm">
              <div className="text-xs text-[#B7ACBD] mb-3 uppercase font-bold">
                HISTORICAL DIAGNOSTIC AUDIT LOGS
              </div>

              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead>
                    <tr className="border-b border-[#443849] text-[#8E8295] text-[10px] uppercase">
                      <th className="py-1.5 px-2">Run</th>
                      <th className="py-1.5 px-2">Model</th>
                      <th className="py-1.5 px-2">Dataset</th>
                      <th className="py-1.5 px-2">Score</th>
                      <th className="py-1.5 px-2">Findings</th>
                      <th className="py-1.5 px-2">Samples</th>
                      <th className="py-1.5 px-2">Duration</th>
                      <th className="py-1.5 px-2">Timestamp</th>
                      <th className="py-1.5 px-2">Status</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#443849] tabular-nums">
                    {runs.map((r) => (
                      <tr
                        key={r.id}
                        onClick={() => onSelectRun && onSelectRun(r)}
                        className="hover:bg-[#332B3C] cursor-pointer"
                      >
                        <td className="py-2 px-2 text-[#F18B78] font-bold">{r.runNumber}</td>
                        <td className="py-2 px-2 text-[#F4EFF6]">{r.modelName} (v{r.modelVersion})</td>
                        <td className="py-2 px-2 text-[#B7ACBD]">{r.datasetName}</td>
                        <td className="py-2 px-2 font-bold text-[#F4EFF6]">{r.healthScore} / 100</td>
                        <td className="py-2 px-2">
                          <span className="text-[#F18B78] font-bold">{r.criticalCount}C</span>
                          {" / "}
                          <span className="text-[#E8B66D] font-bold">{r.warningCount}W</span>
                        </td>
                        <td className="py-2 px-2 text-[#8E8295]">{r.sampleCount.toLocaleString()}</td>
                        <td className="py-2 px-2 text-[#8E8295]">{r.durationMs}ms</td>
                        <td className="py-2 px-2 text-[#8E8295]">{r.timestamp}</td>
                        <td className="py-2 px-2">
                          <span
                            className={`px-1.5 py-0.5 text-[9px] font-bold rounded-xs ${
                              r.status === "FAIL"
                                ? "bg-[#F18B78] text-[#211D29]"
                                : r.status === "WARN"
                                ? "bg-[#E8B66D] text-[#211D29]"
                                : "bg-[#B7D7A8] text-[#211D29]"
                            }`}
                          >
                            {r.status}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
