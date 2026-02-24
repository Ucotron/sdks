"""Edge-case tests for Pydantic type models — validates defaults, serialization, and boundary conditions."""

from pydantic import ValidationError
import pytest

from ucotron_sdk.types import (
    AddMemoryOptions,
    AugmentOptions,
    AugmentRequest,
    AugmentResponse,
    ClientConfig,
    CreateMemoryRequest,
    CreateMemoryResponse,
    EntityOptions,
    EntityResponse,
    HealthResponse,
    IngestionMetricsResponse,
    LearnOptions,
    LearnRequest,
    LearnResponse,
    ListEntitiesOptions,
    ListMemoriesOptions,
    MemoryResponse,
    MetricsResponse,
    ModelStatus,
    NeighborResponse,
    RetryConfig,
    SearchOptions,
    SearchRequest,
    SearchResponse,
    SearchResultItem,
    UpdateMemoryRequest,
)


# ---------------------------------------------------------------------------
# IngestionMetricsResponse
# ---------------------------------------------------------------------------


class TestIngestionMetricsResponse:
    def test_defaults_all_zero(self):
        m = IngestionMetricsResponse()
        assert m.chunks_processed == 0
        assert m.entities_extracted == 0
        assert m.relations_extracted == 0
        assert m.contradictions_detected == 0
        assert m.total_us == 0

    def test_from_dict_partial(self):
        m = IngestionMetricsResponse.model_validate({"chunks_processed": 5})
        assert m.chunks_processed == 5
        assert m.total_us == 0


# ---------------------------------------------------------------------------
# CreateMemoryResponse
# ---------------------------------------------------------------------------


class TestCreateMemoryResponse:
    def test_defaults(self):
        r = CreateMemoryResponse()
        assert r.chunk_node_ids == []
        assert r.entity_node_ids == []
        assert r.edges_created == 0
        assert r.metrics.total_us == 0

    def test_large_node_ids(self):
        r = CreateMemoryResponse.model_validate({
            "chunk_node_ids": [2**53 - 1],
            "entity_node_ids": [2**53 - 2],
            "edges_created": 999999,
            "metrics": {"total_us": 1000000},
        })
        assert r.chunk_node_ids[0] == 2**53 - 1
        assert r.edges_created == 999999


# ---------------------------------------------------------------------------
# SearchResultItem
# ---------------------------------------------------------------------------


class TestSearchResultItem:
    def test_score_defaults(self):
        item = SearchResultItem.model_validate({
            "id": 1,
            "content": "test",
            "node_type": "Entity",
            "score": 0.5,
        })
        assert item.vector_sim == 0.0
        assert item.graph_centrality == 0.0
        assert item.recency == 0.0

    def test_full_score_breakdown(self):
        item = SearchResultItem.model_validate({
            "id": 1,
            "content": "test",
            "node_type": "Entity",
            "score": 0.87,
            "vector_sim": 0.92,
            "graph_centrality": 0.75,
            "recency": 0.65,
        })
        assert item.vector_sim > item.graph_centrality

    def test_zero_scores(self):
        item = SearchResultItem.model_validate({
            "id": 1,
            "content": "",
            "node_type": "Fact",
            "score": 0.0,
        })
        assert item.score == 0.0


# ---------------------------------------------------------------------------
# SearchResponse
# ---------------------------------------------------------------------------


class TestSearchResponse:
    def test_empty_results(self):
        r = SearchResponse()
        assert r.results == []
        assert r.total == 0
        assert r.query == ""

    def test_multiple_results(self):
        r = SearchResponse.model_validate({
            "results": [
                {"id": 1, "content": "a", "node_type": "Entity", "score": 0.9},
                {"id": 2, "content": "b", "node_type": "Event", "score": 0.8},
            ],
            "total": 2,
            "query": "test",
        })
        assert len(r.results) == 2
        assert r.results[0].score > r.results[1].score


# ---------------------------------------------------------------------------
# EntityResponse / NeighborResponse
# ---------------------------------------------------------------------------


