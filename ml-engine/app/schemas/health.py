from datetime import datetime, timezone
from typing import Dict, List, Optional
from pydantic import BaseModel, Field


class HealthResponse(BaseModel):
    status: str = Field(default="healthy", description="Current service health status")
    service: str = Field(default="model-doctor-ml-engine", description="Service identifier")
    version: str = Field(default="0.1.0", description="Semantic service version")
    timestamp: datetime = Field(
        default_factory=lambda: datetime.now(timezone.utc),
        description="UTC ISO-8601 timestamp"
    )
    python_version: str = Field(..., description="Active Python runtime version")
    engines_registered: List[str] = Field(
        default_factory=list,
        description="List of registered diagnostic modules"
    )
    storage_connected: bool = Field(default=False, description="Object storage status")
    system_metrics: Optional[Dict[str, float]] = Field(
        default=None,
        description="CPU and Memory utilization"
    )
