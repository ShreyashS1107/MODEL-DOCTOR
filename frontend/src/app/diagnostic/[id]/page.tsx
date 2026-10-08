"use client";

import React, { use, useEffect, useState } from "react";
import Link from "next/link";
import { WorkstationHeader } from "@/components/layout/WorkstationHeader";
import { WorkstationSidebar } from "@/components/layout/WorkstationSidebar";
import { DiagnosticViewport } from "@/components/viewport/DiagnosticViewport";
import { RightInspector } from "@/components/inspector/RightInspector";
import { EventLogStream } from "@/components/console/EventLogStream";
import { DataQualityView } from "@/components/views/DataQualityView";
import { ForensicsView } from "@/components/views/ForensicsView";
import { DriftView } from "@/components/views/DriftView";
import { PerformanceView } from "@/components/views/PerformanceView";
import { ErrorForensicsView } from "@/components/views/ErrorForensicsView";
import { ExplainView } from "@/components/views/ExplainView";
import { BiasView } from "@/components/views/BiasView";
import { RobustnessView } from "@/components/views/RobustnessView";
import { ExperimentsView } from "@/components/views/ExperimentsView";
import { ReportsView } from "@/components/views/ReportsView";
import { IntelligenceView } from "@/components/views/IntelligenceView";
import { InvestigationView } from "@/components/views/InvestigationView";
import { RemediationView } from "@/components/views/RemediationView";
import { ExperimentView } from "@/components/views/ExperimentView";
import { TemporalView } from "@/components/views/TemporalView";
import { Modal } from "@/components/ui/Modal";
import {
  getDiagnosticRun,
  getDiagnosticResults,
  startDiagnosticRun,
  retryDiagnosticRun,
  retryDiagnosticModules,
  getDiagnosticRunEvents,
  getDiagnosticRunProgress,
  getDiagnosticCorrelations,
  getDiagnosticRunSummary,
  DiagnosticProgress,
  DiagnosticRunEvent,
  DiagnosticCorrelation,
  RunSummary,
} from "@/lib/api";
import {
  CALIBRATION_BINS,
  DIAGNOSTIC_RUNS,
  DISTRIBUTION_BINS,
  EVENT_LOGS,
  FEATURE_EDGES,
  FEATURE_NODES,
  MODEL_SUMMARY,
} from "@/lib/mockData";
import {
  DiagnosticRunRecord,
  FeatureNode,
  LogEntry,
  ModelDiagnosticSummary,
  NavSection,
} from "@/types/diagnostics";

interface DiagnosticWorkstationPageProps {
  params: Promise<{ id: string }>;
}

