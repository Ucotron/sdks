"""Cross-language SDK integration tests for Python.

These tests run against a live Ucotron server. Set UCOTRON_TEST_SERVER_URL
to the server URL (e.g., "http://127.0.0.1:8420"). If the env var is not
set, tests are skipped.
"""

import os
import pytest

# Skip entire module if no server URL is set.
SERVER_URL = os.environ.get("UCOTRON_TEST_SERVER_URL")
pytestmark = pytest.mark.skipif(
    SERVER_URL is None,
    reason="UCOTRON_TEST_SERVER_URL not set — skipping integration tests",
)


@pytest.fixture
def server_url() -> str:
    """Return the test server URL (guaranteed non-None by pytestmark)."""
    assert SERVER_URL is not None
    return SERVER_URL


# ---------------------------------------------------------------------------
# Sync client tests
# ---------------------------------------------------------------------------

class TestCrossLanguageSync:
    """Integration tests using the synchronous UcotronSync client."""

    def _client(self, url: str):
        from ucotron_sdk import UcotronSync
        return UcotronSync(url)

    def test_health(self, server_url: str) -> None:
        client = self._client(server_url)
        health = client.health()
        assert health.status == "ok"
        assert health.version
        assert health.instance_id
        assert health.instance_role
        assert health.storage_mode

    def test_metrics(self, server_url: str) -> None:
        client = self._client(server_url)
        metrics = client.metrics()
        assert metrics.instance_id
        assert isinstance(metrics.total_requests, int)
        assert metrics.uptime_secs >= 0

    def test_add_memory(self, server_url: str) -> None:
        from ucotron_sdk import AddMemoryOptions
        client = self._client(server_url)
        ns = f"py_test_{os.getpid()}"
        result = client.add_memory(
            "Python SDK test: The Great Wall of China is a wonder of the world.",
            opts=AddMemoryOptions(namespace=ns),
        )
        assert hasattr(result, "chunk_node_ids")
        assert hasattr(result, "entity_node_ids")
        assert hasattr(result, "edges_created")
        assert hasattr(result, "metrics")
        assert isinstance(result.chunk_node_ids, list)

    def test_search(self, server_url: str) -> None:
        from ucotron_sdk import SearchOptions
        client = self._client(server_url)
        ns = f"py_search_{os.getpid()}"
        result = client.search(
            "Great Wall of China",
            opts=SearchOptions(limit=5, namespace=ns),
        )
        assert hasattr(result, "results")
        assert hasattr(result, "total")
        assert hasattr(result, "query")
        assert isinstance(result.results, list)
        assert result.query

    def test_augment(self, server_url: str) -> None:
        from ucotron_sdk import AugmentOptions
        client = self._client(server_url)
        ns = f"py_aug_{os.getpid()}"
        result = client.augment(
            "What do you know about artificial intelligence?",
            opts=AugmentOptions(namespace=ns),
        )
        assert hasattr(result, "memories")
        assert hasattr(result, "entities")
        assert hasattr(result, "context_text")
        assert isinstance(result.memories, list)
        assert isinstance(result.entities, list)
        assert isinstance(result.context_text, str)

    def test_learn(self, server_url: str) -> None:
        from ucotron_sdk import LearnOptions
        client = self._client(server_url)
        ns = f"py_learn_{os.getpid()}"
        result = client.learn(
            "The user mentioned they prefer dark mode and use VSCode.",
            opts=LearnOptions(namespace=ns),
        )
        assert hasattr(result, "memories_created")
        assert hasattr(result, "entities_found")
        assert hasattr(result, "conflicts_found")
        assert isinstance(result.memories_created, int)

    def test_list_memories(self, server_url: str) -> None:
        client = self._client(server_url)
        result = client.list_memories()
        assert isinstance(result, list)

    def test_list_entities(self, server_url: str) -> None:
        client = self._client(server_url)
        result = client.list_entities()
        assert isinstance(result, list)


# ---------------------------------------------------------------------------
# Async client tests
# ---------------------------------------------------------------------------

class TestCrossLanguageAsync:
    """Integration tests using the async Ucotron client."""

    def _client(self, url: str):
        from ucotron_sdk import Ucotron
        return Ucotron(url)

    @pytest.mark.asyncio
    async def test_health(self, server_url: str) -> None:
        async with self._client(server_url) as client:
            health = await client.health()
            assert health.status == "ok"
            assert health.version
            assert health.instance_id

    @pytest.mark.asyncio
    async def test_metrics(self, server_url: str) -> None:
        async with self._client(server_url) as client:
            metrics = await client.metrics()
            assert metrics.instance_id
            assert metrics.uptime_secs >= 0

    @pytest.mark.asyncio
    async def test_add_memory(self, server_url: str) -> None:
        from ucotron_sdk import AddMemoryOptions
        async with self._client(server_url) as client:
            ns = f"py_async_{os.getpid()}"
            result = await client.add_memory(
                "Async Python test: Tokyo is the capital of Japan.",
                opts=AddMemoryOptions(namespace=ns),
            )
            assert isinstance(result.chunk_node_ids, list)

    @pytest.mark.asyncio
    async def test_search(self, server_url: str) -> None:
        from ucotron_sdk import SearchOptions
        async with self._client(server_url) as client:
            ns = f"py_async_search_{os.getpid()}"
            result = await client.search(
                "capital of Japan",
                opts=SearchOptions(limit=5, namespace=ns),
            )
            assert isinstance(result.results, list)
            assert result.query

    @pytest.mark.asyncio
    async def test_augment(self, server_url: str) -> None:
        from ucotron_sdk import AugmentOptions
        async with self._client(server_url) as client:
            ns = f"py_async_aug_{os.getpid()}"
            result = await client.augment(
                "Tell me about machine learning",
                opts=AugmentOptions(namespace=ns),
            )
            assert isinstance(result.context_text, str)

    @pytest.mark.asyncio
    async def test_learn(self, server_url: str) -> None:
        from ucotron_sdk import LearnOptions
        async with self._client(server_url) as client:
            ns = f"py_async_learn_{os.getpid()}"
            result = await client.learn(
                "The user prefers light theme and uses IntelliJ.",
                opts=LearnOptions(namespace=ns),
            )
            assert isinstance(result.memories_created, int)
