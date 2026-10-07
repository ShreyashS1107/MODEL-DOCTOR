# Model Doctor // Infrastructure

This directory contains the Docker Compose configurations and initialization scripts for local and cloud container environments.

## Services

| Service | Port | Description |
| :--- | :--- | :--- |
| **PostgreSQL** | `5432` | Relational storage for model registry, diagnostic run history, and evaluation records. |
| **MinIO API** | `9000` | S3-compatible object storage for serialized model weights, test datasets, and forensic reports. |
| **MinIO Console** | `9001` | Web management interface for browsing buckets (`http://localhost:9001`). |
| **minio-init** | - | One-shot bucket provisioner (`model-doctor-artifacts`, `model-doctor-datasets`). |

## Quickstart

1. Create your environment configuration:
   ```bash
   cp .env.example .env
   ```

2. Spin up PostgreSQL and MinIO:
   ```bash
   docker compose up -d postgres minio minio-init
   ```

3. Spin up all services including Backend and ML Engine in containers:
   ```bash
   docker compose --profile full-stack up -d --build
   ```

4. View logs:
   ```bash
   docker compose logs -f
   ```
