package com.ucotron.sdk.android

import com.ucotron.sdk.ClientConfig
import com.ucotron.sdk.UcotronClient
import com.ucotron.sdk.UcotronException
import com.ucotron.sdk.types.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android-friendly Ucotron client with Kotlin coroutine suspend functions.
 *
 * Wraps [UcotronClient] to provide non-blocking API calls suitable for
 * use with Android lifecycleScope and viewModelScope.
 *
 * Usage:
 * ```kotlin
 * val ucotron = UcotronAndroid("http://10.0.2.2:8420")
 *
 * lifecycleScope.launch {
 *     val health = ucotron.health()
 *     val result = ucotron.addMemory("user said hello")
 *     val search = ucotron.search("hello")
 * }
 * ```
 */
class UcotronAndroid(
    serverUrl: String,
    config: ClientConfig = ClientConfig.builder().build(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val client = UcotronClient(serverUrl, config)

    /** The underlying synchronous client. */
    val syncClient: UcotronClient get() = client

    // -----------------------------------------------------------------------
    // Health & Metrics
    // -----------------------------------------------------------------------

    /** Check server health. */
    @Throws(UcotronException::class)
    suspend fun health(): HealthResponse = withContext(dispatcher) {
        client.health()
    }

    /** Get server metrics. */
    @Throws(UcotronException::class)
    suspend fun metrics(): MetricsResponse = withContext(dispatcher) {
        client.metrics()
    }

    // -----------------------------------------------------------------------
    // Memories CRUD
    // -----------------------------------------------------------------------

    /** Add a new memory. */
    @Throws(UcotronException::class)
    suspend fun addMemory(text: String): CreateMemoryResult = withContext(dispatcher) {
        client.addMemory(text)
    }

    /** Add a new memory with namespace. */
    @Throws(UcotronException::class)
    suspend fun addMemory(text: String, namespace: String?): CreateMemoryResult = withContext(dispatcher) {
        client.addMemory(text, namespace)
    }

    /** Add a new memory with namespace and metadata. */
    @Throws(UcotronException::class)
    suspend fun addMemory(
        text: String,
        namespace: String?,
        metadata: Map<String, Any>?
    ): CreateMemoryResult = withContext(dispatcher) {
        client.addMemory(text, namespace, metadata)
    }

    /** Get a single memory by ID. */
    @Throws(UcotronException::class)
    suspend fun getMemory(id: Long): MemoryResponse = withContext(dispatcher) {
        client.getMemory(id)
    }

    /** Get a single memory by ID with namespace. */
    @Throws(UcotronException::class)
    suspend fun getMemory(id: Long, namespace: String?): MemoryResponse = withContext(dispatcher) {
        client.getMemory(id, namespace)
    }

    /** List memories with default parameters. */
    @Throws(UcotronException::class)
    suspend fun listMemories(): List<MemoryResponse> = withContext(dispatcher) {
        client.listMemories()
    }

    /** List memories with filters. */
    @Throws(UcotronException::class)
    suspend fun listMemories(
        nodeType: String? = null,
        limit: Int? = null,
        offset: Int? = null,
        namespace: String? = null
    ): List<MemoryResponse> = withContext(dispatcher) {
        client.listMemories(nodeType, limit, offset, namespace)
    }

    /** Update a memory by ID. */
    @Throws(UcotronException::class)
    suspend fun updateMemory(id: Long, content: String): MemoryResponse = withContext(dispatcher) {
        client.updateMemory(id, content)
    }

    /** Update a memory by ID with metadata and namespace. */
    @Throws(UcotronException::class)
    suspend fun updateMemory(
        id: Long,
        content: String,
        metadata: Map<String, Any>? = null,
        namespace: String? = null
    ): MemoryResponse = withContext(dispatcher) {
        client.updateMemory(id, content, metadata, namespace)
    }

    /** Delete a memory by ID. */
    @Throws(UcotronException::class)
    suspend fun deleteMemory(id: Long): Unit = withContext(dispatcher) {
        client.deleteMemory(id)
    }

    /** Delete a memory by ID with namespace. */
    @Throws(UcotronException::class)
    suspend fun deleteMemory(id: Long, namespace: String?): Unit = withContext(dispatcher) {
        client.deleteMemory(id, namespace)
    }

    // -----------------------------------------------------------------------
    // Search
    // -----------------------------------------------------------------------

    /** Search memories. */
    @Throws(UcotronException::class)
    suspend fun search(query: String): SearchResult = withContext(dispatcher) {
        client.search(query)
    }

    /** Search memories with options. */
    @Throws(UcotronException::class)
    suspend fun search(
        query: String,
        limit: Int? = null,
        nodeType: String? = null,
        namespace: String? = null
    ): SearchResult = withContext(dispatcher) {
        client.search(query, limit, nodeType, namespace)
    }

    // -----------------------------------------------------------------------
    // Entities
    // -----------------------------------------------------------------------

    /** Get an entity by ID. */
    @Throws(UcotronException::class)
    suspend fun getEntity(id: Long): EntityResponse = withContext(dispatcher) {
        client.getEntity(id)
    }

    /** Get an entity by ID with namespace. */
    @Throws(UcotronException::class)
    suspend fun getEntity(id: Long, namespace: String?): EntityResponse = withContext(dispatcher) {
        client.getEntity(id, namespace)
    }

    /** List entities with default parameters. */
    @Throws(UcotronException::class)
    suspend fun listEntities(): List<EntityResponse> = withContext(dispatcher) {
        client.listEntities()
    }

    /** List entities with filters. */
    @Throws(UcotronException::class)
    suspend fun listEntities(
        limit: Int? = null,
        offset: Int? = null,
        namespace: String? = null
    ): List<EntityResponse> = withContext(dispatcher) {
        client.listEntities(limit, offset, namespace)
    }

    // -----------------------------------------------------------------------
    // Augment & Learn
    // -----------------------------------------------------------------------

    /** Augment context with relevant memories. */
    @Throws(UcotronException::class)
    suspend fun augment(context: String): AugmentResult = withContext(dispatcher) {
        client.augment(context)
    }

    /** Augment context with options. */
    @Throws(UcotronException::class)
    suspend fun augment(
        context: String,
        limit: Int? = null,
        namespace: String? = null
    ): AugmentResult = withContext(dispatcher) {
        client.augment(context, limit, namespace)
    }

    /** Learn from agent output. */
    @Throws(UcotronException::class)
    suspend fun learn(output: String): LearnResult = withContext(dispatcher) {
        client.learn(output)
    }

    /** Learn from agent output with options. */
    @Throws(UcotronException::class)
    suspend fun learn(
        output: String,
        metadata: Map<String, Any>? = null,
        namespace: String? = null
    ): LearnResult = withContext(dispatcher) {
        client.learn(output, metadata, namespace)
    }
}
