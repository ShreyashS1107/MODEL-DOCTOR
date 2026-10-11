"use client";

import React, { useState, useEffect, useMemo } from "react";
import {
  DiagnosticIncident,
  IncidentEvidenceDossier,
  IncidentLifecycleState,
  IncidentDecisionState,
  DecisionConfidence,
  NavSection,
} from "@/types/diagnostics";
import {
  getIncidents,
  getIncidentDossier,
  recalculateIncidents,
  acknowledgeIncident,
  investigateIncident,
  planIncidentRemediation,
  startIncidentValidation,
  resolveIncident,
  suppressIncident,
  RunSummary,
} from "@/lib/api";

export interface IncidentDecisionViewProps {
  runId: string;
  runSummary?: RunSummary | null;
  onSelectSection?: (section: NavSection) => void;
  onSelectFeature?: (featureName: string) => void;
  onRefresh?: () => void;
  onNavigateSection?: (section: NavSection) => void;
  onSelectInvestigationTarget?: (targetKey: string) => void;
}

const SEVERITY_STYLES: Record<string, { bg: string; text: string; border: string }> = {
  CRITICAL: { bg: "bg-[#F18B78]/15", text: "text-[#F18B78]", border: "border-[#F18B78]/40" },
  HIGH: { bg: "bg-[#F18B78]/15", text: "text-[#F18B78]", border: "border-[#F18B78]/40" },
  MEDIUM: { bg: "bg-[#E8B66D]/15", text: "text-[#E8B66D]", border: "border-[#E8B66D]/40" },
  WARNING: { bg: "bg-[#E8B66D]/15", text: "text-[#E8B66D]", border: "border-[#E8B66D]/40" },
  LOW: { bg: "bg-[#B7D7A8]/15", text: "text-[#B7D7A8]", border: "border-[#B7D7A8]/40" },
  INFO: { bg: "bg-[#332B3C]", text: "text-[#B7ACBD]", border: "border-[#443849]" },
};

const LIFECYCLE_STYLES: Record<IncidentLifecycleState, { bg: string; text: string; border: string }> = {
  OPEN: { bg: "bg-[#F18B78]/15", text: "text-[#F18B78]", border: "border-[#F18B78]/40" },
  ACKNOWLEDGED: { bg: "bg-[#E8B66D]/15", text: "text-[#E8B66D]", border: "border-[#E8B66D]/40" },
  INVESTIGATING: { bg: "bg-[#332B3C]", text: "text-[#F18B78]", border: "border-[#F18B78]/40" },
  MITIGATION_PLANNED: { bg: "bg-[#332B3C]", text: "text-[#B7ACBD]", border: "border-[#443849]" },
  VALIDATING: { bg: "bg-[#E8B66D]/15", text: "text-[#E8B66D]", border: "border-[#E8B66D]/40" },
  MONITORING: { bg: "bg-[#B7D7A8]/15", text: "text-[#B7D7A8]", border: "border-[#B7D7A8]/40" },
  RESOLVED: { bg: "bg-[#B7D7A8]/20", text: "text-[#B7D7A8]", border: "border-[#B7D7A8]/50" },
  REOPENED: { bg: "bg-[#F18B78]/20", text: "text-[#F18B78]", border: "border-[#F18B78]/50" },
  SUPPRESSED: { bg: "bg-[#443849]/30", text: "text-[#8E8295]", border: "border-[#443849]" },
};

const DECISION_STYLES: Record<IncidentDecisionState, { bg: string; text: string; border: string }> = {
  NO_ACTION: { bg: "bg-[#B7D7A8]/15", text: "text-[#B7D7A8]", border: "border-[#B7D7A8]/40" },
  INVESTIGATE: { bg: "bg-[#F18B78]/15", text: "text-[#F18B78]", border: "border-[#F18B78]/40" },
  REVIEW_REMEDIATION: { bg: "bg-[#332B3C]", text: "text-[#F4EFF6]", border: "border-[#443849]" },
  VALIDATE_REMEDIATION: { bg: "bg-[#E8B66D]/15", text: "text-[#E8B66D]", border: "border-[#E8B66D]/40" },
  MONITOR: { bg: "bg-[#B7D7A8]/15", text: "text-[#B7D7A8]", border: "border-[#B7D7A8]/40" },
  REOPEN_INVESTIGATION: { bg: "bg-[#F18B78]/15", text: "text-[#F18B78]", border: "border-[#F18B78]/40" },
  ESCALATE: { bg: "bg-[#F18B78]/20", text: "text-[#F18B78]", border: "border-[#F18B78]/50" },
  INSUFFICIENT_EVIDENCE: { bg: "bg-[#332B3C]", text: "text-[#8E8295]", border: "border-[#443849]" },
};

const CONFIDENCE_BADGES: Record<DecisionConfidence, { bg: string; text: string; border: string }> = {
  HIGH: { bg: "bg-[#B7D7A8]/20", text: "text-[#B7D7A8]", border: "border-[#B7D7A8]/50" },
  MEDIUM: { bg: "bg-[#E8B66D]/20", text: "text-[#E8B66D]", border: "border-[#E8B66D]/50" },
  LOW: { bg: "bg-[#F18B78]/20", text: "text-[#F18B78]", border: "border-[#F18B78]/50" },
  INSUFFICIENT: { bg: "bg-[#443849]/30", text: "text-[#8E8295]", border: "border-[#443849]" },
};

