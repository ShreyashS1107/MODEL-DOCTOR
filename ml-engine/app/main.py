import sys
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.core.config import settings
from app.core.logging import logger
from app.api.router import api_router
from app.diagnostics import AVAILABLE_ENGINES
from app.schemas.health import HealthResponse


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Initializing Model Doctor ML Engine...")
    logger.info(f"Registered {len(AVAILABLE_ENGINES)} diagnostic engines: {list(AVAILABLE_ENGINES.keys())}")
    yield
    logger.info("Shutting down Model Doctor ML Engine.")


app = FastAPI(
    title=settings.APP_NAME,
    version=settings.APP_VERSION,
    description="Scientific & Forensic ML Model Diagnostic Engine API",
    lifespan=lifespan,
    docs_url="/docs",
    redoc_url="/redoc",
)

# CORS middleware configuration
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get(
    "/health",
    response_model=HealthResponse,
    tags=["Health & Status"],
    summary="Root Health Check",
    description="Returns ML Engine status, Python runtime, and registered diagnostic modules."
)
async def health_check() -> HealthResponse:
    return HealthResponse(
        status="healthy",
        service="model-doctor-ml-engine",
        version=settings.APP_VERSION,
        python_version=f"{sys.version_info.major}.{sys.version_info.minor}.{sys.version_info.micro}",
        engines_registered=list(AVAILABLE_ENGINES.keys()),
        storage_connected=False,
    )


# Mount API version 1 router
app.include_router(api_router, prefix=settings.API_V1_PREFIX)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host=settings.HOST,
        port=settings.PORT,
        reload=settings.DEBUG,
    )