class TestEntityResponse:
    def test_empty_neighbors_list(self):
        e = EntityResponse.model_validate({
            "id": 1, "content": "X", "node_type": "Entity", "timestamp": 0,
            "neighbors": [],
        })
        assert e.neighbors == []

    def test_multiple_neighbors(self):
        e = EntityResponse.model_validate({
            "id": 1, "content": "Apple", "node_type": "Entity", "timestamp": 0,
            "neighbors": [
                {"node_id": 2, "content": "Tim Cook", "edge_type": "RELATES_TO", "weight": 0.9},
                {"node_id": 3, "content": "iPhone", "edge_type": "HAS_PROPERTY", "weight": 0.8},
            ],
        })
        assert len(e.neighbors) == 2
        assert e.neighbors[0].edge_type == "RELATES_TO"

    def test_rich_metadata(self):
        e = EntityResponse.model_validate({
            "id": 1, "content": "X", "node_type": "Entity", "timestamp": 0,
            "metadata": {"tags": ["a", "b"], "confidence": 0.95},
        })
        assert e.metadata["tags"] == ["a", "b"]


class TestNeighborResponse:
    def test_all_edge_types(self):
        edge_types = ["RELATES_TO", "CAUSED_BY", "CONFLICTS_WITH", "HAS_PROPERTY", "SUPERSEDES", "ACTOR", "OBJECT"]
        for et in edge_types:
            n = NeighborResponse(node_id=1, content="x", edge_type=et, weight=0.5)
            assert n.edge_type == et

    def test_weight_range(self):
        n = NeighborResponse(node_id=1, content="x", edge_type="RELATES_TO", weight=0.001)
        assert n.weight == pytest.approx(0.001)
        n2 = NeighborResponse(node_id=1, content="x", edge_type="RELATES_TO", weight=1.0)
        assert n2.weight == 1.0


# ---------------------------------------------------------------------------
# ModelStatus
# ---------------------------------------------------------------------------


class TestModelStatus:
    def test_defaults(self):
        m = ModelStatus()
        assert m.embedder_loaded is False
        assert m.embedding_model == ""
        assert m.ner_loaded is False
        assert m.relation_extractor_loaded is False

    def test_all_loaded(self):
        m = ModelStatus.model_validate({
            "embedder_loaded": True,
            "embedding_model": "all-MiniLM-L6-v2",
            "ner_loaded": True,
            "relation_extractor_loaded": True,
        })
        assert m.embedder_loaded is True
        assert m.ner_loaded is True


# ---------------------------------------------------------------------------
# HealthResponse
# ---------------------------------------------------------------------------


class TestHealthResponse:
    def test_minimal(self):
        h = HealthResponse.model_validate({"status": "ok"})
        assert h.status == "ok"
        assert h.version == ""
        assert h.models.embedder_loaded is False

    def test_writer_role(self):
        h = HealthResponse.model_validate({
            "status": "ok",
            "instance_role": "writer",
            "storage_mode": "shared",
        })
        assert h.instance_role == "writer"
        assert h.storage_mode == "shared"


# ---------------------------------------------------------------------------
# MetricsResponse
# ---------------------------------------------------------------------------


class TestMetricsResponse:
    def test_defaults(self):
        m = MetricsResponse()
        assert m.total_requests == 0
        assert m.uptime_secs == 0

    def test_high_counts(self):
        m = MetricsResponse.model_validate({
            "instance_id": "prod",
            "total_requests": 10_000_000,
            "total_ingestions": 500_000,
            "total_searches": 2_000_000,
            "uptime_secs": 86400 * 365,
        })
        assert m.total_requests == 10_000_000


# ---------------------------------------------------------------------------
# AugmentResponse
# ---------------------------------------------------------------------------


class TestAugmentResponse:
    def test_empty(self):
        r = AugmentResponse()
        assert r.memories == []
        assert r.entities == []
        assert r.context_text == ""

    def test_with_memories_and_entities(self):
        r = AugmentResponse.model_validate({
            "memories": [{"id": 1, "content": "x", "node_type": "Entity", "score": 0.5}],
            "entities": [{"id": 2, "content": "y", "node_type": "Entity", "timestamp": 0}],
            "context_text": "Relevant info.",
        })
        assert len(r.memories) == 1
        assert len(r.entities) == 1


# ---------------------------------------------------------------------------
# LearnResponse
# ---------------------------------------------------------------------------


