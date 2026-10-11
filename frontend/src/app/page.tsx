"use client";

import React from "react";
import Link from "next/link";

export default function HomePage() {
  return (
    <div className="min-h-screen bg-[#211D29] text-[#F4EFF6] flex flex-col font-mono selection:bg-[#F18B78] selection:text-[#211D29]">
      {/* Top Navigation Bar */}
      <header className="h-14 bg-[#2A2432] border-b border-[#443849] px-4 lg:px-8 flex items-center justify-between sticky top-0 z-30 select-none">
        <div className="flex items-center gap-6">
          <Link href="/" className="flex items-center gap-2.5">
            <span className="font-sans font-bold text-base tracking-wider text-[#F4EFF6]">
              MODEL DOCTOR
            </span>
            <span className="text-[10px] font-mono text-[#B7ACBD] border border-[#443849] px-1.5 py-0.5 bg-[#332B3C] rounded-sm">
              v1.0
            </span>
          </Link>

          <nav className="hidden md:flex items-center gap-5 text-xs text-[#B7ACBD]">
            <a href="#what-it-does" className="hover:text-[#F4EFF6] transition-colors">
              OVERVIEW
            </a>
            <a href="#diagnostic-areas" className="hover:text-[#F4EFF6] transition-colors">
              DIAGNOSTIC ENGINES
            </a>
            <a href="#how-it-works" className="hover:text-[#F4EFF6] transition-colors">
              HOW IT WORKS
            </a>
            <Link href="/diagnostic/run_0042" className="text-[#F18B78] hover:text-[#F39C8B] font-semibold transition-colors">
              SAMPLE RUN #0042
            </Link>
          </nav>
        </div>

        <div className="flex items-center gap-3">
          <Link
            href="/diagnose"
            className="px-3.5 py-1.5 bg-[#F18B78] hover:bg-[#F39C8B] text-[#211D29] text-xs font-bold tracking-wide border border-[#F18B78] transition-colors rounded-sm shadow-sm"
          >
            START DIAGNOSTIC &rarr;
          </Link>
        </div>
      </header>

      {/* Main Content Sections */}
      <main className="flex-1 max-w-5xl w-full mx-auto px-4 sm:px-6 py-12 space-y-16">
        {/* =============================================================== */}
        {/* HERO SECTION                                                    */}
        {/* =============================================================== */}
        <section className="space-y-6 pt-4 pb-8 border-b border-[#443849]">
          <div className="space-y-3">
            <div className="text-[11px] text-[#F18B78] font-bold tracking-wider uppercase">
              MACHINE LEARNING DIAGNOSTICS &amp; FORENSIC OBSERVABILITY
            </div>
            <h1 className="font-sans text-3xl sm:text-4xl lg:text-5xl font-extrabold text-[#F4EFF6] tracking-tight leading-tight">
              Find out whether your machine-learning model can actually be trusted.
            </h1>
            <p className="text-sm sm:text-base text-[#B7ACBD] max-w-3xl leading-relaxed font-sans">
              Model Doctor examines datasets, prediction distributions, and model artifacts to identify hidden failure modes—such as <strong>data leakage</strong>, <strong>distribution drift</strong>, <strong>calibration collapse</strong>, and <strong>spurious correlations</strong>—that standard test accuracy metrics fail to reveal.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-3 pt-2">
            <Link
              href="/diagnose"
              className="px-5 py-2.5 bg-[#F18B78] hover:bg-[#F39C8B] text-[#211D29] text-xs font-bold tracking-wide border border-[#F18B78] transition-colors rounded-sm shadow-sm"
            >
              START DIAGNOSTIC &rarr;
            </Link>
            <a
              href="#how-it-works"
              className="px-4 py-2.5 bg-[#332B3C] hover:bg-[#3D3448] text-[#B7ACBD] hover:text-[#F4EFF6] text-xs font-semibold border border-[#443849] transition-colors rounded-sm"
            >
              HOW IT WORKS &darr;
            </a>
            <Link
              href="/diagnostic/run_0042"
              className="px-4 py-2.5 bg-[#332B3C] hover:bg-[#3D3448] text-[#F18B78] hover:text-[#F39C8B] text-xs font-semibold border border-[#443849] transition-colors rounded-sm"
            >
              VIEW SAMPLE RUN #0042
            </Link>
          </div>

          {/* Technical Terminal Spec Snippet */}
          <div className="p-3 bg-[#1C1824] border border-[#443849] text-xs space-y-1 text-[#8E8295] rounded-sm">
            <div className="text-[#B7ACBD]">
              $ model-doctor audit --model fraud_classifier_v17 --dataset production_2026_09 --baseline baseline_golden
            </div>
            <div className="text-[#F18B78]">
              [CRITICAL] Leakage detected in feature &apos;transaction_id_hash&apos; (Mutual Info: 0.941 &gt; 0.850)
            </div>
            <div className="text-[#E8B66D]">
              [WARNING]  Covariate drift detected in &apos;user_velocity_6h&apos; (PSI = 0.312 &gt;= 0.250)
            </div>
            <div className="text-[#B7D7A8]">
              [COMPLETE] Audit finished. Health index computed at 73 / 100.
            </div>
          </div>
        </section>

        {/* =============================================================== */}
        {/* 1. WHAT MODEL DOCTOR DOES                                       */}
        {/* =============================================================== */}
        <section id="what-it-does" className="space-y-4">
          <div className="text-xs text-[#F18B78] font-bold uppercase tracking-wider">
            01 // CORE CONCEPT
          </div>
          <h2 className="font-sans text-xl sm:text-2xl font-bold text-[#F4EFF6]">
            Beyond Macro Accuracy
          </h2>
          <p className="text-xs sm:text-sm text-[#B7ACBD] leading-relaxed max-w-3xl">
            A model boasting 98% validation accuracy in offline testing can silently collapse in production if it relied on leaked target proxies, memorized dataset artifacts, or encounters shifting population quantiles.
          </p>
          <p className="text-xs sm:text-sm text-[#B7ACBD] leading-relaxed max-w-3xl">
            Model Doctor audits the underlying statistical evidence across 7 diagnostic dimensions, providing actionable causal explanations and remediation recommendations before failure impacts users.
          </p>
        </section>

        {/* =============================================================== */}
        {/* 2. DIAGNOSTIC AREAS                                             */}
        {/* =============================================================== */}
        <section id="diagnostic-areas" className="space-y-4">
          <div className="text-xs text-[#F18B78] font-bold uppercase tracking-wider">
            02 // DIAGNOSTIC DOMAINS
          </div>
          <h2 className="font-sans text-xl sm:text-2xl font-bold text-[#F4EFF6]">
            7-Engine Forensic Suite
          </h2>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-3 pt-2">
            <div className="p-3.5 bg-[#2A2432] border border-[#443849] hover:border-[#5A4B62] space-y-1.5 rounded-sm transition-colors">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-[#F4EFF6]">DATA QUALITY</span>
                <span className="text-[10px] text-[#B7D7A8] font-bold">ENGINE 01</span>
              </div>
              <p className="text-[11px] text-[#B7ACBD] leading-relaxed">
                Audits structural missingness topology, zero-variance columns, type mismatch anomalies, and multivariate Mahalanobis outliers.
              </p>
            </div>

            <div className="p-3.5 bg-[#2A2432] border border-[#443849] hover:border-[#5A4B62] space-y-1.5 rounded-sm transition-colors">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-[#F4EFF6]">DATA LEAKAGE</span>
                <span className="text-[10px] text-[#F18B78] font-bold">ENGINE 02</span>
              </div>
              <p className="text-[11px] text-[#B7ACBD] leading-relaxed">
                Calculates target mutual information spikes, detects unstripped surrogate IDs, temporal lookahead bias, and train/eval row hash overlaps.
              </p>
            </div>

            <div className="p-3.5 bg-[#2A2432] border border-[#443849] hover:border-[#5A4B62] space-y-1.5 rounded-sm transition-colors">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-[#F4EFF6]">DISTRIBUTION DRIFT</span>
                <span className="text-[10px] text-[#E8B66D] font-bold">ENGINE 03</span>
              </div>
              <p className="text-[11px] text-[#B7ACBD] leading-relaxed">
                Applies two-sample Kolmogorov-Smirnov continuous tests, Population Stability Index (PSI) deciles, and Wasserstein-1 transport distances.
              </p>
            </div>

            <div className="p-3.5 bg-[#2A2432] border border-[#443849] hover:border-[#5A4B62] space-y-1.5 rounded-sm transition-colors">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-[#F4EFF6]">PERFORMANCE &amp; CALIBRATION</span>
                <span className="text-[10px] text-[#8E8295]">ENGINE 04</span>
              </div>
              <p className="text-[11px] text-[#B7ACBD] leading-relaxed">
                Decomposes Expected Calibration Error (ECE), Brier scores, reliability diagrams, and minority-class precision-recall under heavy imbalance.
              </p>
            </div>

            <div className="p-3.5 bg-[#2A2432] border border-[#443849] hover:border-[#5A4B62] space-y-1.5 rounded-sm transition-colors">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-[#F4EFF6]">EXPLAINABILITY (SHAP)</span>
                <span className="text-[10px] text-[#8E8295]">ENGINE 05</span>
              </div>
              <p className="text-[11px] text-[#B7ACBD] leading-relaxed">
                Computes TreeSHAP and KernelSHAP global feature impact rankings, second-order interaction indices, and waterfall force plots.
              </p>
            </div>

            <div className="p-3.5 bg-[#2A2432] border border-[#443849] hover:border-[#5A4B62] space-y-1.5 rounded-sm transition-colors">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-[#F4EFF6]">BIAS &amp; FAIRNESS</span>
                <span className="text-[10px] text-[#8E8295]">ENGINE 06</span>
              </div>
              <p className="text-[11px] text-[#B7ACBD] leading-relaxed">
                Measures demographic parity differences, equalized odds false-positive gaps, and validates compliance with the 80% disparate impact rule.
              </p>
            </div>

            <div className="p-3.5 bg-[#2A2432] border border-[#443849] hover:border-[#5A4B62] space-y-1.5 md:col-span-2 rounded-sm transition-colors">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-[#F4EFF6]">ADVERSARIAL ROBUSTNESS</span>
                <span className="text-[10px] text-[#8E8295]">ENGINE 07</span>
              </div>
              <p className="text-[11px] text-[#B7ACBD] leading-relaxed">
                Stress-tests prediction stability under Gaussian feature jitter, missingness dropout masking, and class decision boundary proximity.
              </p>
            </div>
          </div>
        </section>

        {/* =============================================================== */}
        {/* 3. HOW IT WORKS                                                 */}
        {/* =============================================================== */}
        <section id="how-it-works" className="space-y-4">
          <div className="text-xs text-[#F18B78] font-bold uppercase tracking-wider">
            03 // EXECUTION LIFECYCLE
          </div>
          <h2 className="font-sans text-xl sm:text-2xl font-bold text-[#F4EFF6]">
            Analytical Workflow
          </h2>

          <div className="grid grid-cols-1 sm:grid-cols-4 gap-2 pt-2 text-xs">
            <div className="p-3 bg-[#2A2432] border border-[#443849] space-y-1 rounded-sm">
              <div className="text-[10px] text-[#F18B78] font-bold">STEP 1</div>
              <div className="font-bold text-[#F4EFF6] uppercase">INPUT</div>
              <p className="text-[11px] text-[#B7ACBD]">
                Supply evaluation dataset (CSV/Parquet), baseline reference, and model weights.
              </p>
            </div>

            <div className="p-3 bg-[#2A2432] border border-[#443849] space-y-1 rounded-sm">
              <div className="text-[10px] text-[#F18B78] font-bold">STEP 2</div>
              <div className="font-bold text-[#F4EFF6] uppercase">ANALYSIS</div>
              <p className="text-[11px] text-[#B7ACBD]">
                FastAPI ML Engine executes 7 statistical diagnostic probes concurrently.
              </p>
            </div>

            <div className="p-3 bg-[#2A2432] border border-[#443849] space-y-1 rounded-sm">
              <div className="text-[10px] text-[#F18B78] font-bold">STEP 3</div>
              <div className="font-bold text-[#F4EFF6] uppercase">INVESTIGATION</div>
              <p className="text-[11px] text-[#B7ACBD]">
                Inspect topology networks, distribution shifts, and root-cause explanations.
              </p>
            </div>

            <div className="p-3 bg-[#2A2432] border border-[#443849] space-y-1 rounded-sm">
              <div className="text-[10px] text-[#F18B78] font-bold">STEP 4</div>
              <div className="font-bold text-[#F4EFF6] uppercase">DIAGNOSIS</div>
              <p className="text-[11px] text-[#B7ACBD]">
                Generate composite Health Index (0-100) and export certified evidence dossiers.
              </p>
            </div>
          </div>
        </section>

        {/* =============================================================== */}
        {/* 4. FINAL CTA                                                    */}
        {/* =============================================================== */}
        <section className="p-6 bg-[#2A2432] border border-[#443849] space-y-4 text-center rounded-sm">
          <h3 className="font-sans text-xl font-bold text-[#F4EFF6]">
            Ready to audit your model?
          </h3>
          <p className="text-xs text-[#B7ACBD] max-w-xl mx-auto">
            Configure a new evaluation partition or inspect existing diagnostic runs in the forensic workstation.
          </p>
          <div className="flex flex-wrap items-center justify-center gap-3 pt-1">
            <Link
              href="/diagnose"
              className="px-5 py-2 bg-[#F18B78] hover:bg-[#F39C8B] text-[#211D29] text-xs font-bold tracking-wide border border-[#F18B78] transition-colors rounded-sm shadow-sm"
            >
              START A DIAGNOSTIC &rarr;
            </Link>
            <Link
              href="/diagnostic/run_0042"
              className="px-4 py-2 bg-[#332B3C] hover:bg-[#3D3448] text-[#B7ACBD] hover:text-[#F4EFF6] text-xs font-semibold border border-[#443849] transition-colors rounded-sm"
            >
              EXPLORE SAMPLE WORKSTATION (#0042)
            </Link>
          </div>
        </section>
      </main>

      {/* Footer */}
      <footer className="border-t border-[#443849] bg-[#2A2432] px-4 lg:px-8 py-4 text-[11px] text-[#8E8295] flex flex-col sm:flex-row items-center justify-between gap-2 select-none">
        <div>
          MODEL DOCTOR // SCIENTIFIC ML DIAGNOSTIC INSTRUMENT
        </div>
        <div className="flex items-center gap-4 text-[10px]">
          <span>JAVA 21 / SPRING BOOT</span>
          <span>&bull;</span>
          <span>PYTHON 3.14 / FASTAPI</span>
          <span>&bull;</span>
          <span>NEXT.JS 15</span>
        </div>
      </footer>
    </div>
  );
}
