# MODEL DOCTOR // SYSTEM ARCHITECTURE

## 1. High-Level Concept

**Model Doctor** is a forensic ML diagnostics platform built to inspect, stress-test, and certify trained machine-learning models before and during production deployment. 

Unlike conventional observability dashboards that only plot macro accuracy or latency graphs, Model Doctor performs deep causal and statistical forensic analysis to answer **WHY** a model may be unreliable, brittle, biased, or over-optimistic.

```
+-------------------------------------------------------------------------------+
|                      MODEL DOCTOR // FORENSIC WORKSTATION                     |
|                               (Next.js + R3F)                                 |
+-----------------------+-------------------------------+-----------------------+
                        | REST / SSE / WS (8080)        |
                        v                               v
+-------------------------------------------------------------------------------+
|                      BACKEND ORCHESTRATOR (Java 21 / Spring Boot)             |
|  - Model Registry     - Diagnostic Pipeline Manager  - WebSockets & Telemetry |
+-----------------------+-------------------------------+-----------------------+
                        | gRPC / REST (8000)            | S3 / JDBC
                        v                               v
+------------------------------------+   +--------------------------------------+
| ML ENGINE (Python FastAPI)         |   | PERSISTENCE & OBJECT STORE           |
| - Data Quality Audit Engine        |   | - PostgreSQL (Metadata & Run History)|
| - Data Leakage Probe               |   | - MinIO (Model Weights & Datasets)   |
| - Distribution Drift Analyzer      |   +--------------------------------------+
| - Calibration & Performance Engine |
| - Bias & Fairness Auditor          |
| - Adversarial Robustness Probe     |
| - SHAP Attribution Explainer       |
+------------------------------------+
```

---

## 2. Monorepo Structure

```
/model-doctor
  ├── /frontend      # Next.js 15 App Router, React Three Fiber, Tailwind CSS
  ├── /backend       # Java 21 Spring Boot REST & WebSocket Service
  ├── /ml-engine     # Python 3.10+ FastAPI Forensic Diagnostics Engine
  ├── /infra         # Docker Compose, PostgreSQL, MinIO configs
  └── /docs          # Architectural and Diagnostic specifications
```

---

## 3. Forensic Diagnostic Dimensions

| Dimension | Key Failure Modes Inspected | Primary Statistical / ML Heuristics |
| :--- | :--- | :--- |
| **01 Data Quality** | Corrupted inputs, unexpected null patterns, zero-variance columns, structural type drift. | Missingness topology, Outlier score (Isolation Forest / IQR), Cardinality audit. |
| **02 Data Leakage** | Target leakage, temporal lookahead bias, train/test contamination, proxy IDs. | Mutual Information thresholding, feature-target correlation spikes, duplicate hash check. |
| **03 Drift** | Covariate shift, prior probability shift, concept drift across time partitions. | Kolmogorov-Smirnov (KS) test, Population Stability Index (PSI), Wasserstein distance. |
| **04 Calibration & Fit** | Overconfidence, underconfidence, severe train/val generalization gap. | Brier Score, Expected Calibration Error (ECE), Reliability Diagrams, Log-Loss gap. |
| **05 Fairness & Bias** | Disparate impact, demographic bias, unequal false-positive rates across protected groups. | Demographic Parity Ratio, Equalized Odds gap, 80% Rule (Disparate Impact). |
| **06 Robustness** | Vulnerability to input noise, missing feature collapse, adversarial perturbation. | Gaussian jitter sensitivity, HopSkipJump boundary proximity, feature drop test. |
| **07 Explainability** | Spurious feature reliance, non-causal shortcuts, hidden interaction loops. | TreeSHAP, KernelSHAP, SHAP interaction values, waterfall attribution. |

---

## 4. Communication & Execution Lifecycle

1. **Ingestion**: The user uploads or links a model artifact (e.g., XGBoost, LightGBM, PyTorch ONNX) and an evaluation dataset.
2. **Orchestration**: The Java backend registers the artifact, validates metadata against Postgres/MinIO, and generates a structured `DiagnosticRun` plan.
3. **Dispatch**: The backend invokes the FastAPI ML Engine with dataset references and diagnostic parameters.
4. **Telemetry Streaming**: During analysis, the ML Engine streams step-by-step forensic probes back to the backend, which broadcasts them via WebSockets (`/topic/telemetry`) to the Next.js HUD.
5. **Report Compilation**: The composite diagnostic report is assembled, flagged issues are indexed, and the overall Model Health Score is computed.