export default function DiagnosticWorkstationPage({
  params,
}: DiagnosticWorkstationPageProps) {
  const resolvedParams = use(params);
  const runIdParam = resolvedParams.id;

  // Resolve matching run record from mock fixture or build dynamic summary
  const matchingRun = DIAGNOSTIC_RUNS.find(
    (r) =>
      r.id.toLowerCase() === runIdParam.toLowerCase() ||
      r.runNumber.toLowerCase() === `#${runIdParam}`.toLowerCase()
  );

  const initialSummary: ModelDiagnosticSummary = matchingRun
    ? {
      ...MODEL_SUMMARY,
      runId: matchingRun.runNumber,
      modelId: matchingRun.modelName,
      modelVersion: matchingRun.modelVersion,
      datasetName: matchingRun.datasetName,
      healthScore: matchingRun.healthScore,
      criticalFindingsCount: matchingRun.criticalCount,
      warningsCount: matchingRun.warningCount,
      sampleCount: matchingRun.sampleCount,
    }
    : {
      ...MODEL_SUMMARY,
      runId: runIdParam.startsWith("run_") || runIdParam.startsWith("#") ? runIdParam : `#${runIdParam}`,
    };

  const [activeSection, setActiveSection] = useState<NavSection>("01_OVERVIEW");
  const [selectedFeature, setSelectedFeature] = useState<FeatureNode | null>(
    FEATURE_NODES[0]
  );
  const [logs, setLogs] = useState<LogEntry[]>(EVENT_LOGS);
  const [summary, setSummary] = useState<ModelDiagnosticSummary>(initialSummary);
  const [backendStatus, setBackendStatus] = useState<string>("READY");
  const [retryCount, setRetryCount] = useState<number>(0);
  const [progress, setProgress] = useState<DiagnosticProgress | null>(null);
  const [moduleResults, setModuleResults] = useState<any[]>([]);
  const [isRetrying, setIsRetrying] = useState<boolean>(false);
  const [correlations, setCorrelations] = useState<DiagnosticCorrelation[]>([]);
  const [runSummary, setRunSummary] = useState<RunSummary | null>(null);
  const [isLoadingCorrelations, setIsLoadingCorrelations] = useState<boolean>(false);

  // Poll backend run state, events, progress, and results
  useEffect(() => {
    let isMounted = true;
    let pollTimer: NodeJS.Timeout | null = null;

    async function fetchExecutionState() {
      try {
        const runData = await getDiagnosticRun(runIdParam);
        if (!isMounted) return;

        setBackendStatus(runData.status);
        setRetryCount(runData.retryCount || 0);

        setSummary((prev) => ({
          ...prev,
          runId: runData.id,
          modelId: runData.modelArtifact?.originalFilename || runData.model?.name || prev.modelId,
          framework: runData.modelArtifact?.framework || runData.model?.framework || prev.framework,
          taskType: runData.modelArtifact?.taskType || runData.model?.taskType || prev.taskType,
          datasetName: runData.evaluationDatasetArtifact?.originalFilename || runData.evaluationDataset || prev.datasetName,
          executionMode: runData.executionMode,
          modelArtifact: runData.modelArtifact ? {
            id: runData.modelArtifact.id,
            originalFilename: runData.modelArtifact.originalFilename,
            framework: runData.modelArtifact.framework,
            modelFormat: runData.modelArtifact.modelFormat,
            fileSize: runData.modelArtifact.fileSize,
            sha256: runData.modelArtifact.sha256,
          } : undefined,
          evaluationDatasetArtifact: runData.evaluationDatasetArtifact ? {
            id: runData.evaluationDatasetArtifact.id,
            originalFilename: runData.evaluationDatasetArtifact.originalFilename,
            datasetFormat: runData.evaluationDatasetArtifact.datasetFormat,
            fileSize: runData.evaluationDatasetArtifact.fileSize,
            rowCount: runData.evaluationDatasetArtifact.rowCount,
            columnCount: runData.evaluationDatasetArtifact.columnCount,
            sha256: runData.evaluationDatasetArtifact.sha256,
          } : undefined,
          baselineDatasetArtifact: runData.baselineDatasetArtifact ? {
            id: runData.baselineDatasetArtifact.id,
            originalFilename: runData.baselineDatasetArtifact.originalFilename,
            datasetFormat: runData.baselineDatasetArtifact.datasetFormat,
            fileSize: runData.baselineDatasetArtifact.fileSize,
            rowCount: runData.baselineDatasetArtifact.rowCount,
            columnCount: runData.baselineDatasetArtifact.columnCount,
            sha256: runData.baselineDatasetArtifact.sha256,
          } : undefined,
          isMockData: false,
        }));

        // Fetch real events timeline
        try {
          const eventsData = await getDiagnosticRunEvents(runIdParam);
          if (eventsData && eventsData.length > 0 && isMounted) {
            const formattedEvents: LogEntry[] = eventsData.map((ev) => {
              const d = new Date(ev.timestamp);
              const timeStr = isNaN(d.getTime()) ? ev.timestamp : d.toLocaleTimeString();
              let level: LogEntry["level"] = "INFO";
              if (ev.eventType.includes("FAILED") || ev.eventType.includes("CRIT")) level = "CRIT";
              else if (ev.eventType.includes("PARTIAL") || ev.eventType.includes("WARN") || ev.eventType.includes("RECOVERED")) level = "WARN";
              else if (ev.eventType.includes("COMPLETED") || ev.eventType.includes("EVAL")) level = "EVAL";

              return {
                id: `ev_${ev.id || Math.random()}`,
                timestamp: timeStr,
                level,
                module: ev.module || "ORCHESTRATOR",
                message: `[${ev.eventType}] ${ev.message}`,
              };
            });
            // Reverse so newest events appear at top
            setLogs(formattedEvents.reverse());
          }
        } catch {
          // Fallback if events not ready
        }

        // Fetch progress
        try {
          const progData = await getDiagnosticRunProgress(runIdParam);
          if (progData && isMounted) {
            setProgress(progData);
          }
        } catch {}

        // Fetch structured results if completed/partial
        if (runData.status === "COMPLETED" || runData.status === "PARTIAL") {
          try {
            const resultsData = await getDiagnosticResults(runIdParam);
            if (resultsData?.results && isMounted) {
              setModuleResults(resultsData.results);

              const realDq = resultsData.results.find((m: any) => m.module === "DATA_QUALITY" && m.status === "COMPLETED")?.result;
              const realLeak = resultsData.results.find((m: any) => m.module === "LEAKAGE" && m.status === "COMPLETED")?.result;
              const realDrift = resultsData.results.find((m: any) => m.module === "DRIFT" && m.status === "COMPLETED")?.result;
              const realPerf = resultsData.results.find((m: any) => m.module === "PERFORMANCE" && m.status === "COMPLETED")?.result;
              const realExplain = resultsData.results.find((m: any) => m.module === "EXPLAINABILITY" && m.status === "COMPLETED")?.result;
              const realBias = resultsData.results.find((m: any) => (m.module === "BIAS" || m.module === "FAIRNESS") && m.status === "COMPLETED")?.result;
              const realRobustness = resultsData.results.find((m: any) => m.module === "ROBUSTNESS" && m.status === "COMPLETED")?.result;

              if (realDq || realLeak || realDrift || realPerf || realExplain || realBias || realRobustness) {
                setSummary((prev) => {
                  const sampleCount = realDq?.summary?.rowCount ?? realPerf?.summary?.sampleCount ?? realDrift?.summary?.evaluationRowCount ?? realExplain?.summary?.sampleCount ?? realBias?.summary?.totalEvaluatedRows ?? realRobustness?.summary?.samplesEvaluated ?? prev.sampleCount;
                  const featureCount = realDq?.summary?.columnCount ?? realDrift?.summary?.featuresEvaluated ?? realExplain?.summary?.featureCount ?? realRobustness?.summary?.numericFeaturesEvaluated ?? prev.featureCount;
                  const healthScores = [
                    realDq?.summary?.healthScore,
                    realLeak?.summary?.healthScore,
                    realDrift?.summary?.healthScore,
                    realPerf?.summary?.healthScore,
                    realExplain?.summary?.healthScore,
                    realBias?.summary?.healthScore,
                    realRobustness?.summary?.healthScore,
                  ].filter((s): s is number => s !== undefined && s !== null);

                  const avgHealth = healthScores.length > 0
                    ? Math.round(healthScores.reduce((a, b) => a + b, 0) / healthScores.length)
                    : prev.healthScore;

                  const dqCrit = realDq?.findings?.filter((f: any) => f.severity === "CRITICAL").length || 0;
                  const leakCrit = realLeak?.summary?.criticalLeakageCount || 0;
                  const driftCrit = realDrift?.summary?.criticalDriftCount || (realDrift?.findings?.filter((f: any) => f.severity === "CRITICAL").length || 0);
                  const perfCrit = realPerf?.findings?.filter((f: any) => f.severity === "CRITICAL").length || 0;
                  const explainCrit = realExplain?.findings?.filter((f: any) => f.severity === "CRITICAL").length || 0;
                  const biasCrit = realBias?.findings?.filter((f: any) => f.severity === "CRITICAL" || f.severity === "HIGH").length || 0;
                  const robustCrit = realRobustness?.findings?.filter((f: any) => f.severity === "CRITICAL" || f.severity === "HIGH").length || 0;

                  const dqWarn = realDq?.findings?.filter((f: any) => f.severity === "WARNING" || f.severity === "HIGH").length || 0;
                  const leakHigh = realLeak?.summary?.highLeakageCount || 0;
                  const driftWarn = realDrift?.summary?.highDriftCount || (realDrift?.findings?.filter((f: any) => f.severity === "WARNING" || f.severity === "HIGH").length || 0);
                  const perfWarn = realPerf?.findings?.filter((f: any) => f.severity === "WARNING" || f.severity === "HIGH").length || 0;
                  const explainWarn = realExplain?.findings?.filter((f: any) => f.severity === "WARNING" || f.severity === "HIGH").length || 0;
                  const biasWarn = realBias?.findings?.filter((f: any) => f.severity === "WARNING" || f.severity === "MEDIUM").length || 0;
                  const robustWarn = realRobustness?.findings?.filter((f: any) => f.severity === "WARNING" || f.severity === "MEDIUM").length || 0;

                  return {
                    ...prev,
                    sampleCount,
                    featureCount,
                    healthScore: avgHealth,
                    criticalFindingsCount: dqCrit + leakCrit + driftCrit + perfCrit + explainCrit + biasCrit + robustCrit,
                    warningsCount: dqWarn + leakHigh + driftWarn + perfWarn + explainWarn + biasWarn + robustWarn,
                    maxMutualInfoLeakage: realLeak?.summary?.maxMutualInformation ?? prev.maxMutualInfoLeakage,
                    maxPsiDrift: realDrift?.summary?.maxPsi ?? prev.maxPsiDrift,
                    rocAuc: realPerf?.summary?.rocAuc ?? prev.rocAuc,
                    f1Score: realPerf?.summary?.f1 ?? prev.f1Score,
                    expectedCalibrationError: realPerf?.summary?.expectedCalibrationError ?? prev.expectedCalibrationError,
                    brierScore: realPerf?.summary?.brierScore ?? prev.brierScore,
                    disparateImpactRatio: realBias?.summary?.worstDisparateImpactRatio ?? prev.disparateImpactRatio,
                    gaussianJitterFlipRate: realRobustness?.summary?.gaussianJitter5PctFlipRate ?? prev.gaussianJitterFlipRate,
                    isMockData: false,
                  };
                });
              }
            }
          } catch {}

          // Fetch Phase 4 cross-module correlations and run summary
          try {
            setIsLoadingCorrelations(true);
            const [correlationsData, summaryData] = await Promise.all([
              getDiagnosticCorrelations(runIdParam),
              getDiagnosticRunSummary(runIdParam),
            ]);
            if (isMounted) {
              setCorrelations(correlationsData || []);
              setRunSummary(summaryData || null);
            }
          } catch {
            // Suppress if correlations not generated yet
          } finally {
            if (isMounted) setIsLoadingCorrelations(false);
          }
        }

        // Schedule next poll if still running/queued
        if (runData.status === "RUNNING" || runData.status === "QUEUED") {
          pollTimer = setTimeout(fetchExecutionState, 1500);
        }
      } catch {
        // Fallback for demo mode
      }
    }

    fetchExecutionState();

    return () => {
      isMounted = false;
      if (pollTimer) clearTimeout(pollTimer);
    };
  }, [runIdParam]);

  const handleRetryRun = async () => {
    if (!runIdParam.startsWith("run_")) return;
    setIsRetrying(true);
    try {
      await retryDiagnosticRun(runIdParam);
      setBackendStatus("RUNNING");
      const res = await getDiagnosticRun(runIdParam);
      setBackendStatus(res.status);
    } catch (err: any) {
      alert(`Retry failed: ${err.message || err}`);
    } finally {
      setIsRetrying(false);
    }
  };

  const handleRetryFailedModules = async () => {
    if (!runIdParam.startsWith("run_")) return;
    try {
      const runData = await getDiagnosticRun(runIdParam);
      const failedMods = (runData.selectedModules || [])
        .filter((m) => m.status === "FAILED")
        .map((m) => m.module);

      if (failedMods.length === 0) {
        alert("No failed modules to retry.");
        return;
      }

      setIsRetrying(true);
      await retryDiagnosticModules(runIdParam, failedMods);
      setBackendStatus("RUNNING");
    } catch (err: any) {
      alert(`Module retry failed: ${err.message || err}`);
    } finally {
      setIsRetrying(false);
    }
  };

  const dqModule = moduleResults.find((m) => m.module === "DATA_QUALITY");
  const leakageModule = moduleResults.find((m) => m.module === "LEAKAGE");
  const driftModule = moduleResults.find((m) => m.module === "DRIFT");
  const perfModule = moduleResults.find((m) => m.module === "PERFORMANCE");
  const explainModule = moduleResults.find((m) => m.module === "EXPLAINABILITY");
  const biasModule = moduleResults.find((m) => m.module === "BIAS" || m.module === "FAIRNESS");
  const robustModule = moduleResults.find((m) => m.module === "ROBUSTNESS");
  const errorForensicsModule = moduleResults.find((m) => m.module === "ERROR_FORENSICS");

  // Modal dialog states
  const [isScanModalOpen, setIsScanModalOpen] = useState(false);
  const [isScanning, setIsScanning] = useState(false);
  const [scanStep, setScanStep] = useState("");

  const handleRunDiagnostic = async () => {
    setIsScanning(true);
    setScanStep("Dispatching re-audit job to Spring Boot orchestrator...");

    try {
      if (runIdParam.startsWith("run_")) {
        await startDiagnosticRun(runIdParam);
      }
    } catch {
      // Continue simulation for demo fallback
    }

    setTimeout(() => setScanStep("1/3 Python ML engine executing forensic probes..."), 400);
    setTimeout(() => setScanStep("2/3 Synchronizing module results and persistence..."), 800);
    setTimeout(() => setScanStep("3/3 Updating workstation viewport..."), 1200);

    setTimeout(() => {
      setIsScanning(false);
      setIsScanModalOpen(false);
      const newLog: LogEntry = {
        id: `log_${Date.now()}`,
        timestamp: new Date().toLocaleTimeString(),
        level: "INFO",
        module: "ORCHESTRATOR",
        message: `Diagnostic audit refreshed for ${summary.runId}. Results synchronized.`,
      };
      setLogs((prev) => [newLog, ...prev]);
    }, 1600);
  };


  const renderCenterContent = () => {
    switch (activeSection) {
      case "00_INTELLIGENCE":
        return (
          <div className="flex-1 overflow-auto p-4 bg-[#090b0e]">
            <IntelligenceView
              runId={runIdParam}
              correlations={correlations}
              runSummary={runSummary}
              isLoading={isLoadingCorrelations}
              onSelectSection={(sec) => setActiveSection(sec)}
              onSelectFeature={(feat) => {
                const node = FEATURE_NODES.find((n) => n.name === feat);
                if (node) setSelectedFeature(node);
              }}
              onRefresh={async () => {
                try {
                  const corrs = await getDiagnosticCorrelations(runIdParam);
                  const summ = await getDiagnosticRunSummary(runIdParam);
                  setCorrelations(corrs);
                  setRunSummary(summ);
                } catch {}
              }}
            />
          </div>
        );
      case "06_INVESTIGATION":
        return (
          <div className="flex-1 overflow-auto p-4 bg-[#090b0e]">
            <InvestigationView
              runId={runIdParam}
              runSummary={runSummary}
              onSelectSection={(sec) => setActiveSection(sec)}
              onSelectFeature={(feat) => {
                const node = FEATURE_NODES.find((n) => n.name === feat);
                if (node) setSelectedFeature(node);
              }}
              onRefresh={async () => {
                try {
                  const corrs = await getDiagnosticCorrelations(runIdParam);
                  const summ = await getDiagnosticRunSummary(runIdParam);
                  setCorrelations(corrs);
                  setRunSummary(summ);
                } catch {}
              }}
            />
          </div>
        );
      case "01_OVERVIEW":
        return (
          <DiagnosticViewport
            nodes={FEATURE_NODES}
            edges={FEATURE_EDGES}
            distributionBins={DISTRIBUTION_BINS}
            calibrationBins={CALIBRATION_BINS}
            runs={DIAGNOSTIC_RUNS}
            selectedFeature={selectedFeature}
            onSelectFeature={setSelectedFeature}
            onSelectRun={(run: DiagnosticRunRecord) => {
              window.location.href = `/diagnostic/${run.id}`;
            }}
          />
        );
      case "02_DATA":
        return (
          <div className="flex-1 overflow-auto p-4 bg-[#090b0e]">
            <DataQualityView
              result={dqModule?.result}
              status={dqModule?.status}
              statusMessage={dqModule?.statusMessage}
            />
          </div>
        );
      case "03_FORENSICS":
        return (
          <div className="flex-1 overflow-auto p-4 bg-[#090b0e]">
            <ForensicsView
              result={leakageModule?.result}
              status={leakageModule?.status}
              statusMessage={leakageModule?.statusMessage}
            />
          </div>
        );
      case "04_DRIFT":
        return (
          <div className="flex-1 overflow-auto p-4 bg-[#090b0e]">
            <DriftView
              result={driftModule?.result}
              status={driftModule?.status}
              statusMessage={driftModule?.statusMessage}
            />
          </div>
        );
      case "05_PERFORMANCE":
        return (
          <div className="flex-1 overflow-auto p-4 bg-[#090b0e]">
            <PerformanceView
              result={perfModule?.result}
              status={perfModule?.status}
              statusMessage={perfModule?.statusMessage}
            />
          </div>
        );
      case "05_ERROR_FORENSICS":
        return (
          <div className="flex-1 overflow-auto p-4 bg-[#090b0e]">
            <ErrorForensicsView
              result={errorForensicsModule?.result}
              status={errorForensicsModule?.status}
              statusMessage={errorForensicsModule?.statusMessage}
            />
          </div>
        );
      case "06_EXPLAIN":
        return (
          <div className="flex-1 overflow-auto p-4 bg-[#090b0e]">
            <ExplainView
              explainResult={explainModule?.result}
              status={explainModule?.status}
              statusMessage={explainModule?.statusMessage}
            />
          </div>
        );
      case "07_REMEDIATION":
        return (
          <div className="flex-1 overflow-auto bg-[#090b0e]">
            <RemediationView
              runId={runIdParam}
              runSummary={runSummary}
              onSelectSection={setActiveSection}
              onSelectFeature={(featName) => {
                const node = FEATURE_NODES.find((f) => f.name === featName) || null;
                setSelectedFeature(node);
              }}
              onRefresh={async () => {
                const summ = await getDiagnosticRunSummary(runIdParam);
                setRunSummary(summ);
              }}
            />
          </div>
        );
      case "08_EXPERIMENT":
        return (
          <div className="flex-1 overflow-auto bg-[#090b0e]">
            <ExperimentView
              runId={runIdParam}
              runSummary={runSummary}
              onSelectSection={setActiveSection}
              onSelectFeature={(featName) => {
                const node = FEATURE_NODES.find((f) => f.name === featName) || null;
                setSelectedFeature(node);
              }}
              onRefresh={async () => {
                const summ = await getDiagnosticRunSummary(runIdParam);
                setRunSummary(summ);
              }}
            />
          </div>
        );
      case "09_TEMPORAL":
        return (
          <div className="flex-1 overflow-auto bg-[#090b0e] p-6">
            <TemporalView
              runId={runIdParam}
              runSummary={runSummary}
              onSelectSection={setActiveSection}
              onSelectFeature={(featName) => {
                const node = FEATURE_NODES.find((f) => f.name === featName) || null;
                setSelectedFeature(node);
              }}
              onRefresh={async () => {
                const summ = await getDiagnosticRunSummary(runIdParam);
                setRunSummary(summ);
              }}
            />
          </div>
        );
      case "07_BIAS":
        return (
          <div className="flex-1 overflow-auto p-4 bg-[#090b0e]">
            <BiasView
              biasResult={biasModule?.result}
              status={biasModule?.status}
              statusMessage={biasModule?.statusMessage}
            />
          </div>
        );
      case "08_ROBUSTNESS":
        return (
          <div className="flex-1 overflow-auto p-4 bg-[#090b0e]">
            <RobustnessView
              robustnessResult={robustModule?.result}
              status={robustModule?.status}
              statusMessage={robustModule?.statusMessage}
            />
          </div>
        );
      case "09_EXPERIMENTS":
        return (
          <div className="flex-1 overflow-auto p-4 bg-[#090b0e]">
            <ExperimentsView />
          </div>
        );
      case "10_REPORTS":
        return (
          <div className="flex-1 overflow-auto p-4 bg-[#090b0e]">
            <ReportsView />
          </div>
        );
      default:
        return null;
    }
  };

  return (
    <div className="h-screen w-screen flex flex-col bg-[#090b0e] text-[#f1f3f8] overflow-hidden">
      {/* Top Application Status Bar */}
      <WorkstationHeader
        summary={summary}
        onRunDiagnostic={() => setIsScanModalOpen(true)}
        onCompareBaseline={() => setActiveSection("09_EXPERIMENTS")}
        onExportDossier={() => setActiveSection("10_REPORTS")}
      />

      {/* Breadcrumb Navigation & Execution Control Strip */}
      <div className="h-7 bg-[#07090c] border-b border-[#1f2533] px-3 flex items-center justify-between text-[10px] font-mono text-[#64748b]">
        <div className="flex items-center gap-2">
          <Link href="/" className="hover:text-white transition-colors">
            HOME
          </Link>
          <span>/</span>
          <Link href="/diagnose" className="hover:text-white transition-colors">
            DIAGNOSE SETUP
          </Link>
          <span>/</span>
          <span className="text-[#3b82f6] font-bold">
            RUN {summary.runId}
          </span>
          {retryCount > 0 && (
            <span className="px-1.5 py-0.2 bg-[#3b82f6]/20 text-[#60a5fa] border border-[#3b82f6]/40 rounded text-[9px]">
              RETRY #{retryCount}
            </span>
          )}
        </div>

        <div className="flex items-center gap-3">
          {progress && (backendStatus === "RUNNING" || backendStatus === "QUEUED") && (
            <div className="flex items-center gap-2">
              <div className="w-24 h-2 bg-[#1f2533] rounded-full overflow-hidden border border-[#2d3748]">
                <div
                  className="h-full bg-[#3b82f6] transition-all duration-300"
                  style={{ width: `${progress.progressPercent}%` }}
                />
              </div>
              <span className="text-white font-bold">{progress.progressPercent}%</span>
              <span className="text-[#64748b]">
                ({progress.completedModulesCount}/{progress.selectedModulesCount})
              </span>
            </div>
          )}

          {(backendStatus === "FAILED" || backendStatus === "PARTIAL") && (
            <div className="flex items-center gap-1.5">
              <button
                onClick={handleRetryRun}
                disabled={isRetrying}
                className="px-2 py-0.5 bg-[#f59e0b]/20 hover:bg-[#f59e0b]/30 text-[#fbbf24] border border-[#f59e0b]/50 rounded cursor-pointer disabled:opacity-50 transition-colors"
                title="Retry all non-completed modules"
              >
                {isRetrying ? "RETRYING..." : "⟳ RETRY RUN"}
              </button>
              <button
                onClick={handleRetryFailedModules}
                disabled={isRetrying}
                className="px-2 py-0.5 bg-[#141822] hover:bg-[#1a202c] text-[#94a3b8] hover:text-white border border-[#222633] rounded cursor-pointer disabled:opacity-50 transition-colors"
                title="Retry only failed modules"
              >
                RETRY FAILED
              </button>
            </div>
          )}

          <span className={`font-bold flex items-center gap-1 ${
            backendStatus === "COMPLETED"
              ? "text-[#10b981]"
              : backendStatus === "PARTIAL"
              ? "text-[#f59e0b]"
              : backendStatus === "FAILED"
              ? "text-[#ef4444]"
              : "text-[#3b82f6] animate-pulse"
          }`}>
            &bull; {backendStatus}
          </span>
          <span className="text-[#333a4d]">|</span>
          <Link
            href="/diagnose"
            className="text-[#94a3b8] hover:text-white transition-colors"
          >
            [+ NEW DIAGNOSTIC RUN]
          </Link>
        </div>
      </div>

      {/* Main Workspace Stage: Sidebar (Left) + Viewport (Center) + Inspector (Right) */}
      <div className="flex-1 flex min-h-0 overflow-hidden">
        {/* Left Module Sidebar */}
        <WorkstationSidebar
          activeSection={activeSection}
          onSelectSection={setActiveSection}
        />

        {/* Central Analytical Viewport */}
        {renderCenterContent()}

        {/* Right Feature & Model Inspector */}
        <RightInspector
          summary={summary}
          selectedFeature={selectedFeature}
          onRunProbe={(f) => {
            alert(`Triggering isolated probe pipeline on feature: ${f.name}`);
          }}
          onExportFeatureEvidence={(f) => {
            alert(`Exporting JSON evidence payload for ${f.name}`);
          }}
        />
      </div>

      {/* Bottom Diagnostic Event Stream Console */}
      <EventLogStream logs={logs} />

      {/* Diagnostic Scan Execution Modal */}
      <Modal
        isOpen={isScanModalOpen}
        onClose={() => !isScanning && setIsScanModalOpen(false)}
        title="RE-RUN DIAGNOSTIC PROBE"
        subtitle={`Re-evaluates all 47 features for ${summary.modelId} on partition ${summary.datasetName}`}
      >
        <div className="space-y-3 font-mono text-xs">
          <div className="p-2.5 bg-[#0e1117] border border-[#1f2533] space-y-1.5">
            <div className="flex justify-between">
              <span className="text-[#64748b]">TARGET MODEL:</span>
              <span className="text-white font-bold">{summary.modelId}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-[#64748b]">DATASET PARTITION:</span>
              <span className="text-[#94a3b8]">{summary.datasetName}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-[#64748b]">ACTIVE ENGINES:</span>
              <span className="text-[#10b981]">7 / 7 REGISTERED</span>
            </div>
          </div>

          {isScanning ? (
            <div className="p-3 bg-[#141822] border border-[#3b82f6] text-center space-y-1.5">
              <div className="text-xs text-white font-bold">{scanStep}</div>
              <div className="text-[10px] text-[#64748b]">
                Streaming live metrics to STOMP broker...
              </div>
            </div>
          ) : (
            <div className="flex items-center justify-end gap-2 pt-2 border-t border-[#1f2533]">
              <button
                onClick={() => setIsScanModalOpen(false)}
                className="px-3 py-1 bg-[#141822] hover:bg-[#1a202c] text-[#94a3b8] hover:text-white border border-[#222633] cursor-pointer"
              >
                CANCEL
              </button>
              <button
                onClick={handleRunDiagnostic}
                className="px-3 py-1 bg-[#1d4ed8] hover:bg-[#2563eb] text-white font-semibold border border-[#3b82f6] cursor-pointer"
              >
                START AUDIT
              </button>
            </div>
          )}
        </div>
      </Modal>
    </div>
  );
}
