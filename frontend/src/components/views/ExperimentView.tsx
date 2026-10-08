"use client";

import React, { useState, useEffect, useMemo } from "react";
import {
  DiagnosticExperiment,
  CreateExperimentRequest,
  ExperimentType,
  ExperimentStatus,
  ExperimentConclusion,
  DiagnosticRemediation,
  MetricComparisonItem,
  NavSection,
} from "@/types/diagnostics";
import {
  getDiagnosticExperiments,
  createDiagnosticExperiment,
  executeDiagnosticExperiment,
  cancelDiagnosticExperiment,
  getDiagnosticRemediations,
  RunSummary,
} from "@/lib/api";

export interface ExperimentViewProps {
  runId: string;
  runSummary?: RunSummary | null;
  onSelectSection?: (section: NavSection) => void;
  onSelectFeature?: (featureName: string) => void;
  onRefresh?: () => void;
  onNavigateSection?: (section: NavSection) => void;
  onSelectInvestigationTarget?: (targetKey: string) => void;
}

const EXPERIMENT_TYPES: { type: ExperimentType; label: string; desc: string }[] = [
  { type: "FEATURE_ABLATION", label: "Feature Ablation", desc: "Ablate or zero a feature to evaluate impact on error rate and performance." },
  { type: "FEATURE_TRANSFORMATION", label: "Feature Transformation", desc: "Apply deterministic quantile clipping, winsorization, or scaling." },
  { type: "MISSING_VALUE_STRESS", label: "Missingness Stress", desc: "Inject controlled missing values at specified rates to test resilience." },
  { type: "THRESHOLD_COUNTERFACTUAL", label: "Threshold Counterfactual", desc: "Evaluate decision operating grid to find FNR/FPR tradeoffs without retraining." },
  { type: "CALIBRATION_COUNTERFACTUAL", label: "Calibration Counterfactual", desc: "Evaluate Platt / Isotonic probability calibration on independent split." },
  { type: "SUBGROUP_COUNTERFACTUAL", label: "Subgroup Counterfactual", desc: "Evaluate performance and error disparity changes across protected subgroups." },
];

