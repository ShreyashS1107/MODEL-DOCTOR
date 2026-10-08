"use client";

import React, { useState, useEffect, useMemo } from "react";
import {
  ModelLineageHistory,
  TemporalMetricHistory,
  IssueTrack,
  TemporalAlert,
  ChangePoint,
  RemediationDurability,
  NavSection,
} from "@/types/diagnostics";
import {
  getDiagnosticTemporalHistory,
  recalculateDiagnosticTemporal,
  RunSummary,
} from "@/lib/api";

export interface TemporalViewProps {
  runId: string;
  runSummary?: RunSummary | null;
  onSelectSection?: (section: NavSection) => void;
  onSelectFeature?: (featureName: string) => void;
  onRefresh?: () => void;
  onNavigateSection?: (section: NavSection) => void;
  onSelectInvestigationTarget?: (targetKey: string) => void;
}

export const TemporalView: React.FC<TemporalViewProps> = ({
  runId,
  runSummary,
  onSelectSection,
  onSelectFeature,
  onRefresh,
  onNavigateSection,
  onSelectInvestigationTarget,
}) => {
  const navigate = onSelectSection || onNavigateSection;

  const [history, setHistory] = useState<ModelLineageHistory | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isRecalculating, setIsRecalculating] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [statusNotification, setStatusNotification] = useState<string | null>(null);

  // Filters and controls
  const [selectedWindow, setSelectedWindow] = useState<string>("ALL_AVAILABLE");
  const [showExperiments, setShowExperiments] = useState<boolean>(false);
  const [selectedMetricKey, setSelectedMetricKey] = useState<string>("");
  const [selectedIssueTrack, setSelectedIssueTrack] = useState<IssueTrack | null>(null);
  const [selectedAlert, setSelectedAlert] = useState<TemporalAlert | null>(null);
  const [activeTab, setActiveTab] = useState<"TIMELINE" | "METRICS" | "TRACKS" | "ALERTS" | "DURABILITY">("TIMELINE");

  const loadData = async (windowChoice = selectedWindow) => {
    try {
      setIsLoading(true);
      setErrorMessage(null);
      const data = await getDiagnosticTemporalHistory(runId, windowChoice);
      setHistory(data);

      if (data.metricHistories && data.metricHistories.length > 0) {
        if (!selectedMetricKey || !data.metricHistories.some((m: TemporalMetricHistory) => getMetricKey(m) === selectedMetricKey)) {
          setSelectedMetricKey(getMetricKey(data.metricHistories[0]));
        }
      }
      if (data.issueTracks && data.issueTracks.length > 0 && !selectedIssueTrack) {
        setSelectedIssueTrack(data.issueTracks[0]);
      }
    } catch (err: any) {
      setErrorMessage(err.message || "Failed to load temporal intelligence data.");
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadData(selectedWindow);
  }, [runId, selectedWindow]);

  const handleRecalculate = async () => {
    try {
      setIsRecalculating(true);
      setStatusNotification(null);
      const res = await recalculateDiagnosticTemporal(runId);
      setStatusNotification(
        `Recalculation Complete: ${res.runsProcessed} runs analyzed, ${res.observationsExtracted} observations, ${res.issueTracksBuilt} tracks built.`
      );
      await loadData(selectedWindow);
    } catch (err: any) {
      setErrorMessage(err.message || "Recalculation failed.");
    } finally {
      setIsRecalculating(false);
    }
  };

  const getMetricKey = (m: TemporalMetricHistory) =>
    `${m.module}::${m.metricName}::${m.targetKey || "GLOBAL"}`;

  const selectedMetric = useMemo(() => {
    if (!history || !history.metricHistories) return null;
    return history.metricHistories.find((m) => getMetricKey(m) === selectedMetricKey) || null;
  }, [history, selectedMetricKey]);

  // Color helpers
  const getSeverityBadge = (severity: string) => {
    switch (severity?.toUpperCase()) {
      case "CRITICAL":
        return "bg-rose-950/80 text-rose-300 border-rose-800/80";
      case "HIGH":
      case "WARNING":
        return "bg-amber-950/80 text-amber-300 border-amber-800/80";
      case "MEDIUM":
      case "OBSERVATION":
        return "bg-yellow-950/80 text-yellow-300 border-yellow-800/80";
      case "LOW":
      case "NOMINAL":
      case "RECOVERED":
        return "bg-emerald-950/80 text-emerald-300 border-emerald-800/80";
      default:
        return "bg-slate-900 text-slate-400 border-slate-700";
    }
  };

  const getStatusBadge = (status: string) => {
    switch (status?.toUpperCase()) {
      case "PERSISTENT":
        return "bg-purple-950/80 text-purple-300 border-purple-800/80";
      case "EMERGING":
        return "bg-cyan-950/80 text-cyan-300 border-cyan-800/80";
      case "RECURRING":
        return "bg-orange-950/80 text-orange-300 border-orange-800/80";
      case "ESCALATING":
        return "bg-rose-950/80 text-rose-300 border-rose-800/80";
      case "RECOVERED":
        return "bg-emerald-950/80 text-emerald-300 border-emerald-800/80";
      case "TRANSIENT":
        return "bg-slate-900 text-slate-400 border-slate-700";
      default:
        return "bg-zinc-900 text-zinc-400 border-zinc-700";
    }
  };

  const getDurabilityBadge = (status: string) => {
    switch (status?.toUpperCase()) {
      case "SUSTAINED":
        return "bg-emerald-950/80 text-emerald-300 border-emerald-800/80";
      case "TEMPORARY":
      case "FAILED_TO_SUSTAIN":
        return "bg-rose-950/80 text-rose-300 border-rose-800/80";
      case "INSUFFICIENT_FOLLOWUP":
        return "bg-amber-950/80 text-amber-300 border-amber-800/80";
      default:
        return "bg-slate-900 text-slate-400 border-slate-700";
    }
  };

  if (isLoading && !history) {
    return (
      <div className="flex h-96 items-center justify-center">
        <div className="flex flex-col items-center gap-3 text-slate-400">
          <div className="h-8 w-8 animate-spin rounded-full border-2 border-cyan-500 border-t-transparent" />
          <span className="font-mono text-xs tracking-wider uppercase">Loading Temporal Intelligence...</span>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6 pb-12">
      {/* 1. Header & Non-Causality Banner */}
      <div className="flex flex-col gap-3 rounded-lg border border-slate-800 bg-slate-900/60 p-5 backdrop-blur">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-3">
              <div className="flex h-7 w-7 items-center justify-center rounded border border-cyan-500/40 bg-cyan-950/40 text-xs font-mono font-bold text-cyan-400">
                09
              </div>
              <h1 className="font-mono text-xl font-bold tracking-tight text-slate-100">
                TEMPORAL INTELLIGENCE & LONGITUDINAL MONITORING
              </h1>
            </div>
            <p className="mt-1 font-mono text-xs text-slate-400">
              Model Lineage:{" "}
              <span className="text-cyan-300 font-semibold">{history?.modelLineageId || "Unknown Lineage"}</span>{" "}
              — Multi-Run Temporal Signals, Step Change-Points, Issue Tracks, and Durability Verification
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            {/* Window selector */}
            <div className="flex items-center rounded border border-slate-700 bg-slate-800/80 p-1 text-xs font-mono">
              <span className="px-2 text-slate-400">WINDOW:</span>
              {["LAST_3", "LAST_5", "LAST_10", "ALL_AVAILABLE"].map((win) => (
                <button
                  key={win}
                  onClick={() => setSelectedWindow(win)}
                  className={`rounded px-2.5 py-1 transition ${
                    selectedWindow === win
                      ? "bg-cyan-600 font-bold text-white shadow"
                      : "text-slate-400 hover:text-slate-200"
                  }`}
                >
                  {win.replace("_", " ")}
                </button>
              ))}
            </div>

            {/* Experiment Overlay Toggle */}
            <button
              onClick={() => setShowExperiments(!showExperiments)}
              className={`flex items-center gap-2 rounded border px-3 py-1.5 font-mono text-xs transition ${
                showExperiments
                  ? "border-purple-500 bg-purple-950/60 text-purple-200"
                  : "border-slate-700 bg-slate-800/80 text-slate-400 hover:text-slate-200"
              }`}
            >
              <div className={`h-2 w-2 rounded-full ${showExperiments ? "bg-purple-400" : "bg-slate-500"}`} />
              EXPERIMENT OVERLAY
            </button>

            {/* Recalculate Button */}
            <button
              onClick={handleRecalculate}
              disabled={isRecalculating}
              className="flex items-center gap-2 rounded border border-cyan-500/50 bg-cyan-950/40 px-3.5 py-1.5 font-mono text-xs font-semibold text-cyan-300 hover:bg-cyan-900/50 disabled:opacity-50"
            >
              {isRecalculating ? (
                <>
                  <div className="h-3 w-3 animate-spin rounded-full border border-cyan-300 border-t-transparent" />
                  RECALCULATING...
                </>
              ) : (
                <>
                  <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                  </svg>
                  RECALCULATE
                </>
              )}
            </button>
          </div>
        </div>

        {/* Associative Non-Causality Banner */}
        <div className="flex items-center gap-2 rounded border border-cyan-900/40 bg-cyan-950/20 px-3 py-1.5 text-xs font-mono text-cyan-300/90">
          <span className="font-bold text-cyan-400">[MONITORING DIRECTIVE]</span>
          <span>
            Temporal observations reflect deterministic longitudinal associations. Temporal ordering alone does not establish causal direction. Provenance is preserved for all historical runs.
          </span>
        </div>

        {statusNotification && (
          <div className="rounded border border-emerald-900/40 bg-emerald-950/30 px-3 py-2 font-mono text-xs text-emerald-300">
            {statusNotification}
          </div>
        )}

        {errorMessage && (
          <div className="rounded border border-rose-900/40 bg-rose-950/30 px-3 py-2 font-mono text-xs text-rose-300">
            {errorMessage}
          </div>
        )}
      </div>

      {/* 2. Top HUD Metrics */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-7">
        <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-3 text-center">
          <div className="font-mono text-[10px] uppercase tracking-wider text-slate-400">Runs Observed</div>
          <div className="mt-1 font-mono text-2xl font-bold text-slate-100">{history?.totalRunsCount || 0}</div>
          <div className="font-mono text-[10px] text-slate-400">
            {history?.baselineRunsCount || 0} base / {history?.experimentRunsCount || 0} exp
          </div>
        </div>

        <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-3 text-center">
          <div className="font-mono text-[10px] uppercase tracking-wider text-slate-400">Active Issues</div>
          <div className="mt-1 font-mono text-2xl font-bold text-amber-400">{history?.activeIssuesCount || 0}</div>
          <div className="font-mono text-[10px] text-slate-400">unresolved tracks</div>
        </div>

        <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-3 text-center">
          <div className="font-mono text-[10px] uppercase tracking-wider text-slate-400">Persistent</div>
          <div className="mt-1 font-mono text-2xl font-bold text-purple-400">{history?.persistentIssuesCount || 0}</div>
          <div className="font-mono text-[10px] text-slate-400">≥3 consecutive runs</div>
        </div>

        <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-3 text-center">
          <div className="font-mono text-[10px] uppercase tracking-wider text-slate-400">Emerging</div>
          <div className="mt-1 font-mono text-2xl font-bold text-cyan-400">{history?.emergingIssuesCount || 0}</div>
          <div className="font-mono text-[10px] text-slate-400">newly observed</div>
        </div>

        <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-3 text-center">
          <div className="font-mono text-[10px] uppercase tracking-wider text-slate-400">Recurring</div>
          <div className="mt-1 font-mono text-2xl font-bold text-orange-400">{history?.recurringIssuesCount || 0}</div>
          <div className="font-mono text-[10px] text-slate-400">returned after gap</div>
        </div>

        <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-3 text-center">
          <div className="font-mono text-[10px] uppercase tracking-wider text-slate-400">Active Alerts</div>
          <div className="mt-1 font-mono text-2xl font-bold text-rose-400">{history?.activeAlertsCount || 0}</div>
          <div className="font-mono text-[10px] text-slate-400">{history?.criticalAlertsCount || 0} critical</div>
        </div>

        <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-3 text-center">
          <div className="font-mono text-[10px] uppercase tracking-wider text-slate-400">Change Points</div>
          <div className="mt-1 font-mono text-2xl font-bold text-emerald-400">{history?.changePointsCount || 0}</div>
          <div className="font-mono text-[10px] text-slate-400">step shifts detected</div>
        </div>
      </div>

      {/* 3. Model History Strip */}
      <div className="rounded-lg border border-slate-800 bg-slate-900/60 p-4">
        <div className="mb-3 flex items-center justify-between">
          <div className="flex items-center gap-2 font-mono text-xs font-bold text-slate-300">
            <span className="text-cyan-400">●</span> OPERATIONAL LINEAGE TIMELINE
          </div>
          <div className="font-mono text-[11px] text-slate-400">
            Current Run: <span className="font-bold text-cyan-300">{runId}</span>
          </div>
        </div>

        <div className="flex items-center gap-2 overflow-x-auto pb-2 pt-1">
          {history?.orderedRuns.map((r, idx) => {
            const isCurrent = r.runId === runId;
            return (
              <React.Fragment key={r.runId}>
                {idx > 0 && <div className="h-[2px] w-6 shrink-0 bg-slate-800" />}
                <div
                  className={`group relative flex shrink-0 flex-col rounded-lg border p-3 font-mono transition ${
                    isCurrent
                      ? "border-cyan-500 bg-cyan-950/40 shadow-lg shadow-cyan-950/50"
                      : "border-slate-800 bg-slate-900/80 hover:border-slate-700"
                  }`}
                  style={{ minWidth: "160px" }}
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-[10px] text-slate-400">RUN #{idx + 1}</span>
                    <span
                      className={`rounded px-1.5 py-0.5 text-[9px] font-bold ${
                        r.status === "COMPLETED"
                          ? "bg-emerald-950/80 text-emerald-400 border border-emerald-800/80"
                          : "bg-slate-800 text-slate-300"
                      }`}
                    >
                      {r.status}
                    </span>
                  </div>
                  <div className="mt-1 truncate text-xs font-bold text-slate-200" title={r.runId}>
                    {r.runId}
                  </div>
                  <div className="mt-1 flex items-center justify-between text-[10px] text-slate-400">
                    <span>{r.completedModules}/{r.totalModules} modules</span>
                    <span>{new Date(r.createdAt).toLocaleDateString()}</span>
                  </div>
                  {isCurrent && (
                    <div className="mt-1.5 flex items-center justify-center rounded bg-cyan-900/60 py-0.5 text-[9px] font-bold text-cyan-300">
                      CURRENT VIEW
                    </div>
                  )}
                </div>
              </React.Fragment>
            );
          })}
        </div>
      </div>

      {/* 4. Tab Navigation */}
      <div className="flex border-b border-slate-800">
        {[
          { id: "TIMELINE", label: "TIMELINE OVERVIEW", count: history?.orderedRuns.length },
          { id: "METRICS", label: "METRIC TIME-SERIES", count: history?.metricHistories.length },
          { id: "TRACKS", label: "ISSUE TRACKS", count: history?.issueTracks.length },
          { id: "ALERTS", label: "ALERT CONSOLE", count: history?.alerts.length },
          { id: "DURABILITY", label: "REMEDIATION DURABILITY", count: history?.remediationDurability.length },
        ].map((tab) => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id as any)}
            className={`flex items-center gap-2 border-b-2 px-5 py-3 font-mono text-xs font-semibold transition ${
              activeTab === tab.id
                ? "border-cyan-500 bg-slate-900/80 text-cyan-300"
                : "border-transparent text-slate-400 hover:text-slate-200"
            }`}
          >
            {tab.label}
            {tab.count !== undefined && (
              <span className="rounded bg-slate-800 px-1.5 py-0.5 text-[10px] text-slate-300">
                {tab.count}
              </span>
            )}
          </button>
        ))}
      </div>

      {/* TAB CONTENT 1: TIMELINE OVERVIEW */}
      {activeTab === "TIMELINE" && (
        <div className="space-y-6">
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
            {/* Recent Alerts Feed */}
            <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-5">
              <div className="mb-4 flex items-center justify-between">
                <h3 className="font-mono text-xs font-bold uppercase tracking-wider text-slate-200">
                  Critical Temporal Alerts
                </h3>
                <span className="font-mono text-xs text-slate-400">{history?.alerts.length || 0} total</span>
              </div>
              <div className="space-y-2.5">
                {history?.alerts.slice(0, 5).map((a, idx) => (
                  <div
                    key={idx}
                    onClick={() => {
                      setSelectedAlert(a);
                      setActiveTab("ALERTS");
                    }}
                    className="cursor-pointer rounded-lg border border-slate-800 bg-slate-900/80 p-3 hover:border-slate-700 transition"
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className={`rounded border px-2 py-0.5 font-mono text-[10px] font-bold ${getSeverityBadge(a.priority)}`}>
                        {a.priority}
                      </span>
                      <span className="font-mono text-xs font-semibold text-slate-200">{a.alertType}</span>
                      <span className="font-mono text-[10px] text-slate-400">{new Date(a.createdAt).toLocaleTimeString()}</span>
                    </div>
                    <p className="mt-1.5 font-mono text-xs text-slate-300">{a.triggerDescription}</p>
                    <div className="mt-2 flex items-center justify-between text-[10px] font-mono text-slate-400">
                      <span>Target: <span className="text-cyan-300">{a.targetKey || "GLOBAL"}</span></span>
                      <span>Metric: <span className="text-slate-200">{a.metricName || "N/A"}</span></span>
                    </div>
                  </div>
                ))}
                {(!history?.alerts || history.alerts.length === 0) && (
                  <div className="py-8 text-center font-mono text-xs text-slate-400">No active temporal alerts.</div>
                )}
              </div>
            </div>

            {/* Persistent & Emerging Tracks */}
            <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-5">
              <div className="mb-4 flex items-center justify-between">
                <h3 className="font-mono text-xs font-bold uppercase tracking-wider text-slate-200">
                  Dominant Issue Tracks
                </h3>
                <span className="font-mono text-xs text-slate-400">{history?.issueTracks.length || 0} tracks</span>
              </div>
              <div className="space-y-2.5">
                {history?.issueTracks.slice(0, 5).map((t, idx) => (
                  <div
                    key={idx}
                    onClick={() => {
                      setSelectedIssueTrack(t);
                      setActiveTab("TRACKS");
                    }}
                    className="cursor-pointer rounded-lg border border-slate-800 bg-slate-900/80 p-3 hover:border-slate-700 transition"
                  >
                    <div className="flex items-center justify-between">
                      <div className="font-mono text-xs font-bold text-cyan-300">{t.targetKey}</div>
                      <span className={`rounded border px-2 py-0.5 font-mono text-[10px] font-bold ${getStatusBadge(t.status)}`}>
                        {t.status}
                      </span>
                    </div>
                    <div className="mt-2 grid grid-cols-3 gap-2 font-mono text-[10px] text-slate-400">
                      <div>Peak: <span className={`font-bold ${getSeverityBadge(t.peakSeverity)} px-1 rounded`}>{t.peakSeverity}</span></div>
                      <div>Consecutive: <span className="text-slate-200 font-bold">{t.consecutiveCount} runs</span></div>
                      <div>Modules: <span className="text-slate-200">{t.modulesInvolved.join(", ")}</span></div>
                    </div>
                  </div>
                ))}
                {(!history?.issueTracks || history.issueTracks.length === 0) && (
                  <div className="py-8 text-center font-mono text-xs text-slate-400">No target issue tracks identified.</div>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* TAB CONTENT 2: METRICS TIME-SERIES */}
      {activeTab === "METRICS" && (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-4">
          {/* Metric Selector Column */}
          <div className="lg:col-span-1 space-y-2 rounded-lg border border-slate-800 bg-slate-900/50 p-4 max-h-[600px] overflow-y-auto">
            <div className="font-mono text-xs font-bold uppercase tracking-wider text-slate-400 pb-2 border-b border-slate-800">
              Select Metric ({history?.metricHistories.length || 0})
            </div>
            {history?.metricHistories.map((m) => {
              const key = getMetricKey(m);
              const isSelected = key === selectedMetricKey;
              return (
                <button
                  key={key}
                  onClick={() => setSelectedMetricKey(key)}
                  className={`w-full text-left rounded-lg p-2.5 font-mono text-xs transition border ${
                    isSelected
                      ? "border-cyan-500 bg-cyan-950/40 text-cyan-200"
                      : "border-slate-800/80 bg-slate-900/60 text-slate-400 hover:border-slate-700 hover:text-slate-200"
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <span className="font-bold text-slate-200">{m.metricName}</span>
                    <span className={`text-[9px] px-1.5 py-0.5 rounded ${getStatusBadge(m.trendDirection)}`}>
                      {m.trendDirection}
                    </span>
                  </div>
                  <div className="mt-1 flex items-center justify-between text-[10px] text-slate-400">
                    <span>{m.module}</span>
                    <span>{m.targetKey || "GLOBAL"}</span>
                  </div>
                </button>
              );
            })}
          </div>

          {/* Metric Detail & Graph Column */}
          <div className="lg:col-span-3 space-y-5">
            {selectedMetric ? (
              <div className="rounded-lg border border-slate-800 bg-slate-900/60 p-5 space-y-5">
                {/* Metric Summary Bar */}
                <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-800 pb-4">
                  <div>
                    <div className="flex items-center gap-3">
                      <h2 className="font-mono text-lg font-bold text-slate-100">{selectedMetric.metricName}</h2>
                      <span className={`rounded border px-2 py-0.5 font-mono text-[10px] font-bold ${getStatusBadge(selectedMetric.trendDirection)}`}>
                        TREND: {selectedMetric.trendDirection}
                      </span>
                    </div>
                    <p className="mt-1 font-mono text-xs text-slate-400">
                      Module: <span className="text-cyan-300">{selectedMetric.module}</span> | Target:{" "}
                      <span className="text-slate-200">{selectedMetric.targetKey || "GLOBAL"}</span>
                    </p>
                  </div>

                  <div className="flex items-center gap-4 font-mono text-xs">
                    <div className="text-right">
                      <div className="text-slate-400 text-[10px]">LATEST VALUE</div>
                      <div className="text-base font-bold text-cyan-300">{selectedMetric.latestValue?.toFixed(4) ?? "N/A"}</div>
                    </div>
                    <div className="text-right">
                      <div className="text-slate-400 text-[10px]">DELTA</div>
                      <div className={`text-base font-bold ${
                        (selectedMetric.absoluteDelta ?? 0) > 0 ? "text-emerald-400" : (selectedMetric.absoluteDelta ?? 0) < 0 ? "text-rose-400" : "text-slate-400"
                      }`}>
                        {selectedMetric.absoluteDelta !== undefined ? (selectedMetric.absoluteDelta > 0 ? `+${selectedMetric.absoluteDelta.toFixed(4)}` : selectedMetric.absoluteDelta.toFixed(4)) : "N/A"}
                      </div>
                    </div>
                  </div>
                </div>

                {/* Statistical Trend Metrics */}
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 font-mono text-xs">
                  <div className="rounded border border-slate-800 bg-slate-900/80 p-2.5">
                    <div className="text-[10px] text-slate-400">SLOPE (per run)</div>
                    <div className="font-bold text-slate-200">{selectedMetric.slope?.toFixed(5) ?? "N/A"}</div>
                  </div>
                  <div className="rounded border border-slate-800 bg-slate-900/80 p-2.5">
                    <div className="text-[10px] text-slate-400">R² FIT</div>
                    <div className="font-bold text-slate-200">{selectedMetric.rSquared?.toFixed(4) ?? "N/A"}</div>
                  </div>
                  <div className="rounded border border-slate-800 bg-slate-900/80 p-2.5">
                    <div className="text-[10px] text-slate-400">MANN-KENDALL TAU</div>
                    <div className="font-bold text-slate-200">{selectedMetric.mannKendallTau?.toFixed(4) ?? "N/A"}</div>
                  </div>
                  <div className="rounded border border-slate-800 bg-slate-900/80 p-2.5">
                    <div className="text-[10px] text-slate-400">P-VALUE</div>
                    <div className="font-bold text-slate-200">{selectedMetric.mannKendallPValue?.toFixed(4) ?? "N/A"}</div>
                  </div>
                </div>

                {/* Historical Points Table / Visual Points */}
                <div>
                  <div className="mb-2 flex items-center justify-between font-mono text-xs font-bold text-slate-300">
                    <span>OBSERVATION POINTS ({selectedMetric.baselinePoints.length} operational)</span>
                    {showExperiments && <span className="text-purple-400">{selectedMetric.experimentPoints?.length || 0} experiments overlaid</span>}
                  </div>
                  <div className="overflow-x-auto rounded border border-slate-800 bg-slate-950/40">
                    <table className="w-full text-left font-mono text-xs">
                      <thead className="border-b border-slate-800 bg-slate-900/80 text-[10px] uppercase text-slate-400">
                        <tr>
                          <th className="px-3 py-2">Index</th>
                          <th className="px-3 py-2">Run ID</th>
                          <th className="px-3 py-2">Type</th>
                          <th className="px-3 py-2">Timestamp</th>
                          <th className="px-3 py-2 text-right">Value</th>
                          <th className="px-3 py-2 text-right">Severity</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-800/60 text-slate-300">
                        {selectedMetric.baselinePoints.map((pt, idx) => (
                          <tr key={pt.runId} className="hover:bg-slate-900/40">
                            <td className="px-3 py-2 font-bold text-slate-400">#{pt.runIndex || idx + 1}</td>
                            <td className="px-3 py-2 text-cyan-300 font-semibold">{pt.runId}</td>
                            <td className="px-3 py-2">
                              <span className="rounded bg-slate-800 px-1.5 py-0.5 text-[9px] text-slate-300">BASELINE</span>
                            </td>
                            <td className="px-3 py-2 text-slate-400">{new Date(pt.timestamp).toLocaleString()}</td>
                            <td className="px-3 py-2 text-right font-bold text-slate-100">{pt.value.toFixed(4)}</td>
                            <td className="px-3 py-2 text-right">
                              <span className={`rounded border px-1.5 py-0.5 text-[9px] font-bold ${getSeverityBadge(pt.severity || "LOW")}`}>
                                {pt.severity || "LOW"}
                              </span>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>

                {/* Change Points */}
                {selectedMetric.changePoints && selectedMetric.changePoints.length > 0 && (
                  <div className="rounded-lg border border-amber-900/40 bg-amber-950/20 p-3.5 space-y-2">
                    <div className="font-mono text-xs font-bold text-amber-400">
                      STEP CHANGE-POINT DETECTED
                    </div>
                    {selectedMetric.changePoints.map((cp, idx) => (
                      <div key={idx} className="font-mono text-xs text-slate-300">
                        At run <span className="font-bold text-amber-300">{cp.changeRunId}</span>: Step shift from mean{" "}
                        <span className="text-slate-200">{cp.beforeMean.toFixed(4)}</span> to{" "}
                        <span className="text-slate-200">{cp.afterMean.toFixed(4)}</span> (Absolute Shift:{" "}
                        <span className="text-amber-300">{cp.absoluteShift.toFixed(4)}</span>, Confidence: {cp.confidenceLevel})
                      </div>
                    ))}
                  </div>
                )}
              </div>
            ) : (
              <div className="flex h-64 items-center justify-center rounded-lg border border-slate-800 bg-slate-900/40 text-slate-400 font-mono text-xs">
                Select a metric from the left panel to inspect historical trajectory.
              </div>
            )}
          </div>
        </div>
      )}

      {/* TAB CONTENT 3: ISSUE TRACKS */}
      {activeTab === "TRACKS" && (
        <div className="space-y-6">
          <div className="overflow-x-auto rounded-lg border border-slate-800 bg-slate-900/50">
            <table className="w-full text-left font-mono text-xs">
              <thead className="border-b border-slate-800 bg-slate-900/80 text-[10px] uppercase text-slate-400">
                <tr>
                  <th className="px-4 py-3">Target Fingerprint</th>
                  <th className="px-4 py-3">Type</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3">Current Sev</th>
                  <th className="px-4 py-3">Peak Sev</th>
                  <th className="px-4 py-3 text-right">Consecutive</th>
                  <th className="px-4 py-3">Modules Involved</th>
                  <th className="px-4 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60 text-slate-300">
                {history?.issueTracks.map((t, idx) => (
                  <tr key={idx} className="hover:bg-slate-900/40 transition">
                    <td className="px-4 py-3 font-bold text-cyan-300">{t.targetKey}</td>
                    <td className="px-4 py-3 text-slate-400">{t.targetType}</td>
                    <td className="px-4 py-3">
                      <span className={`rounded border px-2 py-0.5 text-[10px] font-bold ${getStatusBadge(t.status)}`}>
                        {t.status}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <span className={`rounded border px-2 py-0.5 text-[10px] font-bold ${getSeverityBadge(t.currentSeverity)}`}>
                        {t.currentSeverity}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <span className={`rounded border px-2 py-0.5 text-[10px] font-bold ${getSeverityBadge(t.peakSeverity)}`}>
                        {t.peakSeverity}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right font-bold text-slate-200">{t.consecutiveCount} runs</td>
                    <td className="px-4 py-3 text-slate-400">{t.modulesInvolved.join(", ")}</td>
                    <td className="px-4 py-3 text-right">
                      <button
                        onClick={() => setSelectedIssueTrack(t)}
                        className="rounded border border-cyan-500/50 bg-cyan-950/40 px-2.5 py-1 text-[10px] font-bold text-cyan-300 hover:bg-cyan-900/50"
                      >
                        DOSSIER
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Dossier Drawer if Selected */}
          {selectedIssueTrack && (
            <div className="rounded-lg border border-cyan-500/40 bg-slate-950/90 p-5 space-y-4">
              <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                <div>
                  <div className="font-mono text-xs uppercase tracking-wider text-cyan-400">
                    TEMPORAL INVESTIGATION DOSSIER
                  </div>
                  <h3 className="font-mono text-base font-bold text-slate-100">{selectedIssueTrack.targetKey}</h3>
                </div>
                <button
                  onClick={() => setSelectedIssueTrack(null)}
                  className="font-mono text-xs text-slate-400 hover:text-slate-200"
                >
                  ✕ CLOSE
                </button>
              </div>

              <div className="grid grid-cols-2 gap-4 sm:grid-cols-4 font-mono text-xs">
                <div className="rounded border border-slate-800 bg-slate-900/60 p-3">
                  <div className="text-[10px] text-slate-400">FIRST OBSERVED</div>
                  <div className="font-bold text-slate-200">{selectedIssueTrack.firstSeenRunId}</div>
                  <div className="text-[10px] text-slate-400">{new Date(selectedIssueTrack.firstSeenAt).toLocaleDateString()}</div>
                </div>
                <div className="rounded border border-slate-800 bg-slate-900/60 p-3">
                  <div className="text-[10px] text-slate-400">LAST OBSERVED</div>
                  <div className="font-bold text-slate-200">{selectedIssueTrack.lastSeenRunId}</div>
                  <div className="text-[10px] text-slate-400">{new Date(selectedIssueTrack.lastSeenAt).toLocaleDateString()}</div>
                </div>
                <div className="rounded border border-slate-800 bg-slate-900/60 p-3">
                  <div className="text-[10px] text-slate-400">TOTAL OBSERVATIONS</div>
                  <div className="font-bold text-slate-200">{selectedIssueTrack.observationCount} occurrences</div>
                </div>
                <div className="rounded border border-slate-800 bg-slate-900/60 p-3">
                  <div className="text-[10px] text-slate-400">DURABILITY</div>
                  <div className={`font-bold ${getDurabilityBadge(selectedIssueTrack.durabilityStatus || "NOT_APPLICABLE")} px-1.5 py-0.5 rounded text-center mt-1`}>
                    {selectedIssueTrack.durabilityStatus || "NOT_APPLICABLE"}
                  </div>
                </div>
              </div>

              {/* Navigation Back */}
              <div className="flex items-center gap-3 pt-2">
                {navigate && (
                  <button
                    onClick={() => {
                      if (onSelectInvestigationTarget) {
                        onSelectInvestigationTarget(selectedIssueTrack.targetKey);
                      }
                      navigate("06_INVESTIGATION");
                    }}
                    className="rounded border border-slate-700 bg-slate-800 px-3 py-1.5 font-mono text-xs font-bold text-slate-200 hover:bg-slate-700"
                  >
                    VIEW IN 06 INVESTIGATION →
                  </button>
                )}
                {navigate && (
                  <button
                    onClick={() => navigate("07_REMEDIATION")}
                    className="rounded border border-slate-700 bg-slate-800 px-3 py-1.5 font-mono text-xs font-bold text-slate-200 hover:bg-slate-700"
                  >
                    VIEW IN 07 REMEDIATION →
                  </button>
                )}
              </div>
            </div>
          )}
        </div>
      )}

      {/* TAB CONTENT 4: ALERT CONSOLE */}
      {activeTab === "ALERTS" && (
        <div className="space-y-4">
          <div className="overflow-x-auto rounded-lg border border-slate-800 bg-slate-900/50">
            <table className="w-full text-left font-mono text-xs">
              <thead className="border-b border-slate-800 bg-slate-900/80 text-[10px] uppercase text-slate-400">
                <tr>
                  <th className="px-4 py-3">Timestamp</th>
                  <th className="px-4 py-3">Priority</th>
                  <th className="px-4 py-3">Alert Type</th>
                  <th className="px-4 py-3">Target</th>
                  <th className="px-4 py-3">Metric</th>
                  <th className="px-4 py-3">Trigger Evidence</th>
                  <th className="px-4 py-3">Confidence</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60 text-slate-300">
                {history?.alerts.map((a, idx) => (
                  <tr key={idx} className="hover:bg-slate-900/40">
                    <td className="px-4 py-3 text-slate-400">{new Date(a.createdAt).toLocaleTimeString()}</td>
                    <td className="px-4 py-3">
                      <span className={`rounded border px-2 py-0.5 text-[10px] font-bold ${getSeverityBadge(a.priority)}`}>
                        {a.priority}
                      </span>
                    </td>
                    <td className="px-4 py-3 font-bold text-slate-100">{a.alertType}</td>
                    <td className="px-4 py-3 text-cyan-300">{a.targetKey || "GLOBAL"}</td>
                    <td className="px-4 py-3 text-slate-300">{a.metricName || "N/A"}</td>
                    <td className="px-4 py-3 max-w-xs truncate text-slate-300" title={a.triggerDescription}>
                      {a.triggerDescription}
                    </td>
                    <td className="px-4 py-3 text-slate-400">{a.confidence}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* TAB CONTENT 5: REMEDIATION DURABILITY */}
      {activeTab === "DURABILITY" && (
        <div className="space-y-4">
          <div className="rounded-lg border border-slate-800 bg-slate-900/60 p-4 font-mono text-xs text-slate-400">
            Remediation Durability measures whether experimental improvements sustained across subsequent operational baseline runs.
          </div>

          <div className="overflow-x-auto rounded-lg border border-slate-800 bg-slate-900/50">
            <table className="w-full text-left font-mono text-xs">
              <thead className="border-b border-slate-800 bg-slate-900/80 text-[10px] uppercase text-slate-400">
                <tr>
                  <th className="px-4 py-3">Remediation / Target</th>
                  <th className="px-4 py-3">Experiment</th>
                  <th className="px-4 py-3 text-right">Pre-Exp</th>
                  <th className="px-4 py-3 text-right">Candidate</th>
                  <th className="px-4 py-3 text-right">Latest Follow-Up</th>
                  <th className="px-4 py-3">Durability State</th>
                  <th className="px-4 py-3">Explanation</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60 text-slate-300">
                {history?.remediationDurability.map((d, idx) => (
                  <tr key={idx} className="hover:bg-slate-900/40">
                    <td className="px-4 py-3">
                      <div className="font-bold text-slate-100">{d.remediationTitle}</div>
                      <div className="text-[10px] text-cyan-300">{d.targetKey}</div>
                    </td>
                    <td className="px-4 py-3 text-slate-400">{d.experimentType}</td>
                    <td className="px-4 py-3 text-right font-bold text-slate-300">{d.preExperimentValue.toFixed(4)}</td>
                    <td className="px-4 py-3 text-right font-bold text-cyan-300">{d.candidateValue.toFixed(4)}</td>
                    <td className="px-4 py-3 text-right font-bold text-slate-100">
                      {d.latestFollowUpValue !== undefined ? d.latestFollowUpValue.toFixed(4) : "N/A"}
                    </td>
                    <td className="px-4 py-3">
                      <span className={`rounded border px-2 py-0.5 text-[10px] font-bold ${getDurabilityBadge(d.durabilityStatus)}`}>
                        {d.durabilityStatus}
                      </span>
                    </td>
                    <td className="px-4 py-3 max-w-sm truncate text-slate-300" title={d.durabilityExplanation}>
                      {d.durabilityExplanation}
                    </td>
                  </tr>
                ))}
                {(!history?.remediationDurability || history.remediationDurability.length === 0) && (
                  <tr>
                    <td colSpan={7} className="py-8 text-center text-slate-400">
                      No candidate experiments evaluated for follow-up durability in this window.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
};
