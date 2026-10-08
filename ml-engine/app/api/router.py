from fastapi import APIRouter
from app.api.v1 import health, diagnostics, experiments, temporal

api_router = APIRouter()
api_router.include_router(health.router)
api_router.include_router(diagnostics.router)
api_router.include_router(experiments.router)
api_router.include_router(temporal.router)