export const ExperimentView: React.FC<ExperimentViewProps> = ({
  runId,
  runSummary,
  onSelectSection,
  onSelectFeature,
  onRefresh,
  onNavigateSection,
  onSelectInvestigationTarget,
}) => {
  const navigate = onSelectSection || onNavigateSection;
  const [experiments, setExperiments] = useState<DiagnosticExperiment[]>([]);
  const [remediations, setRemediations] = useState<DiagnosticRemediation[]>([]);
  const [selectedExperiment, setSelectedExperiment] = useState<DiagnosticExperiment | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isExecuting, setIsExecuting] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Filter state
  const [statusFilter, setStatusFilter] = useState<string>("ALL");
  const [typeFilter, setTypeFilter] = useState<string>("ALL");

  // Create Modal state
  const [showCreateModal, setShowCreateModal] = useState<boolean>(false);
  const [newExpType, setNewExpType] = useState<ExperimentType>("FEATURE_ABLATION");
  const [selectedRemediationId, setSelectedRemediationId] = useState<string>("");
  const [targetFeature, setTargetFeature] = useState<string>("drifted_feature");
  const [ablationStrategy, setAblationStrategy] = useState<"zero" | "drop" | "median">("zero");
  const [transformType, setTransformType] = useState<"CLIP" | "WINSORIZE" | "MISSING_REPLACE" | "STANDARDIZE">("CLIP");
  const [lowerQuantile, setLowerQuantile] = useState<number>(0.01);
  const [upperQuantile, setUpperQuantile] = useState<number>(0.99);
  const [missingnessRate, setMissingnessRate] = useState<number>(0.10);
  const [baseThreshold, setBaseThreshold] = useState<number>(0.50);
  const [candThreshold, setCandThreshold] = useState<number>(0.40);
  const [calibrationMethod, setCalibrationMethod] = useState<"PLATT" | "ISOTONIC">("PLATT");
  const [subgroupAttribute, setSubgroupAttribute] = useState<string>("protected_region");

  const loadData = async () => {
    try {
      setIsLoading(true);
      const [expData, remData] = await Promise.all([
        getDiagnosticExperiments(runId),
        getDiagnosticRemediations(runId).catch(() => []),
      ]);
      setExperiments(expData);
      setRemediations(remData);
      if (expData.length > 0 && !selectedExperiment) {
        setSelectedExperiment(expData[0]);
      } else if (selectedExperiment) {
        const updated = expData.find((e) => e.id === selectedExperiment.id);
        if (updated) setSelectedExperiment(updated);
      }
    } catch (err: any) {
      setErrorMessage(err.message || "Failed to load experiment data.");
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [runId]);

  // HUD Metrics
  const hudMetrics = useMemo(() => {
    const total = experiments.length;
    const running = experiments.filter((e) => e.status === "RUNNING").length;
    const completed = experiments.filter((e) => e.status === "COMPLETED").length;
    const validated = experiments.filter((e) => e.conclusion === "VALIDATED").length;
    const rejected = experiments.filter((e) => e.conclusion === "REJECTED").length;
    const inconclusive = experiments.filter((e) => e.conclusion === "INCONCLUSIVE" || e.conclusion === "PARTIALLY_VALIDATED").length;
    return { total, running, completed, validated, rejected, inconclusive };
  }, [experiments]);

  // Filtered experiments
  const filteredExperiments = useMemo(() => {
    return experiments.filter((exp) => {
      const matchStatus = statusFilter === "ALL" || exp.status === statusFilter || exp.conclusion === statusFilter;
      const matchType = typeFilter === "ALL" || exp.experimentType === typeFilter;
      return matchStatus && matchType;
    });
  }, [experiments, statusFilter, typeFilter]);

  const handleLaunchExperiment = async () => {
    try {
      setIsExecuting(true);
      setErrorMessage(null);

      const remId = selectedRemediationId ? parseInt(selectedRemediationId) : undefined;
      const selectedRem = remediations.find((r) => r.id === remId);

      const req: CreateExperimentRequest = {
        remediationId: remId,
        experimentType: newExpType,
        targetKey: selectedRem ? selectedRem.targetKey : `FEATURE::${targetFeature}`,
        targetType: selectedRem ? selectedRem.targetType : "FEATURE",
        title: `Validation: ${newExpType} on ${targetFeature}`,
        description: `Controlled candidate intervention to validate remediation hypothesis.`,
        intervention: {
          feature: targetFeature,
          strategy: ablationStrategy,
          transformationType: transformType,
          lowerQuantile,
          upperQuantile,
          missingnessRate,
          baselineThreshold: baseThreshold,
          candidateThreshold: candThreshold,
          calibrationMethod,
          subgroupAttribute,
          deterministicSeed: 42,
        },
        deterministicSeed: 42,
      };

      const created = await createDiagnosticExperiment(runId, req);
      const executed = await executeDiagnosticExperiment(runId, created.id);

      setShowCreateModal(false);
      await loadData();
      setSelectedExperiment(executed);
    } catch (err: any) {
      setErrorMessage(err.message || "Failed to execute experiment.");
    } finally {
      setIsExecuting(false);
    }
  };

  const handleRunExisting = async (expId: string) => {
    try {
      setIsExecuting(true);
      const executed = await executeDiagnosticExperiment(runId, expId);
      await loadData();
      setSelectedExperiment(executed);
    } catch (err: any) {
      setErrorMessage(err.message || "Execution failed.");
    } finally {
      setIsExecuting(false);
    }
  };

  const handleCancel = async (expId: string) => {
    try {
      await cancelDiagnosticExperiment(runId, expId);
      await loadData();
    } catch (err: any) {
      setErrorMessage(err.message || "Cancellation failed.");
    }
  };

  const getConclusionBadge = (conclusion?: ExperimentConclusion) => {
    switch (conclusion) {
      case "VALIDATED":
        return <span className="px-2 py-0.5 text-[10px] font-mono font-bold bg-emerald-950/80 text-emerald-400 border border-emerald-800">VALIDATED</span>;
      case "PARTIALLY_VALIDATED":
        return <span className="px-2 py-0.5 text-[10px] font-mono font-bold bg-amber-950/80 text-amber-400 border border-amber-800">PARTIAL</span>;
      case "REJECTED":
        return <span className="px-2 py-0.5 text-[10px] font-mono font-bold bg-rose-950/80 text-rose-400 border border-rose-800">REJECTED</span>;
      case "INCONCLUSIVE":
        return <span className="px-2 py-0.5 text-[10px] font-mono font-bold bg-purple-950/80 text-purple-400 border border-purple-800">INCONCLUSIVE</span>;
      case "NOT_EXECUTABLE":
        return <span className="px-2 py-0.5 text-[10px] font-mono font-bold bg-zinc-900 text-zinc-400 border border-zinc-700">NOT EXECUTABLE</span>;
      case "FAILED":
        return <span className="px-2 py-0.5 text-[10px] font-mono font-bold bg-red-950 text-red-400 border border-red-800">FAILED</span>;
      default:
        return <span className="px-2 py-0.5 text-[10px] font-mono text-zinc-500 border border-zinc-800">PENDING</span>;
    }
  };

  const getStatusBadge = (status: ExperimentStatus) => {
    switch (status) {
      case "COMPLETED":
        return <span className="px-1.5 py-0.5 text-[10px] font-mono text-blue-400 bg-blue-950/50 border border-blue-800">COMPLETED</span>;
      case "RUNNING":
        return <span className="px-1.5 py-0.5 text-[10px] font-mono text-amber-400 bg-amber-950/50 border border-amber-800 animate-pulse">RUNNING</span>;
      case "PROPOSED":
        return <span className="px-1.5 py-0.5 text-[10px] font-mono text-zinc-400 bg-zinc-900 border border-zinc-700">PROPOSED</span>;
      case "NOT_EXECUTABLE":
        return <span className="px-1.5 py-0.5 text-[10px] font-mono text-zinc-500 bg-zinc-950 border border-zinc-800">UNMET PREREQ</span>;
      case "FAILED":
        return <span className="px-1.5 py-0.5 text-[10px] font-mono text-rose-400 bg-rose-950/50 border border-rose-800">FAILED</span>;
      case "CANCELLED":
        return <span className="px-1.5 py-0.5 text-[10px] font-mono text-zinc-500 border border-zinc-800">CANCELLED</span>;
      default:
        return <span className="px-1.5 py-0.5 text-[10px] font-mono text-zinc-400">{status}</span>;
    }
  };

  return (
    <div className="flex flex-col h-full bg-[#0a0c10] text-[#e2e8f0] font-sans overflow-hidden">
      {/* Safety & Non-Causal Advisory Banner */}
      <div className="bg-[#121620] border-b border-[#1f2533] px-4 py-2 flex items-center justify-between text-xs font-mono shrink-0">
        <div className="flex items-center space-x-2">
          <span className="inline-block w-2 h-2 rounded-full bg-blue-500"></span>
          <span className="text-[#94a3b8]">SAFETY DIRECTIVE:</span>
          <span className="text-[#cbd5e1] font-semibold">
            EXPERIMENTAL VALIDATION TESTS CONTROLLED COUNTERFACTUAL INTERVENTIONS. DOES NOT CLAIM CAUSALITY OR AUTONOMOUSLY ALTER MODELS.
          </span>
        </div>
        <button
          onClick={() => setShowCreateModal(true)}
          className="px-3 py-1 bg-blue-600 hover:bg-blue-500 text-white text-xs font-mono font-bold rounded cursor-pointer transition-colors shadow-sm"
        >
          + NEW EXPERIMENT
        </button>
      </div>

      {/* Top HUD */}
      <div className="bg-[#0e1117] border-b border-[#1f2533] px-4 py-3 grid grid-cols-6 gap-3 shrink-0">
        <div className="bg-[#141822] border border-[#1f2533] p-2.5 rounded">
          <div className="text-[10px] font-mono text-[#64748b] tracking-wider uppercase">TOTAL EXPERIMENTS</div>
          <div className="text-xl font-mono font-bold text-white mt-0.5">{hudMetrics.total}</div>
        </div>
        <div className="bg-[#141822] border border-[#1f2533] p-2.5 rounded">
          <div className="text-[10px] font-mono text-[#64748b] tracking-wider uppercase">RUNNING</div>
          <div className="text-xl font-mono font-bold text-amber-400 mt-0.5">{hudMetrics.running}</div>
        </div>
        <div className="bg-[#141822] border border-[#1f2533] p-2.5 rounded">
          <div className="text-[10px] font-mono text-[#64748b] tracking-wider uppercase">COMPLETED</div>
          <div className="text-xl font-mono font-bold text-blue-400 mt-0.5">{hudMetrics.completed}</div>
        </div>
        <div className="bg-[#141822] border border-[#1f2533] p-2.5 rounded">
          <div className="text-[10px] font-mono text-[#64748b] tracking-wider uppercase">VALIDATED</div>
          <div className="text-xl font-mono font-bold text-emerald-400 mt-0.5">{hudMetrics.validated}</div>
        </div>
        <div className="bg-[#141822] border border-[#1f2533] p-2.5 rounded">
          <div className="text-[10px] font-mono text-[#64748b] tracking-wider uppercase">REJECTED</div>
          <div className="text-xl font-mono font-bold text-rose-400 mt-0.5">{hudMetrics.rejected}</div>
        </div>
        <div className="bg-[#141822] border border-[#1f2533] p-2.5 rounded">
          <div className="text-[10px] font-mono text-[#64748b] tracking-wider uppercase">PARTIAL / INCONCLUSIVE</div>
          <div className="text-xl font-mono font-bold text-purple-400 mt-0.5">{hudMetrics.inconclusive}</div>
        </div>
      </div>

      {errorMessage && (
        <div className="bg-rose-950/80 border-b border-rose-800 text-rose-200 px-4 py-2 text-xs font-mono flex items-center justify-between">
          <span>{errorMessage}</span>
          <button onClick={() => setErrorMessage(null)} className="text-rose-400 font-bold hover:underline">DISMISS</button>
        </div>
      )}

      {/* Main Content Split Pane */}
      <div className="flex-1 flex min-h-0 overflow-hidden">
        {/* Left Column: Experiment Queue */}
        <div className="w-5/12 border-r border-[#1f2533] flex flex-col bg-[#0b0e14]">
          <div className="px-3 py-2 border-b border-[#1f2533] flex items-center justify-between bg-[#111520]">
            <span className="text-xs font-mono font-bold text-[#94a3b8]">EXPERIMENT QUEUE ({filteredExperiments.length})</span>
            <div className="flex items-center space-x-2">
              <select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
                className="bg-[#1a202c] border border-[#2d3748] text-[11px] font-mono text-zinc-300 rounded px-1.5 py-0.5"
              >
                <option value="ALL">All Statuses</option>
                <option value="PROPOSED">Proposed</option>
                <option value="COMPLETED">Completed</option>
                <option value="VALIDATED">Validated</option>
                <option value="REJECTED">Rejected</option>
                <option value="NOT_EXECUTABLE">Not Executable</option>
              </select>
              <select
                value={typeFilter}
                onChange={(e) => setTypeFilter(e.target.value)}
                className="bg-[#1a202c] border border-[#2d3748] text-[11px] font-mono text-zinc-300 rounded px-1.5 py-0.5"
              >
                <option value="ALL">All Types</option>
                {EXPERIMENT_TYPES.map((t) => (
                  <option key={t.type} value={t.type}>{t.label}</option>
                ))}
              </select>
            </div>
          </div>

          <div className="flex-1 overflow-y-auto divide-y divide-[#181d28]">
            {filteredExperiments.length === 0 ? (
              <div className="p-8 text-center text-zinc-500 font-mono text-xs">
                No experiments found. Click "+ NEW EXPERIMENT" to test a remediation hypothesis.
              </div>
            ) : (
              filteredExperiments.map((exp) => {
                const isSelected = selectedExperiment?.id === exp.id;
                return (
                  <div
                    key={exp.id}
                    onClick={() => setSelectedExperiment(exp)}
                    className={`p-3 cursor-pointer transition-colors ${
                      isSelected ? "bg-[#161c28] border-l-2 border-blue-500" : "hover:bg-[#10141e] border-l-2 border-transparent"
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex items-center space-x-2">
                        {getStatusBadge(exp.status)}
                        {getConclusionBadge(exp.conclusion)}
                      </div>
                      <span className="text-[10px] font-mono text-zinc-500">{exp.id}</span>
                    </div>

                    <div className="text-xs font-mono font-bold text-white mt-1.5 line-clamp-1">{exp.title}</div>
                    <div className="text-[11px] font-mono text-zinc-400 mt-0.5">
                      Target: <span className="text-blue-400">{exp.targetKey || "Global Model"}</span> | Type: <span className="text-zinc-300">{exp.experimentType}</span>
                    </div>

                    {exp.conclusionReason && (
                      <div className="text-[10px] font-mono text-zinc-400 mt-1.5 bg-[#0e121a] p-1.5 rounded border border-[#1f2533] line-clamp-2">
                        {exp.conclusionReason}
                      </div>
                    )}
                  </div>
                );
              })
            )}
          </div>
        </div>

        {/* Right Column: Experiment Dossier */}
        <div className="flex-1 overflow-y-auto bg-[#0a0c10] p-5">
          {selectedExperiment ? (
            <div className="space-y-6">
              {/* Header */}
              <div className="border border-[#1f2533] bg-[#111520] p-4 rounded">
                <div className="flex items-center justify-between">
                  <div className="flex items-center space-x-2">
                    <span className="text-xs font-mono text-zinc-400">EXPERIMENT DOSSIER:</span>
                    <span className="text-xs font-mono font-bold text-blue-400">{selectedExperiment.id}</span>
                    {getStatusBadge(selectedExperiment.status)}
                    {getConclusionBadge(selectedExperiment.conclusion)}
                  </div>
                  <div className="flex items-center space-x-2">
                    {selectedExperiment.status === "PROPOSED" && (
                      <button
                        onClick={() => handleRunExisting(selectedExperiment.id)}
                        disabled={isExecuting}
                        className="px-3 py-1 bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-mono font-bold rounded cursor-pointer transition-colors"
                      >
                        {isExecuting ? "EXECUTING..." : "EXECUTE EXPERIMENT"}
                      </button>
                    )}
                    {selectedExperiment.status === "RUNNING" && (
                      <button
                        onClick={() => handleCancel(selectedExperiment.id)}
                        className="px-3 py-1 bg-rose-700 hover:bg-rose-600 text-white text-xs font-mono font-bold rounded cursor-pointer"
                      >
                        CANCEL
                      </button>
                    )}
                  </div>
                </div>

                <h1 className="text-base font-mono font-bold text-white mt-2">{selectedExperiment.title}</h1>
                <p className="text-xs text-zinc-300 font-mono mt-1">{selectedExperiment.description}</p>

                {/* Pipeline Flow Visualization */}
                <div className="mt-4 pt-3 border-t border-[#1f2533] flex items-center space-x-2 text-[10px] font-mono">
                  <span className="px-2 py-1 bg-zinc-900 border border-zinc-700 text-zinc-300">BASELINE: {selectedExperiment.baselineRunId}</span>
                  <span className="text-zinc-600">→</span>
                  <span className="px-2 py-1 bg-blue-950/60 border border-blue-800 text-blue-300">INTERVENTION: {selectedExperiment.experimentType}</span>
                  <span className="text-zinc-600">→</span>
                  <span className="px-2 py-1 bg-purple-950/60 border border-purple-800 text-purple-300">
                    CANDIDATE: {selectedExperiment.candidateRunId || "PENDING"}
                  </span>
                  <span className="text-zinc-600">→</span>
                  <span className="px-2 py-1 bg-emerald-950/60 border border-emerald-800 text-emerald-300">
                    STATUS: {selectedExperiment.conclusion || "EVALUATING"}
                  </span>
                </div>
              </div>

              {/* Conclusion & Rationale Banner */}
              {selectedExperiment.conclusion && (
                <div className={`p-4 border rounded font-mono ${
                  selectedExperiment.conclusion === "VALIDATED"
                    ? "bg-emerald-950/40 border-emerald-800 text-emerald-200"
                    : selectedExperiment.conclusion === "REJECTED"
                    ? "bg-rose-950/40 border-rose-800 text-rose-200"
                    : selectedExperiment.conclusion === "PARTIALLY_VALIDATED"
                    ? "bg-amber-950/40 border-amber-800 text-amber-200"
                    : "bg-zinc-900 border-zinc-700 text-zinc-300"
                }`}>
                  <div className="text-xs font-bold uppercase tracking-wide">
                    EXPERIMENT CONCLUSION: {selectedExperiment.conclusion}
                  </div>
                  <div className="text-xs mt-1">
                    {selectedExperiment.conclusionReason || "Controlled empirical hypothesis testing completed."}
                  </div>
                </div>
              )}

              {/* Acceptance Criteria & Regression Guards */}
              <div className="grid grid-cols-2 gap-4">
                {/* Acceptance Criteria */}
                <div className="border border-[#1f2533] bg-[#111520] p-3.5 rounded">
                  <div className="text-xs font-mono font-bold text-zinc-300 border-b border-[#1f2533] pb-1.5 flex items-center justify-between">
                    <span>ACCEPTANCE CRITERIA</span>
                    <span className="text-[10px] text-zinc-500 font-normal">Empirical Validation Targets</span>
                  </div>
                  <div className="mt-2.5 space-y-2">
                    {selectedExperiment.acceptanceResults && selectedExperiment.acceptanceResults.length > 0 ? (
                      selectedExperiment.acceptanceResults.map((ar, idx) => (
                        <div key={idx} className="bg-[#0b0e14] p-2 rounded border border-[#1c2230] text-xs font-mono">
                          <div className="flex items-center justify-between">
                            <span className="text-zinc-200 font-semibold">{ar.criterion}</span>
                            <span className={`px-1.5 py-0.5 text-[10px] font-bold ${ar.passed ? "text-emerald-400 bg-emerald-950 border border-emerald-800" : "text-rose-400 bg-rose-950 border border-rose-800"}`}>
                              {ar.passed ? "PASSED" : "FAILED"}
                            </span>
                          </div>
                          <div className="text-[11px] text-zinc-400 mt-1">{ar.reason}</div>
                        </div>
                      ))
                    ) : (
                      <div className="text-xs font-mono text-zinc-500">No explicit acceptance criteria evaluated.</div>
                    )}
                  </div>
                </div>

                {/* Regression Guards */}
                <div className="border border-[#1f2533] bg-[#111520] p-3.5 rounded">
                  <div className="text-xs font-mono font-bold text-zinc-300 border-b border-[#1f2533] pb-1.5 flex items-center justify-between">
                    <span>REGRESSION GUARDS</span>
                    <span className="text-[10px] text-zinc-500 font-normal">Safety Invariants</span>
                  </div>
                  <div className="mt-2.5 space-y-2">
                    {selectedExperiment.regressionResults && selectedExperiment.regressionResults.length > 0 ? (
                      selectedExperiment.regressionResults.map((rg, idx) => (
                        <div key={idx} className="bg-[#0b0e14] p-2 rounded border border-[#1c2230] text-xs font-mono">
                          <div className="flex items-center justify-between">
                            <span className="text-zinc-200 font-semibold">{rg.guard}</span>
                            <span className={`px-1.5 py-0.5 text-[10px] font-bold ${rg.passed ? "text-emerald-400 bg-emerald-950 border border-emerald-800" : "text-rose-400 bg-rose-950 border border-rose-800"}`}>
                              {rg.passed ? "PASSED" : "FAILED"}
                            </span>
                          </div>
                          <div className="text-[11px] text-zinc-400 mt-1">{rg.reason}</div>
                        </div>
                      ))
                    ) : (
                      <div className="text-xs font-mono text-zinc-500">No explicit regression guards evaluated.</div>
                    )}
                  </div>
                </div>
              </div>

              {/* Metric Deltas Table */}
              {selectedExperiment.metricDeltas && selectedExperiment.metricDeltas.length > 0 && (
                <div className="border border-[#1f2533] bg-[#111520] p-3.5 rounded">
                  <div className="text-xs font-mono font-bold text-zinc-300 border-b border-[#1f2533] pb-1.5 flex items-center justify-between">
                    <span>BEFORE / AFTER METRIC COMPARISON</span>
                    <span className="text-[10px] text-zinc-500 font-normal">Baseline ({selectedExperiment.baselineRunId}) vs Candidate</span>
                  </div>

                  <table className="w-full mt-2 text-left font-mono text-xs border-collapse">
                    <thead>
                      <tr className="border-b border-[#1f2533] text-[#64748b] text-[10px] uppercase">
                        <th className="py-1.5 px-2">Metric</th>
                        <th className="py-1.5 px-2">Baseline</th>
                        <th className="py-1.5 px-2">Candidate</th>
                        <th className="py-1.5 px-2">Delta (Δ)</th>
                        <th className="py-1.5 px-2">Direction</th>
                        <th className="py-1.5 px-2">Assessment</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-[#181d28]">
                      {selectedExperiment.metricDeltas.map((item, idx) => (
                        <tr key={idx} className="hover:bg-[#161c28]">
                          <td className="py-1.5 px-2 font-semibold text-white">{item.metricName}</td>
                          <td className="py-1.5 px-2 text-zinc-400">{item.baselineValue?.toFixed(3) ?? "N/A"}</td>
                          <td className="py-1.5 px-2 text-zinc-200">{item.candidateValue?.toFixed(3) ?? "N/A"}</td>
                          <td className="py-1.5 px-2 text-zinc-300">
                            {item.delta !== undefined ? (item.delta > 0 ? `+${item.delta.toFixed(3)}` : item.delta.toFixed(3)) : "N/A"}
                          </td>
                          <td className="py-1.5 px-2 text-[11px] text-zinc-400">{item.direction}</td>
                          <td className="py-1.5 px-2">
                            <span className={`px-1.5 py-0.5 text-[10px] font-bold ${
                              item.assessment === "IMPROVED" ? "text-emerald-400 bg-emerald-950/60 border border-emerald-800" :
                              item.assessment === "REGRESSED" ? "text-rose-400 bg-rose-950/60 border border-rose-800" :
                              "text-zinc-400 bg-zinc-900 border border-zinc-700"
                            }`}>
                              {item.assessment}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}

              {/* Paired Statistical Evidence */}
              {selectedExperiment.statisticalEvidence && (
                <div className="border border-[#1f2533] bg-[#111520] p-3.5 rounded">
                  <div className="text-xs font-mono font-bold text-zinc-300 border-b border-[#1f2533] pb-1.5 flex items-center justify-between">
                    <span>PAIRED STATISTICAL EVIDENCE</span>
                    <span className="text-[10px] text-zinc-500 font-normal">Seed = {selectedExperiment.statisticalEvidence.deterministicSeed ?? 42}</span>
                  </div>

                  <div className="grid grid-cols-4 gap-3 mt-3">
                    <div className="bg-[#0b0e14] p-2.5 rounded border border-[#1f2533]">
                      <div className="text-[10px] font-mono text-zinc-500">PREDICTION FLIP RATE</div>
                      <div className="text-base font-mono font-bold text-blue-400 mt-0.5">
                        {selectedExperiment.statisticalEvidence.predictionFlipRate !== undefined
                          ? `${(selectedExperiment.statisticalEvidence.predictionFlipRate * 100).toFixed(1)}%`
                          : "0.0%"}
                      </div>
                    </div>
                    <div className="bg-[#0b0e14] p-2.5 rounded border border-[#1f2533]">
                      <div className="text-[10px] font-mono text-zinc-500">MCNEMAR STATISTIC (χ²)</div>
                      <div className="text-base font-mono font-bold text-zinc-200 mt-0.5">
                        {selectedExperiment.statisticalEvidence.mcNemarStatistic?.toFixed(3) ?? "N/A"}
                      </div>
                    </div>
                    <div className="bg-[#0b0e14] p-2.5 rounded border border-[#1f2533]">
                      <div className="text-[10px] font-mono text-zinc-500">MCNEMAR P-VALUE</div>
                      <div className="text-base font-mono font-bold text-zinc-200 mt-0.5">
                        {selectedExperiment.statisticalEvidence.mcNemarPValue?.toFixed(4) ?? "N/A"}
                      </div>
                    </div>
                    <div className="bg-[#0b0e14] p-2.5 rounded border border-[#1f2533]">
                      <div className="text-[10px] font-mono text-zinc-500">PROB SHIFT 95% CI</div>
                      <div className="text-xs font-mono text-zinc-300 mt-1">
                        [{selectedExperiment.statisticalEvidence.probShiftCiLower?.toFixed(3) ?? "0.000"},{" "}
                        {selectedExperiment.statisticalEvidence.probShiftCiUpper?.toFixed(3) ?? "0.000"}]
                      </div>
                    </div>
                  </div>

                  {/* Subgroup table if available */}
                  {selectedExperiment.statisticalEvidence.subgroups && selectedExperiment.statisticalEvidence.subgroups.length > 0 && (
                    <div className="mt-3 pt-2 border-t border-[#1f2533]">
                      <div className="text-[10px] font-mono text-zinc-400 mb-1">SUBGROUP BREAKDOWN</div>
                      <div className="grid grid-cols-2 gap-2">
                        {selectedExperiment.statisticalEvidence.subgroups.map((sg, idx) => (
                          <div key={idx} className="bg-[#0b0e14] p-2 rounded border border-[#1c2230] text-[11px] font-mono">
                            <div className="font-bold text-white">{sg.group} (N={sg.sampleSize})</div>
                            <div className="text-zinc-400 mt-0.5">
                              Baseline F1: {sg.baselineF1.toFixed(3)} → Candidate: {sg.candidateF1.toFixed(3)} | Flip Rate: {(sg.flipRate * 100).toFixed(1)}%
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              )}

              {/* Provenance Trace */}
              <div className="border border-[#1f2533] bg-[#111520] p-3.5 rounded">
                <div className="text-xs font-mono font-bold text-zinc-300 border-b border-[#1f2533] pb-1.5 flex items-center justify-between">
                  <span>PROVENANCE & AUDIT TRACE</span>
                  <span className="text-[10px] text-zinc-500 font-normal">Source Verification</span>
                </div>

                <div className="mt-2.5 grid grid-cols-2 gap-3 text-xs font-mono">
                  <div className="bg-[#0b0e14] p-2 rounded border border-[#1c2230]">
                    <span className="text-zinc-500 text-[10px]">SOURCE REMEDIATION:</span>
                    <div className="text-zinc-200 mt-0.5">
                      {selectedExperiment.remediationId ? `Remediation #${selectedExperiment.remediationId}` : "Direct Hypothesis"}
                    </div>
                  </div>
                  <div className="bg-[#0b0e14] p-2 rounded border border-[#1c2230]">
                    <span className="text-zinc-500 text-[10px]">SOURCE TARGET:</span>
                    <div className="text-blue-400 mt-0.5">
                      {selectedExperiment.targetKey || "Global Model"}
                    </div>
                  </div>
                </div>
              </div>
            </div>
          ) : (
            <div className="flex flex-col items-center justify-center h-64 text-zinc-500 font-mono text-xs">
              <span>Select an experiment from the queue or click "+ NEW EXPERIMENT"</span>
            </div>
          )}
        </div>
      </div>

      {/* New Experiment Modal */}
      {showCreateModal && (
        <div className="fixed inset-0 bg-black/80 flex items-center justify-center p-4 z-50">
          <div className="bg-[#111520] border border-[#2d3748] rounded-lg max-w-xl w-full p-5 shadow-2xl font-mono text-xs">
            <div className="flex items-center justify-between border-b border-[#1f2533] pb-2">
              <span className="font-bold text-white text-sm">LAUNCH CONTROLLED EXPERIMENT</span>
              <button onClick={() => setShowCreateModal(false)} className="text-zinc-400 hover:text-white font-bold cursor-pointer">✕</button>
            </div>

            <div className="mt-4 space-y-4">
              {/* Select Experiment Type */}
              <div>
                <label className="text-zinc-400 text-[10px] uppercase font-bold block mb-1">EXPERIMENT TYPE</label>
                <select
                  value={newExpType}
                  onChange={(e) => setNewExpType(e.target.value as ExperimentType)}
                  className="w-full bg-[#1a202c] border border-[#2d3748] text-zinc-200 p-2 rounded text-xs"
                >
                  {EXPERIMENT_TYPES.map((t) => (
                    <option key={t.type} value={t.type}>{t.label} - {t.desc}</option>
                  ))}
                </select>
              </div>

              {/* Link Remediation (Optional) */}
              <div>
                <label className="text-zinc-400 text-[10px] uppercase font-bold block mb-1">LINK REMEDIATION HYPOTHESIS</label>
                <select
                  value={selectedRemediationId}
                  onChange={(e) => {
                    setSelectedRemediationId(e.target.value);
                    const rem = remediations.find((r) => r.id === parseInt(e.target.value));
                    if (rem && rem.targetKey.startsWith("FEATURE::")) {
                      setTargetFeature(rem.targetKey.replace("FEATURE::", ""));
                    }
                  }}
                  className="w-full bg-[#1a202c] border border-[#2d3748] text-zinc-200 p-2 rounded text-xs"
                >
                  <option value="">Direct Counterfactual Experiment (No Linked Remediation)</option>
                  {remediations.map((r) => (
                    <option key={r.id} value={r.id}>
                      #{r.id} [{r.priority}] {r.remediationType} ({r.targetKey})
                    </option>
                  ))}
                </select>
              </div>

              {/* Target Feature */}
              {newExpType !== "THRESHOLD_COUNTERFACTUAL" && newExpType !== "CALIBRATION_COUNTERFACTUAL" && (
                <div>
                  <label className="text-zinc-400 text-[10px] uppercase font-bold block mb-1">TARGET FEATURE</label>
                  <input
                    type="text"
                    value={targetFeature}
                    onChange={(e) => setTargetFeature(e.target.value)}
                    className="w-full bg-[#1a202c] border border-[#2d3748] text-zinc-200 p-2 rounded text-xs"
                    placeholder="e.g. drifted_feature"
                  />
                </div>
              )}

              {/* Specific params by type */}
              {newExpType === "FEATURE_ABLATION" && (
                <div>
                  <label className="text-zinc-400 text-[10px] uppercase font-bold block mb-1">ABLATION STRATEGY</label>
                  <select
                    value={ablationStrategy}
                    onChange={(e) => setAblationStrategy(e.target.value as any)}
                    className="w-full bg-[#1a202c] border border-[#2d3748] text-zinc-200 p-2 rounded text-xs"
                  >
                    <option value="zero">Zero Out Feature Values</option>
                    <option value="median">Impute with Feature Median</option>
                    <option value="drop">Drop Column Entirely (if model supports)</option>
                  </select>
                </div>
              )}

              {newExpType === "FEATURE_TRANSFORMATION" && (
                <div className="grid grid-cols-3 gap-2">
                  <div>
                    <label className="text-zinc-400 text-[10px] uppercase font-bold block mb-1">TRANSFORM</label>
                    <select
                      value={transformType}
                      onChange={(e) => setTransformType(e.target.value as any)}
                      className="w-full bg-[#1a202c] border border-[#2d3748] text-zinc-200 p-2 rounded text-xs"
                    >
                      <option value="CLIP">Quantile Clip</option>
                      <option value="WINSORIZE">Winsorize</option>
                      <option value="STANDARDIZE">Standardize (Z-Score)</option>
                    </select>
                  </div>
                  <div>
                    <label className="text-zinc-400 text-[10px] uppercase font-bold block mb-1">LOWER Q</label>
                    <input
                      type="number"
                      step="0.01"
                      value={lowerQuantile}
                      onChange={(e) => setLowerQuantile(parseFloat(e.target.value))}
                      className="w-full bg-[#1a202c] border border-[#2d3748] text-zinc-200 p-2 rounded text-xs"
                    />
                  </div>
                  <div>
                    <label className="text-zinc-400 text-[10px] uppercase font-bold block mb-1">UPPER Q</label>
                    <input
                      type="number"
                      step="0.01"
                      value={upperQuantile}
                      onChange={(e) => setUpperQuantile(parseFloat(e.target.value))}
                      className="w-full bg-[#1a202c] border border-[#2d3748] text-zinc-200 p-2 rounded text-xs"
                    />
                  </div>
                </div>
              )}

              {newExpType === "MISSING_VALUE_STRESS" && (
                <div>
                  <label className="text-zinc-400 text-[10px] uppercase font-bold block mb-1">MISSINGNESS INJECTION RATE</label>
                  <select
                    value={missingnessRate}
                    onChange={(e) => setMissingnessRate(parseFloat(e.target.value))}
                    className="w-full bg-[#1a202c] border border-[#2d3748] text-zinc-200 p-2 rounded text-xs"
                  >
                    <option value="0.05">5% Missing Values</option>
                    <option value="0.10">10% Missing Values</option>
                    <option value="0.20">20% Missing Values</option>
                  </select>
                </div>
              )}

              {newExpType === "THRESHOLD_COUNTERFACTUAL" && (
                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <label className="text-zinc-400 text-[10px] uppercase font-bold block mb-1">BASELINE THRESHOLD</label>
                    <input
                      type="number"
                      step="0.05"
                      value={baseThreshold}
                      onChange={(e) => setBaseThreshold(parseFloat(e.target.value))}
                      className="w-full bg-[#1a202c] border border-[#2d3748] text-zinc-200 p-2 rounded text-xs"
                    />
                  </div>
                  <div>
                    <label className="text-zinc-400 text-[10px] uppercase font-bold block mb-1">CANDIDATE THRESHOLD</label>
                    <input
                      type="number"
                      step="0.05"
                      value={candThreshold}
                      onChange={(e) => setCandThreshold(parseFloat(e.target.value))}
                      className="w-full bg-[#1a202c] border border-[#2d3748] text-zinc-200 p-2 rounded text-xs"
                    />
                  </div>
                </div>
              )}

              {newExpType === "CALIBRATION_COUNTERFACTUAL" && (
                <div>
                  <label className="text-zinc-400 text-[10px] uppercase font-bold block mb-1">CALIBRATION METHOD</label>
                  <select
                    value={calibrationMethod}
                    onChange={(e) => setCalibrationMethod(e.target.value as any)}
                    className="w-full bg-[#1a202c] border border-[#2d3748] text-zinc-200 p-2 rounded text-xs"
                  >
                    <option value="PLATT">Platt Scaling (Logistic Regression)</option>
                    <option value="ISOTONIC">Isotonic Regression</option>
                  </select>
                </div>
              )}
            </div>

            <div className="mt-6 flex items-center justify-end space-x-2 border-t border-[#1f2533] pt-3">
              <button
                onClick={() => setShowCreateModal(false)}
                className="px-3 py-1.5 bg-zinc-800 hover:bg-zinc-700 text-zinc-300 rounded cursor-pointer"
              >
                Cancel
              </button>
              <button
                onClick={handleLaunchExperiment}
                disabled={isExecuting}
                className="px-4 py-1.5 bg-blue-600 hover:bg-blue-500 text-white font-bold rounded cursor-pointer shadow"
              >
                {isExecuting ? "Executing..." : "Launch & Evaluate"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
