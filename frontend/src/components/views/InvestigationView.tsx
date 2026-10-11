"use client";

import React, { useState, useEffect, useMemo } from "react";
import {
  InvestigationTarget,
  InvestigationDossier,
  EvidenceGraph,
  EvidenceGraphNode,
  EvidenceGraphEdge,
  NavSection,
} from "@/types/diagnostics";
import {
  getDiagnosticInvestigations,
  getDiagnosticInvestigationDossier,
  getDiagnosticEvidenceGraph,
  recalculateDiagnosticCorrelations,
  RunSummary,
} from "@/lib/api";

export interface InvestigationViewProps {
  runId: string;
  runSummary?: RunSummary | null;
  onSelectSection?: (section: NavSection) => void;
  onSelectFeature?: (featureName: string) => void;
  onRefresh?: () => void;
}

const MODULE_COLORS: Record<string, { bg: string; text: string; border: string; hex: string }> = {
  DRIFT: { bg: "bg-[#F18B78]/15", text: "text-[#F18B78]", border: "border-[#F18B78]/40", hex: "#F18B78" },
  EXPLAINABILITY: { bg: "bg-[#C490D1]/15", text: "text-[#C490D1]", border: "border-[#C490D1]/40", hex: "#C490D1" },
  ERROR_FORENSICS: { bg: "bg-[#F18B78]/15", text: "text-[#F18B78]", border: "border-[#F18B78]/40", hex: "#F18B78" },
  ROBUSTNESS: { bg: "bg-[#E8B66D]/15", text: "text-[#E8B66D]", border: "border-[#E8B66D]/40", hex: "#E8B66D" },
  DATA_QUALITY: { bg: "bg-[#E8B66D]/15", text: "text-[#E8B66D]", border: "border-[#E8B66D]/40", hex: "#E8B66D" },
  LEAKAGE: { bg: "bg-[#F18B78]/15", text: "text-[#F18B78]", border: "border-[#F18B78]/40", hex: "#F18B78" },
  BIAS: { bg: "bg-[#B7D7A8]/15", text: "text-[#B7D7A8]", border: "border-[#B7D7A8]/40", hex: "#B7D7A8" },
  PERFORMANCE: { bg: "bg-[#B7ACBD]/15", text: "text-[#F4EFF6]", border: "border-[#B7ACBD]/40", hex: "#B7ACBD" },
};

const DEFAULT_COLOR = { bg: "bg-[#332B3C]", text: "text-[#B7ACBD]", border: "border-[#443849]", hex: "#8E8295" };

