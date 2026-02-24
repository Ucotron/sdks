"""Tests for Ucotron Python SDK client — async and sync variants."""

from __future__ import annotations

import json
import time
from typing import Any, Dict, List

import httpx
import pytest

from ucotron_sdk import (
    Ucotron,
    UcotronSync,
    ClientConfig,
    RetryConfig,
    UcotronConnectionError,
    UcotronRetriesExhaustedError,
    UcotronServerError,
)
from ucotron_sdk.types import (
    AugmentOptions,
    LearnOptions,
    ListEntitiesOptions,
    ListMemoriesOptions,
    SearchOptions,
)


# ---------------------------------------------------------------------------
# Test data fixtures
# ---------------------------------------------------------------------------

HEALTH_DATA = {
    "status": "healthy",
    "version": "0.1.0",
    "instance_id": "test-1",
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

METRICS_DATA = {
    "instance_id": "test-1",
    "total_requests": 42,
    "total_ingestions": 5,
    "total_searches": 20,
    "uptime_secs": 1000,
}

MEMORY_DATA = {
    "id": 1,
    "content": "Juan lives in Berlin",
    "node_type": "Entity",
    "timestamp": 1700000000,
    "metadata": {},
}

SEARCH_DATA = {
    "results": [
        {
            "id": 1,
            "content": "Juan lives in Berlin",
            "node_type": "Entity",
            "score": 0.95,
            "vector_sim": 0.9,
            "graph_centrality": 0.4,
            "recency": 0.8,
        }
    ],
    "total": 1,
    "query": "Juan",
}

CREATE_MEMORY_DATA = {
    "chunk_node_ids": [100, 101],
    "entity_node_ids": [200],
    "edges_created": 3,
    "metrics": {
        "chunks_processed": 2,
        "entities_extracted": 1,
        "relations_extracted": 1,
        "contradictions_detected": 0,
        "total_us": 5000,
    },
}

ENTITY_DATA = {
    "id": 10,
    "content": "Juan",
    "node_type": "Entity",
    "timestamp": 1700000000,
    "metadata": {},
    "neighbors": [
        {"node_id": 11, "content": "Berlin", "edge_type": "RELATES_TO", "weight": 0.8}
    ],
}

AUGMENT_DATA = {
    "memories": [
        {"id": 1, "content": "test", "node_type": "Entity", "score": 0.9}
    ],
    "entities": [
        {"id": 10, "content": "Juan", "node_type": "Entity", "timestamp": 1700000000}
    ],
    "context_text": "Context about Juan.",
}

LEARN_DATA = {
    "memories_created": 2,
    "entities_found": 1,
    "conflicts_found": 0,
}

BASE_URL = "http://testserver:8420"


# ---------------------------------------------------------------------------
# Helper: build client with mock transport
# ---------------------------------------------------------------------------


def _json_resp(data: Any, status: int = 200) -> httpx.Response:
    return httpx.Response(status_code=status, json=data)


def _text_resp(text: str, status: int = 500) -> httpx.Response:
    return httpx.Response(status_code=status, text=text)


def _async_client(handler, config: ClientConfig | None = None) -> Ucotron:
    """Create an async Ucotron with a mock transport injected."""
    c = Ucotron(BASE_URL, config=config)
    c._client = httpx.AsyncClient(
        transport=httpx.MockTransport(handler),
        base_url=BASE_URL,
        timeout=httpx.Timeout(30.0),
    )
    return c


def _sync_client(handler, config: ClientConfig | None = None) -> UcotronSync:
    """Create a sync UcotronSync with a mock transport injected."""
    c = UcotronSync(BASE_URL, config=config)
    c._client = httpx.Client(
        transport=httpx.MockTransport(handler),
        base_url=BASE_URL,
        timeout=httpx.Timeout(30.0),
    )
    return c


# ===========================================================================
# Async client — endpoint tests
# ===========================================================================


class TestAsyncHealth:
    @pytest.mark.asyncio
    async def test_health(self):
        def handler(req: httpx.Request) -> httpx.Response:
            assert req.url.path == "/api/v1/health"
            return _json_resp(HEALTH_DATA)

        client = _async_client(handler)
        resp = await client.health()
        assert resp.status == "healthy"
        assert resp.models.embedder_loaded is True
        await client.close()


class TestAsyncMetrics:
    @pytest.mark.asyncio
    async def test_metrics(self):
        def handler(req: httpx.Request) -> httpx.Response:
            return _json_resp(METRICS_DATA)

        client = _async_client(handler)
        resp = await client.metrics()
        assert resp.total_requests == 42
        assert resp.uptime_secs == 1000
        await client.close()


class TestAsyncSearch:
    @pytest.mark.asyncio
    async def test_search_basic(self):
        def handler(req: httpx.Request) -> httpx.Response:
            body = json.loads(req.content)
            assert body["query"] == "Juan"
            return _json_resp(SEARCH_DATA)

        client = _async_client(handler)
        resp = await client.search("Juan")
        assert resp.total == 1
        assert resp.results[0].score == 0.95
        await client.close()

    @pytest.mark.asyncio
    async def test_search_with_options(self):
        def handler(req: httpx.Request) -> httpx.Response:
            body = json.loads(req.content)
            assert body["limit"] == 5
            assert body["node_type"] == "Entity"
            assert body["time_range"] == [1000, 2000]
            return _json_resp(SEARCH_DATA)

        client = _async_client(handler)
        opts = SearchOptions(limit=5, node_type="Entity", time_range=(1000, 2000))
        resp = await client.search("location", opts=opts)
        assert resp.total == 1
        await client.close()


class TestAsyncAddMemory:
    @pytest.mark.asyncio
    async def test_add_memory(self):
        def handler(req: httpx.Request) -> httpx.Response:
            body = json.loads(req.content)
            assert body["text"] == "Juan lives in Berlin"
            return _json_resp(CREATE_MEMORY_DATA)

        client = _async_client(handler)
        resp = await client.add_memory("Juan lives in Berlin")
        assert len(resp.chunk_node_ids) == 2
        assert resp.edges_created == 3
        await client.close()


class TestAsyncAugment:
    @pytest.mark.asyncio
    async def test_augment(self):
        def handler(req: httpx.Request) -> httpx.Response:
            body = json.loads(req.content)
            assert body["context"] == "Tell me about Juan"
            return _json_resp(AUGMENT_DATA)

        client = _async_client(handler)
        resp = await client.augment("Tell me about Juan")
        assert resp.context_text == "Context about Juan."
        assert len(resp.memories) == 1
        await client.close()

    @pytest.mark.asyncio
    async def test_augment_with_limit(self):
        def handler(req: httpx.Request) -> httpx.Response:
            body = json.loads(req.content)
            assert body["limit"] == 3
            return _json_resp(AUGMENT_DATA)

        client = _async_client(handler)
        opts = AugmentOptions(limit=3)
        resp = await client.augment("test", opts=opts)
        assert len(resp.entities) == 1
        await client.close()


class TestAsyncLearn:
    @pytest.mark.asyncio
    async def test_learn(self):
        def handler(req: httpx.Request) -> httpx.Response:
            body = json.loads(req.content)
            assert body["output"] == "Juan works at SAP"
            return _json_resp(LEARN_DATA)

        client = _async_client(handler)
        resp = await client.learn("Juan works at SAP")
        assert resp.memories_created == 2
        await client.close()

    @pytest.mark.asyncio
    async def test_learn_with_metadata(self):
        def handler(req: httpx.Request) -> httpx.Response:
            body = json.loads(req.content)
            assert body["metadata"] == {"source": "test"}
            return _json_resp(LEARN_DATA)

        client = _async_client(handler)
        opts = LearnOptions(metadata={"source": "test"})
        resp = await client.learn("output text", opts=opts)
        assert resp.memories_created == 2
        await client.close()


class TestAsyncEntities:
    @pytest.mark.asyncio
    async def test_get_entity(self):
        def handler(req: httpx.Request) -> httpx.Response:
            assert req.url.path == "/api/v1/entities/10"
            return _json_resp(ENTITY_DATA)

        client = _async_client(handler)
        resp = await client.get_entity(10)
        assert resp.id == 10
        assert resp.neighbors is not None
        assert len(resp.neighbors) == 1
        await client.close()

    @pytest.mark.asyncio
    async def test_list_entities(self):
        def handler(req: httpx.Request) -> httpx.Response:
            return _json_resp([ENTITY_DATA])

        client = _async_client(handler)
        resp = await client.list_entities()
        assert len(resp) == 1
        assert resp[0].id == 10
        await client.close()

    @pytest.mark.asyncio
    async def test_list_entities_with_pagination(self):
        def handler(req: httpx.Request) -> httpx.Response:
            assert "limit=5" in str(req.url)
            assert "offset=10" in str(req.url)
            return _json_resp([])

        client = _async_client(handler)
        opts = ListEntitiesOptions(limit=5, offset=10)
        resp = await client.list_entities(opts=opts)
        assert len(resp) == 0
        await client.close()


class TestAsyncMemories:
    @pytest.mark.asyncio
    async def test_get_memory(self):
        def handler(req: httpx.Request) -> httpx.Response:
            assert req.url.path == "/api/v1/memories/1"
            return _json_resp(MEMORY_DATA)

        client = _async_client(handler)
        resp = await client.get_memory(1)
        assert resp.id == 1
        assert resp.content == "Juan lives in Berlin"
        await client.close()

    @pytest.mark.asyncio
    async def test_list_memories(self):
        def handler(req: httpx.Request) -> httpx.Response:
            return _json_resp([MEMORY_DATA])

        client = _async_client(handler)
        resp = await client.list_memories()
        assert len(resp) == 1
        await client.close()

    @pytest.mark.asyncio
    async def test_list_memories_with_filter(self):
        def handler(req: httpx.Request) -> httpx.Response:
            assert "node_type=Entity" in str(req.url)
            return _json_resp([MEMORY_DATA])

        client = _async_client(handler)
        opts = ListMemoriesOptions(node_type="Entity", limit=10)
        resp = await client.list_memories(opts=opts)
        assert len(resp) == 1
        await client.close()

    @pytest.mark.asyncio
    async def test_update_memory(self):
        updated = {**MEMORY_DATA, "content": "Juan lives in Madrid"}

        def handler(req: httpx.Request) -> httpx.Response:
            assert req.method == "PUT"
            body = json.loads(req.content)
            assert body["content"] == "Juan lives in Madrid"
            return _json_resp(updated)

        client = _async_client(handler)
        resp = await client.update_memory(1, content="Juan lives in Madrid")
        assert resp.content == "Juan lives in Madrid"
        await client.close()

    @pytest.mark.asyncio
    async def test_delete_memory(self):
        def handler(req: httpx.Request) -> httpx.Response:
            assert req.method == "DELETE"
            assert req.url.path == "/api/v1/memories/1"
            return httpx.Response(status_code=200)

        client = _async_client(handler)
        await client.delete_memory(1)
        await client.close()


# ===========================================================================
# Namespace header tests
# ===========================================================================


class TestNamespaceHeader:
    @pytest.mark.asyncio
    async def test_default_namespace(self):
        def handler(req: httpx.Request) -> httpx.Response:
            assert req.headers["X-Ucotron-Namespace"] == "default"
            return _json_resp(HEALTH_DATA)

        client = _async_client(handler)
        await client.health()
        await client.close()

    @pytest.mark.asyncio
    async def test_config_namespace(self):
        def handler(req: httpx.Request) -> httpx.Response:
            assert req.headers["X-Ucotron-Namespace"] == "my-project"
            return _json_resp(SEARCH_DATA)

        config = ClientConfig(default_namespace="my-project")
        client = _async_client(handler, config=config)
        await client.search("test")
        await client.close()

    @pytest.mark.asyncio
    async def test_per_request_namespace_overrides_config(self):
        def handler(req: httpx.Request) -> httpx.Response:
            assert req.headers["X-Ucotron-Namespace"] == "override-ns"
            return _json_resp(SEARCH_DATA)

        config = ClientConfig(default_namespace="my-project")
        client = _async_client(handler, config=config)
        opts = SearchOptions(namespace="override-ns")
        await client.search("test", opts=opts)
        await client.close()


# ===========================================================================
# Error handling tests
# ===========================================================================


class TestAsyncErrorHandling:
    @pytest.mark.asyncio
    async def test_4xx_raises_server_error_immediately(self):
        call_count = 0

        def handler(req: httpx.Request) -> httpx.Response:
            nonlocal call_count
            call_count += 1
            return _text_resp("Not Found", status=404)

        config = ClientConfig(retry=RetryConfig(max_retries=3))
        client = _async_client(handler, config=config)

        with pytest.raises(UcotronServerError) as exc_info:
            await client.health()
        assert exc_info.value.status == 404
        assert call_count == 1  # no retries for 4xx
        await client.close()

    @pytest.mark.asyncio
    async def test_5xx_retries_then_exhausts(self):
        call_count = 0

        def handler(req: httpx.Request) -> httpx.Response:
            nonlocal call_count
            call_count += 1
            return _text_resp("Internal Server Error", status=500)

        config = ClientConfig(
            retry=RetryConfig(max_retries=2, base_delay_ms=1, max_delay_ms=1)
        )
        client = _async_client(handler, config=config)

        with pytest.raises(UcotronRetriesExhaustedError) as exc_info:
            await client.health()
        assert exc_info.value.attempts == 3
        assert call_count == 3
        await client.close()

    @pytest.mark.asyncio
    async def test_5xx_then_success(self):
        call_count = 0

        def handler(req: httpx.Request) -> httpx.Response:
            nonlocal call_count
            call_count += 1
            if call_count < 3:
                return _text_resp("Server Error", status=500)
            return _json_resp(HEALTH_DATA)

        config = ClientConfig(
            retry=RetryConfig(max_retries=3, base_delay_ms=1, max_delay_ms=1)
        )
        client = _async_client(handler, config=config)

        resp = await client.health()
        assert resp.status == "healthy"
        assert call_count == 3
        await client.close()

    @pytest.mark.asyncio
    async def test_connection_error_retries(self):
        call_count = 0

        def handler(req: httpx.Request) -> httpx.Response:
            nonlocal call_count
            call_count += 1
            if call_count < 2:
                raise httpx.ConnectError("Connection refused")
            return _json_resp(HEALTH_DATA)

        config = ClientConfig(
            retry=RetryConfig(max_retries=3, base_delay_ms=1, max_delay_ms=1)
        )
        client = _async_client(handler, config=config)

        resp = await client.health()
        assert resp.status == "healthy"
        assert call_count == 2
        await client.close()


# ===========================================================================
# Retry delay calculation
# ===========================================================================


class TestRetryDelay:
    def test_exponential_backoff(self):
        config = ClientConfig(
            retry=RetryConfig(base_delay_ms=100, max_delay_ms=5000)
        )
        client = Ucotron(BASE_URL, config=config)
        assert client._retry_delay(0) == pytest.approx(0.1)
        assert client._retry_delay(1) == pytest.approx(0.2)
        assert client._retry_delay(2) == pytest.approx(0.4)
        assert client._retry_delay(3) == pytest.approx(0.8)

    def test_max_delay_cap(self):
        config = ClientConfig(
            retry=RetryConfig(base_delay_ms=1000, max_delay_ms=2000)
        )
        client = Ucotron(BASE_URL, config=config)
        assert client._retry_delay(0) == pytest.approx(1.0)
        assert client._retry_delay(1) == pytest.approx(2.0)
        assert client._retry_delay(2) == pytest.approx(2.0)
        assert client._retry_delay(10) == pytest.approx(2.0)


# ===========================================================================
# Context manager tests
# ===========================================================================


class TestContextManager:
    @pytest.mark.asyncio
    async def test_async_context_manager(self):
        def handler(req: httpx.Request) -> httpx.Response:
            return _json_resp(HEALTH_DATA)

        async with Ucotron(BASE_URL) as client:
            # Replace internal client with mock transport
            client._client = httpx.AsyncClient(
                transport=httpx.MockTransport(handler),
                base_url=BASE_URL,
            )
            resp = await client.health()
            assert resp.status == "healthy"

    def test_sync_context_manager(self):
        def handler(req: httpx.Request) -> httpx.Response:
            return _json_resp(HEALTH_DATA)

        with UcotronSync(BASE_URL) as client:
            client._client = httpx.Client(
                transport=httpx.MockTransport(handler),
                base_url=BASE_URL,
            )
            resp = client.health()
            assert resp.status == "healthy"


# ===========================================================================
# Sync client tests
# ===========================================================================


class TestSyncHealth:
    def test_health(self):
        def handler(req: httpx.Request) -> httpx.Response:
            return _json_resp(HEALTH_DATA)

        client = _sync_client(handler)
        resp = client.health()
        assert resp.status == "healthy"
        client.close()


class TestSyncMetrics:
    def test_metrics(self):
        def handler(req: httpx.Request) -> httpx.Response:
            return _json_resp(METRICS_DATA)

        client = _sync_client(handler)
        resp = client.metrics()
        assert resp.total_requests == 42
        client.close()


class TestSyncSearch:
    def test_search(self):
        def handler(req: httpx.Request) -> httpx.Response:
            body = json.loads(req.content)
            assert body["query"] == "Juan"
            return _json_resp(SEARCH_DATA)

        client = _sync_client(handler)
        resp = client.search("Juan")
        assert resp.total == 1
        client.close()


class TestSyncAddMemory:
    def test_add_memory(self):
        def handler(req: httpx.Request) -> httpx.Response:
            body = json.loads(req.content)
            assert body["text"] == "test memory"
            return _json_resp(CREATE_MEMORY_DATA)

        client = _sync_client(handler)
        resp = client.add_memory("test memory")
        assert len(resp.chunk_node_ids) == 2
        client.close()


class TestSyncAugment:
    def test_augment(self):
        def handler(req: httpx.Request) -> httpx.Response:
            return _json_resp(AUGMENT_DATA)

        client = _sync_client(handler)
        resp = client.augment("test context")
        assert resp.context_text == "Context about Juan."
        client.close()


class TestSyncLearn:
    def test_learn(self):
        def handler(req: httpx.Request) -> httpx.Response:
            return _json_resp(LEARN_DATA)

        client = _sync_client(handler)
        resp = client.learn("agent output")
        assert resp.memories_created == 2
        client.close()


class TestSyncEntities:
    def test_get_entity(self):
        def handler(req: httpx.Request) -> httpx.Response:
            return _json_resp(ENTITY_DATA)

        client = _sync_client(handler)
        resp = client.get_entity(10)
        assert resp.id == 10
        client.close()

    def test_list_entities(self):
        def handler(req: httpx.Request) -> httpx.Response:
            return _json_resp([ENTITY_DATA])

        client = _sync_client(handler)
        resp = client.list_entities()
        assert len(resp) == 1
        client.close()


class TestSyncMemories:
    def test_get_memory(self):
        def handler(req: httpx.Request) -> httpx.Response:
            return _json_resp(MEMORY_DATA)

        client = _sync_client(handler)
        resp = client.get_memory(1)
        assert resp.id == 1
        client.close()

    def test_list_memories(self):
        def handler(req: httpx.Request) -> httpx.Response:
            return _json_resp([MEMORY_DATA])

        client = _sync_client(handler)
        resp = client.list_memories()
        assert len(resp) == 1
        client.close()

    def test_update_memory(self):
        updated = {**MEMORY_DATA, "content": "updated"}

        def handler(req: httpx.Request) -> httpx.Response:
            return _json_resp(updated)

        client = _sync_client(handler)
        resp = client.update_memory(1, content="updated")
        assert resp.content == "updated"
        client.close()

    def test_delete_memory(self):
        def handler(req: httpx.Request) -> httpx.Response:
            return httpx.Response(status_code=200)

        client = _sync_client(handler)
        client.delete_memory(1)
        client.close()


class TestSyncErrorHandling:
    def test_4xx_raises_immediately(self):
        call_count = 0

        def handler(req: httpx.Request) -> httpx.Response:
            nonlocal call_count
            call_count += 1
            return _text_resp("Bad Request", status=400)

        config = ClientConfig(retry=RetryConfig(max_retries=3))
        client = _sync_client(handler, config=config)

        with pytest.raises(UcotronServerError) as exc_info:
            client.health()
        assert exc_info.value.status == 400
        assert call_count == 1
        client.close()

    def test_5xx_retries(self):
        call_count = 0

        def handler(req: httpx.Request) -> httpx.Response:
            nonlocal call_count
            call_count += 1
            return _text_resp("Server Error", status=502)

        config = ClientConfig(
            retry=RetryConfig(max_retries=2, base_delay_ms=1, max_delay_ms=1)
        )
        client = _sync_client(handler, config=config)

        with pytest.raises(UcotronRetriesExhaustedError):
            client.health()
        assert call_count == 3
        client.close()


class TestSyncNamespace:
    def test_namespace_header_set(self):
        def handler(req: httpx.Request) -> httpx.Response:
            assert req.headers["X-Ucotron-Namespace"] == "prod"
            return _json_resp(HEALTH_DATA)

        config = ClientConfig(default_namespace="prod")
        client = _sync_client(handler, config=config)
        client.health()
        client.close()


# ===========================================================================
# URL normalization
# ===========================================================================


class TestUrlNormalization:
    def test_trailing_slash_stripped(self):
        client = Ucotron("http://localhost:8420/")
        assert client._base_url == "http://localhost:8420"

    def test_no_trailing_slash_unchanged(self):
        client = Ucotron("http://localhost:8420")
        assert client._base_url == "http://localhost:8420"

    def test_sync_trailing_slash_stripped(self):
        client = UcotronSync("http://localhost:8420/")
        assert client._base_url == "http://localhost:8420"
