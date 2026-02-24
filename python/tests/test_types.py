"""Tests for Pydantic type models."""

from ucotron_sdk.types import (
    AugmentOptions,
    AugmentResponse,
    ClientConfig,
    CreateMemoryResponse,
    EntityResponse,
    HealthResponse,
    IngestionMetricsResponse,
    LearnResponse,
    MemoryResponse,
    MetricsResponse,
    ModelStatus,
    NeighborResponse,
    RetryConfig,
    SearchOptions,
    SearchResponse,
    SearchResultItem,
)


def test_client_config_defaults():
    cfg = ClientConfig()
    assert cfg.timeout_s == 30.0
    assert cfg.retry.max_retries == 3
    assert cfg.retry.base_delay_ms == 100
    assert cfg.retry.max_delay_ms == 5000
    assert cfg.default_namespace is None


def test_client_config_custom():
    cfg = ClientConfig(
        timeout_s=10.0,
        retry=RetryConfig(max_retries=5, base_delay_ms=200, max_delay_ms=10000),
        default_namespace="test-ns",
    )
    assert cfg.timeout_s == 10.0
    assert cfg.retry.max_retries == 5
    assert cfg.default_namespace == "test-ns"


def test_memory_response_from_dict():
    data = {
        "id": 42,
        "content": "Hello world",
        "node_type": "Entity",
        "timestamp": 1700000000,
        "metadata": {"key": "value"},
    }
    resp = MemoryResponse.model_validate(data)
    assert resp.id == 42
    assert resp.content == "Hello world"
    assert resp.node_type == "Entity"
    assert resp.metadata["key"] == "value"


def test_search_response_from_dict():
    data = {
        "results": [
            {
                "id": 1,
                "content": "test",
                "node_type": "Entity",
                "score": 0.95,
                "vector_sim": 0.9,
                "graph_centrality": 0.5,
                "recency": 0.8,
            }
        ],
        "total": 1,
        "query": "test query",
    }
    resp = SearchResponse.model_validate(data)
    assert resp.total == 1
    assert len(resp.results) == 1
    assert resp.results[0].score == 0.95
    assert resp.results[0].vector_sim == 0.9


def test_entity_response_with_neighbors():
    data = {
        "id": 10,
        "content": "Juan",
        "node_type": "Entity",
        "timestamp": 1700000000,
        "metadata": {},
        "neighbors": [
            {
                "node_id": 11,
                "content": "Madrid",
                "edge_type": "RELATES_TO",
                "weight": 0.8,
            }
        ],
    }
    resp = EntityResponse.model_validate(data)
    assert resp.id == 10
    assert resp.neighbors is not None
    assert len(resp.neighbors) == 1
    assert resp.neighbors[0].node_id == 11


def test_entity_response_without_neighbors():
    data = {
        "id": 10,
        "content": "Juan",
        "node_type": "Entity",
        "timestamp": 1700000000,
    }
    resp = EntityResponse.model_validate(data)
    assert resp.neighbors is None


def test_health_response_from_dict():
    data = {
        "status": "healthy",
        "version": "0.1.0",
        "instance_id": "test-123",
        "instance_role": "standalone",
        "storage_mode": "embedded",
        "vector_backend": "helix",
        "graph_backend": "helix",
        "models": {
            "embedder_loaded": True,
            "embedding_model": "all-MiniLM-L6-v2",
            "ner_loaded": False,
            "relation_extractor_loaded": False,
        },
    }
    resp = HealthResponse.model_validate(data)
    assert resp.status == "healthy"
    assert resp.models.embedder_loaded is True
    assert resp.models.embedding_model == "all-MiniLM-L6-v2"


def test_metrics_response_from_dict():
    data = {
        "instance_id": "test-123",
        "total_requests": 100,
        "total_ingestions": 10,
        "total_searches": 50,
        "uptime_secs": 3600,
    }
    resp = MetricsResponse.model_validate(data)
    assert resp.total_requests == 100
    assert resp.uptime_secs == 3600


def test_create_memory_response():
    data = {
        "chunk_node_ids": [1, 2, 3],
        "entity_node_ids": [10, 11],
        "edges_created": 5,
        "metrics": {
            "chunks_processed": 3,
            "entities_extracted": 2,
            "relations_extracted": 1,
            "contradictions_detected": 0,
            "total_us": 5000,
        },
    }
    resp = CreateMemoryResponse.model_validate(data)
    assert len(resp.chunk_node_ids) == 3
    assert resp.edges_created == 5
    assert resp.metrics.chunks_processed == 3


def test_augment_response():
    data = {
        "memories": [
            {
                "id": 1,
                "content": "test",
                "node_type": "Entity",
                "score": 0.9,
            }
        ],
        "entities": [
            {
                "id": 10,
                "content": "Juan",
                "node_type": "Entity",
                "timestamp": 1700000000,
            }
        ],
        "context_text": "Relevant context here.",
    }
    resp = AugmentResponse.model_validate(data)
    assert len(resp.memories) == 1
    assert len(resp.entities) == 1
    assert resp.context_text == "Relevant context here."


def test_learn_response():
    data = {
        "memories_created": 3,
        "entities_found": 2,
        "conflicts_found": 1,
    }
    resp = LearnResponse.model_validate(data)
    assert resp.memories_created == 3
    assert resp.conflicts_found == 1


def test_search_options_defaults():
    opts = SearchOptions()
    assert opts.limit is None
    assert opts.namespace is None
    assert opts.node_type is None
    assert opts.time_range is None


def test_search_options_custom():
    opts = SearchOptions(
        limit=20,
        namespace="my-ns",
        node_type="Entity",
        time_range=(1000, 2000),
    )
    assert opts.limit == 20
    assert opts.time_range == (1000, 2000)


def test_augment_options_defaults():
    opts = AugmentOptions()
    assert opts.limit is None
    assert opts.namespace is None
