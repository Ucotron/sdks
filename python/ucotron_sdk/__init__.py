"""Ucotron SDK — Python client for the Ucotron cognitive memory server."""

from .client import Ucotron, UcotronSync
from .errors import (
    UcotronConnectionError,
    UcotronError,
    UcotronRetriesExhaustedError,
    UcotronServerError,
)
from .types import (
    AddMemoryOptions,
    AugmentOptions,
    AugmentResponse,
    ClientConfig,
    CreateMemoryResponse,
    EntityOptions,
    EntityResponse,
    HealthResponse,
    LearnOptions,
    LearnResponse,
    ListEntitiesOptions,
    ListMemoriesOptions,
    MemoryResponse,
    MetricsResponse,
    RetryConfig,
    SearchOptions,
    SearchResponse,
    SearchResultItem,
)

__all__ = [
    "Ucotron",
    "UcotronSync",
    "UcotronError",
    "UcotronServerError",
    "UcotronConnectionError",
    "UcotronRetriesExhaustedError",
    "ClientConfig",
    "RetryConfig",
    "AugmentOptions",
    "AugmentResponse",
    "LearnOptions",
    "LearnResponse",
    "SearchOptions",
    "SearchResponse",
    "SearchResultItem",
    "AddMemoryOptions",
    "CreateMemoryResponse",
    "EntityOptions",
    "EntityResponse",
    "ListMemoriesOptions",
    "ListEntitiesOptions",
    "MemoryResponse",
    "HealthResponse",
    "MetricsResponse",
]

__version__ = "0.1.0"