class TestLearnResponse:
    def test_defaults(self):
        r = LearnResponse()
        assert r.memories_created == 0
        assert r.entities_found == 0
        assert r.conflicts_found == 0

    def test_with_conflicts(self):
        r = LearnResponse.model_validate({
            "memories_created": 5,
            "entities_found": 3,
            "conflicts_found": 2,
        })
        assert r.conflicts_found == 2


# ---------------------------------------------------------------------------
# Request models
# ---------------------------------------------------------------------------


class TestRequestModels:
    def test_create_memory_request(self):
        req = CreateMemoryRequest(text="Hello world")
        assert req.text == "Hello world"
        assert req.metadata == {}

    def test_create_memory_with_metadata(self):
        req = CreateMemoryRequest(text="test", metadata={"source": "agent"})
        assert req.metadata["source"] == "agent"

    def test_search_request_minimal(self):
        req = SearchRequest(query="test")
        assert req.query == "test"
        assert req.limit is None
        assert req.node_type is None
        assert req.time_range is None

    def test_search_request_full(self):
        req = SearchRequest(query="q", limit=10, node_type="Entity", time_range=(100, 200))
        assert req.time_range == (100, 200)

    def test_augment_request(self):
        req = AugmentRequest(context="Tell me about Juan")
        assert req.context == "Tell me about Juan"
        assert req.limit is None

    def test_learn_request(self):
        req = LearnRequest(output="Agent said something")
        assert req.output == "Agent said something"
        assert req.metadata is None

    def test_update_memory_request_partial(self):
        req = UpdateMemoryRequest(content="new content")
        assert req.content == "new content"
        assert req.metadata is None

    def test_update_memory_request_metadata_only(self):
        req = UpdateMemoryRequest(metadata={"key": "val"})
        assert req.content is None
        assert req.metadata == {"key": "val"}


# ---------------------------------------------------------------------------
# Options models
# ---------------------------------------------------------------------------


class TestOptionsModels:
    def test_add_memory_options(self):
        opts = AddMemoryOptions(namespace="ns", metadata={"k": "v"})
        assert opts.namespace == "ns"
        assert opts.metadata == {"k": "v"}

    def test_entity_options(self):
        opts = EntityOptions(namespace="prod")
        assert opts.namespace == "prod"

    def test_learn_options(self):
        opts = LearnOptions(namespace="dev", metadata={"source": "test"})
        assert opts.metadata == {"source": "test"}

    def test_list_memories_options(self):
        opts = ListMemoriesOptions(node_type="Fact", limit=50, offset=100, namespace="ns")
        assert opts.node_type == "Fact"
        assert opts.offset == 100

    def test_list_entities_options(self):
        opts = ListEntitiesOptions(limit=25, offset=0, namespace="default")
        assert opts.limit == 25

    def test_all_options_default_none(self):
        for cls in [AugmentOptions, LearnOptions, SearchOptions, AddMemoryOptions,
                     EntityOptions, ListMemoriesOptions, ListEntitiesOptions]:
            opts = cls()
            assert opts.namespace is None


# ---------------------------------------------------------------------------
# RetryConfig / ClientConfig
# ---------------------------------------------------------------------------


class TestRetryConfig:
    def test_defaults(self):
        rc = RetryConfig()
        assert rc.max_retries == 3
        assert rc.base_delay_ms == 100
        assert rc.max_delay_ms == 5000

    def test_custom(self):
        rc = RetryConfig(max_retries=0, base_delay_ms=50, max_delay_ms=500)
        assert rc.max_retries == 0


class TestClientConfig:
    def test_model_dump(self):
        cfg = ClientConfig(timeout_s=10.0, default_namespace="test")
        d = cfg.model_dump()
        assert d["timeout_s"] == 10.0
        assert d["default_namespace"] == "test"
        assert d["retry"]["max_retries"] == 3

    def test_model_validate_from_dict(self):
        cfg = ClientConfig.model_validate({
            "timeout_s": 5.0,
            "retry": {"max_retries": 1},
            "default_namespace": "ns",
        })
        assert cfg.timeout_s == 5.0
        assert cfg.retry.max_retries == 1
