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
        return "bg-[#F18B78]/20 text-[#F18B78] border-[#F18B78]/40";
      case "HIGH":
      case "WARNING":
        return "bg-[#E8B66D]/20 text-[#E8B66D] border-[#E8B66D]/40";
      case "MEDIUM":
      case "OBSERVATION":
        return "bg-[#E8B66D]/15 text-[#E8B66D] border-[#E8B66D]/30";
      case "LOW":
      case "NOMINAL":
      case "RECOVERED":
        return "bg-[#B7D7A8]/20 text-[#B7D7A8] border-[#B7D7A8]/40";
      default:
        return "bg-[#332B3C] text-[#B7ACBD] border-[#443849]";
    }
  };

  const getStatusBadge = (status: string) => {
    switch (status?.toUpperCase()) {
      case "PERSISTENT":
        return "bg-[#332B3C] text-[#B7ACBD] border-[#443849]";
      case "EMERGING":
        return "bg-[#F18B78]/20 text-[#F18B78] border-[#F18B78]/40";
      case "RECURRING":
        return "bg-[#E8B66D]/20 text-[#E8B66D] border-[#E8B66D]/40";
      case "ESCALATING":
        return "bg-[#F18B78]/25 text-[#F18B78] border-[#F18B78]/50";
      case "RECOVERED":
        return "bg-[#B7D7A8]/20 text-[#B7D7A8] border-[#B7D7A8]/40";
      case "TRANSIENT":
        return "bg-[#1C1824] text-[#8E8295] border-[#443849]";
      default:
        return "bg-[#2A2432] text-[#8E8295] border-[#443849]";
    }
  };

  const getDurabilityBadge = (status: string) => {
    switch (status?.toUpperCase()) {
      case "SUSTAINED":
        return "bg-[#B7D7A8]/20 text-[#B7D7A8] border-[#B7D7A8]/40";
      case "TEMPORARY":
      case "FAILED_TO_SUSTAIN":
        return "bg-[#F18B78]/20 text-[#F18B78] border-[#F18B78]/40";
      case "INSUFFICIENT_FOLLOWUP":
        return "bg-[#E8B66D]/20 text-[#E8B66D] border-[#E8B66D]/40";
      default:
        return "bg-[#332B3C] text-[#8E8295] border-[#443849]";
    }
  };

  if (isLoading && !history) {
    return (
      <div className="flex h-96 items-center justify-center">
        <div className="flex flex-col items-center gap-3 text-[#B7ACBD]">
          <div className="h-8 w-8 animate-spin rounded-full border-2 border-[#F18B78] border-t-transparent" />
          <span className="font-mono text-xs tracking-wider uppercase">Loading Temporal Intelligence...</span>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6 pb-12 font-mono text-xs text-[#F4EFF6]">
      {/* 1. Header & Non-Causality Banner */}
      <div className="flex flex-col gap-3 rounded-lg border border-[#443849] bg-[#2A2432] p-5 backdrop-blur">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-3">
              <div className="flex h-7 w-7 items-center justify-center rounded border border-[#F18B78]/40 bg-[#332B3C] text-xs font-mono font-bold text-[#F18B78]">
                09
              </div>
              <h1 className="font-mono text-xl font-bold tracking-tight text-[#F4EFF6]">
                TEMPORAL INTELLIGENCE & LONGITUDINAL MONITORING
              </h1>
            </div>
            <p className="mt-1 font-mono text-xs text-[#B7ACBD]">
              Model Lineage:{" "}
              <span className="text-[#F18B78] font-semibold">{history?.modelLineageId || "Unknown Lineage"}</span>{" "}
              — Multi-Run Temporal Signals, Step Change-Points, Issue Tracks, and Durability Verification
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            {/* Window selector */}
            <div className="flex items-center rounded border border-[#443849] bg-[#1C1824] p-1 text-xs font-mono">
              <span className="px-2 text-[#8E8295]">WINDOW:</span>
              {["LAST_3", "LAST_5", "LAST_10", "ALL_AVAILABLE"].map((win) => (
                <button
                  key={win}
                  onClick={() => setSelectedWindow(win)}
                  className={`rounded px-2.5 py-1 transition cursor-pointer ${
                    selectedWindow === win
                      ? "bg-[#F18B78] font-bold text-[#211D29] shadow"
                      : "text-[#B7ACBD] hover:text-[#F4EFF6]"
                  }`}
                >
                  {win.replace("_", " ")}
                </button>
              ))}
            </div>

            {/* Experiment Overlay Toggle */}
            <button
              onClick={() => setShowExperiments(!showExperiments)}
              className={`flex items-center gap-2 rounded border px-3 py-1.5 font-mono text-xs transition cursor-pointer ${
                showExperiments
                  ? "border-[#F18B78]/50 bg-[#332B3C] text-[#F18B78]"
                  : "border-[#443849] bg-[#1C1824] text-[#B7ACBD] hover:text-[#F4EFF6]"
              }`}
            >
              <div className={`h-2 w-2 rounded-full ${showExperiments ? "bg-[#F18B78]" : "bg-[#8E8295]"}`} />
              EXPERIMENT OVERLAY
            </button>

            {/* Recalculate Button */}
            <button
              onClick={handleRecalculate}
              disabled={isRecalculating}
              className="flex items-center gap-2 rounded border border-[#F18B78]/40 bg-[#F18B78] hover:bg-[#F39C8B] px-3.5 py-1.5 font-mono text-xs font-bold text-[#211D29] disabled:opacity-50 cursor-pointer transition-colors"
            >
              {isRecalculating ? (
                <>
                  <div className="h-3 w-3 animate-spin rounded-full border border-[#211D29] border-t-transparent" />
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
        <div className="flex items-center gap-2 rounded border border-[#443849] bg-[#1C1824] px-3 py-1.5 text-xs font-mono text-[#B7ACBD]">
          <span className="font-bold text-[#F18B78]">[MONITORING DIRECTIVE]</span>
          <span>
            Temporal observations reflect deterministic longitudinal associations. Temporal ordering alone does not establish causal direction. Provenance is preserved for all historical runs.
          </span>
        </div>

        {statusNotification && (
          <div className="rounded border border-[#B7D7A8]/40 bg-[#B7D7A8]/10 px-3 py-2 font-mono text-xs text-[#B7D7A8]">
            {statusNotification}
          </div>
        )}

        {errorMessage && (
          <div className="rounded border border-[#F18B78]/40 bg-[#F18B78]/10 px-3 py-2 font-mono text-xs text-[#F18B78]">
            {errorMessage}
          </div>
        )}
      </div>

      {/* 2. Top HUD Metrics */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-7">
        <div className="rounded-lg border border-[#443849] bg-[#2A2432] p-3 text-center">
          <div className="font-mono text-[10px] uppercase tracking-wider text-[#8E8295]">Runs Observed</div>
          <div className="mt-1 font-mono text-2xl font-bold text-[#F4EFF6]">{history?.totalRunsCount || 0}</div>
          <div className="font-mono text-[10px] text-[#8E8295]">
            {history?.baselineRunsCount || 0} base / {history?.experimentRunsCount || 0} exp
          </div>
        </div>

        <div className="rounded-lg border border-[#443849] bg-[#2A2432] p-3 text-center">
          <div className="font-mono text-[10px] uppercase tracking-wider text-[#8E8295]">Active Issues</div>
          <div className="mt-1 font-mono text-2xl font-bold text-[#E8B66D]">{history?.activeIssuesCount || 0}</div>
          <div className="font-mono text-[10px] text-[#8E8295]">unresolved tracks</div>
        </div>

        <div className="rounded-lg border border-[#443849] bg-[#2A2432] p-3 text-center">
          <div className="font-mono text-[10px] uppercase tracking-wider text-[#8E8295]">Persistent</div>
          <div className="mt-1 font-mono text-2xl font-bold text-[#B7ACBD]">{history?.persistentIssuesCount || 0}</div>
          <div className="font-mono text-[10px] text-[#8E8295]">≥3 consecutive runs</div>
        </div>

        <div className="rounded-lg border border-[#443849] bg-[#2A2432] p-3 text-center">
          <div className="font-mono text-[10px] uppercase tracking-wider text-[#8E8295]">Emerging</div>
          <div className="mt-1 font-mono text-2xl font-bold text-[#F18B78]">{history?.emergingIssuesCount || 0}</div>
          <div className="font-mono text-[10px] text-[#8E8295]">newly observed</div>
        </div>

        <div className="rounded-lg border border-[#443849] bg-[#2A2432] p-3 text-center">
          <div className="font-mono text-[10px] uppercase tracking-wider text-[#8E8295]">Recurring</div>
          <div className="mt-1 font-mono text-2xl font-bold text-[#E8B66D]">{history?.recurringIssuesCount || 0}</div>
          <div className="font-mono text-[10px] text-[#8E8295]">returned after gap</div>
        </div>

        <div className="rounded-lg border border-[#443849] bg-[#2A2432] p-3 text-center">
          <div className="font-mono text-[10px] uppercase tracking-wider text-[#8E8295]">Active Alerts</div>
          <div className="mt-1 font-mono text-2xl font-bold text-[#F18B78]">{history?.activeAlertsCount || 0}</div>
          <div className="font-mono text-[10px] text-[#8E8295]">{history?.criticalAlertsCount || 0} critical</div>
        </div>

        <div className="rounded-lg border border-[#443849] bg-[#2A2432] p-3 text-center">
          <div className="font-mono text-[10px] uppercase tracking-wider text-[#8E8295]">Change Points</div>
          <div className="mt-1 font-mono text-2xl font-bold text-[#B7D7A8]">{history?.changePointsCount || 0}</div>
          <div className="font-mono text-[10px] text-[#8E8295]">step shifts detected</div>
        </div>
      </div>

      {/* 3. Model History Strip */}
      <div className="rounded-lg border border-[#443849] bg-[#2A2432] p-4">
        <div className="mb-3 flex items-center justify-between">
          <div className="flex items-center gap-2 font-mono text-xs font-bold text-[#F4EFF6]">
            <span className="text-[#F18B78]">●</span> OPERATIONAL LINEAGE TIMELINE
          </div>
          <div className="font-mono text-[11px] text-[#B7ACBD]">
            Current Run: <span className="font-bold text-[#F18B78]">{runId}</span>
          </div>
        </div>

        <div className="flex items-center gap-2 overflow-x-auto pb-2 pt-1">
          {history?.orderedRuns.map((r, idx) => {
            const isCurrent = r.runId === runId;
            return (
              <React.Fragment key={r.runId}>
                {idx > 0 && <div className="h-[2px] w-6 shrink-0 bg-[#443849]" />}
                <div
                  className={`group relative flex shrink-0 flex-col rounded-lg border p-3 font-mono transition ${
                    isCurrent
                      ? "border-[#F18B78] bg-[#332B3C] shadow-lg shadow-[#F18B78]/10"
                      : "border-[#443849] bg-[#1C1824] hover:border-[#5A4B62]"
                  }`}
                  style={{ minWidth: "160px" }}
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-[10px] text-[#8E8295]">RUN #{idx + 1}</span>
                    <span
                      className={`rounded px-1.5 py-0.5 text-[9px] font-bold ${
                        r.status === "COMPLETED"
                          ? "bg-[#B7D7A8]/15 text-[#B7D7A8] border border-[#B7D7A8]/40"
                          : "bg-[#332B3C] text-[#B7ACBD] border border-[#443849]"
                      }`}
                    >
                      {r.status}
                    </span>
                  </div>
                  <div className="mt-1 truncate text-xs font-bold text-[#F4EFF6]" title={r.runId}>
                    {r.runId}
                  </div>
                  <div className="mt-1 flex items-center justify-between text-[10px] text-[#8E8295]">
                    <span>{r.completedModules}/{r.totalModules} modules</span>
                    <span>{new Date(r.createdAt).toLocaleDateString()}</span>
                  </div>
                  {isCurrent && (
                    <div className="mt-1.5 flex items-center justify-center rounded bg-[#F18B78]/20 py-0.5 text-[9px] font-bold text-[#F18B78]">
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
      <div className="flex border-b border-[#443849]">
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
            className={`flex items-center gap-2 border-b-2 px-5 py-3 font-mono text-xs font-semibold transition cursor-pointer ${
              activeTab === tab.id
                ? "border-[#F18B78] bg-[#332B3C] text-[#F18B78]"
                : "border-transparent text-[#B7ACBD] hover:text-[#F4EFF6]"
            }`}
          >
            {tab.label}
            {tab.count !== undefined && (
              <span className="rounded bg-[#1C1824] px-1.5 py-0.5 text-[10px] text-[#B7ACBD] border border-[#443849]">
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
            <div className="rounded-lg border border-[#443849] bg-[#2A2432] p-5">
              <div className="mb-4 flex items-center justify-between">
                <h3 className="font-mono text-xs font-bold uppercase tracking-wider text-[#F4EFF6]">
                  Critical Temporal Alerts
                </h3>
                <span className="font-mono text-xs text-[#8E8295]">{history?.alerts.length || 0} total</span>
              </div>
              <div className="space-y-2.5">
                {history?.alerts.slice(0, 5).map((a, idx) => (
                  <div
                    key={idx}
                    onClick={() => {
                      setSelectedAlert(a);
                      setActiveTab("ALERTS");
                    }}
                    className="cursor-pointer rounded-lg border border-[#443849] bg-[#1C1824] p-3 hover:border-[#5A4B62] transition"
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className={`rounded border px-2 py-0.5 font-mono text-[10px] font-bold ${getSeverityBadge(a.priority)}`}>
                        {a.priority}
                      </span>
                      <span className="font-mono text-xs font-semibold text-[#F4EFF6]">{a.alertType}</span>
                      <span className="font-mono text-[10px] text-[#8E8295]">{new Date(a.createdAt).toLocaleTimeString()}</span>
                    </div>
                    <p className="mt-1.5 font-mono text-xs text-[#B7ACBD]">{a.triggerDescription}</p>
                    <div className="mt-2 flex items-center justify-between text-[10px] font-mono text-[#8E8295]">
                      <span>Target: <span className="text-[#F18B78]">{a.targetKey || "GLOBAL"}</span></span>
                      <span>Metric: <span className="text-[#F4EFF6]">{a.metricName || "N/A"}</span></span>
                    </div>
                  </div>
                ))}
                {(!history?.alerts || history.alerts.length === 0) && (
                  <div className="py-8 text-center font-mono text-xs text-[#8E8295]">No active temporal alerts.</div>
                )}
              </div>
            </div>

            {/* Persistent & Emerging Tracks */}
            <div className="rounded-lg border border-[#443849] bg-[#2A2432] p-5">
              <div className="mb-4 flex items-center justify-between">
                <h3 className="font-mono text-xs font-bold uppercase tracking-wider text-[#F4EFF6]">
                  Dominant Issue Tracks
                </h3>
                <span className="font-mono text-xs text-[#8E8295]">{history?.issueTracks.length || 0} tracks</span>
              </div>
              <div className="space-y-2.5">
                {history?.issueTracks.slice(0, 5).map((t, idx) => (
                  <div
                    key={idx}
                    onClick={() => {
                      setSelectedIssueTrack(t);
                      setActiveTab("TRACKS");
                    }}
                    className="cursor-pointer rounded-lg border border-[#443849] bg-[#1C1824] p-3 hover:border-[#5A4B62] transition"
                  >
                    <div className="flex items-center justify-between">
                      <div className="font-mono text-xs font-bold text-[#F18B78]">{t.targetKey}</div>
                      <span className={`rounded border px-2 py-0.5 font-mono text-[10px] font-bold ${getStatusBadge(t.status)}`}>
                        {t.status}
                      </span>
                    </div>
                    <div className="mt-2 grid grid-cols-3 gap-2 font-mono text-[10px] text-[#8E8295]">
                      <div>Peak: <span className={`font-bold ${getSeverityBadge(t.peakSeverity)} px-1 rounded`}>{t.peakSeverity}</span></div>
                      <div>Consecutive: <span className="text-[#F4EFF6] font-bold">{t.consecutiveCount} runs</span></div>
                      <div>Modules: <span className="text-[#B7ACBD]">{t.modulesInvolved.join(", ")}</span></div>
                    </div>
                  </div>
                ))}
                {(!history?.issueTracks || history.issueTracks.length === 0) && (
                  <div className="py-8 text-center font-mono text-xs text-[#8E8295]">No target issue tracks identified.</div>
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
          <div className="lg:col-span-1 space-y-2 rounded-lg border border-[#443849] bg-[#2A2432] p-4 max-h-[600px] overflow-y-auto">
            <div className="font-mono text-xs font-bold uppercase tracking-wider text-[#8E8295] pb-2 border-b border-[#443849]">
              Select Metric ({history?.metricHistories.length || 0})
            </div>
            {history?.metricHistories.map((m) => {
              const key = getMetricKey(m);
              const isSelected = key === selectedMetricKey;
              return (
                <button
                  key={key}
                  onClick={() => setSelectedMetricKey(key)}
                  className={`w-full text-left rounded-lg p-2.5 font-mono text-xs transition border cursor-pointer ${
                    isSelected
                      ? "border-[#F18B78] bg-[#332B3C] text-[#F18B78]"
                      : "border-[#443849] bg-[#1C1824] text-[#B7ACBD] hover:border-[#5A4B62] hover:text-[#F4EFF6]"
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <span className="font-bold text-[#F4EFF6]">{m.metricName}</span>
                    <span className={`text-[9px] px-1.5 py-0.5 rounded ${getStatusBadge(m.trendDirection)}`}>
                      {m.trendDirection}
                    </span>
                  </div>
                  <div className="mt-1 flex items-center justify-between text-[10px] text-[#8E8295]">
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
              <div className="rounded-lg border border-[#443849] bg-[#2A2432] p-5 space-y-5">
                {/* Metric Summary Bar */}
                <div className="flex flex-wrap items-center justify-between gap-4 border-b border-[#443849] pb-4">
                  <div>
                    <div className="flex items-center gap-3">
                      <h2 className="font-mono text-lg font-bold text-[#F4EFF6]">{selectedMetric.metricName}</h2>
                      <span className={`rounded border px-2 py-0.5 font-mono text-[10px] font-bold ${getStatusBadge(selectedMetric.trendDirection)}`}>
                        TREND: {selectedMetric.trendDirection}
                      </span>
                    </div>
                    <p className="mt-1 font-mono text-xs text-[#B7ACBD]">
                      Module: <span className="text-[#F18B78]">{selectedMetric.module}</span> | Target:{" "}
                      <span className="text-[#F4EFF6]">{selectedMetric.targetKey || "GLOBAL"}</span>
                    </p>
                  </div>

                  <div className="flex items-center gap-4 font-mono text-xs">
                    <div className="text-right">
                      <div className="text-[#8E8295] text-[10px]">LATEST VALUE</div>
                      <div className="text-base font-bold text-[#F18B78]">{selectedMetric.latestValue?.toFixed(4) ?? "N/A"}</div>
                    </div>
                    <div className="text-right">
                      <div className="text-[#8E8295] text-[10px]">DELTA</div>
                      <div className={`text-base font-bold ${
                        (selectedMetric.absoluteDelta ?? 0) > 0 ? "text-[#B7D7A8]" : (selectedMetric.absoluteDelta ?? 0) < 0 ? "text-[#F18B78]" : "text-[#B7ACBD]"
                      }`}>
                        {selectedMetric.absoluteDelta !== undefined ? (selectedMetric.absoluteDelta > 0 ? `+${selectedMetric.absoluteDelta.toFixed(4)}` : selectedMetric.absoluteDelta.toFixed(4)) : "N/A"}
                      </div>
                    </div>
                  </div>
                </div>

                {/* Statistical Trend Metrics */}
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 font-mono text-xs">
                  <div className="rounded border border-[#443849] bg-[#1C1824] p-2.5">
                    <div className="text-[10px] text-[#8E8295]">SLOPE (per run)</div>
                    <div className="font-bold text-[#F4EFF6]">{selectedMetric.slope?.toFixed(5) ?? "N/A"}</div>
                  </div>
                  <div className="rounded border border-[#443849] bg-[#1C1824] p-2.5">
                    <div className="text-[10px] text-[#8E8295]">R² FIT</div>
                    <div className="font-bold text-[#F4EFF6]">{selectedMetric.rSquared?.toFixed(4) ?? "N/A"}</div>
                  </div>
                  <div className="rounded border border-[#443849] bg-[#1C1824] p-2.5">
                    <div className="text-[10px] text-[#8E8295]">MANN-KENDALL TAU</div>
                    <div className="font-bold text-[#F4EFF6]">{selectedMetric.mannKendallTau?.toFixed(4) ?? "N/A"}</div>
                  </div>
                  <div className="rounded border border-[#443849] bg-[#1C1824] p-2.5">
                    <div className="text-[10px] text-[#8E8295]">P-VALUE</div>
                    <div className="font-bold text-[#B7D7A8]">{selectedMetric.mannKendallPValue?.toFixed(4) ?? "N/A"}</div>
                  </div>
                </div>

                {/* Historical Points Table / Visual Points */}
                <div>
                  <div className="mb-2 flex items-center justify-between font-mono text-xs font-bold text-[#F4EFF6]">
                    <span>OBSERVATION POINTS ({selectedMetric.baselinePoints.length} operational)</span>
                    {showExperiments && <span className="text-[#F18B78]">{selectedMetric.experimentPoints?.length || 0} experiments overlaid</span>}
                  </div>
                  <div className="overflow-x-auto rounded border border-[#443849] bg-[#1C1824]">
                    <table className="w-full text-left font-mono text-xs">
                      <thead className="border-b border-[#443849] bg-[#2A2432] text-[10px] uppercase text-[#8E8295]">
                        <tr>
                          <th className="px-3 py-2">Index</th>
                          <th className="px-3 py-2">Run ID</th>
                          <th className="px-3 py-2">Type</th>
                          <th className="px-3 py-2">Timestamp</th>
                          <th className="px-3 py-2 text-right">Value</th>
                          <th className="px-3 py-2 text-right">Severity</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-[#443849]/60 text-[#F4EFF6]">
                        {selectedMetric.baselinePoints.map((pt, idx) => (
                          <tr key={pt.runId} className="hover:bg-[#332B3C]/50 transition-colors">
                            <td className="px-3 py-2 font-bold text-[#8E8295]">#{pt.runIndex || idx + 1}</td>
                            <td className="px-3 py-2 text-[#F18B78] font-semibold">{pt.runId}</td>
                            <td className="px-3 py-2">
                              <span className="rounded bg-[#332B3C] border border-[#443849] px-1.5 py-0.5 text-[9px] text-[#B7ACBD]">BASELINE</span>
                            </td>
                            <td className="px-3 py-2 text-[#8E8295]">{new Date(pt.timestamp).toLocaleString()}</td>
                            <td className="px-3 py-2 text-right font-bold text-[#F4EFF6]">{pt.value.toFixed(4)}</td>
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
                  <div className="rounded-lg border border-[#E8B66D]/40 bg-[#E8B66D]/10 p-3.5 space-y-2">
                    <div className="font-mono text-xs font-bold text-[#E8B66D]">
                      STEP CHANGE-POINT DETECTED
                    </div>
                    {selectedMetric.changePoints.map((cp, idx) => (
                      <div key={idx} className="font-mono text-xs text-[#B7ACBD]">
                        At run <span className="font-bold text-[#E8B66D]">{cp.changeRunId}</span>: Step shift from mean{" "}
                        <span className="text-[#F4EFF6]">{cp.beforeMean.toFixed(4)}</span> to{" "}
                        <span className="text-[#F4EFF6]">{cp.afterMean.toFixed(4)}</span> (Absolute Shift:{" "}
                        <span className="text-[#E8B66D]">{cp.absoluteShift.toFixed(4)}</span>, Confidence: {cp.confidenceLevel})
                      </div>
                    ))}
                  </div>
                )}
              </div>
            ) : (
              <div className="flex h-64 items-center justify-center rounded-lg border border-[#443849] bg-[#2A2432] text-[#8E8295] font-mono text-xs">
                Select a metric from the left panel to inspect historical trajectory.
              </div>
            )}
          </div>
        </div>
      )}

      {/* TAB CONTENT 3: ISSUE TRACKS */}
      {activeTab === "TRACKS" && (
        <div className="space-y-6">
          <div className="overflow-x-auto rounded-lg border border-[#443849] bg-[#2A2432]">
            <table className="w-full text-left font-mono text-xs">
              <thead className="border-b border-[#443849] bg-[#1C1824] text-[10px] uppercase text-[#8E8295]">
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
              <tbody className="divide-y divide-[#443849]/60 text-[#F4EFF6]">
                {history?.issueTracks.map((t, idx) => (
                  <tr key={idx} className="hover:bg-[#332B3C]/50 transition">
                    <td className="px-4 py-3 font-bold text-[#F18B78]">{t.targetKey}</td>
                    <td className="px-4 py-3 text-[#8E8295]">{t.targetType}</td>
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
                    <td className="px-4 py-3 text-right font-bold text-[#F4EFF6]">{t.consecutiveCount} runs</td>
                    <td className="px-4 py-3 text-[#B7ACBD]">{t.modulesInvolved.join(", ")}</td>
                    <td className="px-4 py-3 text-right">
                      <button
                        onClick={() => setSelectedIssueTrack(t)}
                        className="rounded border border-[#F18B78]/40 bg-[#332B3C] hover:bg-[#3D3448] px-2.5 py-1 text-[10px] font-bold text-[#F18B78] cursor-pointer transition-colors"
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
            <div className="rounded-lg border border-[#F18B78]/40 bg-[#1C1824] p-5 space-y-4 shadow-xl">
              <div className="flex items-center justify-between border-b border-[#443849] pb-3">
                <div>
                  <div className="font-mono text-xs uppercase tracking-wider text-[#F18B78] font-bold">
                    TEMPORAL INVESTIGATION DOSSIER
                  </div>
                  <h3 className="font-mono text-base font-bold text-[#F4EFF6]">{selectedIssueTrack.targetKey}</h3>
                </div>
                <button
                  onClick={() => setSelectedIssueTrack(null)}
                  className="font-mono text-xs text-[#8E8295] hover:text-[#F4EFF6] cursor-pointer"
                >
                  ✕ CLOSE
                </button>
              </div>

              <div className="grid grid-cols-2 gap-4 sm:grid-cols-4 font-mono text-xs">
                <div className="rounded border border-[#443849] bg-[#2A2432] p-3">
                  <div className="text-[10px] text-[#8E8295]">FIRST OBSERVED</div>
                  <div className="font-bold text-[#F4EFF6]">{selectedIssueTrack.firstSeenRunId}</div>
                  <div className="text-[10px] text-[#8E8295]">{new Date(selectedIssueTrack.firstSeenAt).toLocaleDateString()}</div>
                </div>
                <div className="rounded border border-[#443849] bg-[#2A2432] p-3">
                  <div className="text-[10px] text-[#8E8295]">LAST OBSERVED</div>
                  <div className="font-bold text-[#F4EFF6]">{selectedIssueTrack.lastSeenRunId}</div>
                  <div className="text-[10px] text-[#8E8295]">{new Date(selectedIssueTrack.lastSeenAt).toLocaleDateString()}</div>
                </div>
                <div className="rounded border border-[#443849] bg-[#2A2432] p-3">
                  <div className="text-[10px] text-[#8E8295]">TOTAL OBSERVATIONS</div>
                  <div className="font-bold text-[#F4EFF6]">{selectedIssueTrack.observationCount} occurrences</div>
                </div>
                <div className="rounded border border-[#443849] bg-[#2A2432] p-3">
                  <div className="text-[10px] text-[#8E8295]">DURABILITY</div>
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
                    className="rounded border border-[#F18B78]/40 bg-[#332B3C] hover:bg-[#3D3448] px-3 py-1.5 font-mono text-xs font-bold text-[#F18B78] cursor-pointer transition-colors"
                  >
                    VIEW IN 06 INVESTIGATION →
                  </button>
                )}
                {navigate && (
                  <button
                    onClick={() => navigate("07_REMEDIATION")}
                    className="rounded border border-[#443849] bg-[#332B3C] hover:bg-[#3D3448] px-3 py-1.5 font-mono text-xs font-bold text-[#B7ACBD] hover:text-[#F4EFF6] cursor-pointer transition-colors"
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
          <div className="overflow-x-auto rounded-lg border border-[#443849] bg-[#2A2432]">
            <table className="w-full text-left font-mono text-xs">
              <thead className="border-b border-[#443849] bg-[#1C1824] text-[10px] uppercase text-[#8E8295]">
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
              <tbody className="divide-y divide-[#443849]/60 text-[#F4EFF6]">
                {history?.alerts.map((a, idx) => (
                  <tr key={idx} className="hover:bg-[#332B3C]/50 transition">
                    <td className="px-4 py-3 text-[#8E8295]">{new Date(a.createdAt).toLocaleTimeString()}</td>
                    <td className="px-4 py-3">
                      <span className={`rounded border px-2 py-0.5 text-[10px] font-bold ${getSeverityBadge(a.priority)}`}>
                        {a.priority}
                      </span>
                    </td>
                    <td className="px-4 py-3 font-bold text-[#F4EFF6]">{a.alertType}</td>
                    <td className="px-4 py-3 text-[#F18B78]">{a.targetKey || "GLOBAL"}</td>
                    <td className="px-4 py-3 text-[#B7ACBD]">{a.metricName || "N/A"}</td>
                    <td className="px-4 py-3 max-w-xs truncate text-[#B7ACBD]" title={a.triggerDescription}>
                      {a.triggerDescription}
                    </td>
                    <td className="px-4 py-3 text-[#8E8295]">{a.confidence}</td>
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
          <div className="rounded-lg border border-[#443849] bg-[#2A2432] p-4 font-mono text-xs text-[#B7ACBD]">
            Remediation Durability measures whether experimental improvements sustained across subsequent operational baseline runs.
          </div>

          <div className="overflow-x-auto rounded-lg border border-[#443849] bg-[#2A2432]">
            <table className="w-full text-left font-mono text-xs">
              <thead className="border-b border-[#443849] bg-[#1C1824] text-[10px] uppercase text-[#8E8295]">
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
              <tbody className="divide-y divide-[#443849]/60 text-[#F4EFF6]">
                {history?.remediationDurability.map((d, idx) => (
                  <tr key={idx} className="hover:bg-[#332B3C]/50 transition">
                    <td className="px-4 py-3">
                      <div className="font-bold text-[#F4EFF6]">{d.remediationTitle}</div>
                      <div className="text-[10px] text-[#F18B78]">{d.targetKey}</div>
                    </td>
                    <td className="px-4 py-3 text-[#8E8295]">{d.experimentType}</td>
                    <td className="px-4 py-3 text-right font-bold text-[#B7ACBD]">{d.preExperimentValue.toFixed(4)}</td>
                    <td className="px-4 py-3 text-right font-bold text-[#F18B78]">{d.candidateValue.toFixed(4)}</td>
                    <td className="px-4 py-3 text-right font-bold text-[#B7D7A8]">
                      {d.latestFollowUpValue !== undefined ? d.latestFollowUpValue.toFixed(4) : "N/A"}
                    </td>
                    <td className="px-4 py-3">
                      <span className={`rounded border px-2 py-0.5 text-[10px] font-bold ${getDurabilityBadge(d.durabilityStatus)}`}>
                        {d.durabilityStatus}
                      </span>
                    </td>
                    <td className="px-4 py-3 max-w-sm truncate text-[#B7ACBD]" title={d.durabilityExplanation}>
                      {d.durabilityExplanation}
                    </td>
                  </tr>
                ))}
                {(!history?.remediationDurability || history.remediationDurability.length === 0) && (
                  <tr>
                    <td colSpan={7} className="py-8 text-center text-[#8E8295]">
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