export const InvestigationView: React.FC<InvestigationViewProps> = ({
  runId,
  runSummary,
  onSelectSection,
  onSelectFeature,
  onRefresh,
}) => {
  const [targets, setTargets] = useState<InvestigationTarget[]>([]);
  const [graph, setGraph] = useState<EvidenceGraph | null>(null);
  const [selectedTargetKey, setSelectedTargetKey] = useState<string | null>(null);
  const [dossier, setDossier] = useState<InvestigationDossier | null>(null);
  const [selectedEdge, setSelectedEdge] = useState<EvidenceGraphEdge | null>(null);
  const [selectedNode, setSelectedNode] = useState<EvidenceGraphNode | null>(null);
  const [activeTab, setActiveTab] = useState<"QUEUE" | "GRAPH" | "MATRIX">("QUEUE");
  const [priorityFilter, setPriorityFilter] = useState<string>("ALL");
  const [typeFilter, setTypeFilter] = useState<string>("ALL");
  const [searchQuery, setSearchQuery] = useState<string>("");
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isLoadingDossier, setIsLoadingDossier] = useState<boolean>(false);
  const [isRecalculating, setIsRecalculating] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [zoomLevel, setZoomLevel] = useState<number>(1);

  // Load targets and evidence graph
  const fetchData = async () => {
    if (!runId) return;
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const [targetsData, graphData] = await Promise.all([
        getDiagnosticInvestigations(runId).catch(() => []),
        getDiagnosticEvidenceGraph(runId).catch(() => ({ runId, nodes: [], edges: [] })),
      ]);
      setTargets(targetsData || []);
      setGraph(graphData || { runId, nodes: [], edges: [] });
      if (targetsData && targetsData.length > 0 && !selectedTargetKey) {
        setSelectedTargetKey(targetsData[0].targetKey);
      }
    } catch (err: any) {
      setErrorMessage(err.message || "Failed to load root-cause investigation data");
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, [runId]);

  // Load Dossier when selected target changes
  useEffect(() => {
    if (!runId || !selectedTargetKey) {
      setDossier(null);
      return;
    }
    let isMounted = true;
    setIsLoadingDossier(true);
    getDiagnosticInvestigationDossier(runId, selectedTargetKey)
      .then((data) => {
        if (isMounted) setDossier(data);
      })
      .catch(() => {
        if (isMounted) setDossier(null);
      })
      .finally(() => {
        if (isMounted) setIsLoadingDossier(false);
      });

    return () => {
      isMounted = false;
    };
  }, [runId, selectedTargetKey]);

  // Recalculate handler
  const handleRecalculate = async () => {
    if (!runId) return;
    setIsRecalculating(true);
    try {
      await recalculateDiagnosticCorrelations(runId);
      await fetchData();
      if (onRefresh) onRefresh();
    } catch (err: any) {
      alert(`Recalculation error: ${err.message || err}`);
    } finally {
      setIsRecalculating(false);
    }
  };

  // Filtered targets
  const filteredTargets = useMemo(() => {
    return targets.filter((t) => {
      if (priorityFilter !== "ALL" && t.priority !== priorityFilter) return false;
      if (typeFilter !== "ALL" && t.targetType !== typeFilter) return false;
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase();
        const matchKey = t.targetKey.toLowerCase().includes(q);
        const matchName = t.displayName.toLowerCase().includes(q);
        const matchHypo = t.hypothesis.toLowerCase().includes(q);
        if (!matchKey && !matchName && !matchHypo) return false;
      }
      return true;
    });
  }, [targets, priorityFilter, typeFilter, searchQuery]);

  // Priority color styling helper
  const getPriorityStyle = (priority: string) => {
    switch (priority) {
      case "CRITICAL":
        return { badge: "bg-[#F18B78] text-[#211D29] font-bold", border: "border-[#F18B78]/40", text: "text-[#F18B78]" };
      case "HIGH":
        return { badge: "bg-[#F18B78]/80 text-[#211D29] font-bold", border: "border-[#F18B78]/40", text: "text-[#F18B78]" };
      case "MEDIUM":
        return { badge: "bg-[#E8B66D] text-[#211D29] font-bold", border: "border-[#E8B66D]/40", text: "text-[#E8B66D]" };
      case "LOW":
        return { badge: "bg-[#B7D7A8] text-[#211D29] font-bold", border: "border-[#B7D7A8]/40", text: "text-[#B7D7A8]" };
      case "INFO":
      default:
        return { badge: "bg-[#332B3C] text-[#F4EFF6]", border: "border-[#443849]", text: "text-[#B7ACBD]" };
    }
  };

  // SVG graph layout calculation
  const graphLayout = useMemo(() => {
    if (!graph || !graph.nodes || graph.nodes.length === 0) {
      return { nodes: [], edges: [], width: 900, height: 600 };
    }

    const width = 960;
    const height = 620;
    const nodes = [...graph.nodes];
    const edges = [...graph.edges];

    // Group nodes by type for layered positioning
    const moduleNodes = nodes.filter((n) => n.nodeType === "MODULE");
    const targetNodes = nodes.filter((n) => ["FEATURE", "SUBGROUP", "BEHAVIOR", "ERROR_TYPE"].includes(n.nodeType));
    const findingNodes = nodes.filter((n) => n.nodeType === "FINDING");
    const metricNodes = nodes.filter((n) => ["METRIC", "ERROR"].includes(n.nodeType));

    const positions: Record<string, { x: number; y: number }> = {};

    // 1. MODULES: top row
    const modCount = Math.max(moduleNodes.length, 1);
    moduleNodes.forEach((node, idx) => {
      positions[node.id] = {
        x: 80 + ((width - 160) / (modCount + 1)) * (idx + 1),
        y: 65,
      };
    });

    // 2. FINDINGS: mid-upper row
    const findCount = Math.max(findingNodes.length, 1);
    findingNodes.forEach((node, idx) => {
      positions[node.id] = {
        x: 90 + ((width - 180) / (findCount + 1)) * (idx + 1),
        y: 190,
      };
    });

    // 3. TARGETS (Features/Subgroups/Behaviors): center focus
    const targCount = Math.max(targetNodes.length, 1);
    targetNodes.forEach((node, idx) => {
      positions[node.id] = {
        x: 80 + ((width - 160) / (targCount + 1)) * (idx + 1),
        y: 340,
      };
    });

    // 4. METRICS / ERRORS: bottom row
    const metricCount = Math.max(metricNodes.length, 1);
    metricNodes.forEach((node, idx) => {
      positions[node.id] = {
        x: 80 + ((width - 160) / (metricCount + 1)) * (idx + 1),
        y: 490,
      };
    });

    // Positioned nodes
    const positionedNodes = nodes.map((node) => ({
      ...node,
      x: positions[node.id]?.x || (node.x ?? width / 2),
      y: positions[node.id]?.y || (node.y ?? height / 2),
    }));

    return { nodes: positionedNodes, edges, width, height };
  }, [graph]);

  return (
    <div className="space-y-4 font-mono text-xs text-[#F4EFF6] pb-12">
      {/* Non-Causality Disclaimer Banner */}
      <div className="px-3.5 py-2 bg-[#2A2432] border border-[#443849] flex items-center justify-between text-[11px] rounded">
        <div className="flex items-center gap-2">
          <span className="px-1.5 py-0.2 bg-[#F18B78]/20 text-[#F18B78] border border-[#F18B78]/50 rounded font-bold text-[9px]">
            NOTICE
          </span>
          <span className="text-[#B7ACBD] font-semibold">
            INVESTIGATION RELATIONSHIPS ARE ASSOCIATIVE. THE SYSTEM DOES NOT ESTABLISH CAUSALITY.
          </span>
        </div>
        <div className="text-[10px] text-[#8E8295]">
          PHASE 6 ROOT-CAUSE INVESTIGATION
        </div>
      </div>

      {/* Top Engineering Metrics HUD */}
      <div className="p-3.5 bg-[#2A2432] border border-[#443849] rounded space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-[#443849] pb-3">
          <div className="flex items-center gap-3">
            <span className="text-sm font-bold text-[#F4EFF6] tracking-wide uppercase">
              ROOT-CAUSE INVESTIGATION & EVIDENCE GRAPH
            </span>
            <span className="px-2 py-0.5 text-[10px] font-bold bg-[#332B3C] text-[#F18B78] border border-[#443849] rounded">
              RUN: {runId}
            </span>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={handleRecalculate}
              disabled={isRecalculating}
              className="px-3 py-1 bg-[#F18B78] hover:bg-[#F39C8B] text-[#211D29] rounded font-bold transition-colors cursor-pointer disabled:opacity-50"
            >
              {isRecalculating ? "⟳ RECALCULATING..." : "⟳ RECALCULATE INVESTIGATIONS"}
            </button>
          </div>
        </div>

        {/* HUD Counters */}
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-6 gap-2">
          <div className="p-2 bg-[#332B3C] border border-[#443849] rounded">
            <div className="text-[10px] text-[#8E8295]">TARGETS</div>
            <div className="text-base font-bold text-[#F4EFF6] tabular-nums">
              {targets.length}
            </div>
          </div>
          <div className="p-2 bg-[#332B3C] border border-[#443849] rounded">
            <div className="text-[10px] text-[#F18B78]">CRITICAL TARGETS</div>
            <div className="text-base font-bold text-[#F18B78] tabular-nums">
              {targets.filter((t) => t.priority === "CRITICAL").length}
            </div>
          </div>
          <div className="p-2 bg-[#332B3C] border border-[#443849] rounded">
            <div className="text-[10px] text-[#E8B66D]">HIGH TARGETS</div>
            <div className="text-base font-bold text-[#E8B66D] tabular-nums">
              {targets.filter((t) => t.priority === "HIGH").length}
            </div>
          </div>
          <div className="p-2 bg-[#332B3C] border border-[#443849] rounded">
            <div className="text-[10px] text-[#8E8295]">GRAPH NODES</div>
            <div className="text-base font-bold text-[#B7ACBD] tabular-nums">
              {graph?.nodes?.length ?? 0}
            </div>
          </div>
          <div className="p-2 bg-[#332B3C] border border-[#443849] rounded">
            <div className="text-[10px] text-[#8E8295]">EVIDENCE EDGES</div>
            <div className="text-base font-bold text-[#B7ACBD] tabular-nums">
              {graph?.edges?.length ?? 0}
            </div>
          </div>
          <div className="p-2 bg-[#332B3C] border border-[#443849] rounded">
            <div className="text-[10px] text-[#8E8295]">TOP TARGET</div>
            <div className="text-xs font-bold text-[#F4EFF6] truncate" title={targets[0]?.targetKey || "NONE"}>
              {targets[0]?.displayName || "NONE"}
            </div>
          </div>
        </div>

        {/* View Tabs */}
        <div className="flex items-center gap-2 pt-1 border-t border-[#443849]">
          <button
            onClick={() => setActiveTab("QUEUE")}
            className={`px-3 py-1 font-bold text-xs rounded transition-colors cursor-pointer ${
              activeTab === "QUEUE"
                ? "bg-[#F18B78] text-[#211D29]"
                : "bg-[#332B3C] text-[#B7ACBD] hover:text-[#F4EFF6] border border-[#443849]"
            }`}
          >
            01 INVESTIGATION QUEUE & DOSSIER ({targets.length})
          </button>
          <button
            onClick={() => setActiveTab("GRAPH")}
            className={`px-3 py-1 font-bold text-xs rounded transition-colors cursor-pointer ${
              activeTab === "GRAPH"
                ? "bg-[#F18B78] text-[#211D29]"
                : "bg-[#332B3C] text-[#B7ACBD] hover:text-[#F4EFF6] border border-[#443849]"
            }`}
          >
            02 EVIDENCE GRAPH ({graph?.edges?.length ?? 0} EDGES)
          </button>
          <button
            onClick={() => setActiveTab("MATRIX")}
            className={`px-3 py-1 font-bold text-xs rounded transition-colors cursor-pointer ${
              activeTab === "MATRIX"
                ? "bg-[#F18B78] text-[#211D29]"
                : "bg-[#332B3C] text-[#B7ACBD] hover:text-[#F4EFF6] border border-[#443849]"
            }`}
          >
            03 FORENSIC INVESTIGATION MATRIX
          </button>
        </div>
      </div>

      {/* Loading and Error States */}
      {isLoading && (
        <div className="p-12 text-center text-[#8E8295] bg-[#2A2432] border border-[#443849] rounded">
          <div className="animate-pulse font-bold text-[#F4EFF6] text-sm mb-1">
            CONSTRUCTING EVIDENCE GRAPH & INVESTIGATION DOSSIERS...
          </div>
          <div>Synthesizing cross-module signals, calculating graph connectivity, and building ranked hypotheses</div>
        </div>
      )}

      {errorMessage && !isLoading && (
        <div className="p-4 bg-[#F18B78]/15 border border-[#F18B78]/40 text-[#F18B78] rounded space-y-1">
          <div className="font-bold">INVESTIGATION INGESTION ERROR</div>
          <div className="text-xs">{errorMessage}</div>
        </div>
      )}

      {!isLoading && !errorMessage && targets.length === 0 && (
        <div className="p-12 text-center text-[#8E8295] bg-[#2A2432] border border-[#443849] rounded space-y-2">
          <div className="text-[#F4EFF6] font-bold text-sm">NO INVESTIGATION TARGETS DETECTED</div>
          <p className="max-w-md mx-auto text-xs text-[#B7ACBD]">
            Either all diagnostic modules completed with nominal metrics, or insufficient cross-module evidence was produced to generate high-priority investigation targets.
          </p>
        </div>
      )}

      {/* TAB 1: INVESTIGATION QUEUE & DOSSIER */}
      {!isLoading && targets.length > 0 && activeTab === "QUEUE" && (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
          {/* Left Column: Ranked Queue (5 cols) */}
          <div className="lg:col-span-5 space-y-3">
            {/* Filter Bar */}
            <div className="p-2.5 bg-[#2A2432] border border-[#443849] rounded flex flex-wrap gap-2 items-center">
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search target, feature, hypothesis..."
                className="flex-1 min-w-[140px] px-2 py-1 bg-[#1C1824] border border-[#443849] rounded text-[#F4EFF6] text-xs placeholder-[#8E8295]"
              />
              <select
                value={priorityFilter}
                onChange={(e) => setPriorityFilter(e.target.value)}
                className="px-2 py-1 bg-[#1C1824] border border-[#443849] rounded text-[#F4EFF6] text-xs"
              >
                <option value="ALL">ALL PRIORITIES</option>
                <option value="CRITICAL">CRITICAL</option>
                <option value="HIGH">HIGH</option>
                <option value="MEDIUM">MEDIUM</option>
                <option value="LOW">LOW</option>
              </select>
              <select
                value={typeFilter}
                onChange={(e) => setTypeFilter(e.target.value)}
                className="px-2 py-1 bg-[#1C1824] border border-[#443849] rounded text-[#F4EFF6] text-xs"
              >
                <option value="ALL">ALL TYPES</option>
                <option value="FEATURE">FEATURE</option>
                <option value="SUBGROUP">SUBGROUP</option>
                <option value="BEHAVIOR">BEHAVIOR</option>
                <option value="ERROR_TYPE">ERROR TYPE</option>
              </select>
            </div>

            {/* Target Cards List */}
            <div className="space-y-2 max-h-[750px] overflow-y-auto pr-1">
              {filteredTargets.map((target, idx) => {
                const isSelected = selectedTargetKey === target.targetKey;
                const pStyle = getPriorityStyle(target.priority);
                const rankFormatted = String(idx + 1).padStart(2, "0");

                return (
                  <div
                    key={target.targetKey}
                    onClick={() => setSelectedTargetKey(target.targetKey)}
                    className={`p-3 rounded cursor-pointer transition-all border ${
                      isSelected
                        ? "border-[#F18B78] bg-[#332B3C] shadow-lg"
                        : "border-[#443849] hover:border-[#5A4B62] hover:bg-[#332B3C]/50 bg-[#211D29]"
                    }`}
                  >
                    <div className="flex items-start justify-between gap-2 mb-1.5">
                      <div className="flex items-center gap-2">
                        <span className="text-[10px] text-[#8E8295] font-bold">
                          #{rankFormatted}
                        </span>
                        <span className={`px-1.5 py-0.2 text-[9px] rounded ${pStyle.badge}`}>
                          {target.priority}
                        </span>
                        <span className="text-[#F4EFF6] font-bold truncate max-w-[200px]">
                          {target.displayName}
                        </span>
                      </div>
                      <div className="text-right shrink-0">
                        <div className="text-xs font-bold text-[#F4EFF6] tabular-nums">
                          {target.priorityScore.toFixed(1)}
                        </div>
                        <div className="text-[9px] text-[#8E8295]">SCORE</div>
                      </div>
                    </div>

                    {/* Meta badges */}
                    <div className="flex flex-wrap gap-1.5 mb-2 text-[10px]">
                      <span className="px-1.5 py-0.2 bg-[#1C1824] text-[#B7ACBD] rounded border border-[#443849]">
                        TYPE: {target.targetType}
                      </span>
                      <span className="px-1.5 py-0.2 bg-[#1C1824] text-[#F18B78] rounded border border-[#443849]">
                        {target.supportingModuleCount} MODULES
                      </span>
                      <span className="px-1.5 py-0.2 bg-[#1C1824] text-[#B7ACBD] rounded border border-[#443849]">
                        {target.supportingFindingCount} FINDINGS
                      </span>
                      <span className="px-1.5 py-0.2 bg-[#1C1824] text-[#B7D7A8] rounded border border-[#443849]">
                        CONFIDENCE: {target.evidenceConfidence}
                      </span>
                    </div>

                    {/* Hypothesis snippet */}
                    <p className="text-[11px] text-[#B7ACBD] line-clamp-2 leading-relaxed">
                      {target.hypothesis}
                    </p>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Right Column: Target Dossier (7 cols) */}
          <div className="lg:col-span-7">
            {isLoadingDossier ? (
              <div className="p-12 text-center text-[#8E8295] bg-[#2A2432] border border-[#443849] rounded">
                <div className="animate-pulse font-bold text-[#F4EFF6] text-sm mb-1">
                  LOADING INVESTIGATION DOSSIER...
                </div>
              </div>
            ) : dossier ? (
              <div className="p-4 bg-[#2A2432] border border-[#443849] rounded space-y-4">
                {/* Dossier Header */}
                <div className="flex flex-wrap items-start justify-between gap-3 border-b border-[#443849] pb-3">
                  <div>
                    <div className="text-[10px] text-[#8E8295] uppercase tracking-wider">
                      INVESTIGATION TARGET DOSSIER
                    </div>
                    <div className="text-base font-bold text-[#F4EFF6]">
                      {dossier.displayName}
                    </div>
                    <div className="text-[11px] text-[#B7ACBD] font-mono mt-0.5">
                      CANONICAL KEY: <span className="text-[#F18B78]">{dossier.targetKey}</span>
                    </div>
                  </div>

                  <div className="flex items-center gap-3">
                    <div className="text-right">
                      <div className="text-lg font-bold text-[#F4EFF6] tabular-nums">
                        {dossier.priorityScore.toFixed(1)}
                      </div>
                      <div className="text-[9px] text-[#8E8295]">PRIORITY SCORE</div>
                    </div>
                    <span className={`px-2 py-1 text-xs rounded font-bold ${getPriorityStyle(dossier.priority).badge}`}>
                      {dossier.priority}
                    </span>
                  </div>
                </div>

                {/* Supporting Modules Strip */}
                <div>
                  <div className="text-[10px] text-[#8E8295] uppercase mb-1.5 font-bold">
                    SUPPORTING CONVERGENT MODULES ({dossier.supportingModules.length})
                  </div>
                  <div className="flex flex-wrap gap-1.5">
                    {dossier.supportingModules.map((mod) => {
                      const c = MODULE_COLORS[mod] || DEFAULT_COLOR;
                      return (
                        <button
                          key={mod}
                          onClick={() => {
                            if (onSelectSection && mod === "DRIFT") onSelectSection("04_DRIFT");
                            if (onSelectSection && mod === "EXPLAINABILITY") onSelectSection("06_EXPLAIN");
                            if (onSelectSection && mod === "ERROR_FORENSICS") onSelectSection("05_ERROR_FORENSICS");
                            if (onSelectSection && mod === "ROBUSTNESS") onSelectSection("08_ROBUSTNESS");
                            if (onSelectSection && mod === "DATA_QUALITY") onSelectSection("02_DATA");
                            if (onSelectSection && mod === "LEAKAGE") onSelectSection("03_FORENSICS");
                            if (onSelectSection && mod === "BIAS") onSelectSection("07_BIAS");
                            if (onSelectSection && mod === "PERFORMANCE") onSelectSection("05_PERFORMANCE");
                          }}
                          className={`px-2 py-0.5 text-[10px] font-bold rounded border ${c.bg} ${c.text} ${c.border} hover:brightness-125 transition-all cursor-pointer`}
                          title={`Navigate to ${mod} view`}
                        >
                          {mod} ↗
                        </button>
                      );
                    })}
                  </div>
                </div>

                {/* Deterministic Investigation Hypothesis */}
                <div className="p-3 bg-[#1C1824] border border-[#443849] rounded space-y-1">
                  <div className="text-[10px] text-[#F18B78] font-bold uppercase tracking-wider">
                    INVESTIGATION HYPOTHESIS (EVIDENCE-DERIVED)
                  </div>
                  <p className="text-xs text-[#F4EFF6] leading-relaxed">
                    {dossier.hypothesis}
                  </p>
                </div>

                {/* Ordered Investigation Path */}
                {dossier.investigationPath && dossier.investigationPath.length > 0 && (
                  <div className="space-y-2">
                    <div className="text-[10px] text-[#8E8295] uppercase font-bold">
                      ORDERED EVIDENCE PATH ({dossier.investigationPath.length} STEPS)
                    </div>
                    <div className="space-y-1.5">
                      {dossier.investigationPath.map((step) => {
                        const modColor = MODULE_COLORS[step.sourceModule] || DEFAULT_COLOR;
                        return (
                          <div
                            key={step.stepNumber}
                            className="p-2.5 bg-[#1C1824] border border-[#443849] rounded flex items-start gap-2.5 text-xs"
                          >
                            <span className="px-1.5 py-0.2 bg-[#332B3C] text-[#F4EFF6] rounded text-[10px] font-bold shrink-0">
                              {step.stepNumber}
                            </span>
                            <div className="flex-1 space-y-1">
                              <div className="flex flex-wrap items-center justify-between gap-1">
                                <span className={`text-[10px] font-bold ${modColor.text}`}>
                                  {step.sourceModule}
                                </span>
                                {step.formattedValue && (
                                  <span className="px-1.5 py-0.2 bg-[#332B3C] text-[#F18B78] rounded text-[10px] font-mono">
                                    {step.formattedValue}
                                  </span>
                                )}
                              </div>
                              <p className="text-[11px] text-[#B7ACBD]">
                                {step.description}
                              </p>
                              {step.correlationRuleId && (
                                <div className="text-[9px] text-[#8E8295]">
                                  RULE: <span className="text-[#B7ACBD]">{step.correlationRuleId}</span>
                                </div>
                              )}
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}

                {/* Deterministic Next Actions */}
                {dossier.nextActions && dossier.nextActions.length > 0 && (
                  <div className="p-3 bg-[#1C1824] border border-[#443849] rounded space-y-2">
                    <div className="text-[10px] text-[#F18B78] font-bold uppercase tracking-wider">
                      DETERMINISTIC NEXT ACTIONS
                    </div>
                    <div className="space-y-1.5">
                      {dossier.nextActions.map((action, idx) => (
                        <div key={idx} className="flex items-start gap-2 text-xs text-[#B7ACBD]">
                          <span className="text-[#F18B78] font-bold shrink-0">[{idx + 1}]</span>
                          <span className="leading-relaxed">{action}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Provenance Traceability */}
                {dossier.provenance && (
                  <div className="space-y-2 pt-2 border-t border-[#443849]">
                    <div className="text-[10px] text-[#8E8295] uppercase font-bold">
                      PROVENANCE & RESULT TRACEABILITY
                    </div>
                    <div className="overflow-x-auto">
                      <table className="w-full text-left text-[10px] border-collapse">
                        <thead>
                          <tr className="border-b border-[#443849] text-[#8E8295]">
                            <th className="py-1 px-1.5">MODULE</th>
                            <th className="py-1 px-1.5">METRIC</th>
                            <th className="py-1 px-1.5">VALUE</th>
                            <th className="py-1 px-1.5">RULE ID</th>
                            <th className="py-1 px-1.5">RESULT ID</th>
                          </tr>
                        </thead>
                        <tbody>
                          {(Array.isArray(dossier.provenance)
                            ? dossier.provenance
                            : dossier.provenance.evidenceItems || []
                          ).map((p: any, idx: number) => (
                            <tr key={idx} className="border-b border-[#443849]/50 hover:bg-[#332B3C]/50">
                              <td className="py-1 px-1.5 font-bold text-[#F4EFF6]">{p.sourceModule}</td>
                              <td className="py-1 px-1.5 text-[#B7ACBD]">{p.metric || "N/A"}</td>
                              <td className="py-1 px-1.5 font-mono text-[#F18B78]">
                                {typeof p.value === "number" ? p.value.toFixed(4) : (p.value ?? "N/A")}
                              </td>
                              <td className="py-1 px-1.5 text-[#B7ACBD]">{p.ruleId || "N/A"}</td>
                              <td className="py-1 px-1.5 font-mono text-[#8E8295] truncate max-w-[120px]">
                                {p.sourceResultId ? (
                                  <span className="text-[#F18B78]" title={p.sourceResultId}>
                                    {p.sourceResultId}
                                  </span>
                                ) : (
                                  <span className="text-[#8E8295]">N/A</span>
                                )}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                )}
              </div>
            ) : (
              <div className="p-12 text-center text-[#8E8295] bg-[#2A2432] border border-[#443849] rounded">
                Select an investigation target from the queue to view its full evidence dossier.
              </div>
            )}
          </div>
        </div>
      )}

      {/* TAB 2: INTERACTIVE EVIDENCE GRAPH */}
      {!isLoading && activeTab === "GRAPH" && (
        <div className="space-y-3">
          {/* Graph Controls Bar */}
          <div className="p-2.5 bg-[#2A2432] border border-[#443849] rounded flex flex-wrap items-center justify-between gap-2 text-xs">
            <div className="flex items-center gap-2">
              <span className="text-[#8E8295] text-[10px] font-bold">LEGEND:</span>
              {Object.entries(MODULE_COLORS).map(([mod, col]) => (
                <span key={mod} className={`px-1.5 py-0.5 text-[9px] rounded font-bold ${col.bg} ${col.text} border ${col.border}`}>
                  {mod}
                </span>
              ))}
            </div>

            <div className="flex items-center gap-2">
              <span className="text-[10px] text-[#8E8295] font-bold">ZOOM:</span>
              <button
                onClick={() => setZoomLevel((z) => Math.max(0.6, z - 0.1))}
                className="px-2 py-0.5 bg-[#332B3C] hover:bg-[#3D3448] text-[#F4EFF6] border border-[#443849] rounded cursor-pointer transition-colors"
              >
                -
              </button>
              <span className="text-[10px] font-mono text-[#F4EFF6]">{Math.round(zoomLevel * 100)}%</span>
              <button
                onClick={() => setZoomLevel((z) => Math.min(1.8, z + 0.1))}
                className="px-2 py-0.5 bg-[#332B3C] hover:bg-[#3D3448] text-[#F4EFF6] border border-[#443849] rounded cursor-pointer transition-colors"
              >
                +
              </button>
              <button
                onClick={() => setZoomLevel(1)}
                className="px-2 py-0.5 bg-[#332B3C] hover:bg-[#3D3448] text-[#B7ACBD] hover:text-[#F4EFF6] border border-[#443849] rounded cursor-pointer text-[10px] transition-colors"
              >
                RESET
              </button>
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
            {/* SVG Canvas (8 cols) */}
            <div className="lg:col-span-8 bg-[#1C1824] border border-[#443849] rounded p-2 overflow-auto flex items-center justify-center min-h-[550px]">
              <svg
                width={graphLayout.width * zoomLevel}
                height={graphLayout.height * zoomLevel}
                viewBox={`0 0 ${graphLayout.width} ${graphLayout.height}`}
                className="select-none"
              >
                <defs>
                  <marker
                    id="arrowhead"
                    markerWidth="8"
                    markerHeight="6"
                    refX="7"
                    refY="3"
                    orient="auto"
                  >
                    <polygon points="0 0, 8 3, 0 6" fill="#5A4B62" />
                  </marker>
                  <marker
                    id="arrowhead-active"
                    markerWidth="8"
                    markerHeight="6"
                    refX="7"
                    refY="3"
                    orient="auto"
                  >
                    <polygon points="0 0, 8 3, 0 6" fill="#F18B78" />
                  </marker>
                </defs>

                {/* Render Edges */}
                {graphLayout.edges.map((edge) => {
                  const srcNode = graphLayout.nodes.find((n) => n.id === edge.source);
                  const tgtNode = graphLayout.nodes.find((n) => n.id === edge.target);
                  if (!srcNode || !tgtNode) return null;

                  const isSelected = selectedEdge?.id === edge.id;
                  const strokeColor = isSelected ? "#F18B78" : "#443849";
                  const strokeWidth = isSelected ? 2.5 : 1.2;

                  return (
                    <g
                      key={edge.id}
                      className="cursor-pointer"
                      onClick={() => {
                        setSelectedEdge(edge);
                        setSelectedNode(null);
                      }}
                    >
                      <line
                        x1={srcNode.x}
                        y1={srcNode.y}
                        x2={tgtNode.x}
                        y2={tgtNode.y}
                        stroke={strokeColor}
                        strokeWidth={strokeWidth}
                        strokeDasharray={isSelected ? undefined : "3,3"}
                        markerEnd={isSelected ? "url(#arrowhead-active)" : "url(#arrowhead)"}
                      />
                      {/* Edge relation text pill */}
                      <rect
                        x={(srcNode.x + tgtNode.x) / 2 - 28}
                        y={(srcNode.y + tgtNode.y) / 2 - 8}
                        width="56"
                        height="14"
                        rx="3"
                        fill="#2A2432"
                        stroke={isSelected ? "#F18B78" : "#443849"}
                        strokeWidth="1"
                      />
                      <text
                        x={(srcNode.x + tgtNode.x) / 2}
                        y={(srcNode.y + tgtNode.y) / 2 + 2}
                        textAnchor="middle"
                        fontSize="7"
                        fill={isSelected ? "#F18B78" : "#B7ACBD"}
                        fontFamily="monospace"
                        fontWeight="bold"
                      >
                        {edge.relationType.substring(0, 10)}
                      </text>
                    </g>
                  );
                })}

                {/* Render Nodes */}
                {graphLayout.nodes.map((node) => {
                  const isSelected = selectedNode?.id === node.id || selectedTargetKey === node.id;
                  const modColor = node.sourceModule ? MODULE_COLORS[node.sourceModule] || DEFAULT_COLOR : DEFAULT_COLOR;
                  const isModule = node.nodeType === "MODULE";
                  const isFinding = node.nodeType === "FINDING";

                  const boxWidth = isModule ? 110 : isFinding ? 120 : 100;
                  const boxHeight = isModule ? 32 : isFinding ? 36 : 30;

                  return (
                    <g
                      key={node.id}
                      className="cursor-pointer transition-all"
                      transform={`translate(${node.x - boxWidth / 2}, ${node.y - boxHeight / 2})`}
                      onClick={() => {
                        setSelectedNode(node);
                        setSelectedEdge(null);
                        if (["FEATURE", "SUBGROUP", "BEHAVIOR", "ERROR_TYPE"].includes(node.nodeType)) {
                          setSelectedTargetKey(node.id);
                        }
                      }}
                    >
                      <rect
                        width={boxWidth}
                        height={boxHeight}
                        rx="4"
                        fill={isSelected ? "#332B3C" : "#2A2432"}
                        stroke={isSelected ? "#F18B78" : isModule ? modColor.hex : "#443849"}
                        strokeWidth={isSelected ? 2 : 1.2}
                      />
                      <text
                        x={boxWidth / 2}
                        y={12}
                        textAnchor="middle"
                        fontSize="8"
                        fill={modColor.hex}
                        fontFamily="monospace"
                        fontWeight="bold"
                      >
                        {node.nodeType}
                      </text>
                      <text
                        x={boxWidth / 2}
                        y={23}
                        textAnchor="middle"
                        fontSize="9"
                        fill="#F4EFF6"
                        fontFamily="monospace"
                        fontWeight="bold"
                        className="truncate"
                      >
                        {node.label.length > 14 ? node.label.substring(0, 13) + "…" : node.label}
                      </text>
                    </g>
                  );
                })}
              </svg>
            </div>

            {/* Edge / Node Inspector Sidebar (4 cols) */}
            <div className="lg:col-span-4 space-y-3">
              {selectedEdge ? (
                <div className="p-3.5 bg-[#2A2432] border border-[#F18B78]/50 rounded space-y-3">
                  <div className="flex items-center justify-between border-b border-[#443849] pb-2">
                    <span className="text-[10px] text-[#F18B78] font-bold uppercase tracking-wider">
                      EDGE INSPECTOR
                    </span>
                    <span className="px-1.5 py-0.5 bg-[#332B3C] text-[#F18B78] rounded text-[9px] font-bold border border-[#F18B78]/30">
                      {selectedEdge.relationType}
                    </span>
                  </div>

                  <div className="space-y-1.5 text-xs">
                    <div className="flex justify-between">
                      <span className="text-[#8E8295]">SOURCE:</span>
                      <span className="text-[#F4EFF6] font-mono">{selectedEdge.source}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-[#8E8295]">TARGET:</span>
                      <span className="text-[#F4EFF6] font-mono">{selectedEdge.target}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-[#8E8295]">MODULE:</span>
                      <span className="text-[#F18B78] font-bold">{selectedEdge.sourceModule}</span>
                    </div>
                    {selectedEdge.ruleId && (
                      <div className="flex justify-between">
                        <span className="text-[#8E8295]">RULE ID:</span>
                        <span className="text-[#B7D7A8] font-mono">{selectedEdge.ruleId}</span>
                      </div>
                    )}
                    {selectedEdge.metric && (
                      <div className="flex justify-between">
                        <span className="text-[#8E8295]">METRIC:</span>
                        <span className="text-[#B7ACBD]">{selectedEdge.metric}</span>
                      </div>
                    )}
                    {selectedEdge.metricValue !== undefined && (
                      <div className="flex justify-between">
                        <span className="text-[#8E8295]">VALUE:</span>
                        <span className="text-[#F18B78] font-bold font-mono">
                          {selectedEdge.metricValue.toFixed(4)}
                        </span>
                      </div>
                    )}
                    {selectedEdge.threshold && (
                      <div className="flex justify-between">
                        <span className="text-[#8E8295]">THRESHOLD:</span>
                        <span className="text-[#E8B66D] font-mono">{selectedEdge.threshold}</span>
                      </div>
                    )}
                    {selectedEdge.confidence && (
                      <div className="flex justify-between">
                        <span className="text-[#8E8295]">CONFIDENCE:</span>
                        <span className="text-[#B7D7A8] font-bold">{selectedEdge.confidence}</span>
                      </div>
                    )}
                    <div className="flex justify-between border-t border-[#443849] pt-1.5">
                      <span className="text-[#8E8295]">SOURCE RESULT ID:</span>
                      <span className="text-[#B7ACBD] font-mono truncate max-w-[140px]">
                        {selectedEdge.sourceResultId || "UNAVAILABLE"}
                      </span>
                    </div>
                  </div>
                </div>
              ) : selectedNode ? (
                <div className="p-3.5 bg-[#2A2432] border border-[#443849] rounded space-y-3">
                  <div className="flex items-center justify-between border-b border-[#443849] pb-2">
                    <span className="text-[10px] text-[#F4EFF6] font-bold uppercase tracking-wider">
                      NODE INSPECTOR
                    </span>
                    <span className="px-1.5 py-0.5 bg-[#332B3C] text-[#B7ACBD] rounded text-[9px] font-bold border border-[#443849]">
                      {selectedNode.nodeType}
                    </span>
                  </div>
                  <div className="space-y-1.5 text-xs">
                    <div className="flex justify-between">
                      <span className="text-[#8E8295]">NODE ID:</span>
                      <span className="text-[#F4EFF6] font-mono">{selectedNode.id}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-[#8E8295]">LABEL:</span>
                      <span className="text-[#F4EFF6] font-bold">{selectedNode.label}</span>
                    </div>
                    {selectedNode.sourceModule && (
                      <div className="flex justify-between">
                        <span className="text-[#8E8295]">MODULE:</span>
                        <span className="text-[#F18B78] font-bold">{selectedNode.sourceModule}</span>
                      </div>
                    )}
                    {selectedNode.priority && (
                      <div className="flex justify-between">
                        <span className="text-[#8E8295]">PRIORITY:</span>
                        <span className="text-[#E8B66D] font-bold">{selectedNode.priority}</span>
                      </div>
                    )}
                    {selectedNode.score !== undefined && (
                      <div className="flex justify-between">
                        <span className="text-[#8E8295]">SCORE:</span>
                        <span className="text-[#F4EFF6] font-mono">{selectedNode.score.toFixed(1)}</span>
                      </div>
                    )}
                  </div>
                  {["FEATURE", "SUBGROUP", "BEHAVIOR", "ERROR_TYPE"].includes(selectedNode.nodeType) && (
                    <button
                      onClick={() => {
                        setSelectedTargetKey(selectedNode.id);
                        setActiveTab("QUEUE");
                      }}
                      className="w-full mt-2 py-1.5 bg-[#F18B78] hover:bg-[#F39C8B] text-[#211D29] font-bold text-xs rounded transition-colors cursor-pointer"
                    >
                      OPEN TARGET DOSSIER ↗
                    </button>
                  )}
                </div>
              ) : (
                <div className="p-6 bg-[#2A2432] border border-[#443849] rounded text-center text-[#8E8295] text-xs">
                  Click any node or edge in the graph to inspect evidence relationships, source modules, and provenance result IDs.
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* TAB 3: EXTENDED FEATURE INVESTIGATION MATRIX */}
      {!isLoading && activeTab === "MATRIX" && (
        <div className="p-4 bg-[#2A2432] border border-[#443849] rounded space-y-3">
          <div className="flex items-center justify-between border-b border-[#443849] pb-2">
            <div>
              <div className="text-sm font-bold text-[#F4EFF6]">
                FEATURE-CENTRIC INVESTIGATION MATRIX
              </div>
              <div className="text-xs text-[#B7ACBD]">
                Extended matrix integrating drift, importance, error association, robustness sensitivity, and calculated investigation priority.
              </div>
            </div>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs border-collapse">
              <thead>
                <tr className="border-b border-[#443849] text-[#8E8295] font-mono text-[10px]">
                  <th className="py-2.5 px-2">FEATURE TARGET</th>
                  <th className="py-2.5 px-2">PRIORITY</th>
                  <th className="py-2.5 px-2 text-right">SCORE</th>
                  <th className="py-2.5 px-2 text-center">MODULES</th>
                  <th className="py-2.5 px-2 text-center">FINDINGS</th>
                  <th className="py-2.5 px-2">INVESTIGATION HYPOTHESIS</th>
                  <th className="py-2.5 px-2 text-right">ACTION</th>
                </tr>
              </thead>
              <tbody>
                {targets.filter((t) => t.targetType === "FEATURE").map((target) => {
                  const pStyle = getPriorityStyle(target.priority);
                  return (
                    <tr
                      key={target.targetKey}
                      className="border-b border-[#443849]/50 hover:bg-[#332B3C] cursor-pointer transition-colors"
                      onClick={() => {
                        setSelectedTargetKey(target.targetKey);
                        setActiveTab("QUEUE");
                      }}
                    >
                      <td className="py-2 px-2 font-bold text-[#F4EFF6] flex items-center gap-1.5">
                        <span className="text-[#F18B78]">{target.displayName}</span>
                      </td>
                      <td className="py-2 px-2">
                        <span className={`px-1.5 py-0.5 text-[9px] rounded font-bold ${pStyle.badge}`}>
                          {target.priority}
                        </span>
                      </td>
                      <td className="py-2 px-2 text-right font-mono text-[#F4EFF6] tabular-nums font-bold">
                        {target.priorityScore.toFixed(1)}
                      </td>
                      <td className="py-2 px-2 text-center text-[#B7D7A8] font-bold">
                        {target.supportingModuleCount}
                      </td>
                      <td className="py-2 px-2 text-center text-[#B7ACBD]">
                        {target.supportingFindingCount}
                      </td>
                      <td className="py-2 px-2 text-[#B7ACBD] text-[11px] truncate max-w-[320px]">
                        {target.hypothesis}
                      </td>
                      <td className="py-2 px-2 text-right">
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            setSelectedTargetKey(target.targetKey);
                            setActiveTab("QUEUE");
                          }}
                          className="px-2 py-0.5 bg-[#332B3C] hover:bg-[#3D3448] text-[#F18B78] hover:text-[#F39C8B] border border-[#F18B78]/40 text-[10px] rounded font-bold transition-colors cursor-pointer"
                        >
                          DOSSIER ↗
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
};
