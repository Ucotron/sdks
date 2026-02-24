"""Ucotron Python SDK client — async and sync HTTP wrapper over httpx."""

from __future__ import annotations

import asyncio
import time
from typing import Any, Dict, List, Optional, Tuple, Type, TypeVar

import httpx

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
    SearchOptions,
    SearchResponse,
)

T = TypeVar("T")

_NAMESPACE_HEADER = "X-Ucotron-Namespace"


class Ucotron:
    """Async Ucotron client using httpx.AsyncClient.

    Example::

        async with Ucotron("http://localhost:8420") as m:
            result = await m.augment("What do you know about Juan?")
            print(result.context_text)
    """

    def __init__(
        self,
        server_url: str,
        config: ClientConfig | None = None,
    ) -> None:
        self._config = config or ClientConfig()
        self._base_url = server_url.rstrip("/")
        self._client: httpx.AsyncClient | None = None

    # -- lifecycle -----------------------------------------------------------

    async def _ensure_client(self) -> httpx.AsyncClient:
        if self._client is None or self._client.is_closed:
            self._client = httpx.AsyncClient(
                base_url=self._base_url,
                timeout=httpx.Timeout(self._config.timeout_s),
            )
        return self._client

    async def close(self) -> None:
        if self._client is not None and not self._client.is_closed:
            await self._client.aclose()

    async def __aenter__(self) -> "Ucotron":
        await self._ensure_client()
        return self

    async def __aexit__(self, *exc: object) -> None:
        await self.close()

    # -- internal helpers ----------------------------------------------------

    def _namespace(self, override: str | None) -> str:
        return override or self._config.default_namespace or "default"

    def _retry_delay(self, attempt: int) -> float:
        delay = self._config.retry.base_delay_ms * (2**attempt)
        return min(delay, self._config.retry.max_delay_ms) / 1000.0

    async def _request_json(
        self,
        method: str,
        path: str,
        *,
        namespace: str | None = None,
        json_body: Any = None,
        params: Dict[str, Any] | None = None,
        response_model: Type[T],
    ) -> T:
        client = await self._ensure_client()
        headers = {_NAMESPACE_HEADER: self._namespace(namespace)}

        last_error: Exception | None = None
        max_attempts = self._config.retry.max_retries + 1

        for attempt in range(max_attempts):
            try:
                resp = await client.request(
                    method,
                    path,
                    headers=headers,
                    json=json_body,
                    params=params,
                )

                if resp.status_code >= 400:
                    body = resp.text
                    err = UcotronServerError(resp.status_code, body)
                    # Don't retry 4xx
                    if resp.status_code < 500:
                        raise err
                    last_error = err
                else:
                    data = resp.json()
                    return response_model.model_validate(data)  # type: ignore[return-value]

            except UcotronServerError:
                raise
            except httpx.HTTPError as exc:
                last_error = UcotronConnectionError(str(exc), cause=exc)
            except Exception as exc:
                last_error = UcotronError(str(exc))

            # Sleep before next retry (skip after last attempt)
            if attempt < max_attempts - 1:
                await asyncio.sleep(self._retry_delay(attempt))

        raise UcotronRetriesExhaustedError(max_attempts, last_error)  # type: ignore[arg-type]

    async def _request_no_body(
        self,
        method: str,
        path: str,
        *,
        namespace: str | None = None,
    ) -> None:
        client = await self._ensure_client()
        headers = {_NAMESPACE_HEADER: self._namespace(namespace)}

        last_error: Exception | None = None
        max_attempts = self._config.retry.max_retries + 1

        for attempt in range(max_attempts):
            try:
                resp = await client.request(method, path, headers=headers)
                if resp.status_code >= 400:
                    body = resp.text
                    err = UcotronServerError(resp.status_code, body)
                    if resp.status_code < 500:
                        raise err
                    last_error = err
                else:
                    return

            except UcotronServerError:
                raise
            except httpx.HTTPError as exc:
                last_error = UcotronConnectionError(str(exc), cause=exc)
            except Exception as exc:
                last_error = UcotronError(str(exc))

            if attempt < max_attempts - 1:
                await asyncio.sleep(self._retry_delay(attempt))

        raise UcotronRetriesExhaustedError(max_attempts, last_error)  # type: ignore[arg-type]

    async def _request_list(
        self,
        path: str,
        *,
        namespace: str | None = None,
        params: Dict[str, Any] | None = None,
        item_model: Type[T],
    ) -> List[T]:
        client = await self._ensure_client()
        headers = {_NAMESPACE_HEADER: self._namespace(namespace)}

        last_error: Exception | None = None
        max_attempts = self._config.retry.max_retries + 1

        for attempt in range(max_attempts):
            try:
                resp = await client.get(path, headers=headers, params=params)
                if resp.status_code >= 400:
                    body = resp.text
                    err = UcotronServerError(resp.status_code, body)
                    if resp.status_code < 500:
                        raise err
                    last_error = err
                else:
                    data = resp.json()
                    return [item_model.model_validate(item) for item in data]  # type: ignore[return-value]

            except UcotronServerError:
                raise
            except httpx.HTTPError as exc:
                last_error = UcotronConnectionError(str(exc), cause=exc)
            except Exception as exc:
                last_error = UcotronError(str(exc))

            if attempt < max_attempts - 1:
                await asyncio.sleep(self._retry_delay(attempt))

        raise UcotronRetriesExhaustedError(max_attempts, last_error)  # type: ignore[arg-type]

    # -- public async API ----------------------------------------------------

    async def augment(
        self,
        context: str,
        opts: AugmentOptions | None = None,
    ) -> AugmentResponse:
        opts = opts or AugmentOptions()
        body: Dict[str, Any] = {"context": context}
        if opts.limit is not None:
            body["limit"] = opts.limit
        return await self._request_json(
            "POST",
            "/api/v1/augment",
            namespace=opts.namespace,
            json_body=body,
            response_model=AugmentResponse,
        )

    async def learn(
        self,
        output: str,
        opts: LearnOptions | None = None,
    ) -> LearnResponse:
        opts = opts or LearnOptions()
        body: Dict[str, Any] = {"output": output}
        if opts.metadata is not None:
            body["metadata"] = opts.metadata
        return await self._request_json(
            "POST",
            "/api/v1/learn",
            namespace=opts.namespace,
            json_body=body,
            response_model=LearnResponse,
        )

    async def search(
        self,
        query: str,
        opts: SearchOptions | None = None,
    ) -> SearchResponse:
        opts = opts or SearchOptions()
        body: Dict[str, Any] = {"query": query}
        if opts.limit is not None:
            body["limit"] = opts.limit
        if opts.node_type is not None:
            body["node_type"] = opts.node_type
        if opts.time_range is not None:
            body["time_range"] = list(opts.time_range)
        return await self._request_json(
            "POST",
            "/api/v1/memories/search",
            namespace=opts.namespace,
            json_body=body,
            response_model=SearchResponse,
        )

    async def add_memory(
        self,
        text: str,
        opts: AddMemoryOptions | None = None,
    ) -> CreateMemoryResponse:
        opts = opts or AddMemoryOptions()
        body: Dict[str, Any] = {"text": text}
        if opts.metadata is not None:
            body["metadata"] = opts.metadata
        return await self._request_json(
            "POST",
            "/api/v1/memories",
            namespace=opts.namespace,
            json_body=body,
            response_model=CreateMemoryResponse,
        )

    async def get_entity(
        self,
        entity_id: int,
        opts: EntityOptions | None = None,
    ) -> EntityResponse:
        opts = opts or EntityOptions()
        return await self._request_json(
            "GET",
            f"/api/v1/entities/{entity_id}",
            namespace=opts.namespace,
            response_model=EntityResponse,
        )

    async def list_entities(
        self,
        opts: ListEntitiesOptions | None = None,
    ) -> List[EntityResponse]:
        opts = opts or ListEntitiesOptions()
        params: Dict[str, Any] = {}
        if opts.limit is not None:
            params["limit"] = opts.limit
        if opts.offset is not None:
            params["offset"] = opts.offset
        return await self._request_list(
            "/api/v1/entities",
            namespace=opts.namespace,
            params=params or None,
            item_model=EntityResponse,
        )

    async def get_memory(self, memory_id: int) -> MemoryResponse:
        return await self._request_json(
            "GET",
            f"/api/v1/memories/{memory_id}",
            response_model=MemoryResponse,
        )

    async def list_memories(
        self,
        opts: ListMemoriesOptions | None = None,
    ) -> List[MemoryResponse]:
        opts = opts or ListMemoriesOptions()
        params: Dict[str, Any] = {}
        if opts.node_type is not None:
            params["node_type"] = opts.node_type
        if opts.limit is not None:
            params["limit"] = opts.limit
        if opts.offset is not None:
            params["offset"] = opts.offset
        return await self._request_list(
            "/api/v1/memories",
            namespace=opts.namespace,
            params=params or None,
            item_model=MemoryResponse,
        )

    async def update_memory(
        self,
        memory_id: int,
        content: str | None = None,
        metadata: Dict[str, Any] | None = None,
    ) -> MemoryResponse:
        body: Dict[str, Any] = {}
        if content is not None:
            body["content"] = content
        if metadata is not None:
            body["metadata"] = metadata
        return await self._request_json(
            "PUT",
            f"/api/v1/memories/{memory_id}",
            json_body=body,
            response_model=MemoryResponse,
        )

    async def delete_memory(self, memory_id: int) -> None:
        await self._request_no_body("DELETE", f"/api/v1/memories/{memory_id}")

    async def health(self) -> HealthResponse:
        return await self._request_json(
            "GET",
            "/api/v1/health",
            response_model=HealthResponse,
        )

    async def metrics(self) -> MetricsResponse:
        return await self._request_json(
            "GET",
            "/api/v1/metrics",
            response_model=MetricsResponse,
        )


