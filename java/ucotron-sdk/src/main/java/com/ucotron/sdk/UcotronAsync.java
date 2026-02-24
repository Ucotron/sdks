package com.ucotron.sdk;

import com.ucotron.sdk.types.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Asynchronous Ucotron client returning CompletableFuture for all methods.
 * <p>
 * Wraps {@link UcotronClient} and executes all calls on a configurable
 * {@link Executor}. Defaults to {@link ForkJoinPool#commonPool()}.
 * <p>
 * Usage:
 * <pre>
 * UcotronAsync async = new UcotronAsync("http://localhost:8420");
 * CompletableFuture&lt;SearchResult&gt; future = async.searchAsync("query");
 * SearchResult result = future.get();
 * </pre>
 */
public class UcotronAsync {
    private final UcotronClient client;
    private final Executor executor;

    /**
     * Create an async client with default config and common pool executor.
     */
    public UcotronAsync(String serverUrl) {
        this(new UcotronClient(serverUrl), ForkJoinPool.commonPool());
    }

    /**
     * Create an async client with custom config and common pool executor.
     */
    public UcotronAsync(String serverUrl, ClientConfig config) {
        this(new UcotronClient(serverUrl, config), ForkJoinPool.commonPool());
    }

    /**
     * Create an async client with custom config and custom executor.
     */
    public UcotronAsync(String serverUrl, ClientConfig config, Executor executor) {
        this(new UcotronClient(serverUrl, config), executor);
    }

    /**
     * Create an async client wrapping an existing sync client with custom executor.
     */
    public UcotronAsync(UcotronClient client, Executor executor) {
        this.client = client;
        this.executor = executor;
    }

    // -----------------------------------------------------------------------
    // Health & Metrics
    // -----------------------------------------------------------------------

    /** Async health check. */
    public CompletableFuture<HealthResponse> healthAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return client.health();
            } catch (UcotronException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, executor);
    }

    /** Async metrics. */
    public CompletableFuture<MetricsResponse> metricsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return client.metrics();
            } catch (UcotronException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, executor);
    }

    // -----------------------------------------------------------------------
    // Memories CRUD
    // -----------------------------------------------------------------------

    /** Async add memory. */
    public CompletableFuture<CreateMemoryResult> addMemoryAsync(String text) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return client.addMemory(text);
            } catch (UcotronException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, executor);
    }

    /** Async add memory with namespace. */
    public CompletableFuture<CreateMemoryResult> addMemoryAsync(String text, String namespace) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return client.addMemory(text, namespace);
            } catch (UcotronException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, executor);
    }

    /** Async add memory with namespace and metadata. */
    public CompletableFuture<CreateMemoryResult> addMemoryAsync(String text, String namespace,
                                                                 Map<String, Object> metadata) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return client.addMemory(text, namespace, metadata);
            } catch (UcotronException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, executor);
    }

    /** Async get memory by ID. */
    public CompletableFuture<MemoryResponse> getMemoryAsync(long id) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return client.getMemory(id);
            } catch (UcotronException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, executor);
    }

    /** Async get memory by ID with namespace. */
    public CompletableFuture<MemoryResponse> getMemoryAsync(long id, String namespace) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return client.getMemory(id, namespace);
            } catch (UcotronException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, executor);
    }

    /** Async list memories. */
    public CompletableFuture<List<MemoryResponse>> listMemoriesAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return client.listMemories();
            } catch (UcotronException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, executor);
    }

    /** Async list memories with filters. */
    public CompletableFuture<List<MemoryResponse>> listMemoriesAsync(String nodeType, Integer limit,
                                                                      Integer offset, String namespace) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return client.listMemories(nodeType, limit, offset, namespace);
            } catch (UcotronException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, executor);
    }

    /** Async update memory. */
    public CompletableFuture<MemoryResponse> updateMemoryAsync(long id, String content) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return client.updateMemory(id, content);
            } catch (UcotronException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, executor);
    }

    /** Async update memory with metadata and namespace. */
    public CompletableFuture<MemoryResponse> updateMemoryAsync(long id, String content,
                                                                Map<String, Object> metadata, String namespace) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return client.updateMemory(id, content, metadata, namespace);
            } catch (UcotronException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, executor);
    }

    /** Async delete memory. */
    public CompletableFuture<Void> deleteMemoryAsync(long id) {
        return CompletableFuture.runAsync(() -> {
            try {
                client.deleteMemory(id);
            } catch (UcotronException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, executor);
    }

    /** Async delete memory with namespace. */
    public CompletableFuture<Void> deleteMemoryAsync(long id, String namespace) {
        return CompletableFuture.runAsync(() -> {
            try {
                client.deleteMemory(id, namespace);
            } catch (UcotronException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, executor);
    }

    // -----------------------------------------------------------------------
    // Search
    // -----------------------------------------------------------------------

    /** Async search. */
    public CompletableFuture<SearchResult> searchAsync(String query) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return client.search(query);
            } catch (UcotronException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, executor);
    }

    /** Async search with options. */
    public CompletableFuture<SearchResult> searchAsync(String query, Integer limit,
                                                        String nodeType, String namespace) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return client.search(query, limit, nodeType, namespace);
            } catch (UcotronException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, executor);
    }

    // -----------------------------------------------------------------------
    // Entities
    // -----------------------------------------------------------------------

    /** Async get entity. */
    public CompletableFuture<EntityResponse> getEntityAsync(long id) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return client.getEntity(id);
            } catch (UcotronException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, executor);
    }

    /** Async get entity with namespace. */
    public CompletableFuture<EntityResponse> getEntityAsync(long id, String namespace) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return client.getEntity(id, namespace);
            } catch (UcotronException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, executor);
    }

    /** Async list entities. */
    public CompletableFuture<List<EntityResponse>> listEntitiesAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return client.listEntities();
            } catch (UcotronException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, executor);
    }

    /** Async list entities with filters. */
    public CompletableFuture<List<EntityResponse>> listEntitiesAsync(Integer limit, Integer offset,
                                                                      String namespace) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return client.listEntities(limit, offset, namespace);
            } catch (UcotronException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, executor);
    }

    // -----------------------------------------------------------------------
    // Augment & Learn
    // -----------------------------------------------------------------------

    /** Async augment. */
    public CompletableFuture<AugmentResult> augmentAsync(String context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return client.augment(context);
            } catch (UcotronException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, executor);
    }

    /** Async augment with options. */
    public CompletableFuture<AugmentResult> augmentAsync(String context, Integer limit, String namespace) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return client.augment(context, limit, namespace);
            } catch (UcotronException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, executor);
    }

    /** Async learn. */
    public CompletableFuture<LearnResult> learnAsync(String output) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return client.learn(output);
            } catch (UcotronException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, executor);
    }

    /** Async learn with options. */
    public CompletableFuture<LearnResult> learnAsync(String output, Map<String, Object> metadata,
                                                      String namespace) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return client.learn(output, metadata, namespace);
            } catch (UcotronException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, executor);
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    /** Get the underlying sync client. */
    public UcotronClient getSyncClient() {
        return client;
    }

    /** Get the executor used for async operations. */
    public Executor getExecutor() {
        return executor;
    }
}
