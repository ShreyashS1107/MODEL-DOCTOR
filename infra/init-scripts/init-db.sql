-- Model Doctor Initial Database Schema
-- Defines core schema for model registry, datasets, and diagnostic runs

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Model Registry Table
CREATE TABLE IF NOT EXISTS model_artifacts (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    version VARCHAR(64) NOT NULL,
    framework VARCHAR(64) NOT NULL,
    task_type VARCHAR(64) NOT NULL,
    storage_uri VARCHAR(1024) NOT NULL,
    size_bytes BIGINT,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. Datasets Table
CREATE TABLE IF NOT EXISTS dataset_records (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    dataset_type VARCHAR(64) NOT NULL, -- baseline, evaluation, production_stream
    storage_uri VARCHAR(1024) NOT NULL,
    row_count BIGINT,
    column_count INT,
    schema_summary JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Diagnostic Runs Table
CREATE TABLE IF NOT EXISTS diagnostic_runs (
    id VARCHAR(64) PRIMARY KEY,
    model_id VARCHAR(64) REFERENCES model_artifacts(id) ON DELETE SET NULL,
    dataset_id VARCHAR(64) REFERENCES dataset_records(id) ON DELETE SET NULL,
    status VARCHAR(64) NOT NULL,
    overall_health_score NUMERIC(5, 2),
    execution_duration_ms BIGINT,
    executed_engines TEXT[],
    detected_issues_count INT DEFAULT 0,
    report_json JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE
);

-- Index for fast lookup on recent runs
CREATE INDEX IF NOT EXISTS idx_diagnostic_runs_created_at ON diagnostic_runs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_diagnostic_runs_model_id ON diagnostic_runs(model_id);
