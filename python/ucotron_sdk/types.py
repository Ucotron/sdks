"""Pydantic models for Ucotron SDK request/response types."""

from __future__ import annotations

from typing import Any, Dict, List, Optional, Tuple

from pydantic import BaseModel, Field


# ---------------------------------------------------------------------------
# Request bodies
# ---------------------------------------------------------------------------

class CreateMemoryRequest(BaseModel):
    text: str
    metadata: Dict[str, Any] = Field(default_factory=dict)


class SearchRequest(BaseModel):
    query: str
    limit: Optional[int] = None
    node_type: Optional[str] = None
    time_range: Optional[Tuple[int, int]] = None


class AugmentRequest(BaseModel):
    context: str
    limit: Optional[int] = None


class LearnRequest(BaseModel):
    output: str
    metadata: Optional[Dict[str, Any]] = None


class UpdateMemoryRequest(BaseModel):
    content: Optional[str] = None
    metadata: Optional[Dict[str, Any]] = None


# ---------------------------------------------------------------------------
# Response models
# ---------------------------------------------------------------------------

class IngestionMetricsResponse(BaseModel):
    chunks_processed: int = 0
    entities_extracted: int = 0
    relations_extracted: int = 0
    contradictions_detected: int = 0
    total_us: int = 0


class CreateMemoryResponse(BaseModel):
    chunk_node_ids: List[int] = Field(default_factory=list)
    entity_node_ids: List[int] = Field(default_factory=list)
    edges_created: int = 0
    metrics: IngestionMetricsResponse = Field(default_factory=IngestionMetricsResponse)


class MemoryResponse(BaseModel):
    id: int
    content: str
    node_type: str
    timestamp: int
    metadata: Dict[str, Any] = Field(default_factory=dict)


class SearchResultItem(BaseModel):
    id: int
    content: str
    node_type: str
    score: float
    vector_sim: float = 0.0
    graph_centrality: float = 0.0
    recency: float = 0.0


class SearchResponse(BaseModel):
    results: List[SearchResultItem] = Field(default_factory=list)
    total: int = 0
    query: str = ""


class NeighborResponse(BaseModel):
    node_id: int
    content: str
    edge_type: str
    weight: float


class EntityResponse(BaseModel):
    id: int
    content: str
    node_type: str
    timestamp: int
    metadata: Dict[str, Any] = Field(default_factory=dict)
    neighbors: Optional[List[NeighborResponse]] = None


class ModelStatus(BaseModel):
    embedder_loaded: bool = False
    embedding_model: str = ""
    ner_loaded: bool = False
    relation_extractor_loaded: bool = False
    transcriber_loaded: bool = False


class HealthResponse(BaseModel):
    status: str
    version: str = ""
    instance_id: str = ""
    instance_role: str = ""
    storage_mode: str = ""
    vector_backend: str = ""
    graph_backend: str = ""
    models: ModelStatus = Field(default_factory=ModelStatus)


class MetricsResponse(BaseModel):
    instance_id: str = ""
    total_requests: int = 0
    total_ingestions: int = 0
    total_searches: int = 0
    uptime_secs: int = 0


class AugmentResponse(BaseModel):
    memories: List[SearchResultItem] = Field(default_factory=list)
    entities: List[EntityResponse] = Field(default_factory=list)
    context_text: str = ""


class LearnResponse(BaseModel):
    memories_created: int = 0
    entities_found: int = 0
    conflicts_found: int = 0


# ---------------------------------------------------------------------------
# Client configuration
# ---------------------------------------------------------------------------

class RetryConfig(BaseModel):
    max_retries: int = 3
    base_delay_ms: int = 100
    max_delay_ms: int = 5000


class ClientConfig(BaseModel):
    timeout_s: float = 30.0
    retry: RetryConfig = Field(default_factory=RetryConfig)
    default_namespace: Optional[str] = None


# ---------------------------------------------------------------------------
# Options for SDK methods
# ---------------------------------------------------------------------------

class AugmentOptions(BaseModel):
    limit: Optional[int] = None
    namespace: Optional[str] = None


class LearnOptions(BaseModel):
    namespace: Optional[str] = None
    metadata: Optional[Dict[str, Any]] = None


class SearchOptions(BaseModel):
    limit: Optional[int] = None
    namespace: Optional[str] = None
    node_type: Optional[str] = None
    time_range: Optional[Tuple[int, int]] = None


class AddMemoryOptions(BaseModel):
    namespace: Optional[str] = None
    metadata: Optional[Dict[str, Any]] = None


class EntityOptions(BaseModel):
    namespace: Optional[str] = None


class ListMemoriesOptions(BaseModel):
    node_type: Optional[str] = None
    limit: Optional[int] = None
    offset: Optional[int] = None
    namespace: Optional[str] = None


class ListEntitiesOptions(BaseModel):
    limit: Optional[int] = None
    offset: Optional[int] = None
    namespace: Optional[str] = None
