# MODEL DOCTOR // DIAGNOSTIC ENGINES SPECIFICATION

This document establishes the technical algorithms, mathematical methods, and failure mode definitions for Model Doctor diagnostic engines.

---

## 1. Engine: Data Quality & Integrity (`data_quality`) — Phase 2B.1 REAL

### 1.1 Mathematical Formulation & Metrics

1. **Dataset Overview Statistics**:
   - $\text{Row Count} = N$
   - $\text{Column Count} = M$
   - $\text{Duplicate Row Count} = \sum \mathbb{I}(\text{row}_i = \text{row}_j \text{ for some } j < i)$
   - $\text{Duplicate Row Rate} = \frac{\text{Duplicate Row Count}}{N}$
   - $\text{Total Missing Cells} = \sum_{j=1}^M \sum_{i=1}^N \mathbb{I}(x_{ij} \text{ is NaN})$
   - $\text{Global Missing Rate} = \frac{\text{Total Missing Cells}}{N \cdot M}$

2. **Per-Column Missingness Topology**:
   - $\text{Missing Count}(c) = \sum_{i=1}^N \mathbb{I}(x_{ic} \text{ is NaN})$
   - $\text{Missing Rate}(c) = \frac{\text{Missing Count}(c)}{N}$

3. **Constant & Near-Constant Analysis**:
   - $\text{Unique Count}(c) = |\{ x_{ic} \mid x_{ic} \text{ is not NaN} \}|$
   - $\text{Constant}: \text{Unique Count}(c) \le 1$
   - $\text{Near-Constant}: \frac{\max_{v} \text{Count}(x_{ic} = v)}{N - \text{Missing Count}(c)} \ge \theta_{\text{near-constant}}$ ($\theta = 0.98$)

4. **Numeric Outlier Detection (Interquartile Range Method)**:
   - $Q_1 = \text{Percentile}_{25}(X_c^{\text{finite}})$
   - $Q_3 = \text{Percentile}_{75}(X_c^{\text{finite}})$
   - $\text{IQR} = Q_3 - Q_1$
   - $\text{Lower Bound} = Q_1 - 1.5 \times \text{IQR}$
   - $\text{Upper Bound} = Q_3 + 1.5 \times \text{IQR}$
   - $\text{Outlier Count}(c) = \sum \mathbb{I}(x_{ic} < \text{Lower Bound} \lor x_{ic} > \text{Upper Bound})$
   - $\text{Outlier Rate}(c) = \frac{\text{Outlier Count}(c)}{|X_c^{\text{finite}}|}$

5. **Non-Finite Inspection**:
   - $\text{Non-Finite Count}(c) = \sum \mathbb{I}(x_{ic} \in \{+\infty, -\infty\})$ (strictly separated from missing $\text{NaN}$).

6. **Inferred Semantic Type Classification**:
   - `BOOLEAN`: Boolean dtype or 2 distinct canonical boolean tokens.
   - `DATETIME`: Datetime dtype or parseable timestamp format on $\ge 85\%$ sample.
   - `NUMERIC`: Continuous/discrete integer or floating point.
   - `TEXT`: String/object with mean character length $> 60$ and unique ratio $> 0.4$.
   - `CATEGORICAL`: String/object with low cardinality.
   - `UNKNOWN`: Unidentifiable or all-null series.

### 1.2 Configurable Thresholds & Severity Rules

| Metric | Warning Threshold | Critical Threshold | Severity Action |
| :--- | :--- | :--- | :--- |
| `missing_rate` (per column) | $\ge 0.02$ (2.0%) | $\ge 0.20$ (20.0%) | `WARNING` / `CRITICAL` finding |
| `duplicate_row_rate` | $\ge 0.01$ (1.0%) | $\ge 0.05$ (5.0%) | `WARNING` / `CRITICAL` finding |
| `outlier_rate` (IQR) | $\ge 0.05$ (5.0%) | $\ge 0.15$ (15.0%) | `MEDIUM` / `HIGH` finding |
| `constant_column` | Unique $\le 1$ | - | `WARNING` (zero variance parameter bloat) |
| `non_finite_count` | $> 0$ | $> 0$ | `CRITICAL` (gradient arithmetic overflow risk) |

