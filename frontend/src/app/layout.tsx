import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "MODEL DOCTOR // Diagnostic Lab",
  description:
    "Scientific & Forensic AI/ML Model Diagnostics Workstation. Deep root-cause inspection of data quality, data leakage, distribution drift, bias, and calibration fragility.",
  keywords: [
    "Machine Learning Diagnostics",
    "Model Doctor",
    "Data Leakage Detection",
    "Covariate Drift",
    "Model Fairness",
    "SHAP Explainability",
    "ML Forensics",
  ],
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html
      lang="en"
      className={`${geistSans.variable} ${geistMono.variable} dark h-full antialiased`}
    >
      <body className="min-h-full flex flex-col bg-[#05070b] text-[#f0f4fc]">
        {children}
      </body>
    </html>
  );
}
