"use client";

import React, { useState, useEffect, useCallback } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  createDiagnosticRun,
  startDiagnosticRun,
  uploadModelArtifact,
  uploadDatasetArtifact,
  listModelArtifacts,
  listDatasetArtifacts,
  deleteModelArtifact,
  deleteDatasetArtifact,
  validateDiagnosticRun,
  ModelArtifactResponse,
  DatasetArtifactResponse,
  ValidationResult,
  ColumnProfile,
} from "@/lib/api";

interface DiagnosticModuleConfig {
  id: string;
  name: string;
  code: string;
  description: string;
  enabled: boolean;
}

const MODULE_ID_MAP: Record<string, string> = {
  data_quality: "DATA_QUALITY",
  leakage: "LEAKAGE",
  drift: "DRIFT",
  performance: "PERFORMANCE",
  explainability: "EXPLAINABILITY",
  fairness: "BIAS",
  robustness: "ROBUSTNESS",
};

export default function DiagnosticSetupPage() {
  const router = useRouter();

  // Ingestion Mode: "REAL" (Artifacts) vs "BENCHMARK" (Pre-built fixture)
  const [ingestionMode, setIngestionMode] = useState<"REAL" | "BENCHMARK">("REAL");

  // Selection mode for model and datasets: "stored" vs "upload"
  const [modelInputMode, setModelInputMode] = useState<"stored" | "upload">("stored");
  const [evalInputMode, setEvalInputMode] = useState<"stored" | "upload">("stored");
  const [baselineInputMode, setBaselineInputMode] = useState<"stored" | "upload" | "none">("stored");

  // Stored artifacts lists
  const [storedModels, setStoredModels] = useState<ModelArtifactResponse[]>([]);
  const [storedDatasets, setStoredDatasets] = useState<DatasetArtifactResponse[]>([]);
  const [isLoadingArtifacts, setIsLoadingArtifacts] = useState<boolean>(false);

  // Selected Real Artifacts
  const [selectedModel, setSelectedModel] = useState<ModelArtifactResponse | null>(null);
  const [selectedEvalDataset, setSelectedEvalDataset] = useState<DatasetArtifactResponse | null>(null);
  const [selectedBaselineDataset, setSelectedBaselineDataset] = useState<DatasetArtifactResponse | null>(null);

  // Upload progress / error states
  const [isUploadingModel, setIsUploadingModel] = useState(false);
  const [modelUploadError, setModelUploadError] = useState<string | null>(null);

  const [isUploadingEval, setIsUploadingEval] = useState(false);
  const [evalUploadError, setEvalUploadError] = useState<string | null>(null);

  const [isUploadingBaseline, setIsUploadingBaseline] = useState(false);
  const [baselineUploadError, setBaselineUploadError] = useState<string | null>(null);

  // Model Metadata Configuration for Upload
  const [modelFramework, setModelFramework] = useState<string>("xgboost");
  const [taskType, setTaskType] = useState<string>("binary_classification");

  // Benchmark fallback state
  const [modelName, setModelName] = useState<string>("fraud_classifier_v17");
  const [evalDatasetName, setEvalDatasetName] = useState<string>("synthetic_eval_benchmark.csv");
  const [baselineDatasetName, setBaselineDatasetName] = useState<string>("synthetic_baseline_benchmark.csv");

  // Schema Mapping State
  const [targetColumn, setTargetColumn] = useState<string>("is_fraud");
  const [predictionColumn, setPredictionColumn] = useState<string>("pred_prob");
  const [protectedAttribute, setProtectedAttribute] = useState<string>("is_foreign_ip");

  // Schema table expanded state
  const [showFullSchema, setShowFullSchema] = useState<boolean>(false);

  // Preflight Validation State
  const [validationResult, setValidationResult] = useState<ValidationResult | null>(null);
  const [isValidating, setIsValidating] = useState<boolean>(false);

  // Modules State
  const [modules, setModules] = useState<DiagnosticModuleConfig[]>([
    {
      id: "data_quality",
      code: "01",
      name: "DATA QUALITY",
      description: "Missingness topology, constant columns, duplicate hashes, outlier fractions.",
      enabled: true,
    },
    {
      id: "leakage",
      code: "02",
      name: "DATA LEAKAGE",
      description: "Target mutual information, proxy surrogate detection, train/eval contamination.",
      enabled: true,
    },
    {
      id: "drift",
      code: "03",
      name: "DISTRIBUTION DRIFT",
      description: "Two-sample KS-test, quantile-binned PSI, Wasserstein-1 distance with FDR control.",
      enabled: true,
    },
    {
      id: "performance",
      code: "04",
      name: "PERFORMANCE & CALIBRATION",
      description: "ROC-AUC, PR-AUC, Expected Calibration Error (ECE), 10-bin reliability diagrams, Brier score.",
      enabled: true,
    },
    {
      id: "explainability",
      code: "05",
      name: "EXPLAINABILITY (SHAP)",
      description: "TreeSHAP feature attributions, permutation importance, waterfall decomposition profiles.",
      enabled: true,
    },
    {
      id: "fairness",
      code: "06",
      name: "BIAS & FAIRNESS",
      description: "Demographic parity ratios, equalized odds gaps, 80% disparate impact rule.",
      enabled: true,
    },
    {
      id: "robustness",
      code: "07",
      name: "ADVERSARIAL ROBUSTNESS",
      description: "Gaussian noise jitter stress, feature perturbation resilience, decision boundary margin flips.",
      enabled: true,
    },
  ]);

  // Execution Progress State
  const [isRunning, setIsRunning] = useState<boolean>(false);
  const [progressStep, setProgressStep] = useState<string>("");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Fetch active artifacts from API
  const refreshArtifacts = useCallback(async () => {
    setIsLoadingArtifacts(true);
    try {
      const [models, datasets] = await Promise.all([
        listModelArtifacts().catch(() => []),
        listDatasetArtifacts().catch(() => []),
      ]);
      setStoredModels(models);
      setStoredDatasets(datasets);

      // Auto-select first stored model if none selected
      if (!selectedModel && models.length > 0) {
        setSelectedModel(models[0]);
      }
      // Auto-select first stored dataset if none selected
      if (!selectedEvalDataset && datasets.length > 0) {
        setSelectedEvalDataset(datasets[0]);
      }
    } catch (e) {
      console.error("Failed to load stored artifacts:", e);
    } finally {
      setIsLoadingArtifacts(false);
    }
  }, [selectedModel, selectedEvalDataset]);

  useEffect(() => {
    refreshArtifacts();
  }, [refreshArtifacts]);

  // Handle module toggle
  const toggleModule = (id: string) => {
    setModules((prev) =>
      prev.map((m) => (m.id === id ? { ...m, enabled: !m.enabled } : m))
    );
  };

  // Run real-time preflight validation
  const runPreflightValidation = useCallback(async () => {
    setIsValidating(true);
    try {
      const selectedModules = modules
        .filter((m) => m.enabled)
        .map((m) => MODULE_ID_MAP[m.id] || m.id.toUpperCase());

      const payload =
        ingestionMode === "REAL"
          ? {
              modelArtifactId: selectedModel?.id,
              evaluationDatasetArtifactId: selectedEvalDataset?.id,
              baselineDatasetArtifactId: selectedBaselineDataset?.id,
              executionMode: "REAL" as const,
              targetColumn: targetColumn.trim(),
              predictionColumn: predictionColumn.trim() || undefined,
              protectedAttribute: protectedAttribute.trim() || undefined,
              modules: selectedModules,
            }
          : {
              model: {
                name: modelName,
                framework: modelFramework,
                taskType: taskType,
              },
              evaluationDataset: evalDatasetName,
              baselineDataset: baselineDatasetName || undefined,
              executionMode: "BENCHMARK" as const,
              targetColumn: targetColumn.trim(),
              predictionColumn: predictionColumn.trim() || undefined,
              protectedAttribute: protectedAttribute.trim() || undefined,
              modules: selectedModules,
            };

      const res = await validateDiagnosticRun(payload);
      setValidationResult(res);
    } catch (err) {
      console.debug("Preflight validation check deferred:", err);
    } finally {
      setIsValidating(false);
    }
  }, [
    ingestionMode,
    selectedModel,
    selectedEvalDataset,
    selectedBaselineDataset,
    targetColumn,
    predictionColumn,
    protectedAttribute,
    modules,
    modelName,
    modelFramework,
    taskType,
    evalDatasetName,
    baselineDatasetName,
  ]);

  useEffect(() => {
    const timer = setTimeout(() => {
      runPreflightValidation();
    }, 250);
    return () => clearTimeout(timer);
  }, [runPreflightValidation]);

  // Handle Model Upload
  const handleModelFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setIsUploadingModel(true);
    setModelUploadError(null);

    try {
      const artifact = await uploadModelArtifact(file, modelFramework, taskType);
      setSelectedModel(artifact);
      await refreshArtifacts();
    } catch (err: any) {
      console.error("Model upload error:", err);
      setModelUploadError(err.message || "Failed to upload model artifact.");
    } finally {
      setIsUploadingModel(false);
    }
  };

  // Handle Evaluation Dataset Upload
  const handleEvalFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setIsUploadingEval(true);
    setEvalUploadError(null);

    try {
      const format = file.name.endsWith(".parquet") || file.name.endsWith(".pq") ? "parquet" : "csv";
      const artifact = await uploadDatasetArtifact(file, format);
      setSelectedEvalDataset(artifact);
      await refreshArtifacts();
    } catch (err: any) {
      console.error("Dataset upload error:", err);
      setEvalUploadError(err.message || "Failed to upload evaluation dataset.");
    } finally {
      setIsUploadingEval(false);
    }
  };

  // Handle Baseline Dataset Upload
  const handleBaselineFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setIsUploadingBaseline(true);
    setBaselineUploadError(null);

    try {
      const format = file.name.endsWith(".parquet") || file.name.endsWith(".pq") ? "parquet" : "csv";
      const artifact = await uploadDatasetArtifact(file, format);
      setSelectedBaselineDataset(artifact);
      await refreshArtifacts();
    } catch (err: any) {
      console.error("Baseline dataset upload error:", err);
      setBaselineUploadError(err.message || "Failed to upload baseline dataset.");
    } finally {
      setIsUploadingBaseline(false);
    }
  };

  // Handle Soft-Delete Model
  const handleDeleteModel = async (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!confirm("Soft-delete this model artifact? (Historical runs will remain intact).")) return;
    try {
      await deleteModelArtifact(id);
      if (selectedModel?.id === id) {
        setSelectedModel(null);
      }
      await refreshArtifacts();
    } catch (err: any) {
      alert("Failed to delete model: " + err.message);
    }
  };

  // Handle Soft-Delete Dataset
  const handleDeleteDataset = async (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!confirm("Soft-delete this dataset artifact? (Historical runs will remain intact).")) return;
    try {
      await deleteDatasetArtifact(id);
      if (selectedEvalDataset?.id === id) {
        setSelectedEvalDataset(null);
      }
      if (selectedBaselineDataset?.id === id) {
        setSelectedBaselineDataset(null);
      }
      await refreshArtifacts();
    } catch (err: any) {
      alert("Failed to delete dataset: " + err.message);
    }
  };

  // Execute diagnostic run
  const handleRunDiagnostic = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);

    if (validationResult && !validationResult.valid) {
      setErrorMessage("Configuration invalid. Please resolve preflight errors before execution.");
      return;
    }

    const selectedModules = modules
      .filter((m) => m.enabled)
      .map((m) => MODULE_ID_MAP[m.id] || m.id.toUpperCase());

    if (selectedModules.length === 0) {
      setErrorMessage("At least one diagnostic module must be enabled.");
      return;
    }

    setIsRunning(true);
    setProgressStep("Registering diagnostic run with Spring Boot orchestrator...");

    try {
      const payload =
        ingestionMode === "REAL"
          ? {
              modelArtifactId: selectedModel!.id,
              evaluationDatasetArtifactId: selectedEvalDataset!.id,
              baselineDatasetArtifactId: selectedBaselineDataset ? selectedBaselineDataset.id : undefined,
              executionMode: "REAL" as const,
              targetColumn: targetColumn.trim(),
              predictionColumn: predictionColumn.trim() || undefined,
              protectedAttribute: protectedAttribute.trim() || undefined,
              modules: selectedModules,
            }
          : {
              model: {
                name: modelName,
                framework: modelFramework,
                taskType: taskType,
              },
              evaluationDataset: evalDatasetName,
              baselineDataset: baselineDatasetName || undefined,
              executionMode: "BENCHMARK" as const,
              targetColumn: targetColumn.trim(),
              predictionColumn: predictionColumn.trim() || undefined,
              protectedAttribute: protectedAttribute.trim() || undefined,
              modules: selectedModules,
            };

      const createdRun = await createDiagnosticRun(payload);
      setProgressStep(`Run [${createdRun.id}] created. Dispatching to Python ML engine...`);

      const executedRun = await startDiagnosticRun(createdRun.id);
      setProgressStep(`Run [${executedRun.id}] complete. Navigating to workstation...`);

      setTimeout(() => {
        router.push(`/diagnostic/${createdRun.id}`);
      }, 400);
    } catch (err: any) {
      console.error("Diagnostic submission error:", err);
      setIsRunning(false);
      setErrorMessage(
        err.message || "Failed to execute diagnostic run. Ensure backend and ML engine services are reachable."
      );
    }
  };

  const isConfigValid = validationResult ? validationResult.valid : false;

  return (
    <div className="min-h-screen bg-[#211D29] text-[#F4EFF6] flex flex-col font-mono text-xs selection:bg-[#F18B78] selection:text-[#211D29]">
      {/* Header */}
      <header className="h-12 bg-[#2A2432] border-b border-[#443849] px-4 flex items-center justify-between shrink-0 select-none">
        <div className="flex items-center gap-3">
          <Link
            href="/"
            className="font-sans font-bold text-sm tracking-wider text-[#F4EFF6] hover:text-[#F18B78] transition-colors"
          >
            MODEL DOCTOR
          </Link>
          <span className="text-[#5A4B62]">/</span>
          <span className="text-[#B7ACBD] font-bold">
            CONFIGURATION CONSOLE &amp; ARTIFACT INTELLIGENCE
          </span>
        </div>

        <div className="flex items-center gap-2 text-[11px]">
          <Link
            href="/"
            className="px-2.5 py-1 bg-[#332B3C] hover:bg-[#3D3448] border border-[#443849] text-[#B7ACBD] hover:text-[#F4EFF6] transition-colors rounded-sm"
          >
            &larr; BACK TO HOME
          </Link>
        </div>
      </header>

      {/* Main Content */}
      <main className="flex-1 max-w-5xl w-full mx-auto p-4 sm:p-6 space-y-6">
        {/* Mode Selector */}
        <div className="p-3 border border-[#443849] bg-[#2A2432] flex flex-col sm:flex-row sm:items-center justify-between gap-3 rounded-sm">
          <div>
            <h1 className="text-sm font-bold text-[#F4EFF6] uppercase tracking-wider">
              DIAGNOSTIC RUN CONFIGURATION
            </h1>
            <p className="text-[11px] text-[#8E8295] mt-0.5">
              Select or upload models &amp; datasets with live schema inspection and preflight validation.
            </p>
          </div>
          <div className="flex items-center gap-1 bg-[#1C1824] p-1 border border-[#443849] rounded-sm">
            <button
              type="button"
              onClick={() => setIngestionMode("REAL")}
              className={`px-3 py-1 text-[11px] font-bold transition-colors cursor-pointer rounded-xs ${
                ingestionMode === "REAL"
                  ? "bg-[#F18B78] text-[#211D29]"
                  : "text-[#B7ACBD] hover:text-[#F4EFF6]"
              }`}
            >
              REAL ARTIFACTS
            </button>
            <button
              type="button"
              onClick={() => setIngestionMode("BENCHMARK")}
              className={`px-3 py-1 text-[11px] font-bold transition-colors cursor-pointer rounded-xs ${
                ingestionMode === "BENCHMARK"
                  ? "bg-[#B7D7A8] text-[#211D29]"
                  : "text-[#B7ACBD] hover:text-[#F4EFF6]"
              }`}
            >
              BENCHMARK FIXTURES
            </button>
          </div>
        </div>

        <form onSubmit={handleRunDiagnostic} className="space-y-6">
          {/* =============================================================== */}
          {/* 1. MODEL SELECTION & INGESTION                                  */}
          {/* =============================================================== */}
          <div className="border border-[#443849] bg-[#2A2432] p-4 space-y-3 rounded-sm">
            <div className="border-b border-[#443849] pb-2 flex items-center justify-between">
              <span className="text-xs font-bold text-[#F4EFF6] uppercase">
                1. MODEL ARTIFACT
              </span>
              <div className="flex items-center gap-1">
                {ingestionMode === "REAL" && (
                  <>
                    <button
                      type="button"
                      onClick={() => setModelInputMode("stored")}
                      className={`px-2 py-0.5 text-[10px] font-bold cursor-pointer rounded-xs ${
                        modelInputMode === "stored" ? "bg-[#F18B78] text-[#211D29]" : "bg-[#332B3C] text-[#B7ACBD]"
                      }`}
                    >
                      EXISTING ({storedModels.length})
                    </button>
                    <button
                      type="button"
                      onClick={() => setModelInputMode("upload")}
                      className={`px-2 py-0.5 text-[10px] font-bold cursor-pointer rounded-xs ${
                        modelInputMode === "upload" ? "bg-[#F18B78] text-[#211D29]" : "bg-[#332B3C] text-[#B7ACBD]"
                      }`}
                    >
                      + UPLOAD NEW
                    </button>
                  </>
                )}
              </div>
            </div>

            {ingestionMode === "REAL" ? (
              modelInputMode === "stored" ? (
                <div className="space-y-2">
                  {storedModels.length === 0 ? (
                    <div className="p-3 bg-[#1C1824] border border-[#443849] text-[#8E8295] text-center rounded-sm">
                      No stored model artifacts found. Please switch to &ldquo;+ UPLOAD NEW&rdquo; to upload a model.
                    </div>
                  ) : (
                    <div className="grid grid-cols-1 gap-2 max-h-48 overflow-y-auto pr-1">
                      {storedModels.map((m) => {
                        const isSelected = selectedModel?.id === m.id;
                        return (
                          <div
                            key={m.id}
                            onClick={() => setSelectedModel(m)}
                            className={`p-2.5 border transition-colors cursor-pointer flex items-center justify-between rounded-sm ${
                              isSelected
                                ? "bg-[#332B3C] border-[#F18B78]"
                                : "bg-[#2A2432] border-[#443849] hover:border-[#5A4B62]"
                            }`}
                          >
                            <div className="space-y-1">
                              <div className="flex items-center gap-2">
                                <span className="text-[#F4EFF6] font-bold">{m.originalFilename}</span>
                                <span className="px-1.5 py-0.2 bg-[#443849] text-[#F4EFF6] text-[9px] font-bold uppercase rounded-xs">
                                  {m.framework}
                                </span>
                                <span className="px-1.5 py-0.2 bg-[#1C1824] text-[#B7ACBD] text-[9px] rounded-xs">
                                  {m.taskType}
                                </span>
                              </div>
                              <div className="flex items-center gap-3 text-[10px] text-[#8E8295]">
                                <span>ID: {m.id}</span>
                                <span>Features: {m.featureCount ?? m.featureNames?.length ?? "N/A"}</span>
                                <span>Size: {(m.fileSize / 1024).toFixed(1)} KB</span>
                                <span>SHA: {m.sha256?.substring(0, 8)}...</span>
                              </div>
                            </div>
                            <div className="flex items-center gap-2">
                              {isSelected && (
                                <span className="px-2 py-0.5 bg-[#B7D7A8] text-[#211D29] text-[9px] font-bold rounded-xs">
                                  SELECTED
                                </span>
                              )}
                              <button
                                type="button"
                                onClick={(e) => handleDeleteModel(m.id, e)}
                                title="Soft delete artifact"
                                className="px-2 py-0.5 bg-[#F18B78]/20 hover:bg-[#F18B78] text-[#F18B78] hover:text-[#211D29] border border-[#F18B78]/40 text-[9px] font-bold cursor-pointer rounded-xs transition-colors"
                              >
                                DELETE
                              </button>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>
              ) : (
                <div className="space-y-3 pt-1">
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <div>
                      <label className="block text-[11px] text-[#B7ACBD] mb-1 font-semibold">Model Framework:</label>
                      <select
                        value={modelFramework}
                        onChange={(e) => setModelFramework(e.target.value)}
                        className="w-full px-2.5 py-1.5 bg-[#332B3C] border border-[#443849] text-[#F4EFF6] focus:outline-none focus:border-[#F18B78] rounded-sm"
                      >
                        <option value="xgboost">XGBoost Native (.json, .xgb, .bin)</option>
                        <option value="sklearn">Scikit-Learn (.joblib, .pkl)</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-[11px] text-[#B7ACBD] mb-1 font-semibold">Task Type:</label>
                      <select
                        value={taskType}
                        onChange={(e) => setTaskType(e.target.value)}
                        className="w-full px-2.5 py-1.5 bg-[#332B3C] border border-[#443849] text-[#F4EFF6] focus:outline-none focus:border-[#F18B78] rounded-sm"
                      >
                        <option value="binary_classification">Binary Classification (Supported)</option>
                        <option value="multiclass_classification">Multi-Class (Unsupported)</option>
                        <option value="regression">Regression (Unsupported)</option>
                      </select>
                    </div>
                  </div>

                  <div className="flex items-center gap-3">
                    <input
                      type="file"
                      accept=".json,.xgb,.bin,.joblib,.pkl"
                      onChange={handleModelFileChange}
                      disabled={isUploadingModel}
                      className="text-xs text-[#B7ACBD] file:mr-3 file:py-1.5 file:px-3 file:border file:border-[#443849] file:bg-[#332B3C] file:text-[#F4EFF6] file:font-mono file:text-xs hover:file:bg-[#3D3448] file:rounded-sm cursor-pointer"
                    />
                    {isUploadingModel && (
                      <span className="text-[#F18B78] text-[11px] font-bold animate-pulse">
                        Ingesting &amp; Hashing Model...
                      </span>
                    )}
                  </div>

                  {modelUploadError && (
                    <div className="p-2 border border-[#F18B78] bg-[#F18B78]/10 text-[#F18B78] text-[11px] rounded-sm">
                      {modelUploadError}
                    </div>
                  )}
                </div>
              )
            ) : (
              <div className="space-y-1">
                <input
                  type="text"
                  value={modelName}
                  onChange={(e) => setModelName(e.target.value)}
                  className="w-full px-2.5 py-1.5 bg-[#332B3C] border border-[#443849] text-[#F4EFF6] focus:outline-none focus:border-[#F18B78] rounded-sm"
                />
                <span className="text-[10px] text-[#8E8295]">
                  Deterministic fraud benchmark model with 47 tabular features.
                </span>
              </div>
            )}
          </div>

          {/* =============================================================== */}
          {/* 2. EVALUATION DATASET SELECTION & INGESTION                     */}
          {/* =============================================================== */}
          <div className="border border-[#443849] bg-[#2A2432] p-4 space-y-3 rounded-sm">
            <div className="border-b border-[#443849] pb-2 flex items-center justify-between">
              <span className="text-xs font-bold text-[#F4EFF6] uppercase">
                2. EVALUATION DATASET (REQUIRED)
              </span>
              <div className="flex items-center gap-1">
                {ingestionMode === "REAL" && (
                  <>
                    <button
                      type="button"
                      onClick={() => setEvalInputMode("stored")}
                      className={`px-2 py-0.5 text-[10px] font-bold cursor-pointer rounded-xs ${
                        evalInputMode === "stored" ? "bg-[#F18B78] text-[#211D29]" : "bg-[#332B3C] text-[#B7ACBD]"
                      }`}
                    >
                      EXISTING ({storedDatasets.length})
                    </button>
                    <button
                      type="button"
                      onClick={() => setEvalInputMode("upload")}
                      className={`px-2 py-0.5 text-[10px] font-bold cursor-pointer rounded-xs ${
                        evalInputMode === "upload" ? "bg-[#F18B78] text-[#211D29]" : "bg-[#332B3C] text-[#B7ACBD]"
                      }`}
                    >
                      + UPLOAD NEW
                    </button>
                  </>
                )}
              </div>
            </div>

            {ingestionMode === "REAL" ? (
              evalInputMode === "stored" ? (
                <div className="space-y-2">
                  {storedDatasets.length === 0 ? (
                    <div className="p-3 bg-[#1C1824] border border-[#443849] text-[#8E8295] text-center rounded-sm">
                      No stored dataset artifacts found. Please switch to &ldquo;+ UPLOAD NEW&rdquo; to upload a dataset.
                    </div>
                  ) : (
                    <div className="grid grid-cols-1 gap-2 max-h-48 overflow-y-auto pr-1">
                      {storedDatasets.map((d) => {
                        const isSelected = selectedEvalDataset?.id === d.id;
                        return (
                          <div
                            key={d.id}
                            onClick={() => setSelectedEvalDataset(d)}
                            className={`p-2.5 border transition-colors cursor-pointer flex items-center justify-between rounded-sm ${
                              isSelected
                                ? "bg-[#332B3C] border-[#F18B78]"
                                : "bg-[#2A2432] border-[#443849] hover:border-[#5A4B62]"
                            }`}
                          >
                            <div className="space-y-1">
                              <div className="flex items-center gap-2">
                                <span className="text-[#F4EFF6] font-bold">{d.originalFilename}</span>
                                <span className="px-1.5 py-0.2 bg-[#443849] text-[#F4EFF6] text-[9px] font-bold uppercase rounded-xs">
                                  {d.datasetFormat}
                                </span>
                              </div>
                              <div className="flex items-center gap-3 text-[10px] text-[#8E8295]">
                                <span>ID: {d.id}</span>
                                <span>Rows: {d.rowCount?.toLocaleString()}</span>
                                <span>Cols: {d.columnCount}</span>
                                <span>Size: {(d.fileSize / 1024).toFixed(1)} KB</span>
                              </div>
                            </div>
                            <div className="flex items-center gap-2">
                              {isSelected && (
                                <span className="px-2 py-0.5 bg-[#B7D7A8] text-[#211D29] text-[9px] font-bold rounded-xs">
                                  SELECTED
                                </span>
                              )}
                              <button
                                type="button"
                                onClick={(e) => handleDeleteDataset(d.id, e)}
                                title="Soft delete artifact"
                                className="px-2 py-0.5 bg-[#F18B78]/20 hover:bg-[#F18B78] text-[#F18B78] hover:text-[#211D29] border border-[#F18B78]/40 text-[9px] font-bold cursor-pointer rounded-xs transition-colors"
                              >
                                DELETE
                              </button>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>
              ) : (
                <div className="space-y-3 pt-1">
                  <input
                    type="file"
                    accept=".csv,.parquet,.json,.txt"
                    onChange={handleEvalFileChange}
                    disabled={isUploadingEval}
                    className="text-xs text-[#B7ACBD] file:mr-3 file:py-1.5 file:px-3 file:border file:border-[#443849] file:bg-[#332B3C] file:text-[#F4EFF6] file:font-mono file:text-xs hover:file:bg-[#3D3448] file:rounded-sm cursor-pointer"
                  />
                  {isUploadingEval && (
                    <span className="text-[#F18B78] text-[11px] font-bold animate-pulse">
                      Parsing Schema &amp; Profiling Statistics...
                    </span>
                  )}
                  {evalUploadError && (
                    <div className="p-2 border border-[#F18B78] bg-[#F18B78]/10 text-[#F18B78] text-[11px] rounded-sm">
                      {evalUploadError}
                    </div>
                  )}
                </div>
              )
            ) : (
              <div className="space-y-1">
                <input
                  type="text"
                  value={evalDatasetName}
                  onChange={(e) => setEvalDatasetName(e.target.value)}
                  className="w-full px-2.5 py-1.5 bg-[#332B3C] border border-[#443849] text-[#F4EFF6] focus:outline-none focus:border-[#F18B78] rounded-sm"
                />
                <span className="text-[10px] text-[#8E8295]">
                  Synthetic fraud test dataset with 5,000 samples.
                </span>
              </div>
            )}
          </div>

          {/* =============================================================== */}
          {/* 3. BASELINE DATASET (OPTIONAL)                                  */}
          {/* =============================================================== */}
          <div className="border border-[#443849] bg-[#2A2432] p-4 space-y-3 rounded-sm">
            <div className="border-b border-[#443849] pb-2 flex items-center justify-between">
              <span className="text-xs font-bold text-[#F4EFF6] uppercase">
                3. BASELINE REFERENCE DATASET (OPTIONAL)
              </span>
              <div className="flex items-center gap-1">
                {ingestionMode === "REAL" && (
                  <>
                    <button
                      type="button"
                      onClick={() => setBaselineInputMode("stored")}
                      className={`px-2 py-0.5 text-[10px] font-bold cursor-pointer rounded-xs ${
                        baselineInputMode === "stored" ? "bg-[#F18B78] text-[#211D29]" : "bg-[#332B3C] text-[#B7ACBD]"
                      }`}
                    >
                      EXISTING ({storedDatasets.length})
                    </button>
                    <button
                      type="button"
                      onClick={() => setBaselineInputMode("upload")}
                      className={`px-2 py-0.5 text-[10px] font-bold cursor-pointer rounded-xs ${
                        baselineInputMode === "upload" ? "bg-[#F18B78] text-[#211D29]" : "bg-[#332B3C] text-[#B7ACBD]"
                      }`}
                    >
                      + UPLOAD NEW
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setBaselineInputMode("none");
                        setSelectedBaselineDataset(null);
                      }}
                      className={`px-2 py-0.5 text-[10px] font-bold cursor-pointer rounded-xs ${
                        baselineInputMode === "none" ? "bg-[#5A4B62] text-[#F4EFF6]" : "bg-[#332B3C] text-[#B7ACBD]"
                      }`}
                    >
                      NONE
                    </button>
                  </>
                )}
              </div>
            </div>

            {ingestionMode === "REAL" ? (
              baselineInputMode === "stored" ? (
                <div className="space-y-2">
                  <div className="grid grid-cols-1 gap-2 max-h-36 overflow-y-auto pr-1">
                    {storedDatasets.map((d) => {
                      const isSelected = selectedBaselineDataset?.id === d.id;
                      return (
                        <div
                          key={d.id}
                          onClick={() => setSelectedBaselineDataset(d)}
                          className={`p-2 border transition-colors cursor-pointer flex items-center justify-between rounded-sm ${
                            isSelected
                              ? "bg-[#332B3C] border-[#F18B78]"
                              : "bg-[#2A2432] border-[#443849] hover:border-[#5A4B62]"
                          }`}
                        >
                          <div className="flex items-center gap-3">
                            <span className="text-[#F4EFF6] font-bold text-[11px]">{d.originalFilename}</span>
                            <span className="text-[10px] text-[#8E8295]">
                              {d.rowCount?.toLocaleString()} rows &bull; {d.columnCount} cols
                            </span>
                          </div>
                          {isSelected && (
                            <span className="px-1.5 py-0.2 bg-[#B7D7A8] text-[#211D29] text-[9px] font-bold rounded-xs">
                              SELECTED
                            </span>
                          )}
                        </div>
                      );
                    })}
                  </div>
                </div>
              ) : baselineInputMode === "upload" ? (
                <div className="space-y-2 pt-1">
                  <input
                    type="file"
                    accept=".csv,.parquet,.json,.txt"
                    onChange={handleBaselineFileChange}
                    disabled={isUploadingBaseline}
                    className="text-xs text-[#B7ACBD] file:mr-3 file:py-1.5 file:px-3 file:border file:border-[#443849] file:bg-[#332B3C] file:text-[#F4EFF6] file:font-mono file:text-xs hover:file:bg-[#3D3448] file:rounded-sm cursor-pointer"
                  />
                  {isUploadingBaseline && (
                    <span className="text-[#F18B78] text-[11px] font-bold animate-pulse">
                      Ingesting Baseline...
                    </span>
                  )}
                  {baselineUploadError && (
                    <div className="p-2 border border-[#F18B78] bg-[#F18B78]/10 text-[#F18B78] text-[11px] rounded-sm">
                      {baselineUploadError}
                    </div>
                  )}
                </div>
              ) : (
                <div className="text-[11px] text-[#8E8295]">
                  No baseline dataset selected. Modules requiring baseline comparison (DRIFT) will be flagged.
                </div>
              )
            ) : (
              <div className="space-y-1">
                <input
                  type="text"
                  value={baselineDatasetName}
                  onChange={(e) => setBaselineDatasetName(e.target.value)}
                  className="w-full px-2.5 py-1.5 bg-[#332B3C] border border-[#443849] text-[#F4EFF6] focus:outline-none focus:border-[#F18B78] rounded-sm"
                />
              </div>
            )}
          </div>

          {/* =============================================================== */}
          {/* 4. SCHEMA INSPECTION, TARGET INTELLIGENCE & BINDING             */}
          {/* =============================================================== */}
          <div className="border border-[#443849] bg-[#2A2432] p-4 space-y-4 rounded-sm">
            <div className="border-b border-[#443849] pb-2 flex items-center justify-between">
              <span className="text-xs font-bold text-[#F4EFF6] uppercase">
                4. SCHEMA INTELLIGENCE &amp; BINDING
              </span>
              {selectedEvalDataset && (
                <span className="text-[10px] text-[#F18B78] font-bold">
                  {selectedEvalDataset.columnCount} COLUMNS PROFILED
                </span>
              )}
            </div>

            {/* Target Suggestions Banner */}
            {validationResult?.targetSuggestions && validationResult.targetSuggestions.length > 0 && (
              <div className="p-2.5 bg-[#1C1824] border border-[#443849] space-y-1.5 rounded-sm">
                <div className="text-[10px] text-[#B7ACBD] font-bold uppercase tracking-wider flex items-center gap-1.5">
                  <span className="text-[#F18B78]">✦</span> TARGET COLUMN SUGGESTIONS (CLICK TO SELECT):
                </div>
                <div className="flex flex-wrap gap-2">
                  {validationResult.targetSuggestions.map((s) => (
                    <button
                      key={s.column}
                      type="button"
                      onClick={() => setTargetColumn(s.column)}
                      className={`px-2.5 py-1 text-[11px] font-bold border transition-colors flex items-center gap-1.5 cursor-pointer rounded-xs ${
                        targetColumn === s.column
                          ? "bg-[#F18B78] border-[#F18B78] text-[#211D29]"
                          : "bg-[#332B3C] border-[#443849] text-[#B7ACBD] hover:border-[#F18B78] hover:text-[#F4EFF6]"
                      }`}
                    >
                      <span>{s.column}</span>
                      <span className="px-1 py-0.2 bg-[#211D29] text-[#B7D7A8] text-[9px] rounded-xs font-mono">
                        {Math.round(s.score * 100)}%
                      </span>
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* Schema Mapping Form Fields */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              <div>
                <label className="block text-[11px] text-[#B7ACBD] mb-1 font-bold">
                  Target / Ground Truth Column:
                </label>
                <input
                  type="text"
                  value={targetColumn}
                  onChange={(e) => setTargetColumn(e.target.value)}
                  className="w-full px-2.5 py-1.5 bg-[#332B3C] border border-[#443849] text-[#F4EFF6] focus:outline-none focus:border-[#F18B78] rounded-sm"
                  placeholder="e.g. is_fraud"
                  required
                />
              </div>

              <div>
                <label className="block text-[11px] text-[#B7ACBD] mb-1">
                  Prediction Probability Column (Optional):
                </label>
                <input
                  type="text"
                  value={predictionColumn}
                  onChange={(e) => setPredictionColumn(e.target.value)}
                  className="w-full px-2.5 py-1.5 bg-[#332B3C] border border-[#443849] text-[#F4EFF6] focus:outline-none focus:border-[#F18B78] rounded-sm"
                  placeholder="e.g. pred_prob"
                />
              </div>

              <div>
                <label className="block text-[11px] text-[#B7ACBD] mb-1">
                  Protected Attribute (Bias / Fairness):
                </label>
                <input
                  type="text"
                  value={protectedAttribute}
                  onChange={(e) => setProtectedAttribute(e.target.value)}
                  className="w-full px-2.5 py-1.5 bg-[#332B3C] border border-[#443849] text-[#F4EFF6] focus:outline-none focus:border-[#F18B78] rounded-sm"
                  placeholder="e.g. is_foreign_ip"
                />
              </div>
            </div>

            {/* Feature Compatibility Summary */}
            {validationResult?.compatibility && (
              <div className="p-2.5 bg-[#1C1824] border border-[#443849] space-y-1 text-[11px] rounded-sm">
                <div className="flex items-center justify-between font-bold">
                  <span className="text-[#B7ACBD]">MODEL ↔ DATASET FEATURE COMPATIBILITY:</span>
                  <span
                    className={
                      validationResult.compatibility.isFeatureCompatible ? "text-[#B7D7A8]" : "text-[#F18B78]"
                    }
                  >
                    {validationResult.compatibility.isFeatureCompatible
                      ? "✓ ALL REQUIRED FEATURES PRESENT"
                      : "✗ INCOMPATIBLE SCHEMA"}
                  </span>
                </div>
                {(validationResult.compatibility.missingModelFeatures?.length ?? 0) > 0 && (
                  <div className="text-[#F18B78] text-[10px]">
                    Missing Features: {validationResult.compatibility.missingModelFeatures?.join(", ")}
                  </div>
                )}
                {(validationResult.compatibility.extraDatasetFeatures?.length ?? 0) > 0 && (
                  <div className="text-[#8E8295] text-[10px]">
                    Non-model Features in Dataset: {validationResult.compatibility.extraDatasetFeatures?.join(", ")}
                  </div>
                )}
              </div>
            )}

            {/* Rich Column Profiling Table Toggle */}
            {selectedEvalDataset?.schemaSummary?.columns && (
              <div className="space-y-2">
                <button
                  type="button"
                  onClick={() => setShowFullSchema(!showFullSchema)}
                  className="text-[11px] text-[#F18B78] hover:underline font-bold cursor-pointer"
                >
                  {showFullSchema ? "▼ Hide Detailed Column Statistics" : "▶ View Detailed Column Profiles & Statistics (" + selectedEvalDataset.schemaSummary.columns.length + " cols)"}
                </button>

                {showFullSchema && (
                  <div className="overflow-x-auto border border-[#443849] bg-[#1C1824] rounded-sm">
                    <table className="w-full text-left border-collapse text-[10px]">
                      <thead>
                        <tr className="bg-[#332B3C] border-b border-[#443849] text-[#B7ACBD]">
                          <th className="p-2">COLUMN</th>
                          <th className="p-2">TYPE</th>
                          <th className="p-2">DTYPE</th>
                          <th className="p-2">NULLS</th>
                          <th className="p-2">UNIQUE</th>
                          <th className="p-2">MIN / MAX</th>
                          <th className="p-2">MEAN</th>
                          <th className="p-2">FLAGS</th>
                        </tr>
                      </thead>
                      <tbody>
                        {selectedEvalDataset.schemaSummary.columns.map((col: ColumnProfile) => (
                          <tr key={col.name} className="border-b border-[#332B3C] hover:bg-[#332B3C]">
                            <td className="p-2 font-bold text-[#F4EFF6]">{col.name}</td>
                            <td className="p-2">
                              <span className="px-1.5 py-0.2 bg-[#443849] text-[#B7ACBD] font-mono uppercase text-[9px] rounded-xs">
                                {col.classification}
                              </span>
                            </td>
                            <td className="p-2 text-[#B7ACBD]">{col.dtype}</td>
                            <td className="p-2 text-[#B7ACBD]">
                              {col.nullCount} ({col.nullPercentage}%)
                            </td>
                            <td className="p-2 text-[#B7ACBD]">
                              {col.uniqueCount} ({col.uniquePercentage}%)
                            </td>
                            <td className="p-2 text-[#B7ACBD]">
                              {col.min != null && col.max != null ? `${col.min} / ${col.max}` : "-"}
                            </td>
                            <td className="p-2 text-[#B7ACBD]">
                              {col.mean != null ? col.mean.toFixed(2) : "-"}
                            </td>
                            <td className="p-2 space-x-1">
                              {col.isConstant && (
                                <span className="px-1 bg-[#F18B78] text-[#211D29] text-[8px] font-bold rounded-xs">CONSTANT</span>
                              )}
                              {col.isIdentifierLike && (
                                <span className="px-1 bg-[#E8B66D] text-[#211D29] text-[8px] font-bold rounded-xs">ID</span>
                              )}
                              {col.isHighCardinality && (
                                <span className="px-1 bg-[#B7ACBD] text-[#211D29] text-[8px] font-bold rounded-xs">HIGH-CARD</span>
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            )}
          </div>

          {/* =============================================================== */}
          {/* 5. DIAGNOSTIC MODULE SELECTION & PREREQUISITES                   */}
          {/* =============================================================== */}
          <div className="border border-[#443849] bg-[#2A2432] p-4 space-y-3 rounded-sm">
            <div className="border-b border-[#443849] pb-2 flex items-center justify-between">
              <span className="text-xs font-bold text-[#F4EFF6] uppercase">
                5. DIAGNOSTIC ANALYTICAL MODULES
              </span>
              <span className="text-[10px] text-[#8E8295]">
                {modules.filter((m) => m.enabled).length} / {modules.length} SELECTED
              </span>
            </div>

            <div className="space-y-2">
              {modules.map((m) => {
                const modCode = MODULE_ID_MAP[m.id] || m.id.toUpperCase();
                const modVal = validationResult?.moduleValidation?.[modCode];
                const isModValid = modVal ? modVal.compatible : true;

                return (
                  <div
                    key={m.id}
                    onClick={() => toggleModule(m.id)}
                    className={`p-2.5 border transition-colors cursor-pointer flex items-start justify-between gap-3 rounded-sm ${
                      m.enabled
                        ? "bg-[#332B3C] border-[#5A4B62]"
                        : "bg-[#1C1824] border-[#443849] opacity-60"
                    }`}
                  >
                    <div className="flex items-start gap-2.5">
                      <input
                        type="checkbox"
                        checked={m.enabled}
                        onChange={() => {}}
                        className="mt-0.5 accent-[#F18B78]"
                      />
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="text-[#F18B78] text-[10px] font-bold">
                            {m.code}
                          </span>
                          <span className="text-[#F4EFF6] font-bold text-xs">
                            {m.name}
                          </span>
                        </div>
                        <div className="text-[11px] text-[#B7ACBD] mt-0.5 leading-tight">
                          {m.description}
                        </div>
                        {!isModValid && m.enabled && (
                          <div className="text-[#F18B78] text-[10px] mt-1 font-bold">
                            Prerequisite missing: {modVal?.missingPrerequisites?.join(", ")}
                          </div>
                        )}
                      </div>
                    </div>

                    <span
                      className={`px-1.5 py-0.5 text-[9px] font-bold shrink-0 rounded-xs ${
                        !isModValid && m.enabled
                          ? "bg-[#F18B78] text-[#211D29]"
                          : m.enabled
                          ? "bg-[#B7D7A8] text-[#211D29]"
                          : "bg-[#443849] text-[#8E8295]"
                      }`}
                    >
                      {!isModValid && m.enabled ? "BLOCKED" : m.enabled ? "READY" : "DISABLED"}
                    </span>
                  </div>
                );
              })}
            </div>
          </div>

          {/* =============================================================== */}
          {/* 6. PREFLIGHT CONSOLE & EXECUTION                                */}
          {/* =============================================================== */}
          <div className="border border-[#443849] bg-[#2A2432] p-4 space-y-4 rounded-sm">
            <div className="border-b border-[#443849] pb-2 flex items-center justify-between">
              <span className="text-xs font-bold text-[#F4EFF6] uppercase">
                6. PREFLIGHT VALIDATION STATUS
              </span>
              {isValidating && (
                <span className="text-[10px] text-[#F18B78] animate-pulse">
                  Validating Configuration...
                </span>
              )}
            </div>

            {/* Validation Banner */}
            {validationResult && (
              <div
                className={`p-3 border space-y-2 rounded-sm ${
                  validationResult.valid
                    ? "bg-[#B7D7A8]/10 border-[#B7D7A8]/50 text-[#B7D7A8]"
                    : "bg-[#F18B78]/10 border-[#F18B78]/50 text-[#F18B78]"
                }`}
              >
                <div className="flex items-center gap-2 font-bold text-xs">
                  <span>{validationResult.valid ? "✓ [READY FOR DIAGNOSTIC]" : "✗ [CONFIGURATION INVALID]"}</span>
                </div>

                {validationResult.errors.length > 0 && (
                  <div className="space-y-1 pt-1">
                    {validationResult.errors.map((err, i) => (
                      <div key={i} className="text-[11px] text-[#F18B78] flex items-start gap-1.5">
                        <span className="font-bold">[{err.code}]</span>
                        <span>{err.message}</span>
                      </div>
                    ))}
                  </div>
                )}

                {validationResult.warnings.length > 0 && (
                  <div className="space-y-1 pt-1 border-t border-[#443849]">
                    {validationResult.warnings.map((w, i) => (
                      <div key={i} className="text-[10px] text-[#E8B66D] flex items-start gap-1.5">
                        <span className="font-bold">[{w.code}]</span>
                        <span>{w.message}</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {errorMessage && (
              <div className="p-3 border border-[#F18B78] bg-[#F18B78]/10 text-[#F18B78] text-xs font-mono space-y-1 rounded-sm">
                <div className="font-bold flex items-center gap-1.5 text-[#F18B78]">
                  <span>[ERROR]</span> DIAGNOSTIC SUBMISSION REJECTED
                </div>
                <div className="text-[11px] text-[#F4EFF6]">{errorMessage}</div>
              </div>
            )}

            <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 pt-2">
              <div className="text-[11px] text-[#8E8295]">
                {ingestionMode === "REAL"
                  ? `Model: ${selectedModel?.originalFilename || "None"} • Eval: ${selectedEvalDataset?.originalFilename || "None"}`
                  : `Benchmark Fixture • ${modelName}`}
              </div>

              <button
                type="submit"
                disabled={isRunning || !isConfigValid}
                className={`w-full sm:w-auto px-6 py-2.5 font-mono text-xs font-bold tracking-wider border transition-colors cursor-pointer shrink-0 rounded-sm ${
                  isConfigValid && !isRunning
                    ? "bg-[#F18B78] hover:bg-[#F39C8B] text-[#211D29] border-[#F18B78] shadow-sm"
                    : "bg-[#332B3C] text-[#8E8295] border-[#443849] cursor-not-allowed opacity-50"
                }`}
              >
                {isRunning ? "PROCESSING AUDIT..." : "RUN DIAGNOSTIC →"}
              </button>
            </div>
          </div>
        </form>

        {/* Execution Overlay Modal */}
        {isRunning && (
          <div className="fixed inset-0 z-50 bg-black/80 flex items-center justify-center p-4">
            <div className="max-w-md w-full bg-[#2A2432] border border-[#443849] p-5 space-y-4 rounded-sm shadow-2xl">
              <div className="text-xs font-bold text-[#F4EFF6] uppercase border-b border-[#443849] pb-2">
                RUNNING MODEL DOCTOR DIAGNOSTIC PIPELINE
              </div>

              <div className="space-y-2">
                <div className="p-3 bg-[#332B3C] border border-[#F18B78] text-center rounded-sm">
                  <div className="text-xs text-[#F4EFF6] font-bold">{progressStep}</div>
                  <div className="text-[10px] text-[#8E8295] mt-1">
                    Spring Boot &harr; Python ML Engine &harr; PostgreSQL
                  </div>
                </div>

                <div className="text-[10px] text-[#8E8295] text-center">
                  Mode: {ingestionMode} &bull; Model: {selectedModel?.originalFilename || modelName}
                </div>
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