export const IncidentDecisionView: React.FC<IncidentDecisionViewProps> = ({
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
  const [incidents, setIncidents] = useState<DiagnosticIncident[]>([]);
  const [selectedIncidentId, setSelectedIncidentId] = useState<number | null>(null);
  const [dossier, setDossier] = useState<IncidentEvidenceDossier | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isDossierLoading, setIsDossierLoading] = useState<boolean>(false);
  const [isRecalculating, setIsRecalculating] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [statusNotification, setStatusNotification] = useState<string | null>(null);

  // Filters
  const [severityFilter, setSeverityFilter] = useState<string>("ALL");
  const [lifecycleFilter, setLifecycleFilter] = useState<string>("ALL");
  const [searchTarget, setSearchTarget] = useState<string>("");

  // Modals for operator actions
  const [activeModal, setActiveModal] = useState<string | null>(null);
  const [modalActor, setModalActor] = useState<string>("USER");
  const [modalText, setModalText] = useState<string>("");
  const [modalDuration, setModalDuration] = useState<number>(24);

  // Load incidents list
  const fetchIncidents = async () => {
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const data = await getIncidents(runId, true);
      setIncidents(data);
      if (data.length > 0) {
        if (!selectedIncidentId || !data.some((i) => i.id === selectedIncidentId)) {
          setSelectedIncidentId(data[0].id);
        }
      } else {
        setSelectedIncidentId(null);
        setDossier(null);
      }
    } catch (err: any) {
      setErrorMessage(err?.message || "Failed to load operational incidents");
    } finally {
      setIsLoading(false);
    }
  };

  // Load selected incident dossier
  const fetchDossier = async (incId: number) => {
    setIsDossierLoading(true);
    try {
      const data = await getIncidentDossier(runId, incId);
      setDossier(data);
    } catch (err: any) {
      setErrorMessage(err?.message || `Failed to load dossier for incident #${incId}`);
    } finally {
      setIsDossierLoading(false);
    }
  };

  useEffect(() => {
    fetchIncidents();
  }, [runId]);

  useEffect(() => {
    if (selectedIncidentId) {
      fetchDossier(selectedIncidentId);
    }
  }, [selectedIncidentId, runId]);

  const handleRecalculate = async () => {
    setIsRecalculating(true);
    setStatusNotification(null);
    setErrorMessage(null);
    try {
      const res = await recalculateIncidents(runId);
      setStatusNotification(res.message);
      await fetchIncidents();
    } catch (err: any) {
      setErrorMessage(err?.message || "Failed to recalculate incidents");
    } finally {
      setIsRecalculating(false);
    }
  };

  // Operator Action Handlers
  const handleAcknowledge = async () => {
    if (!selectedIncidentId) return;
    try {
      await acknowledgeIncident(runId, selectedIncidentId, modalActor, modalText || "Acknowledged by operator");
      setStatusNotification(`Incident #${selectedIncidentId} acknowledged`);
      setActiveModal(null);
      await fetchIncidents();
      await fetchDossier(selectedIncidentId);
    } catch (err: any) {
      setErrorMessage(err?.message || "Failed to acknowledge incident");
    }
  };

  const handleInvestigate = async () => {
    if (!selectedIncidentId) return;
    try {
      await investigateIncident(runId, selectedIncidentId, modalActor, modalText || "Investigation initiated");
      setStatusNotification(`Incident #${selectedIncidentId} set to INVESTIGATING`);
      setActiveModal(null);
      await fetchIncidents();
      await fetchDossier(selectedIncidentId);
    } catch (err: any) {
      setErrorMessage(err?.message || "Failed to start investigation");
    }
  };

  const handlePlanMitigation = async () => {
    if (!selectedIncidentId) return;
    try {
      await planIncidentRemediation(runId, selectedIncidentId, undefined, modalActor, modalText || "Mitigation planned");
      setStatusNotification(`Incident #${selectedIncidentId} set to MITIGATION_PLANNED`);
      setActiveModal(null);
      await fetchIncidents();
      await fetchDossier(selectedIncidentId);
    } catch (err: any) {
      setErrorMessage(err?.message || "Failed to plan mitigation");
    }
  };

  const handleStartValidation = async () => {
    if (!selectedIncidentId) return;
    try {
      await startIncidentValidation(runId, selectedIncidentId, undefined, modalActor, modalText || "Validation experiment launched");
      setStatusNotification(`Incident #${selectedIncidentId} set to VALIDATING`);
      setActiveModal(null);
      await fetchIncidents();
      await fetchDossier(selectedIncidentId);
    } catch (err: any) {
      setErrorMessage(err?.message || "Failed to start validation");
    }
  };

  const handleResolve = async () => {
    if (!selectedIncidentId) return;
    try {
      await resolveIncident(runId, selectedIncidentId, modalText || "Resolved by operator", modalActor);
      setStatusNotification(`Incident #${selectedIncidentId} RESOLVED`);
      setActiveModal(null);
      await fetchIncidents();
      await fetchDossier(selectedIncidentId);
    } catch (err: any) {
      setErrorMessage(err?.message || "Failed to resolve incident");
    }
  };

  const handleSuppress = async () => {
    if (!selectedIncidentId) return;
    try {
      await suppressIncident(runId, selectedIncidentId, modalText || "Suppressed by operator", modalDuration, modalActor);
      setStatusNotification(`Incident #${selectedIncidentId} SUPPRESSED for ${modalDuration}h`);
      setActiveModal(null);
      await fetchIncidents();
      await fetchDossier(selectedIncidentId);
    } catch (err: any) {
      setErrorMessage(err?.message || "Failed to suppress incident");
    }
  };

  // Filtered incidents
  const filteredIncidents = useMemo(() => {
    return incidents.filter((inc) => {
      if (severityFilter !== "ALL" && inc.currentSeverity !== severityFilter) return false;
      if (lifecycleFilter !== "ALL" && inc.lifecycleState !== lifecycleFilter) return false;
      if (searchTarget && !inc.primaryTarget.toLowerCase().includes(searchTarget.toLowerCase())) return false;
      return true;
    });
  }, [incidents, severityFilter, lifecycleFilter, searchTarget]);

  // Incident summary stats
  const stats = useMemo(() => {
    const total = incidents.length;
    const critical = incidents.filter((i) => i.currentSeverity === "CRITICAL").length;
    const high = incidents.filter((i) => i.currentSeverity === "HIGH").length;
    const active = incidents.filter((i) => i.lifecycleState !== "RESOLVED" && i.lifecycleState !== "SUPPRESSED").length;
    const resolved = incidents.filter((i) => i.lifecycleState === "RESOLVED").length;
    return { total, critical, high, active, resolved };
  }, [incidents]);

  return (
    <div className="flex flex-col h-full bg-[#211D29] text-[#F4EFF6] font-mono select-none overflow-hidden">
      {/* 1. TOP DENSE HUD */}
      <div className="flex items-center justify-between px-4 py-2 bg-[#2A2432] border-b border-[#443849] shrink-0">
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-[#F18B78] animate-pulse" />
            <span className="text-xs font-bold tracking-wider text-[#F4EFF6]">INCIDENT DECISION WORKSPACE</span>
            <span className="px-1.5 py-0.5 text-[10px] font-bold bg-[#332B3C] text-[#B7ACBD] rounded border border-[#443849]">
              PHASE 11
            </span>
          </div>

          <span className="text-[#443849]">|</span>

          <div className="flex items-center gap-3 text-xs">
            <span className="text-[#8E8295]">LINEAGE:</span>
            <span className="text-[#F4EFF6] font-bold">{runSummary?.runId || runId}</span>
          </div>

          <span className="text-[#443849]">|</span>

          {/* HUD Counters */}
          <div className="flex items-center gap-2 text-[11px]">
            <span className="text-[#8E8295]">ACTIVE:</span>
            <span className="font-bold text-[#F4EFF6] bg-[#332B3C] px-1.5 py-0.2 rounded border border-[#443849]">
              {stats.active}
            </span>

            <span className="text-[#8E8295] ml-1">CRITICAL:</span>
            <span className="font-bold text-[#F18B78] bg-[#F18B78]/15 px-1.5 py-0.2 rounded border border-[#F18B78]/30">
              {stats.critical}
            </span>

            <span className="text-[#8E8295] ml-1">HIGH:</span>
            <span className="font-bold text-[#F18B78] bg-[#F18B78]/15 px-1.5 py-0.2 rounded border border-[#F18B78]/30">
              {stats.high}
            </span>

            <span className="text-[#8E8295] ml-1">RESOLVED:</span>
            <span className="font-bold text-[#B7D7A8] bg-[#B7D7A8]/15 px-1.5 py-0.2 rounded border border-[#B7D7A8]/30">
              {stats.resolved}
            </span>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {statusNotification && (
            <span className="text-[11px] text-[#B7D7A8] bg-[#B7D7A8]/10 px-2 py-0.5 rounded border border-[#B7D7A8]/30 animate-fade-in">
              {statusNotification}
            </span>
          )}

          <button
            onClick={handleRecalculate}
            disabled={isRecalculating}
            className="flex items-center gap-1.5 px-2.5 py-1 text-xs font-semibold bg-[#F18B78] hover:bg-[#F39C8B] text-[#211D29] font-bold rounded transition-colors cursor-pointer disabled:opacity-50"
          >
            <span className={isRecalculating ? "animate-spin" : ""}>↻</span>
            {isRecalculating ? "RECALCULATING..." : "RECALCULATE INCIDENTS"}
          </button>
        </div>
      </div>

      {errorMessage && (
        <div className="px-4 py-2 bg-[#F18B78]/10 border-b border-[#F18B78]/30 text-xs text-[#F18B78] flex justify-between items-center shrink-0">
          <span>⚠️ {errorMessage}</span>
          <button onClick={() => setErrorMessage(null)} className="text-[#B7ACBD] hover:text-[#F4EFF6]">✕</button>
        </div>
      )}

      {/* 2. MAIN SPLIT CONSOLE */}
      <div className="flex flex-1 min-h-0">
        {/* LEFT COLUMN: INCIDENT QUEUE */}
        <div className="w-[420px] flex flex-col border-r border-[#443849] bg-[#2A2432] shrink-0">
          {/* Queue Filter Bar */}
          <div className="p-2 border-b border-[#443849] space-y-1.5">
            <div className="flex items-center justify-between">
              <span className="text-[10px] text-[#8E8295] uppercase tracking-wider font-bold">
                INCIDENT QUEUE ({filteredIncidents.length})
              </span>
              <div className="flex items-center gap-1 text-[10px]">
                {["ALL", "CRITICAL", "HIGH"].map((sev) => (
                  <button
                    key={sev}
                    onClick={() => setSeverityFilter(sev)}
                    className={`px-1.5 py-0.5 rounded text-[10px] cursor-pointer ${
                      severityFilter === sev
                        ? "bg-[#F18B78] text-[#211D29] font-bold"
                        : "bg-[#332B3C] text-[#B7ACBD] hover:bg-[#3D3448]"
                    }`}
                  >
                    {sev}
                  </button>
                ))}
              </div>
            </div>

            <div className="flex items-center gap-1.5">
              <input
                type="text"
                value={searchTarget}
                onChange={(e) => setSearchTarget(e.target.value)}
                placeholder="Filter target (e.g. FEATURE::income)..."
                className="w-full bg-[#1C1824] text-xs px-2 py-1 border border-[#443849] rounded text-[#F4EFF6] focus:outline-none focus:border-[#F18B78]"
              />
              {searchTarget && (
                <button
                  onClick={() => setSearchTarget("")}
                  className="text-xs text-[#8E8295] hover:text-[#F4EFF6] px-1"
                >
                  ✕
                </button>
              )}
            </div>

            {/* Lifecycle State Pills */}
            <div className="flex items-center gap-1 overflow-x-auto pb-0.5 text-[9px]">
              {["ALL", "OPEN", "INVESTIGATING", "VALIDATING", "MONITORING", "RESOLVED"].map((state) => (
                <button
                  key={state}
                  onClick={() => setLifecycleFilter(state)}
                  className={`px-1.5 py-0.5 rounded whitespace-nowrap cursor-pointer ${
                    lifecycleFilter === state
                      ? "bg-[#332B3C] text-[#F4EFF6] border border-[#F18B78] font-bold"
                      : "bg-[#1C1824] text-[#8E8295] hover:text-[#B7ACBD]"
                  }`}
                >
                  {state}
                </button>
              ))}
            </div>
          </div>

          {/* Queue List */}
          <div className="flex-1 overflow-y-auto p-1.5 space-y-1">
            {isLoading ? (
              <div className="p-8 text-center text-xs text-[#8E8295]">
                <span className="inline-block animate-spin mr-2">⟳</span>
                Correlating operational incidents...
              </div>
            ) : filteredIncidents.length === 0 ? (
              <div className="p-8 text-center text-xs text-[#8E8295]">
                No operational incidents matching current filters.
              </div>
            ) : (
              filteredIncidents.map((inc) => {
                const isSelected = selectedIncidentId === inc.id;
                const sevStyle = SEVERITY_STYLES[inc.currentSeverity] || SEVERITY_STYLES.INFO;
                const lifeStyle = LIFECYCLE_STYLES[inc.lifecycleState] || LIFECYCLE_STYLES.OPEN;
                const decStyle = DECISION_STYLES[inc.decisionRecommendation] || DECISION_STYLES.NO_ACTION;

                return (
                  <div
                    key={inc.id}
                    onClick={() => setSelectedIncidentId(inc.id)}
                    className={`p-2.5 rounded border transition-all cursor-pointer ${
                      isSelected
                        ? "bg-[#332B3C] border-[#F18B78] shadow-sm"
                        : "bg-[#211D29] border-[#443849] hover:bg-[#2A2432] hover:border-[#5A4B62]"
                    }`}
                  >
                    <div className="flex items-center justify-between mb-1">
                      <div className="flex items-center gap-1.5">
                        <span className={`px-1.5 py-0.2 text-[9px] font-bold rounded border ${sevStyle.bg} ${sevStyle.text} ${sevStyle.border}`}>
                          {inc.currentSeverity}
                        </span>
                        <span className="text-xs font-bold text-[#F4EFF6]">{inc.incidentCode}</span>
                      </div>

                      <div className="flex items-center gap-1.5">
                        <span className="text-[10px] text-[#8E8295]">PRIORITY:</span>
                        <span className="text-xs font-bold text-[#F4EFF6] bg-[#332B3C] px-1.5 py-0.2 rounded border border-[#443849] tabular-nums">
                          {inc.priorityScore}
                        </span>
                      </div>
                    </div>

                    <div className="text-xs font-semibold text-[#F18B78] truncate mb-1">
                      {inc.primaryTarget}
                    </div>

                    <div className="flex items-center justify-between text-[10px] text-[#B7ACBD] mb-1.5">
                      <div className="flex items-center gap-2">
                        <span>{inc.independentModuleCount} mod</span>
                        <span>•</span>
                        <span>{inc.relatedAlertsCount} alerts</span>
                        {inc.hasContradictoryEvidence && (
                          <span className="text-[#E8B66D] font-bold" title="Contradictory evidence detected">
                            ⚠️ CONFLICT
                          </span>
                        )}
                      </div>

                      <span className={`px-1.5 py-0.2 rounded text-[9px] border font-semibold ${lifeStyle.bg} ${lifeStyle.text} ${lifeStyle.border}`}>
                        {inc.lifecycleState}
                      </span>
                    </div>

                    {/* Recommendation Pill */}
                    <div className="flex items-center justify-between pt-1 border-t border-[#443849] text-[10px]">
                      <span className="text-[#8E8295]">DECISION:</span>
                      <span className={`px-1.5 py-0.2 rounded font-bold border ${decStyle.bg} ${decStyle.text} ${decStyle.border}`}>
                        {inc.decisionRecommendation}
                      </span>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>

        {/* RIGHT COLUMN: INCIDENT EVIDENCE DOSSIER & DECISION CONSOLE */}
        <div className="flex-1 flex flex-col bg-[#211D29] overflow-y-auto">
          {isDossierLoading && !dossier ? (
            <div className="flex-1 flex items-center justify-center text-xs text-[#8E8295]">
              <span className="inline-block animate-spin mr-2">⟳</span>
              Loading comprehensive incident evidence dossier...
            </div>
          ) : !dossier ? (
            <div className="flex-1 flex items-center justify-center text-xs text-[#8E8295]">
              Select an incident from the queue to inspect evidence and operator decision state.
            </div>
          ) : (
            <div className="p-4 space-y-4 max-w-6xl">
              {/* Incident Header Card */}
              <div className="p-4 rounded bg-[#2A2432] border border-[#443849]">
                <div className="flex items-start justify-between">
                  <div>
                    <div className="flex items-center gap-2 mb-1">
                      <span className="text-base font-bold text-[#F4EFF6]">{dossier.incidentCode}</span>
                      <span className="text-sm font-semibold text-[#B7ACBD]">— {dossier.title}</span>
                    </div>

                    <div className="flex items-center gap-3 text-xs text-[#8E8295]">
                      <div>
                        CATEGORY: <span className="text-[#F4EFF6] font-semibold">{dossier.category}</span>
                      </div>
                      <div>•</div>
                      <div>
                        PRIMARY TARGET:{" "}
                        <button
                          onClick={() => onSelectFeature && onSelectFeature(dossier.primaryTarget.replace("FEATURE::", ""))}
                          className="text-[#F18B78] font-bold hover:underline cursor-pointer"
                        >
                          {dossier.primaryTarget}
                        </button>
                      </div>
                      <div>•</div>
                      <div>
                        LINEAGE: <span className="text-[#B7ACBD]">{dossier.modelLineageId}</span>
                      </div>
                    </div>
                  </div>

                  {/* Priority and Severity HUD */}
                  <div className="flex items-center gap-3">
                    <div className="text-right">
                      <div className="text-[10px] text-[#8E8295]">PRIORITY SCORE</div>
                      <div className="text-xl font-bold text-[#F4EFF6] tabular-nums">
                        {dossier.priorityScore}
                        <span className="text-xs text-[#8E8295]">/100</span>
                      </div>
                    </div>

                    <div className="flex flex-col gap-1 items-end">
                      <span className={`px-2 py-0.5 text-xs font-bold rounded border ${SEVERITY_STYLES[dossier.currentSeverity]?.bg} ${SEVERITY_STYLES[dossier.currentSeverity]?.text} ${SEVERITY_STYLES[dossier.currentSeverity]?.border}`}>
                        {dossier.currentSeverity}
                      </span>
                      <span className={`px-2 py-0.5 text-[10px] font-bold rounded border ${LIFECYCLE_STYLES[dossier.lifecycleState]?.bg} ${LIFECYCLE_STYLES[dossier.lifecycleState]?.text} ${LIFECYCLE_STYLES[dossier.lifecycleState]?.border}`}>
                        {dossier.lifecycleState}
                      </span>
                    </div>
                  </div>
                </div>

                {/* Priority Breakdown Chips */}
                {dossier.priorityBreakdown && (
                  <div className="mt-3 pt-3 border-t border-[#443849] flex items-center gap-2 flex-wrap text-[11px]">
                    <span className="text-[#8E8295] font-bold">SCORE COMPOSITION:</span>
                    <span className="bg-[#332B3C] text-[#F4EFF6] px-2 py-0.5 rounded border border-[#443849]">
                      Severity: +{dossier.priorityBreakdown.severityContribution}
                    </span>
                    <span className="bg-[#332B3C] text-[#F4EFF6] px-2 py-0.5 rounded border border-[#443849]">
                      Independent Modules ({dossier.independentModuleCount}): +{dossier.priorityBreakdown.independentEvidenceContribution}
                    </span>
                    <span className="bg-[#332B3C] text-[#F4EFF6] px-2 py-0.5 rounded border border-[#443849]">
                      Persistence/Escalation: +{dossier.priorityBreakdown.persistenceContribution}
                    </span>
                    <span className="bg-[#332B3C] text-[#F4EFF6] px-2 py-0.5 rounded border border-[#443849]">
                      Health Impact: +{dossier.priorityBreakdown.healthImpactContribution}
                    </span>
                  </div>
                )}
              </div>

              {/* Contradictory Evidence Alert (If Present) */}
              {dossier.contradictoryEvidence?.hasConflict && (
                <div className="p-3.5 rounded bg-[#E8B66D]/15 border border-[#E8B66D]/40 text-xs text-[#E8B66D] space-y-1.5">
                  <div className="flex items-center gap-2 font-bold">
                    <span>⚠️ CONTRADICTORY EVIDENCE DETECTED</span>
                    <span className="text-[10px] px-1.5 py-0.2 rounded bg-[#E8B66D]/30 text-[#F4EFF6] font-mono">
                      CONFIDENCE PENALTY APPLIED
                    </span>
                  </div>
                  <p className="text-[#F4EFF6]">{dossier.contradictoryEvidence.summary}</p>
                  <ul className="list-disc list-inside space-y-0.5 text-[11px] text-[#E8B66D]">
                    {dossier.contradictoryEvidence.conflictingSignals.map((sig, idx) => (
                      <li key={idx}>{sig}</li>
                    ))}
                  </ul>
                  <div className="text-[11px] text-[#E8B66D] pt-1 border-t border-[#E8B66D]/20">
                    Impact: {dossier.contradictoryEvidence.confidenceImpact} • Recommended Action: {dossier.contradictoryEvidence.recommendedAction}
                  </div>
                </div>
              )}

              {/* Evidence Synthesis Summary */}
              <div className="p-4 rounded bg-[#2A2432] border border-[#443849] space-y-2">
                <div className="text-xs font-bold text-[#B7ACBD] uppercase tracking-wider">
                  EVIDENCE SYNTHESIS & ASSOCIATIVE SUMMARY
                </div>
                <div className="text-xs font-mono bg-[#1C1824] p-3 rounded border border-[#443849] text-[#F4EFF6] whitespace-pre-wrap leading-relaxed">
                  {dossier.evidenceSummary}
                </div>
              </div>

              {/* DENSE EVIDENCE MATRIX TABLE */}
              <div className="p-4 rounded bg-[#2A2432] border border-[#443849] space-y-2">
                <div className="flex items-center justify-between">
                  <div className="text-xs font-bold text-[#B7ACBD] uppercase tracking-wider">
                    EVIDENCE MATRIX ({dossier.evidenceMatrix?.length || 0} SIGNALS)
                  </div>
                  <span className="text-[10px] text-[#8E8295]">
                    {dossier.independentModuleCount} INDEPENDENT DIAGNOSTIC MODULES
                  </span>
                </div>

                <div className="overflow-x-auto border border-[#443849] rounded">
                  <table className="w-full text-left text-xs border-collapse">
                    <thead>
                      <tr className="bg-[#332B3C] text-[#8E8295] text-[10px] font-bold uppercase border-b border-[#443849]">
                        <th className="p-2">EVIDENCE TYPE</th>
                        <th className="p-2">MODULE</th>
                        <th className="p-2">METRIC</th>
                        <th className="p-2 text-right">VALUE</th>
                        <th className="p-2 text-right">REFERENCE</th>
                        <th className="p-2 text-center">SEVERITY</th>
                        <th className="p-2">RUN PROVENANCE</th>
                        <th className="p-2">TRIGGER REASON</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-[#443849] text-[11px]">
                      {dossier.evidenceMatrix?.map((row, idx) => {
                        const sev = SEVERITY_STYLES[row.severity] || SEVERITY_STYLES.INFO;
                        return (
                          <tr key={idx} className="hover:bg-[#332B3C]/50 transition-colors">
                            <td className="p-2 font-semibold text-[#F4EFF6]">{row.alertType}</td>
                            <td className="p-2">
                              <span className="px-1.5 py-0.2 rounded bg-[#332B3C] text-[#F18B78] text-[10px] font-bold border border-[#443849]">
                                {row.module}
                              </span>
                            </td>
                            <td className="p-2 text-[#B7ACBD] font-mono">{row.metric}</td>
                            <td className="p-2 text-right font-bold text-[#F4EFF6] tabular-nums">{row.value}</td>
                            <td className="p-2 text-right text-[#8E8295] tabular-nums">{row.threshold}</td>
                            <td className="p-2 text-center">
                              <span className={`px-1.5 py-0.2 rounded text-[9px] font-bold border ${sev.bg} ${sev.text} ${sev.border}`}>
                                {row.severity}
                              </span>
                            </td>
                            <td className="p-2 text-[#B7ACBD] font-mono text-[10px]">{row.runId}</td>
                            <td className="p-2 text-[#B7ACBD] max-w-xs truncate" title={row.triggerDescription}>
                              {row.triggerDescription}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>

              {/* CROSS-PHASE PIPELINE INTEGRATION CHAIN */}
              <div className="p-4 rounded bg-[#2A2432] border border-[#443849] space-y-3">
                <div className="text-xs font-bold text-[#B7ACBD] uppercase tracking-wider">
                  CROSS-PHASE DIAGNOSTIC & REMEDIATION PIPELINE
                </div>

                <div className="grid grid-cols-3 gap-3">
                  {/* Phase 6 Investigation Link */}
                  <div className="p-3 rounded bg-[#332B3C] border border-[#443849] space-y-1.5">
                    <div className="flex items-center justify-between text-[10px] text-[#8E8295]">
                      <span className="font-bold text-[#F18B78]">PHASE 6 ROOT-CAUSE</span>
                      {dossier.investigationTarget ? (
                        <span className="text-[#B7D7A8] font-bold">LINKED</span>
                      ) : (
                        <span className="text-[#8E8295]">NO TARGET</span>
                      )}
                    </div>
                    {dossier.investigationTarget ? (
                      <div className="space-y-1">
                        <div className="text-xs font-bold text-[#F4EFF6] truncate">
                          {dossier.investigationTarget.targetKey}
                        </div>
                        <p className="text-[11px] text-[#B7ACBD] line-clamp-2">
                          {dossier.investigationTarget.hypothesis}
                        </p>
                        {navigate && (
                          <button
                            onClick={() => {
                              if (onSelectInvestigationTarget) onSelectInvestigationTarget(dossier.investigationTarget!.targetKey);
                              navigate("06_INVESTIGATION");
                            }}
                            className="text-[10px] text-[#F18B78] hover:underline block pt-1 cursor-pointer"
                          >
                            Inspect in Phase 6 →
                          </button>
                        )}
                      </div>
                    ) : (
                      <p className="text-[11px] text-[#8E8295] italic">
                        No active Phase 6 investigation target formulated for this target.
                      </p>
                    )}
                  </div>

                  {/* Phase 7 Remediation Link */}
                  <div className="p-3 rounded bg-[#332B3C] border border-[#443849] space-y-1.5">
                    <div className="flex items-center justify-between text-[10px] text-[#8E8295]">
                      <span className="font-bold text-[#F18B78]">PHASE 7 REMEDIATION</span>
                      {dossier.remediation ? (
                        <span className="text-[#B7D7A8] font-bold">PROPOSED</span>
                      ) : (
                        <span className="text-[#8E8295]">NO REMEDIATION</span>
                      )}
                    </div>
                    {dossier.remediation ? (
                      <div className="space-y-1">
                        <div className="text-xs font-bold text-[#F4EFF6] truncate">
                          {dossier.remediation.title}
                        </div>
                        <p className="text-[11px] text-[#B7ACBD] line-clamp-2">
                          {dossier.remediation.hypothesis}
                        </p>
                        {navigate && (
                          <button
                            onClick={() => navigate("07_REMEDIATION")}
                            className="text-[10px] text-[#F18B78] hover:underline block pt-1 cursor-pointer"
                          >
                            Review in Phase 7 →
                          </button>
                        )}
                      </div>
                    ) : (
                      <p className="text-[11px] text-[#8E8295] italic">
                        No remediation proposal formulated yet. Requires investigation review.
                      </p>
                    )}
                  </div>

                  {/* Phase 8 Experiment Link */}
                  <div className="p-3 rounded bg-[#332B3C] border border-[#443849] space-y-1.5">
                    <div className="flex items-center justify-between text-[10px] text-[#8E8295]">
                      <span className="font-bold text-[#B7D7A8]">PHASE 8 VALIDATION</span>
                      {dossier.experiment ? (
                        <span className="text-[#B7D7A8] font-bold">{dossier.experiment.status}</span>
                      ) : (
                        <span className="text-[#8E8295]">NO EXPERIMENT</span>
                      )}
                    </div>
                    {dossier.experiment ? (
                      <div className="space-y-1">
                        <div className="text-xs font-bold text-[#F4EFF6] truncate">
                          {dossier.experiment.id} — {dossier.experiment.title}
                        </div>
                        <p className="text-[11px] text-[#B7ACBD] line-clamp-2">
                          Conclusion: {dossier.experiment.conclusion || "In validation"}
                        </p>
                        {navigate && (
                          <button
                            onClick={() => navigate("08_EXPERIMENT")}
                            className="text-[10px] text-[#B7D7A8] hover:underline block pt-1 cursor-pointer"
                          >
                            Inspect in Phase 8 →
                          </button>
                        )}
                      </div>
                    ) : (
                      <p className="text-[11px] text-[#8E8295] italic">
                        No experimental validation runs executed for this remediation.
                      </p>
                    )}
                  </div>
                </div>
              </div>

              {/* OPERATOR DECISION CONSOLE */}
              {dossier.decision && (
                <div className="p-4 rounded bg-[#2A2432] border border-[#443849] space-y-3">
                  <div className="flex items-center justify-between">
                    <div className="text-xs font-bold text-[#B7ACBD] uppercase tracking-wider">
                      OPERATOR DECISION RECOMMENDATION
                    </div>

                    <div className="flex items-center gap-2">
                      <span className="text-xs text-[#8E8295]">CONFIDENCE:</span>
                      <span className={`px-2 py-0.5 text-xs font-bold rounded border ${CONFIDENCE_BADGES[dossier.decision.confidence]?.bg} ${CONFIDENCE_BADGES[dossier.decision.confidence]?.text} ${CONFIDENCE_BADGES[dossier.decision.confidence]?.border}`}>
                        {dossier.decision.confidence}
                      </span>
                    </div>
                  </div>

                  <div className="p-3 rounded bg-[#1C1824] border border-[#443849] space-y-2">
                    <div className="flex items-center gap-2">
                      <span className="text-xs text-[#8E8295]">RECOMMENDED ACTION:</span>
                      <span className={`px-2.5 py-1 text-xs font-bold rounded border ${DECISION_STYLES[dossier.decision.recommendation]?.bg} ${DECISION_STYLES[dossier.decision.recommendation]?.text} ${DECISION_STYLES[dossier.decision.recommendation]?.border}`}>
                        {dossier.decision.recommendation}
                      </span>
                    </div>

                    <div className="text-xs text-[#F4EFF6]">
                      <span className="text-[#8E8295] font-semibold">RATIONALE:</span>{" "}
                      {dossier.decision.rationale}
                    </div>

                    <div className="text-xs text-[#F18B78]">
                      <span className="text-[#8E8295] font-semibold">NEXT ACTION:</span>{" "}
                      {dossier.decision.nextAction}
                    </div>

                    <div className="text-[10px] text-[#8E8295] italic pt-1 border-t border-[#443849]">
                      {dossier.decision.constraints}
                    </div>
                  </div>

                  {/* LIFECYCLE ACTION BUTTONS */}
                  <div className="pt-2 border-t border-[#443849] flex items-center justify-between flex-wrap gap-2">
                    <div className="text-[11px] text-[#8E8295]">OPERATOR ACTIONS:</div>
                    <div className="flex items-center gap-2 flex-wrap">
                      {dossier.lifecycleState === "OPEN" && (
                        <button
                          onClick={() => {
                            setActiveModal("ACKNOWLEDGE");
                            setModalText("");
                          }}
                          className="px-2.5 py-1 text-xs font-bold bg-[#E8B66D]/20 hover:bg-[#E8B66D]/30 text-[#E8B66D] border border-[#E8B66D]/40 rounded transition-colors cursor-pointer"
                        >
                          ACKNOWLEDGE
                        </button>
                      )}

                      {(dossier.lifecycleState === "OPEN" || dossier.lifecycleState === "ACKNOWLEDGED") && (
                        <button
                          onClick={() => {
                            setActiveModal("INVESTIGATE");
                            setModalText("");
                          }}
                          className="px-2.5 py-1 text-xs font-bold bg-[#F18B78]/20 hover:bg-[#F18B78]/30 text-[#F18B78] border border-[#F18B78]/40 rounded transition-colors cursor-pointer"
                        >
                          INVESTIGATE
                        </button>
                      )}

                      {dossier.lifecycleState === "INVESTIGATING" && (
                        <button
                          onClick={() => {
                            setActiveModal("PLAN_MITIGATION");
                            setModalText("");
                          }}
                          className="px-2.5 py-1 text-xs font-bold bg-[#332B3C] hover:bg-[#3D3448] text-[#F4EFF6] border border-[#443849] rounded transition-colors cursor-pointer"
                        >
                          PLAN MITIGATION
                        </button>
                      )}

                      {dossier.lifecycleState === "MITIGATION_PLANNED" && (
                        <button
                          onClick={() => {
                            setActiveModal("START_VALIDATION");
                            setModalText("");
                          }}
                          className="px-2.5 py-1 text-xs font-bold bg-[#E8B66D]/20 hover:bg-[#E8B66D]/30 text-[#E8B66D] border border-[#E8B66D]/40 rounded transition-colors cursor-pointer"
                        >
                          START VALIDATION
                        </button>
                      )}

                      {dossier.lifecycleState !== "RESOLVED" && (
                        <>
                          <button
                            onClick={() => {
                              setActiveModal("RESOLVE");
                              setModalText("");
                            }}
                            className="px-2.5 py-1 text-xs font-bold bg-[#B7D7A8]/20 hover:bg-[#B7D7A8]/30 text-[#B7D7A8] border border-[#B7D7A8]/40 rounded transition-colors cursor-pointer"
                          >
                            RESOLVE
                          </button>

                          <button
                            onClick={() => {
                              setActiveModal("SUPPRESS");
                              setModalText("");
                            }}
                            className="px-2.5 py-1 text-xs font-bold bg-[#332B3C] hover:bg-[#3D3448] text-[#8E8295] border border-[#443849] rounded transition-colors cursor-pointer"
                          >
                            SUPPRESS
                          </button>
                        </>
                      )}
                    </div>
                  </div>
                </div>
              )}

              {/* AUDIT EVENT LOG */}
              {dossier.auditEvents && dossier.auditEvents.length > 0 && (
                <div className="p-4 rounded bg-[#2A2432] border border-[#443849] space-y-2">
                  <div className="text-xs font-bold text-[#B7ACBD] uppercase tracking-wider">
                    INCIDENT AUDIT LOG ({dossier.auditEvents.length} EVENTS)
                  </div>
                  <div className="space-y-1.5 max-h-48 overflow-y-auto">
                    {dossier.auditEvents.map((evt) => (
                      <div
                        key={evt.id}
                        className="p-2 rounded bg-[#1C1824] border border-[#443849] flex items-center justify-between text-[11px]"
                      >
                        <div className="flex items-center gap-2">
                          <span className="text-[#F18B78] font-bold">{evt.action}</span>
                          <span className="text-[#8E8295]">({evt.previousState} → {evt.newState})</span>
                          <span className="text-[#F4EFF6]">{evt.reason}</span>
                        </div>

                        <div className="flex items-center gap-2 text-[10px] text-[#8E8295]">
                          <span>by {evt.actor}</span>
                          <span>•</span>
                          <span>{new Date(evt.timestamp).toLocaleTimeString()}</span>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {activeModal && (
        <div className="fixed inset-0 bg-black/70 backdrop-blur-xs flex items-center justify-center z-50 p-4">
          <div className="bg-[#2A2432] border border-[#443849] rounded-lg p-5 max-w-md w-full space-y-4 shadow-2xl">
            <div className="flex items-center justify-between border-b border-[#443849] pb-2">
              <span className="text-sm font-bold text-[#F4EFF6]">
                TRANSITION INCIDENT: {activeModal}
              </span>
              <button
                onClick={() => setActiveModal(null)}
                className="text-[#8E8295] hover:text-[#F4EFF6] cursor-pointer"
              >
                ✕
              </button>
            </div>

            <div className="space-y-3 text-xs">
              <div>
                <label className="text-[#8E8295] block mb-1">ACTOR</label>
                <input
                  type="text"
                  value={modalActor}
                  onChange={(e) => setModalActor(e.target.value)}
                  className="w-full bg-[#1C1824] text-[#F4EFF6] px-2.5 py-1.5 border border-[#443849] rounded focus:outline-none focus:border-[#F18B78]"
                />
              </div>

              <div>
                <label className="text-[#8E8295] block mb-1">
                  {activeModal === "RESOLVE" ? "RESOLUTION REASON" : "OPERATOR NOTE / RATIONALE"}
                </label>
                <textarea
                  rows={3}
                  value={modalText}
                  onChange={(e) => setModalText(e.target.value)}
                  placeholder="Enter detailed engineering justification..."
                  className="w-full bg-[#1C1824] text-[#F4EFF6] px-2.5 py-1.5 border border-[#443849] rounded focus:outline-none focus:border-[#F18B78]"
                />
              </div>

              {activeModal === "SUPPRESS" && (
                <div>
                  <label className="text-[#8E8295] block mb-1">SUPPRESSION DURATION (HOURS)</label>
                  <input
                    type="number"
                    value={modalDuration}
                    onChange={(e) => setModalDuration(parseInt(e.target.value) || 24)}
                    className="w-full bg-[#1C1824] text-[#F4EFF6] px-2.5 py-1.5 border border-[#443849] rounded focus:outline-none focus:border-[#F18B78]"
                  />
                </div>
              )}
            </div>

            <div className="flex items-center justify-end gap-2 pt-2 border-t border-[#443849]">
              <button
                onClick={() => setActiveModal(null)}
                className="px-3 py-1.5 text-xs text-[#B7ACBD] hover:bg-[#332B3C] rounded cursor-pointer"
              >
                CANCEL
              </button>
              <button
                onClick={() => {
                  if (activeModal === "ACKNOWLEDGE") handleAcknowledge();
                  else if (activeModal === "INVESTIGATE") handleInvestigate();
                  else if (activeModal === "PLAN_MITIGATION") handlePlanMitigation();
                  else if (activeModal === "START_VALIDATION") handleStartValidation();
                  else if (activeModal === "RESOLVE") handleResolve();
                  else if (activeModal === "SUPPRESS") handleSuppress();
                }}
                className="px-4 py-1.5 text-xs font-bold bg-[#F18B78] hover:bg-[#F39C8B] text-[#211D29] rounded cursor-pointer"
              >
                CONFIRM TRANSITION
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
