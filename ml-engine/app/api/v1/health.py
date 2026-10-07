import sys
from fastapi import APIRouter
from app.core.config import settings
from app.diagnostics import AVAILABLE_ENGINES
from app.schemas.health import HealthResponse

router = APIRouter(tags=["Health & Status"])


@router.get(
    "/health",
    response_model=HealthResponse,
    summary="ML Engine Health Check",
    description="Returns current operational status, Python version, and registered diagnostic engines."
)
async def get_health() -> HealthResponse:
    return HealthResponse(
        status="healthy",
        service="model-doctor-ml-engine",
        version=settings.APP_VERSION,
        python_version=f"{sys.version_info.major}.{sys.version_info.minor}.{sys.version_info.micro}",
        engines_registered=list(AVAILABLE_ENGINES.keys()),
        storage_connected=False,
    )
