# MODEL DOCTOR // DIAGNOSTIC LAB

> **Production-Quality AI/ML Model Diagnostics & Forensic Platform**

Model Doctor analyzes trained machine learning models and datasets to identify root-cause failure modes: **data leakage, covariate & concept drift, corrupted data quality, class imbalance bias, calibration collapse, adversarial fragility, and spurious correlations**.

---

## System Architecture

```
/model-doctor
  ├── /frontend      # Next.js 15, React 19, TypeScript, Tailwind CSS, Three.js, R3F, Framer Motion
  ├── /backend       # Java 21, Spring Boot 3.3, REST APIs, STOMP WebSockets, Actuator
  ├── /ml-engine     # Python 3.10+, FastAPI, NumPy, Pandas, Scikit-learn, XGBoost, SHAP, Evidently
  ├── /infra         # Docker Compose, PostgreSQL 16, MinIO S3-compatible Object Storage
  └── /docs          # Architectural specs, diagnostic algorithms, API contracts
```

---

## Tech Stack Overview

| Tier | Technologies |
| :--- | :--- |
| **Frontend Workstation** | Next.js (App Router), TypeScript (Strict), React, Tailwind CSS, Three.js, React Three Fiber, Framer Motion, Lucide Icons, D3.js |
| **Backend Core** | Java 21, Spring Boot 3.3.x, Spring Web, Spring WebSocket / STOMP, Spring Validation, OpenAPI 3.0 (Swagger UI) |
| **ML Engine** | Python 3.10+, FastAPI, Pydantic v2, NumPy, Pandas, SciPy, Scikit-learn, XGBoost, SHAP, Evidently |
| **Storage & Infra** | PostgreSQL 16, MinIO Object Storage (S3-compatible), Docker Compose |

---

## Quickstart

### 1. Start Infrastructure (Postgres + MinIO)
```bash
cd infra
cp .env.example .env
docker compose up -d postgres minio minio-init
```
* MinIO Console: `http://localhost:9001` (User: `minioadmin`, Pass: `minioadmin_secure_key`)
* PostgreSQL: `localhost:5432` (DB: `modeldoctor_db`)

### 2. Start ML Engine (FastAPI)
```bash
cd ml-engine
python -m pip install -r requirements.txt
python -m uvicorn app.main:app --reload --port 8000
```
* ML Engine Health: `http://localhost:8000/health`
* Interactive API Docs: `http://localhost:8000/docs`

### 3. Start Backend Orchestrator (Spring Boot)
```bash
cd backend
mvn spring-boot:run
```
* Backend Health: `http://localhost:8080/api/health`
* Swagger UI: `http://localhost:8080/swagger-ui.html`

### 4. Start Forensic Frontend Workstation (Next.js)
```bash
cd frontend
npm install
npm run dev
```
* Workstation UI: `http://localhost:3000`

---

## Diagnostic Navigation

- **01 OVERVIEW**: Model Health HUD, Composite Reliability Score, 3D Forensic Tensor Node, Telemetry Feed.
- **02 DATA**: Input integrity, structural missingness topology, cardinality audit.
- **03 FORENSICS**: Target leakage probe, temporal lookahead audit, contamination detection.
- **04 DRIFT**: KS-test, Population Stability Index (PSI), Wasserstein distance.
- **05 EXPLAIN**: TreeSHAP feature importance, waterfall attribution, interaction matrices.
- **06 BIAS**: Demographic parity, equalized odds, disparate impact ratios.
- **07 ROBUSTNESS**: Adversarial boundary stress, Gaussian noise sensitivity.
- **08 EXPERIMENTS**: Multi-run comparative diagnostics and regression tracking.
- **09 REPORTS**: Automated certified compliance and executive forensic summaries.

---

## License

Apache-2.0. Built for production machine learning diagnostics.