---

## 2. Engine: Data Leakage Detection (`leakage`) — Phase 2B.1 REAL

### 2.1 Mathematical Formulation & Metrics

1. **Direct Association Measures**:
   - **Numeric vs Binary / Continuous Target (Pearson Correlation)**:
     $$r_{X, Y} = \frac{\sum (x_i - \bar{x})(y_i - \bar{y})}{\sqrt{\sum (x_i - \bar{x})^2 \sum (y_i - \bar{y})^2}}$$
   - **Categorical vs Categorical Target (Cramer's V)**:
     $$V = \sqrt{\frac{\chi^2}{N \cdot (\min(r, c) - 1)}}$$
   - **Categorical vs Continuous Target (Correlation Ratio $\eta$)**:
     $$\eta = \sqrt{\frac{\text{SS}_{\text{between}}}{\text{SS}_{\text{total}}}}$$

2. **Non-Linear Mutual Information Estimation**:
   - Using $k$-nearest neighbors entropy estimation ($\text{Scikit-Learn } k=3$, deterministic `random_state=42`):
     $$I(X; Y) = \iint p(x, y) \log \frac{p(x, y)}{p(x)p(y)} \, dx \, dy$$
   - Discrete feature masks applied to prevent continuous approximation error on categorical indices.

3. **Leakage Risk Index & Candidate Ranking**:
   - $\text{Suspicion Score} = 0.60 \times I(X; Y) + 0.40 \times |\text{Association}|$
   - Ranked descending by suspicion score.

4. **Multi-Tier Risk Classification**:
   - `CRITICAL`: $I(X; Y) \ge 0.70$ OR $|\text{Association}| \ge 0.85$
   - `HIGH`: $I(X; Y) \ge 0.50$ OR $|\text{Association}| \ge 0.65$
   - `MEDIUM`: $I(X; Y) \ge 0.30$ OR $|\text{Association}| \ge 0.40$
   - `LOW`: $I(X; Y) < 0.30$ AND $|\text{Association}| < 0.40$

5. **Train / Evaluation Sample Contamination**:
   - Computes MD5 128-bit hashes across shared feature tuples between baseline and evaluation sets.
   - $\text{Overlap Rate} = \frac{|\text{Hash}_{\text{eval}} \cap \text{Hash}_{\text{baseline}}|}{|\text{Hash}_{\text{eval}}|}$
   - $\ge 0.001$ triggers `WARNING`; $\ge 0.02$ triggers `CRITICAL`.

### 2.2 Important Forensic Disclaimer

> **Forensic Diagnostic Evidence Note**:
> Potential target leakage flags represent statistical association and information-theoretic dependency. They are diagnostic evidence requiring domain engineer investigation to verify inference-time temporal availability, not mathematical proof of causality.

---

## 3. Deferred Engines (Phases 2B.2+) — `NOT_IMPLEMENTED`

### 3.1 Distribution Drift Engine (`drift`)
* Continuous Feature KS-test, Wasserstein-1 distance, Population Stability Index (PSI).

### 3.2 Model Performance & Calibration (`performance`)
* Expected Calibration Error (ECE), Brier score, reliability diagrams.

### 3.3 Bias & Fairness (`fairness` / `bias`)
* Demographic Parity ratio, Equalized Odds gap, 80% rule disparate impact.

### 3.4 Adversarial & Noise Robustness (`robustness`)
* Gaussian perturbation stress tests, feature dropout margin collapse.

### 3.5 Explainability & Feature Attribution (`explainability`)
* TreeSHAP global attribution and second-order interaction matrices.