class UcotronSync:
    """Synchronous Ucotron client using httpx.Client.

    Example::

        with UcotronSync("http://localhost:8420") as m:
            result = m.augment("What do you know about Juan?")
            print(result.context_text)
    """

    def __init__(
        self,
        server_url: str,
        config: ClientConfig | None = None,
    ) -> None:
        self._config = config or ClientConfig()
        self._base_url = server_url.rstrip("/")
        self._client: httpx.Client | None = None

    # -- lifecycle -----------------------------------------------------------

    def _ensure_client(self) -> httpx.Client:
        if self._client is None or self._client.is_closed:
            self._client = httpx.Client(
                base_url=self._base_url,
                timeout=httpx.Timeout(self._config.timeout_s),
            )
        return self._client

    def close(self) -> None:
        if self._client is not None and not self._client.is_closed:
            self._client.close()

    def __enter__(self) -> "UcotronSync":
        self._ensure_client()
        return self

    def __exit__(self, *exc: object) -> None:
        self.close()

    # -- internal helpers ----------------------------------------------------

    def _namespace(self, override: str | None) -> str:
        return override or self._config.default_namespace or "default"

    def _retry_delay(self, attempt: int) -> float:
        delay = self._config.retry.base_delay_ms * (2**attempt)
        return min(delay, self._config.retry.max_delay_ms) / 1000.0

    def _request_json(
        self,
        method: str,
        path: str,
        *,
        namespace: str | None = None,
        json_body: Any = None,
        params: Dict[str, Any] | None = None,
        response_model: Type[T],
    ) -> T:
        client = self._ensure_client()
        headers = {_NAMESPACE_HEADER: self._namespace(namespace)}

        last_error: Exception | None = None
        max_attempts = self._config.retry.max_retries + 1

        for attempt in range(max_attempts):
            try:
                resp = client.request(
                    method,
                    path,
                    headers=headers,
                    json=json_body,
                    params=params,
                )
                if resp.status_code >= 400:
                    body = resp.text
                    err = UcotronServerError(resp.status_code, body)
                    if resp.status_code < 500:
                        raise err
                    last_error = err
                else:
                    data = resp.json()
                    return response_model.model_validate(data)  # type: ignore[return-value]

            except UcotronServerError:
                raise
            except httpx.HTTPError as exc:
                last_error = UcotronConnectionError(str(exc), cause=exc)
            except Exception as exc:
                last_error = UcotronError(str(exc))

            if attempt < max_attempts - 1:
                time.sleep(self._retry_delay(attempt))

        raise UcotronRetriesExhaustedError(max_attempts, last_error)  # type: ignore[arg-type]

    def _request_no_body(
        self,
        method: str,
        path: str,
        *,
        namespace: str | None = None,
    ) -> None:
        client = self._ensure_client()
        headers = {_NAMESPACE_HEADER: self._namespace(namespace)}

        last_error: Exception | None = None
        max_attempts = self._config.retry.max_retries + 1

        for attempt in range(max_attempts):
            try:
                resp = client.request(method, path, headers=headers)
                if resp.status_code >= 400:
                    body = resp.text
                    err = UcotronServerError(resp.status_code, body)
                    if resp.status_code < 500:
                        raise err
                    last_error = err
                else:
                    return

            except UcotronServerError:
                raise
            except httpx.HTTPError as exc:
                last_error = UcotronConnectionError(str(exc), cause=exc)
            except Exception as exc:
                last_error = UcotronError(str(exc))

            if attempt < max_attempts - 1:
                time.sleep(self._retry_delay(attempt))

        raise UcotronRetriesExhaustedError(max_attempts, last_error)  # type: ignore[arg-type]

    def _request_list(
        self,
        path: str,
        *,
        namespace: str | None = None,
        params: Dict[str, Any] | None = None,
        item_model: Type[T],
    ) -> List[T]:
        client = self._ensure_client()
        headers = {_NAMESPACE_HEADER: self._namespace(namespace)}

        last_error: Exception | None = None
        max_attempts = self._config.retry.max_retries + 1

        for attempt in range(max_attempts):
            try:
                resp = client.get(path, headers=headers, params=params)
                if resp.status_code >= 400:
                    body = resp.text
                    err = UcotronServerError(resp.status_code, body)
                    if resp.status_code < 500:
                        raise err
                    last_error = err
                else:
                    data = resp.json()
                    return [item_model.model_validate(item) for item in data]  # type: ignore[return-value]

            except UcotronServerError:
                raise
            except httpx.HTTPError as exc:
                last_error = UcotronConnectionError(str(exc), cause=exc)
            except Exception as exc:
                last_error = UcotronError(str(exc))

            if attempt < max_attempts - 1:
                time.sleep(self._retry_delay(attempt))

        raise UcotronRetriesExhaustedError(max_attempts, last_error)  # type: ignore[arg-type]

    # -- public sync API -----------------------------------------------------

    def augment(
        self,
        context: str,
        opts: AugmentOptions | None = None,
    ) -> AugmentResponse:
        opts = opts or AugmentOptions()
        body: Dict[str, Any] = {"context": context}
        if opts.limit is not None:
            body["limit"] = opts.limit
        return self._request_json(
            "POST",
            "/api/v1/augment",
            namespace=opts.namespace,
            json_body=body,
            response_model=AugmentResponse,
        )

    def learn(
        self,
        output: str,
        opts: LearnOptions | None = None,
    ) -> LearnResponse:
        opts = opts or LearnOptions()
        body: Dict[str, Any] = {"output": output}
        if opts.metadata is not None:
            body["metadata"] = opts.metadata
        return self._request_json(
            "POST",
            "/api/v1/learn",
            namespace=opts.namespace,
            json_body=body,
            response_model=LearnResponse,
        )

    def search(
        self,
        query: str,
        opts: SearchOptions | None = None,
    ) -> SearchResponse:
        opts = opts or SearchOptions()
        body: Dict[str, Any] = {"query": query}
        if opts.limit is not None:
            body["limit"] = opts.limit
        if opts.node_type is not None:
            body["node_type"] = opts.node_type
        if opts.time_range is not None:
            body["time_range"] = list(opts.time_range)
        return self._request_json(
            "POST",
            "/api/v1/memories/search",
            namespace=opts.namespace,
            json_body=body,
            response_model=SearchResponse,
        )

    def add_memory(
        self,
        text: str,
        opts: AddMemoryOptions | None = None,
    ) -> CreateMemoryResponse:
        opts = opts or AddMemoryOptions()
        body: Dict[str, Any] = {"text": text}
        if opts.metadata is not None:
            body["metadata"] = opts.metadata
        return self._request_json(
            "POST",
            "/api/v1/memories",
            namespace=opts.namespace,
            json_body=body,
            response_model=CreateMemoryResponse,
        )

    def get_entity(
        self,
        entity_id: int,
        opts: EntityOptions | None = None,
    ) -> EntityResponse:
        opts = opts or EntityOptions()
        return self._request_json(
            "GET",
            f"/api/v1/entities/{entity_id}",
            namespace=opts.namespace,
            response_model=EntityResponse,
        )

    def list_entities(
        self,
        opts: ListEntitiesOptions | None = None,
    ) -> List[EntityResponse]:
        opts = opts or ListEntitiesOptions()
        params: Dict[str, Any] = {}
        if opts.limit is not None:
            params["limit"] = opts.limit
        if opts.offset is not None:
            params["offset"] = opts.offset
        return self._request_list(
            "/api/v1/entities",
            namespace=opts.namespace,
            params=params or None,
            item_model=EntityResponse,
        )

    def get_memory(self, memory_id: int) -> MemoryResponse:
        return self._request_json(
            "GET",
            f"/api/v1/memories/{memory_id}",
            response_model=MemoryResponse,
        )

    def list_memories(
        self,
        opts: ListMemoriesOptions | None = None,
    ) -> List[MemoryResponse]:
        opts = opts or ListMemoriesOptions()
        params: Dict[str, Any] = {}
        if opts.node_type is not None:
            params["node_type"] = opts.node_type
        if opts.limit is not None:
            params["limit"] = opts.limit
        if opts.offset is not None:
            params["offset"] = opts.offset
        return self._request_list(
            "/api/v1/memories",
            namespace=opts.namespace,
            params=params or None,
            item_model=MemoryResponse,
        )

    def update_memory(
        self,
        memory_id: int,
        content: str | None = None,
        metadata: Dict[str, Any] | None = None,
    ) -> MemoryResponse:
        body: Dict[str, Any] = {}
        if content is not None:
            body["content"] = content
        if metadata is not None:
            body["metadata"] = metadata
        return self._request_json(
            "PUT",
            f"/api/v1/memories/{memory_id}",
            json_body=body,
            response_model=MemoryResponse,
        )

    def delete_memory(self, memory_id: int) -> None:
        self._request_no_body("DELETE", f"/api/v1/memories/{memory_id}")

    def health(self) -> HealthResponse:
        return self._request_json(
            "GET",
            "/api/v1/health",
            response_model=HealthResponse,
        )

    def metrics(self) -> MetricsResponse:
        return self._request_json(
            "GET",
            "/api/v1/metrics",
            response_model=MetricsResponse,
        )
