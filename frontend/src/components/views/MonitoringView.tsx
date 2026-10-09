"use client";

import React, { useState, useEffect, useMemo } from "react";
import {
  ModelHealthDecision,
  DiagnosticMonitoringPolicy,
  DiagnosticHealthSnapshot,
  OperationalAlert,
  HealthDimensionEvaluation,
  HealthDimension,
  ModelHealthState,
  AlertLifecycleState,
  HealthPenalty,
  DiagnosticAlertEvent,
  NavSection,
} from "@/types/diagnostics";
import {
  getModelHealth,
  getMonitoringPolicy,
  updateMonitoringPolicy,
  getMonitoringHistory,
  getOperationalAlerts,
  acknowledgeOperationalAlert,
  investigateOperationalAlert,
  suppressOperationalAlert,
  resolveOperationalAlert,
  recalculateMonitoring,
  RunSummary,
} from "@/lib/api";

export interface MonitoringViewProps {
  runId: string;
  runSummary?: RunSummary | null;
  onSelectSection?: (section: NavSection) => void;
  onSelectFeature?: (featureName: string) => void;
  onRefresh?: () => void;
  onNavigateSection?: (section: NavSection) => void;
  onSelectInvestigationTarget?: (targetKey: string) => void;
}

const HEALTH_STATE_STYLES: Record<ModelHealthState, { bg: string; text: string; border: string; glow: string }> = {
  HEALTHY: { bg: "bg-[#10b981]/15", text: "text-[#34d399]", border: "border-[#10b981]/40", glow: "shadow-[0_0_12px_rgba(16,185,129,0.2)]" },
  DEGRADED: { bg: "bg-[#f59e0b]/15", text: "text-[#fbbf24]", border: "border-[#f59e0b]/40", glow: "shadow-[0_0_12px_rgba(245,158,11,0.2)]" },
  CRITICAL: { bg: "bg-[#ef4444]/15", text: "text-[#f87171]", border: "border-[#ef4444]/40", glow: "shadow-[0_0_12px_rgba(239,68,68,0.25)]" },
  RECOVERING: { bg: "bg-[#06b6d4]/15", text: "text-[#22d3ee]", border: "border-[#06b6d4]/40", glow: "shadow-[0_0_12px_rgba(6,182,212,0.2)]" },
  UNKNOWN: { bg: "bg-[#64748b]/15", text: "text-[#94a3b8]", border: "border-[#64748b]/40", glow: "" },
};

const SEVERITY_BADGES: Record<string, { bg: string; text: string; border: string }> = {
  CRITICAL: { bg: "bg-[#ef4444]/20", text: "text-[#ef4444]", border: "border-[#ef4444]/50" },
  HIGH: { bg: "bg-[#f97316]/20", text: "text-[#fb923c]", border: "border-[#f97316]/50" },
  MEDIUM: { bg: "bg-[#f59e0b]/20", text: "text-[#fbbf24]", border: "border-[#f59e0b]/50" },
  WARNING: { bg: "bg-[#f59e0b]/20", text: "text-[#fbbf24]", border: "border-[#f59e0b]/50" },
  LOW: { bg: "bg-[#3b82f6]/20", text: "text-[#60a5fa]", border: "border-[#3b82f6]/50" },
  INFO: { bg: "bg-[#3b82f6]/20", text: "text-[#60a5fa]", border: "border-[#3b82f6]/50" },
};

const LIFECYCLE_BADGES: Record<AlertLifecycleState, { bg: string; text: string; border: string }> = {
  OPEN: { bg: "bg-[#ef4444]/15", text: "text-[#f87171]", border: "border-[#ef4444]/40" },
  ACKNOWLEDGED: { bg: "bg-[#f59e0b]/15", text: "text-[#fbbf24]", border: "border-[#f59e0b]/40" },
  INVESTIGATING: { bg: "bg-[#3b82f6]/15", text: "text-[#60a5fa]", border: "border-[#3b82f6]/40" },
  SUPPRESSED: { bg: "bg-[#64748b]/15", text: "text-[#94a3b8]", border: "border-[#64748b]/40" },
  RESOLVED: { bg: "bg-[#10b981]/15", text: "text-[#34d399]", border: "border-[#10b981]/40" },
  REOPENED: { bg: "bg-[#ec4899]/15", text: "text-[#f472b6]", border: "border-[#ec4899]/40" },
};

const DIMENSION_METADATA: Record<HealthDimension, { label: string; code: string; defaultModuleSection?: NavSection }> = {
  DATA_QUALITY: { label: "DATA QUALITY", code: "02", defaultModuleSection: "02_DATA" },
  LEAKAGE: { label: "DATA LEAKAGE", code: "03", defaultModuleSection: "03_FORENSICS" },
  DRIFT: { label: "DISTRIBUTION DRIFT", code: "04", defaultModuleSection: "04_DRIFT" },
  PERFORMANCE: { label: "PERFORMANCE", code: "05", defaultModuleSection: "05_PERFORMANCE" },
  CALIBRATION: { label: "CALIBRATION", code: "05C", defaultModuleSection: "05_PERFORMANCE" },
  ERROR: { label: "ERROR FORENSICS", code: "05B", defaultModuleSection: "05_ERROR_FORENSICS" },
  FAIRNESS: { label: "FAIRNESS & BIAS", code: "07B", defaultModuleSection: "07_BIAS" },
  ROBUSTNESS: { label: "ROBUSTNESS", code: "08B", defaultModuleSection: "08_ROBUSTNESS" },
  TEMPORAL: { label: "TEMPORAL STABILITY", code: "09", defaultModuleSection: "09_TEMPORAL" },
};

export const MonitoringView: React.FC<MonitoringViewProps> = ({
  runId,
  runSummary,
  onSelectSection,
  onSelectFeature,
  onRefresh,
  onNavigateSection,
  onSelectInvestigationTarget,
}) => {
  const navigate = onSelectSection || onNavigateSection;

  // Data states
  const [decision, setDecision] = useState<ModelHealthDecision | null>(null);
  const [policy, setPolicy] = useState<DiagnosticMonitoringPolicy | null>(null);
  const [historySnapshots, setHistorySnapshots] = useState<DiagnosticHealthSnapshot[]>([]);
  const [alerts, setAlerts] = useState<OperationalAlert[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isRecalculating, setIsRecalculating] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [statusNotification, setStatusNotification] = useState<string | null>(null);

  // Selected state / UI drawers
  const [selectedAlert, setSelectedAlert] = useState<OperationalAlert | null>(null);
  const [selectedSnapshot, setSelectedSnapshot] = useState<DiagnosticHealthSnapshot | null>(null);
  const [isPolicyModalOpen, setIsPolicyModalOpen] = useState<boolean>(false);
  const [isScoreBreakdownOpen, setIsScoreBreakdownOpen] = useState<boolean>(false);
  const [activeTab, setActiveTab] = useState<"ACTIVE_ALERTS" | "HEALTH_VECTOR" | "SNAPSHOT_HISTORY" | "AUDIT_LOG">("ACTIVE_ALERTS");

  // Filter state for alerts
  const [alertStateFilter, setAlertStateFilter] = useState<string>("ALL");
  const [alertSeverityFilter, setAlertSeverityFilter] = useState<string>("ALL");

  // Lifecycle action modal state
  const [actionModal, setActionModal] = useState<{
    type: "ACKNOWLEDGE" | "INVESTIGATE" | "SUPPRESS" | "RESOLVE" | null;
    alert: OperationalAlert | null;
    reason: string;
    actor: string;
    durationHours: number;
  }>({
    type: null,
    alert: null,
    reason: "",
    actor: "USER",
    durationHours: 24,
  });

  // Policy edit form state
  const [policyForm, setPolicyForm] = useState<DiagnosticMonitoringPolicy | null>(null);

  const lineageId = decision?.modelLineageId || policy?.modelLineageId || runSummary?.runId || "UNKNOWN_LINEAGE";

  const loadData = async () => {
    try {
      setIsLoading(true);
      setErrorMessage(null);

      // Fetch health decision for this run
      const healthData = await getModelHealth(runId);
      setDecision(healthData);

      const resolvedLineage = healthData.modelLineageId || runId;

      // Fetch policy, history, and operational alerts
      const [pol, history, alertsList] = await Promise.all([
        getMonitoringPolicy(resolvedLineage).catch(() => null),
        getMonitoringHistory(resolvedLineage).catch(() => []),
        getOperationalAlerts(resolvedLineage).catch(() => []),
      ]);

      if (pol) {
        setPolicy(pol);
        setPolicyForm(pol);
      }
      setHistorySnapshots(history || []);
      setAlerts(alertsList || healthData.allAlerts || []);

      const initialAlerts = alertsList || healthData.allAlerts || [];
      if (initialAlerts.length > 0 && !selectedAlert) {
        setSelectedAlert(initialAlerts[0]);
      }
    } catch (err: any) {
      setErrorMessage(err.message || "Failed to load continuous monitoring data.");
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [runId]);

  const handleRecalculate = async () => {
    try {
      setIsRecalculating(true);
      setStatusNotification(null);
      const res = await recalculateMonitoring(runId);
      setStatusNotification(
        `Recalculation complete: Evaluated ${res.baselineRunsEvaluated} baseline runs. Health State: ${res.overallState}. Active Alerts: ${res.activeAlertsCount}.`
      );
      await loadData();
      if (onRefresh) onRefresh();
    } catch (err: any) {
      setErrorMessage(err.message || "Recalculation failed.");
    } finally {
      setIsRecalculating(false);
    }
  };

  const handleSavePolicy = async () => {
    if (!policyForm || !policyForm.modelLineageId) return;
    try {
      setIsLoading(true);
      const updated = await updateMonitoringPolicy(policyForm.modelLineageId, policyForm);
      setPolicy(updated);
      setPolicyForm(updated);
      setIsPolicyModalOpen(false);
      setStatusNotification("Monitoring policy updated successfully.");
      await handleRecalculate();
    } catch (err: any) {
      setErrorMessage(err.message || "Failed to save monitoring policy.");
    } finally {
      setIsLoading(false);
    }
  };

  const executeLifecycleAction = async () => {
    if (!actionModal.type || !actionModal.alert) return;
    const alertId = actionModal.alert.id;
    const currentLineage = lineageId;
    try {
      setIsLoading(true);
      if (actionModal.type === "ACKNOWLEDGE") {
        await acknowledgeOperationalAlert(currentLineage, alertId, actionModal.actor || "USER");
      } else if (actionModal.type === "INVESTIGATE") {
        await investigateOperationalAlert(currentLineage, alertId, actionModal.actor || "USER");
      } else if (actionModal.type === "SUPPRESS") {
        await suppressOperationalAlert(
          currentLineage,
          alertId,
          actionModal.reason || "Suppressed",
          actionModal.durationHours || 24,
          actionModal.actor || "USER"
        );
      } else if (actionModal.type === "RESOLVE") {
        await resolveOperationalAlert(
          currentLineage,
          alertId,
          actionModal.reason || "Resolved",
          actionModal.actor || "USER"
        );
      }

      setActionModal({ type: null, alert: null, reason: "", actor: "USER", durationHours: 24 });
      setStatusNotification(`Alert ${actionModal.alert.targetKey || actionModal.alert.alertType} updated.`);
      await loadData();
    } catch (err: any) {
      setErrorMessage(err.message || `Action ${actionModal.type} failed.`);
    } finally {
      setIsLoading(false);
    }
  };

  // Filtered alerts
  const filteredAlerts = useMemo(() => {
    return alerts.filter((a) => {
      if (alertStateFilter !== "ALL" && a.lifecycleState !== alertStateFilter) return false;
      if (alertSeverityFilter !== "ALL" && a.currentSeverity !== alertSeverityFilter) return false;
      return true;
    });
  }, [alerts, alertStateFilter, alertSeverityFilter]);

  const overallState: ModelHealthState = decision?.overallState || "UNKNOWN";
  const stateStyle = HEALTH_STATE_STYLES[overallState] || HEALTH_STATE_STYLES.UNKNOWN;

  const dimensionVectorList: HealthDimensionEvaluation[] = useMemo(() => {
    if (!decision?.healthVector) return [];
    return Object.values(decision.healthVector);
  }, [decision]);

  return (
    <div className="flex flex-col h-full w-full bg-[#0c0e14] text-[#f1f3f8] overflow-hidden">
      {/* Top Notification / Error Banners */}
      {statusNotification && (
        <div className="bg-[#10b981]/15 border-b border-[#10b981]/40 px-4 py-2 text-xs font-mono text-[#34d399] flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-[#10b981] animate-pulse"></span>
            <span>{statusNotification}</span>
          </div>
          <button
            onClick={() => setStatusNotification(null)}
            className="text-xs text-[#94a3b8] hover:text-white px-2 cursor-pointer"
          >
            DISMISS
          </button>
        </div>
      )}
      {errorMessage && (
        <div className="bg-[#ef4444]/15 border-b border-[#ef4444]/40 px-4 py-2 text-xs font-mono text-[#f87171] flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-[#ef4444]"></span>
            <span>{errorMessage}</span>
          </div>
          <button
            onClick={() => setErrorMessage(null)}
            className="text-xs text-[#94a3b8] hover:text-white px-2 cursor-pointer"
          >
            DISMISS
          </button>
        </div>
      )}

      {/* Header HUD: Continuous Monitoring & Model Health State */}
      <div className="p-4 border-b border-[#1f2533] bg-[#0f121a] shrink-0">
        <div className="flex flex-wrap items-center justify-between gap-4">
          {/* Main State Card */}
          <div className="flex items-center gap-4">
            <div
              className={`px-4 py-2.5 rounded border ${stateStyle.border} ${stateStyle.bg} ${stateStyle.glow} flex flex-col justify-center`}
            >
              <div className="text-[10px] font-mono text-[#64748b] tracking-wider uppercase">
                MODEL OPERATIONAL HEALTH
              </div>
              <div className={`text-xl font-bold font-mono tracking-wide ${stateStyle.text}`}>
                {overallState}
              </div>
            </div>

            {/* Health Index Gauge & Traceability Popover */}
            {decision?.healthIndex !== undefined && (
              <div
                onClick={() => setIsScoreBreakdownOpen(!isScoreBreakdownOpen)}
                className="px-3 py-2 bg-[#141824] border border-[#262f45] rounded flex flex-col justify-center cursor-pointer hover:border-[#3b82f6] transition-colors"
                title="Click to view traceable penalty breakdown"
              >
                <div className="text-[9px] font-mono text-[#64748b] uppercase">HEALTH INDEX</div>
                <div className="flex items-baseline gap-1">
                  <span
                    className={`text-lg font-bold font-mono ${
                      decision.healthIndex >= 85
                        ? "text-[#10b981]"
                        : decision.healthIndex >= 65
                        ? "text-[#f59e0b]"
                        : "text-[#ef4444]"
                    }`}
                  >
                    {decision.healthIndex}
                  </span>
                  <span className="text-[10px] font-mono text-[#64748b]">/ 100</span>
                  <span className="text-[9px] font-mono text-[#3b82f6] ml-1">ℹ</span>
                </div>
              </div>
            )}

            {/* Operational Run Context & Provenance */}
            <div className="hidden md:flex flex-col text-xs font-mono text-[#94a3b8] space-y-0.5 border-l border-[#1f2533] pl-4">
              <div>
                LINEAGE: <span className="text-white font-semibold">{lineageId}</span>
              </div>
              <div>
                EVAL RUN: <span className="text-[#60a5fa]">{decision?.operationalRunId || runId}</span>
              </div>
              <div className="text-[10px] text-[#64748b]">
                EVALUATED: {decision?.evaluationTimestamp ? new Date(decision.evaluationTimestamp).toLocaleString() : "N/A"}
              </div>
            </div>
          </div>

          {/* Key Metrics / Counters HUD */}
          <div className="flex items-center gap-3">
            <div className="px-3 py-1.5 bg-[#141824] border border-[#1f2533] rounded text-center">
              <div className="text-[9px] font-mono text-[#64748b]">ACTIVE ALERTS</div>
              <div
                className={`text-sm font-bold font-mono ${
                  alerts.filter((a) => a.lifecycleState === "OPEN" || a.lifecycleState === "REOPENED" || a.lifecycleState === "INVESTIGATING").length > 0 ? "text-[#f87171]" : "text-[#10b981]"
                }`}
              >
                {decision?.activeAlerts?.length ?? alerts.filter((a) => a.lifecycleState === "OPEN" || a.lifecycleState === "REOPENED" || a.lifecycleState === "INVESTIGATING").length}
              </div>
            </div>

            <div className="px-3 py-1.5 bg-[#141824] border border-[#1f2533] rounded text-center">
              <div className="text-[9px] font-mono text-[#64748b]">CRITICAL ALERTS</div>
              <div
                className={`text-sm font-bold font-mono ${
                  alerts.filter((a) => a.currentSeverity === "CRITICAL" && a.lifecycleState !== "RESOLVED" && a.lifecycleState !== "SUPPRESSED").length > 0 ? "text-[#ef4444]" : "text-[#94a3b8]"
                }`}
              >
                {alerts.filter((a) => a.currentSeverity === "CRITICAL" && a.lifecycleState !== "RESOLVED" && a.lifecycleState !== "SUPPRESSED").length}
              </div>
            </div>

            <div className="px-3 py-1.5 bg-[#141824] border border-[#1f2533] rounded text-center">
              <div className="text-[9px] font-mono text-[#64748b]">DATA SUFFICIENCY</div>
              <div
                className={`text-xs font-bold font-mono ${
                  decision?.dataSufficiency?.isSufficient ? "text-[#10b981]" : "text-[#f59e0b]"
                }`}
              >
                {decision?.dataSufficiency?.isSufficient ? "SUFFICIENT" : "INSUFFICIENT"}
              </div>
            </div>

            {/* Quick Actions */}
            <button
              onClick={handleRecalculate}
              disabled={isRecalculating}
              className="px-3 py-2 bg-[#3b82f6] hover:bg-[#2563eb] text-white font-mono text-xs font-semibold rounded cursor-pointer disabled:opacity-50 transition-colors flex items-center gap-1.5 shadow-sm"
              title="Recalculate continuous monitoring from authoritative baseline runs"
            >
              {isRecalculating ? "EVALUATING..." : "⟳ RECALCULATE"}
            </button>

            <button
              onClick={() => setIsPolicyModalOpen(true)}
              className="px-3 py-2 bg-[#1e293b] hover:bg-[#334155] text-[#94a3b8] hover:text-white font-mono text-xs rounded border border-[#334155] cursor-pointer transition-colors"
              title="Configure Monitoring Policy & Thresholds"
            >
              ⚙ POLICY
            </button>
          </div>
        </div>

        {/* Primary Rationale Banner */}
        {decision?.decisionReason && (
          <div className="mt-3 p-2 bg-[#121622] border-l-2 border-[#3b82f6] text-xs font-mono text-[#cbd5e1] flex items-center justify-between">
            <div>
              <span className="text-[#64748b] mr-2">EVALUATION SUMMARY:</span>
              <span>{decision.decisionReason}</span>
            </div>
            {policy && (
              <span className="text-[10px] text-[#64748b] shrink-0 ml-4">
                POLICY: v{policy.policyVersion} ({policy.observationWindow})
              </span>
            )}
          </div>
        )}
      </div>

      {/* Score Breakdown Modal / Drawer */}
      {isScoreBreakdownOpen && decision?.healthIndexBreakdown && (
        <div className="p-4 bg-[#141926] border-b border-[#262f45] text-xs font-mono">
          <div className="flex items-center justify-between mb-2">
            <span className="font-bold text-white tracking-wide">
              HEALTH INDEX COMPUTATION BREAKDOWN (BASE: {decision.healthIndexBreakdown.baseScore})
            </span>
            <button
              onClick={() => setIsScoreBreakdownOpen(false)}
              className="text-[#94a3b8] hover:text-white"
            >
              ✕ CLOSE
            </button>
          </div>
          <div className="text-[11px] text-[#94a3b8] mb-3">
            Deterministic index calculated from active alerts and dimension states. All penalties are fully traceable to empirical evidence.
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-2">
            {decision.healthIndexBreakdown.penalties?.map((p: HealthPenalty, idx: number) => (
              <div
                key={idx}
                className="p-2 bg-[#0c0e14] border border-[#1f2533] rounded flex items-start justify-between gap-2"
              >
                <div>
                  <div className="text-[10px] text-[#60a5fa] font-semibold">{p.category}</div>
                  <div className="text-[10px] text-[#cbd5e1]">{p.description}</div>
                  {p.traceableEvidence && (
                    <div className="text-[9px] text-[#64748b] truncate max-w-[200px]">
                      REF: {p.traceableEvidence}
                    </div>
                  )}
                </div>
                <div className="text-xs font-bold text-[#ef4444] shrink-0">
                  -{p.penaltyPoints} pts
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Navigation Tab Bar */}
      <div className="flex border-b border-[#1f2533] bg-[#0c0e14] px-4 shrink-0">
        <button
          onClick={() => setActiveTab("ACTIVE_ALERTS")}
          className={`px-4 py-2 text-xs font-mono font-semibold border-b-2 transition-colors cursor-pointer ${
            activeTab === "ACTIVE_ALERTS"
              ? "border-[#3b82f6] text-white bg-[#141824]"
              : "border-transparent text-[#94a3b8] hover:text-[#f1f3f8]"
          }`}
        >
          OPERATIONAL ALERTS ({alerts.length})
        </button>
        <button
          onClick={() => setActiveTab("HEALTH_VECTOR")}
          className={`px-4 py-2 text-xs font-mono font-semibold border-b-2 transition-colors cursor-pointer ${
            activeTab === "HEALTH_VECTOR"
              ? "border-[#3b82f6] text-white bg-[#141824]"
              : "border-transparent text-[#94a3b8] hover:text-[#f1f3f8]"
          }`}
        >
          HEALTH VECTOR (9 DIMENSIONS)
        </button>
        <button
          onClick={() => setActiveTab("SNAPSHOT_HISTORY")}
          className={`px-4 py-2 text-xs font-mono font-semibold border-b-2 transition-colors cursor-pointer ${
            activeTab === "SNAPSHOT_HISTORY"
              ? "border-[#3b82f6] text-white bg-[#141824]"
              : "border-transparent text-[#94a3b8] hover:text-[#f1f3f8]"
          }`}
        >
          HEALTH HISTORY TIMELINE ({historySnapshots.length})
        </button>
        <button
          onClick={() => setActiveTab("AUDIT_LOG")}
          className={`px-4 py-2 text-xs font-mono font-semibold border-b-2 transition-colors cursor-pointer ${
            activeTab === "AUDIT_LOG"
              ? "border-[#3b82f6] text-white bg-[#141824]"
              : "border-transparent text-[#94a3b8] hover:text-[#f1f3f8]"
          }`}
        >
          LIFECYCLE AUDIT LOG
        </button>
      </div>

      {/* Main Content Area */}
      <div className="flex-1 overflow-auto p-4">
        {/* TAB 1: OPERATIONAL ALERTS & INSPECTOR */}
        {activeTab === "ACTIVE_ALERTS" && (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-4 h-full">
            {/* Left Queue: Alerts List */}
            <div className="lg:col-span-7 flex flex-col bg-[#0f121a] border border-[#1f2533] rounded overflow-hidden">
              {/* Queue Filters */}
              <div className="p-3 border-b border-[#1f2533] bg-[#141824] flex items-center justify-between text-xs font-mono gap-2 flex-wrap">
                <div className="flex items-center gap-2">
                  <span className="text-[#64748b]">STATE:</span>
                  <select
                    value={alertStateFilter}
                    onChange={(e) => setAlertStateFilter(e.target.value)}
                    className="bg-[#0c0e14] border border-[#262f45] rounded px-2 py-1 text-xs text-white"
                  >
                    <option value="ALL">ALL STATES</option>
                    <option value="OPEN">OPEN</option>
                    <option value="ACKNOWLEDGED">ACKNOWLEDGED</option>
                    <option value="INVESTIGATING">INVESTIGATING</option>
                    <option value="SUPPRESSED">SUPPRESSED</option>
                    <option value="RESOLVED">RESOLVED</option>
                    <option value="REOPENED">REOPENED</option>
                  </select>
                </div>

                <div className="flex items-center gap-2">
                  <span className="text-[#64748b]">SEVERITY:</span>
                  <select
                    value={alertSeverityFilter}
                    onChange={(e) => setAlertSeverityFilter(e.target.value)}
                    className="bg-[#0c0e14] border border-[#262f45] rounded px-2 py-1 text-xs text-white"
                  >
                    <option value="ALL">ALL SEVERITIES</option>
                    <option value="CRITICAL">CRITICAL</option>
                    <option value="HIGH">HIGH</option>
                    <option value="WARNING">WARNING</option>
                    <option value="INFO">INFO</option>
                  </select>
                </div>

                <span className="text-[11px] text-[#64748b]">
                  SHOWING {filteredAlerts.length} OF {alerts.length} ALERTS
                </span>
              </div>

              {/* Table / List */}
              <div className="flex-1 overflow-auto">
                {filteredAlerts.length === 0 ? (
                  <div className="p-8 text-center text-xs font-mono text-[#64748b]">
                    NO OPERATIONAL ALERTS MATCHING FILTER
                  </div>
                ) : (
                  <table className="w-full text-left text-xs font-mono">
                    <thead className="bg-[#121622] text-[#64748b] border-b border-[#1f2533] sticky top-0">
                      <tr>
                        <th className="py-2 px-3">SEVERITY</th>
                        <th className="py-2 px-3">TARGET</th>
                        <th className="py-2 px-3">TYPE / METRIC</th>
                        <th className="py-2 px-3">STATE</th>
                        <th className="py-2 px-3">PERSISTENCE</th>
                        <th className="py-2 px-3 text-right">ACTION</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-[#1f2533]">
                      {filteredAlerts.map((alert) => {
                        const isSelected = selectedAlert?.id === alert.id;
                        const sevBadge = SEVERITY_BADGES[alert.currentSeverity] || SEVERITY_BADGES.INFO;
                        const lcBadge = (alert.lifecycleState in LIFECYCLE_BADGES)
                          ? LIFECYCLE_BADGES[alert.lifecycleState as AlertLifecycleState]
                          : LIFECYCLE_BADGES.OPEN;

                        return (
                          <tr
                            key={alert.id}
                            onClick={() => setSelectedAlert(alert)}
                            className={`cursor-pointer transition-colors ${
                              isSelected
                                ? "bg-[#182030] border-l-2 border-[#3b82f6]"
                                : "hover:bg-[#141824]"
                            }`}
                          >
                            <td className="py-2.5 px-3">
                              <span
                                className={`px-1.5 py-0.5 rounded text-[10px] font-bold border ${sevBadge.border} ${sevBadge.bg} ${sevBadge.text}`}
                              >
                                {alert.currentSeverity}
                                {alert.escalationCount > 0 && " ▲"}
                              </span>
                            </td>
                            <td className="py-2.5 px-3 font-semibold text-white truncate max-w-[140px]">
                              {alert.targetKey || "GLOBAL"}
                            </td>
                            <td className="py-2.5 px-3 text-[#cbd5e1] truncate max-w-[180px]">
                              {alert.alertType}
                              {alert.metricName && (
                                <span className="text-[#64748b] text-[10px] ml-1">
                                  ({alert.metricName})
                                </span>
                              )}
                            </td>
                            <td className="py-2.5 px-3">
                              <span
                                className={`px-1.5 py-0.5 rounded text-[10px] font-bold border ${lcBadge.border} ${lcBadge.bg} ${lcBadge.text}`}
                              >
                                {alert.lifecycleState}
                              </span>
                            </td>
                            <td className="py-2.5 px-3 text-[#94a3b8] text-[11px]">
                              {alert.consecutiveCount || 1} runs ({alert.occurrenceCount}x)
                            </td>
                            <td className="py-2.5 px-3 text-right">
                              <button
                                onClick={(e) => {
                                  e.stopPropagation();
                                  setSelectedAlert(alert);
                                }}
                                className="px-2 py-0.5 bg-[#1e293b] hover:bg-[#334155] text-xs text-[#60a5fa] rounded border border-[#334155]"
                              >
                                INSPECT
                              </button>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                )}
              </div>
            </div>

            {/* Right Inspector: Selected Alert & Evidence Dossier */}
            <div className="lg:col-span-5 flex flex-col bg-[#0f121a] border border-[#1f2533] rounded overflow-hidden">
              <div className="p-3 border-b border-[#1f2533] bg-[#141824] flex items-center justify-between text-xs font-mono">
                <span className="font-bold text-white">ALERT & EVIDENCE DOSSIER</span>
                {selectedAlert && (
                  <span className="text-[10px] text-[#64748b]">ID: {selectedAlert.id}</span>
                )}
              </div>

              {selectedAlert ? (
                <div className="p-4 flex-1 overflow-auto space-y-4 text-xs font-mono">
                  {/* Alert Header Summary */}
                  <div className="p-3 bg-[#121622] border border-[#1f2533] rounded space-y-2">
                    <div className="flex items-center justify-between">
                      <span className="text-sm font-bold text-white">
                        {selectedAlert.alertType}
                      </span>
                      {selectedAlert.lifecycleState in LIFECYCLE_BADGES && (
                        <span
                          className={`px-2 py-0.5 rounded text-[10px] font-bold border ${
                            LIFECYCLE_BADGES[selectedAlert.lifecycleState as AlertLifecycleState].border
                          } ${LIFECYCLE_BADGES[selectedAlert.lifecycleState as AlertLifecycleState].bg} ${
                            LIFECYCLE_BADGES[selectedAlert.lifecycleState as AlertLifecycleState].text
                          }`}
                        >
                          {selectedAlert.lifecycleState}
                        </span>
                      )}
                    </div>

                    <div className="text-[#94a3b8] text-[11px]">
                      TARGET: <span className="text-white font-semibold">{selectedAlert.targetKey || "GLOBAL"}</span>
                    </div>

                    <div className="text-[#94a3b8] text-[11px] leading-relaxed">
                      {selectedAlert.triggerDescription}
                    </div>

                    <div className="pt-2 border-t border-[#1f2533] flex flex-wrap gap-4 text-[10px] text-[#64748b]">
                      <div>
                        FIRST OBSERVED:{" "}
                        <span className="text-[#cbd5e1]">
                          {selectedAlert.firstObservedAt ? new Date(selectedAlert.firstObservedAt).toLocaleDateString() : "N/A"}
                        </span>
                      </div>
                      <div>
                        LAST OBSERVED:{" "}
                        <span className="text-[#cbd5e1]">
                          {selectedAlert.lastObservedAt ? new Date(selectedAlert.lastObservedAt).toLocaleDateString() : "N/A"}
                        </span>
                      </div>
                      <div>
                        OCCURRENCES:{" "}
                        <span className="text-[#60a5fa] font-bold">{selectedAlert.occurrenceCount}</span>
                      </div>
                      <div>
                        ESCALATIONS:{" "}
                        <span className="text-[#ef4444] font-bold">{selectedAlert.escalationCount}</span>
                      </div>
                    </div>
                  </div>

                  {/* Lifecycle Action Buttons */}
                  <div className="p-3 bg-[#141926] border border-[#262f45] rounded space-y-2">
                    <div className="text-[10px] text-[#64748b] uppercase font-bold">
                      LIFECYCLE STATE TRANSITIONS
                    </div>
                    <div className="flex flex-wrap gap-2">
                      {selectedAlert.lifecycleState !== "ACKNOWLEDGED" && selectedAlert.lifecycleState !== "RESOLVED" && (
                        <button
                          onClick={() =>
                            setActionModal({
                              type: "ACKNOWLEDGE",
                              alert: selectedAlert,
                              reason: "Acknowledged by operator in monitoring queue",
                              actor: "USER",
                              durationHours: 24,
                            })
                          }
                          className="px-2.5 py-1 bg-[#f59e0b]/20 hover:bg-[#f59e0b]/30 text-[#fbbf24] border border-[#f59e0b]/40 rounded text-xs cursor-pointer"
                        >
                          ACKNOWLEDGE
                        </button>
                      )}

                      {selectedAlert.lifecycleState !== "INVESTIGATING" && selectedAlert.lifecycleState !== "RESOLVED" && (
                        <button
                          onClick={() =>
                            setActionModal({
                              type: "INVESTIGATE",
                              alert: selectedAlert,
                              reason: "Starting active investigation",
                              actor: "USER",
                              durationHours: 24,
                            })
                          }
                          className="px-2.5 py-1 bg-[#3b82f6]/20 hover:bg-[#3b82f6]/30 text-[#60a5fa] border border-[#3b82f6]/40 rounded text-xs cursor-pointer"
                        >
                          INVESTIGATE
                        </button>
                      )}

                      {selectedAlert.lifecycleState !== "SUPPRESSED" && selectedAlert.lifecycleState !== "RESOLVED" && (
                        <button
                          onClick={() =>
                            setActionModal({
                              type: "SUPPRESS",
                              alert: selectedAlert,
                              reason: "Temporary maintenance window suppression",
                              actor: "USER",
                              durationHours: 24,
                            })
                          }
                          className="px-2.5 py-1 bg-[#64748b]/20 hover:bg-[#64748b]/30 text-[#94a3b8] border border-[#64748b]/40 rounded text-xs cursor-pointer"
                        >
                          SUPPRESS...
                        </button>
                      )}

                      {selectedAlert.lifecycleState !== "RESOLVED" && (
                        <button
                          onClick={() =>
                            setActionModal({
                              type: "RESOLVE",
                              alert: selectedAlert,
                              reason: "Condition resolved or verified acceptable",
                              actor: "USER",
                              durationHours: 24,
                            })
                          }
                          className="px-2.5 py-1 bg-[#10b981]/20 hover:bg-[#10b981]/30 text-[#34d399] border border-[#10b981]/40 rounded text-xs cursor-pointer"
                        >
                          RESOLVE
                        </button>
                      )}
                    </div>
                  </div>

                  {/* Connected Intelligence (Phases 6/7/8/9 Provenance Chain) */}
                  <div className="space-y-2">
                    <div className="text-[10px] text-[#64748b] uppercase font-bold">
                      PROVENANCE & CONNECTED INTELLIGENCE
                    </div>

                    {selectedAlert.relatedInvestigationTargetKey && (
                      <div className="p-2.5 bg-[#0c0e14] border border-[#1f2533] rounded flex items-center justify-between">
                        <div>
                          <div className="text-[10px] text-[#60a5fa] font-bold">
                            PHASE 6 INVESTIGATION TARGET
                          </div>
                          <div className="text-xs text-white">{selectedAlert.relatedInvestigationTargetKey}</div>
                        </div>
                        {navigate && (
                          <button
                            onClick={() => {
                              if (onSelectInvestigationTarget) {
                                onSelectInvestigationTarget(selectedAlert.relatedInvestigationTargetKey!);
                              }
                              navigate("06_INVESTIGATION");
                            }}
                            className="px-2 py-1 bg-[#1e293b] hover:bg-[#334155] text-[10px] text-[#60a5fa] rounded border border-[#334155]"
                          >
                            VIEW GRAPH →
                          </button>
                        )}
                      </div>
                    )}

                    {selectedAlert.relatedRemediationId && (
                      <div className="p-2.5 bg-[#0c0e14] border border-[#1f2533] rounded flex items-center justify-between">
                        <div>
                          <div className="text-[10px] text-[#10b981] font-bold">
                            PHASE 7 REMEDIATION HYPOTHESIS
                          </div>
                          <div className="text-xs text-white">ID #{selectedAlert.relatedRemediationId}</div>
                        </div>
                        {navigate && (
                          <button
                            onClick={() => navigate("07_REMEDIATION")}
                            className="px-2 py-1 bg-[#1e293b] hover:bg-[#334155] text-[10px] text-[#10b981] rounded border border-[#334155]"
                          >
                            VIEW REMEDIATION →
                          </button>
                        )}
                      </div>
                    )}

                    {selectedAlert.relatedIssueTrackId && (
                      <div className="p-2.5 bg-[#0c0e14] border border-[#1f2533] rounded flex items-center justify-between">
                        <div>
                          <div className="text-[10px] text-[#f59e0b] font-bold">
                            PHASE 9 TEMPORAL ISSUE TRACK
                          </div>
                          <div className="text-xs text-white">TRACK #{selectedAlert.relatedIssueTrackId}</div>
                        </div>
                        {navigate && (
                          <button
                            onClick={() => navigate("09_TEMPORAL")}
                            className="px-2 py-1 bg-[#1e293b] hover:bg-[#334155] text-[10px] text-[#f59e0b] rounded border border-[#334155]"
                          >
                            VIEW TIMELINE →
                          </button>
                        )}
                      </div>
                    )}

                    {/* Fingerprint & Provenance Metadata */}
                    <div className="p-2.5 bg-[#0c0e14] border border-[#1f2533] rounded text-[10px] space-y-1 text-[#64748b]">
                      <div>FINGERPRINT: <span className="text-[#94a3b8]">{selectedAlert.alertFingerprint}</span></div>
                      <div>FIRST RUN: <span className="text-[#94a3b8]">{selectedAlert.firstSeenRunId || "N/A"}</span></div>
                      <div>LAST RUN: <span className="text-[#94a3b8]">{selectedAlert.lastSeenRunId || "N/A"}</span></div>
                    </div>
                  </div>
                </div>
              ) : (
                <div className="p-8 text-center text-xs font-mono text-[#64748b]">
                  SELECT AN ALERT FROM THE QUEUE TO INSPECT EVIDENCE
                </div>
              )}
            </div>
          </div>
        )}

        {/* TAB 2: HEALTH VECTOR (9 MULTIDIMENSIONAL HEALTH DIMENSIONS) */}
        {activeTab === "HEALTH_VECTOR" && (
          <div className="space-y-4">
            <div className="p-3 bg-[#0f121a] border border-[#1f2533] rounded text-xs font-mono text-[#94a3b8]">
              Model health is evaluated deterministically across 9 independent diagnostic dimensions.
              Evidence is preserved without blind averaging.
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {dimensionVectorList.length > 0 ? (
                dimensionVectorList.map((dim: HealthDimensionEvaluation) => {
                  const meta = (dim.dimension in DIMENSION_METADATA)
                    ? DIMENSION_METADATA[dim.dimension as HealthDimension]
                    : { label: dim.dimension, code: "--" };
                  const dimStyle = HEALTH_STATE_STYLES[dim.state as ModelHealthState] || HEALTH_STATE_STYLES.UNKNOWN;

                  return (
                    <div
                      key={dim.dimension}
                      className={`p-4 bg-[#0f121a] border ${dimStyle.border} rounded flex flex-col justify-between space-y-3`}
                    >
                      <div>
                        <div className="flex items-center justify-between">
                          <div className="flex items-center gap-2">
                            <span className="text-[10px] font-mono text-[#64748b]">{meta.code}</span>
                            <span className="text-xs font-bold font-mono text-white">{meta.label}</span>
                          </div>
                          <span
                            className={`px-2 py-0.5 rounded text-[10px] font-bold border ${dimStyle.border} ${dimStyle.bg} ${dimStyle.text}`}
                          >
                            {dim.state}
                          </span>
                        </div>

                        <div className="mt-2 text-xs font-mono text-[#cbd5e1] leading-relaxed">
                          {dim.summary || "Dimension evaluated nominal."}
                        </div>
                      </div>

                      {/* Dimension Metrics & Evidence Details */}
                      <div className="pt-2 border-t border-[#1f2533] space-y-2 text-[11px] font-mono">
                        {dim.metricEvidence && dim.metricEvidence.length > 0 && (
                          <div className="space-y-1">
                            {dim.metricEvidence.map((ev, i) => (
                              <div key={i} className="flex items-center justify-between text-[#94a3b8]">
                                <span className="text-[10px]">{ev.metricName}:</span>
                                <span className="text-white font-semibold tabular-nums">
                                  {ev.value !== undefined ? String(ev.value) : ev.details}
                                </span>
                              </div>
                            ))}
                          </div>
                        )}

                        <div className="flex items-center justify-between pt-1 text-[10px] text-[#64748b]">
                          <span>ACTIVE ALERTS: {dim.alertsCount}</span>
                          {meta.defaultModuleSection && navigate && (
                            <button
                              onClick={() => navigate(meta.defaultModuleSection!)}
                              className="text-[#60a5fa] hover:text-white underline cursor-pointer"
                            >
                              RAW MODULE →
                            </button>
                          )}
                        </div>
                      </div>
                    </div>
                  );
                })
              ) : (
                <div className="col-span-3 p-8 text-center text-xs font-mono text-[#64748b]">
                  NO DIMENSION EVALUATION DATA AVAILABLE. CLICK RECALCULATE TO PROCESS OPERATIONAL RUNS.
                </div>
              )}
            </div>
          </div>
        )}

        {/* TAB 3: SNAPSHOT HISTORY TIMELINE */}
        {activeTab === "SNAPSHOT_HISTORY" && (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-4 h-full">
            {/* Timeline Table */}
            <div className="lg:col-span-8 bg-[#0f121a] border border-[#1f2533] rounded overflow-hidden flex flex-col">
              <div className="p-3 border-b border-[#1f2533] bg-[#141824] flex items-center justify-between text-xs font-mono">
                <span className="font-bold text-white">HISTORICAL HEALTH SNAPSHOTS (RUN-BY-RUN)</span>
                <span className="text-[10px] text-[#64748b]">{historySnapshots.length} EVALUATIONS RECORDED</span>
              </div>

              <div className="flex-1 overflow-auto">
                {historySnapshots.length === 0 ? (
                  <div className="p-8 text-center text-xs font-mono text-[#64748b]">
                    NO HISTORICAL HEALTH SNAPSHOTS RECORDED YET.
                  </div>
                ) : (
                  <table className="w-full text-left text-xs font-mono">
                    <thead className="bg-[#121622] text-[#64748b] border-b border-[#1f2533] sticky top-0">
                      <tr>
                        <th className="py-2 px-3">RUN ID</th>
                        <th className="py-2 px-3">EVALUATED AT</th>
                        <th className="py-2 px-3">HEALTH STATE</th>
                        <th className="py-2 px-3">INDEX</th>
                        <th className="py-2 px-3">ACTIVE / CRIT</th>
                        <th className="py-2 px-3 text-right">ACTION</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-[#1f2533]">
                      {historySnapshots.map((snap) => {
                        const isSelected = selectedSnapshot?.id === snap.id;
                        const snapStyle = HEALTH_STATE_STYLES[snap.overallState as ModelHealthState] || HEALTH_STATE_STYLES.UNKNOWN;

                        return (
                          <tr
                            key={snap.id}
                            onClick={() => setSelectedSnapshot(snap)}
                            className={`cursor-pointer transition-colors ${
                              isSelected
                                ? "bg-[#182030] border-l-2 border-[#3b82f6]"
                                : "hover:bg-[#141824]"
                            }`}
                          >
                            <td className="py-2.5 px-3 font-semibold text-[#60a5fa]">{snap.runId}</td>
                            <td className="py-2.5 px-3 text-[#94a3b8] text-[11px]">
                              {snap.timestamp ? new Date(snap.timestamp).toLocaleString() : "N/A"}
                            </td>
                            <td className="py-2.5 px-3">
                              <span
                                className={`px-2 py-0.5 rounded text-[10px] font-bold border ${snapStyle.border} ${snapStyle.bg} ${snapStyle.text}`}
                              >
                                {snap.overallState}
                              </span>
                            </td>
                            <td className="py-2.5 px-3 text-white font-bold tabular-nums">
                              {snap.healthIndex !== undefined && snap.healthIndex !== null ? snap.healthIndex : "N/A"}
                            </td>
                            <td className="py-2.5 px-3 text-[#cbd5e1] text-[11px]">
                              {snap.activeAlertsCount} active / {snap.criticalAlertsCount} crit
                            </td>
                            <td className="py-2.5 px-3 text-right">
                              <button
                                onClick={(e) => {
                                  e.stopPropagation();
                                  setSelectedSnapshot(snap);
                                }}
                                className="px-2 py-0.5 bg-[#1e293b] hover:bg-[#334155] text-xs text-[#60a5fa] rounded border border-[#334155]"
                              >
                                INSPECT
                              </button>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                )}
              </div>
            </div>

            {/* Snapshot Detail View */}
            <div className="lg:col-span-4 bg-[#0f121a] border border-[#1f2533] rounded p-4 text-xs font-mono space-y-3 overflow-auto">
              <div className="font-bold text-white border-b border-[#1f2533] pb-2">
                SNAPSHOT DETAILS & PROVENANCE
              </div>

              {selectedSnapshot ? (
                <div className="space-y-3">
                  <div className="p-3 bg-[#121622] border border-[#1f2533] rounded space-y-1.5">
                    <div className="text-[10px] text-[#64748b]">OPERATIONAL RUN</div>
                    <div className="text-sm font-bold text-white">{selectedSnapshot.runId}</div>
                    <div className="text-[10px] text-[#64748b]">
                      POLICY: v{selectedSnapshot.policyVersion} | ENGINE: v{selectedSnapshot.decisionEngineVersion}
                    </div>
                  </div>

                  <div className="space-y-1">
                    <div className="text-[10px] text-[#64748b] uppercase font-bold">DIMENSION STATES AT SNAPSHOT</div>
                    {selectedSnapshot.dimensionStates &&
                      Object.entries(selectedSnapshot.dimensionStates).map(([dim, ev]) => {
                        const stateStr = typeof ev === "string" ? ev : (ev as HealthDimensionEvaluation)?.state || "UNKNOWN";
                        const dimSt = HEALTH_STATE_STYLES[stateStr as ModelHealthState] || HEALTH_STATE_STYLES.UNKNOWN;
                        return (
                          <div
                            key={dim}
                            className="p-1.5 bg-[#0c0e14] border border-[#1f2533] rounded flex items-center justify-between"
                          >
                            <span className="text-[10px] text-[#94a3b8]">{dim}</span>
                            <span
                              className={`px-1.5 py-0.2 rounded text-[9px] font-bold border ${dimSt.border} ${dimSt.bg} ${dimSt.text}`}
                            >
                              {stateStr}
                            </span>
                          </div>
                        );
                      })}
                  </div>
                </div>
              ) : (
                <div className="p-8 text-center text-[#64748b]">
                  SELECT A SNAPSHOT ROW TO INSPECT ITS RECORDED DIMENSION STATES
                </div>
              )}
            </div>
          </div>
        )}

        {/* TAB 4: LIFECYCLE AUDIT LOG */}
        {activeTab === "AUDIT_LOG" && (
          <div className="bg-[#0f121a] border border-[#1f2533] rounded p-4 text-xs font-mono space-y-3">
            <div className="font-bold text-white border-b border-[#1f2533] pb-2">
              LIFECYCLE AUDIT EVENTS & OPERATIONAL LOG
            </div>
            <div className="space-y-2 max-h-[500px] overflow-auto">
              {!decision?.recentEvents || decision.recentEvents.length === 0 ? (
                <div className="p-8 text-center text-[#64748b]">
                  NO LIFECYCLE AUDIT EVENTS RECORDED YET.
                </div>
              ) : (
                decision.recentEvents.map((event: DiagnosticAlertEvent, idx: number) => (
                  <div
                    key={idx}
                    className="p-2.5 bg-[#0c0e14] border border-[#1f2533] rounded flex items-start justify-between gap-4"
                  >
                    <div className="space-y-0.5">
                      <div className="flex items-center gap-2">
                        <span className="text-[#60a5fa] font-bold">[{event.action || "EVENT"}]</span>
                        <span className="text-white font-semibold">
                          ALERT #{event.alertId}
                        </span>
                        <span className="text-[10px] text-[#64748b]">
                          {event.previousState || "NONE"} → {event.newState}
                        </span>
                      </div>
                      <div className="text-[11px] text-[#94a3b8]">{event.reason || "Lifecycle transition"}</div>
                      <div className="text-[9px] text-[#64748b]">ACTOR: {event.actor}</div>
                    </div>
                    <div className="text-[10px] text-[#64748b] shrink-0">
                      {event.timestamp ? new Date(event.timestamp).toLocaleString() : "N/A"}
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        )}
      </div>

      {/* LIFECYCLE ACTION MODAL */}
      {actionModal.type && actionModal.alert && (
        <div className="fixed inset-0 bg-black/75 backdrop-blur-xs flex items-center justify-center z-50 p-4">
          <div className="bg-[#121622] border border-[#262f45] rounded-lg max-w-md w-full p-5 space-y-4 text-xs font-mono shadow-2xl">
            <div className="flex items-center justify-between border-b border-[#1f2533] pb-2">
              <span className="font-bold text-white text-sm">
                {actionModal.type} ALERT
              </span>
              <button
                onClick={() => setActionModal({ type: null, alert: null, reason: "", actor: "USER", durationHours: 24 })}
                className="text-[#94a3b8] hover:text-white"
              >
                ✕
              </button>
            </div>

            <div className="text-[#cbd5e1] space-y-1">
              <div>
                TARGET: <span className="text-white font-semibold">{actionModal.alert.targetKey || "GLOBAL"}</span>
              </div>
              <div>
                TYPE: <span className="text-[#60a5fa]">{actionModal.alert.alertType}</span>
              </div>
            </div>

            <div className="space-y-1">
              <label className="text-[10px] text-[#64748b] uppercase font-bold">ACTOR</label>
              <input
                type="text"
                value={actionModal.actor}
                onChange={(e) => setActionModal({ ...actionModal, actor: e.target.value })}
                className="w-full bg-[#0c0e14] border border-[#262f45] rounded px-3 py-1.5 text-white"
                placeholder="USER / ONCALL / SYSTEM"
              />
            </div>

            <div className="space-y-1">
              <label className="text-[10px] text-[#64748b] uppercase font-bold">RATIONALE / REASON</label>
              <textarea
                value={actionModal.reason}
                onChange={(e) => setActionModal({ ...actionModal, reason: e.target.value })}
                rows={3}
                className="w-full bg-[#0c0e14] border border-[#262f45] rounded px-3 py-1.5 text-white resize-none"
                placeholder="Enter justification for this lifecycle transition..."
              />
            </div>

            {actionModal.type === "SUPPRESS" && (
              <div className="space-y-1">
                <label className="text-[10px] text-[#64748b] uppercase font-bold">SUPPRESSION DURATION (HOURS)</label>
                <input
                  type="number"
                  value={actionModal.durationHours}
                  onChange={(e) => setActionModal({ ...actionModal, durationHours: Number(e.target.value) })}
                  className="w-full bg-[#0c0e14] border border-[#262f45] rounded px-3 py-1.5 text-white"
                  min={1}
                  max={720}
                />
              </div>
            )}

            <div className="flex items-center justify-end gap-2 pt-2 border-t border-[#1f2533]">
              <button
                onClick={() => setActionModal({ type: null, alert: null, reason: "", actor: "USER", durationHours: 24 })}
                className="px-3 py-1.5 bg-[#1e293b] hover:bg-[#334155] text-[#94a3b8] rounded"
              >
                CANCEL
              </button>
              <button
                onClick={executeLifecycleAction}
                className="px-4 py-1.5 bg-[#3b82f6] hover:bg-[#2563eb] text-white font-bold rounded shadow"
              >
                CONFIRM TRANSITION
              </button>
            </div>
          </div>
        </div>
      )}

      {/* POLICY CONFIGURATION MODAL */}
      {isPolicyModalOpen && policyForm && (
        <div className="fixed inset-0 bg-black/75 backdrop-blur-xs flex items-center justify-center z-50 p-4">
          <div className="bg-[#121622] border border-[#262f45] rounded-lg max-w-xl w-full p-5 space-y-4 text-xs font-mono shadow-2xl max-h-[90vh] overflow-auto">
            <div className="flex items-center justify-between border-b border-[#1f2533] pb-2">
              <span className="font-bold text-white text-sm">
                EDIT MONITORING POLICY: {policyForm.modelLineageId}
              </span>
              <button
                onClick={() => setIsPolicyModalOpen(false)}
                className="text-[#94a3b8] hover:text-white"
              >
                ✕
              </button>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <div className="space-y-1">
                <label className="text-[10px] text-[#64748b] uppercase font-bold">OBSERVATION WINDOW</label>
                <select
                  value={policyForm.observationWindow}
                  onChange={(e) => setPolicyForm({ ...policyForm, observationWindow: e.target.value })}
                  className="w-full bg-[#0c0e14] border border-[#262f45] rounded px-3 py-1.5 text-white"
                >
                  <option value="LAST_5_RUNS">LAST_5_RUNS</option>
                  <option value="LAST_10_RUNS">LAST_10_RUNS</option>
                  <option value="LAST_20_RUNS">LAST_20_RUNS</option>
                  <option value="ALL_AVAILABLE">ALL_AVAILABLE</option>
                </select>
              </div>

              <div className="space-y-1">
                <label className="text-[10px] text-[#64748b] uppercase font-bold">MIN BASELINE RUNS</label>
                <input
                  type="number"
                  value={policyForm.minBaselineRunsRequired}
                  onChange={(e) => setPolicyForm({ ...policyForm, minBaselineRunsRequired: Number(e.target.value) })}
                  className="w-full bg-[#0c0e14] border border-[#262f45] rounded px-3 py-1.5 text-white"
                  min={1}
                />
              </div>

              <div className="space-y-1">
                <label className="text-[10px] text-[#64748b] uppercase font-bold">ALERT PERSISTENCE (RUNS)</label>
                <input
                  type="number"
                  value={policyForm.alertPersistenceThreshold}
                  onChange={(e) => setPolicyForm({ ...policyForm, alertPersistenceThreshold: Number(e.target.value) })}
                  className="w-full bg-[#0c0e14] border border-[#262f45] rounded px-3 py-1.5 text-white"
                  min={1}
                />
              </div>

              <div className="space-y-1">
                <label className="text-[10px] text-[#64748b] uppercase font-bold">RECOVERY STABILIZATION (RUNS)</label>
                <input
                  type="number"
                  value={policyForm.recoveryConsecutiveRuns}
                  onChange={(e) => setPolicyForm({ ...policyForm, recoveryConsecutiveRuns: Number(e.target.value) })}
                  className="w-full bg-[#0c0e14] border border-[#262f45] rounded px-3 py-1.5 text-white"
                  min={1}
                />
              </div>

              <div className="space-y-1">
                <label className="text-[10px] text-[#64748b] uppercase font-bold">ALERT COOLDOWN (RUNS)</label>
                <input
                  type="number"
                  value={policyForm.alertCooldownRuns}
                  onChange={(e) => setPolicyForm({ ...policyForm, alertCooldownRuns: Number(e.target.value) })}
                  className="w-full bg-[#0c0e14] border border-[#262f45] rounded px-3 py-1.5 text-white"
                  min={0}
                />
              </div>

              <div className="space-y-1">
                <label className="text-[10px] text-[#64748b] uppercase font-bold">EXPERIMENT OVERLAY</label>
                <select
                  value={policyForm.experimentOverlayEnabled ? "TRUE" : "FALSE"}
                  onChange={(e) => setPolicyForm({ ...policyForm, experimentOverlayEnabled: e.target.value === "TRUE" })}
                  className="w-full bg-[#0c0e14] border border-[#262f45] rounded px-3 py-1.5 text-white"
                >
                  <option value="TRUE">ENABLED</option>
                  <option value="FALSE">DISABLED</option>
                </select>
              </div>
            </div>

            <div className="flex items-center justify-end gap-2 pt-2 border-t border-[#1f2533]">
              <button
                onClick={() => setIsPolicyModalOpen(false)}
                className="px-3 py-1.5 bg-[#1e293b] hover:bg-[#334155] text-[#94a3b8] rounded"
              >
                CANCEL
              </button>
              <button
                onClick={handleSavePolicy}
                className="px-4 py-1.5 bg-[#3b82f6] hover:bg-[#2563eb] text-white font-bold rounded shadow"
              >
                SAVE POLICY
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
