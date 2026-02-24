package com.ucotron.sdk;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.ucotron.sdk.types.*;
import okhttp3.*;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Synchronous Ucotron client using OkHttp for HTTP communication.
 * <p>
 * Provides all 12 API methods with retry logic, connection pooling,
 * and multi-tenancy via namespace headers.
 * <p>
 * Usage:
 * <pre>
 * UcotronClient client = new UcotronClient("http://localhost:8420");
 * HealthResponse health = client.health();
 * CreateMemoryResult result = client.addMemory("some text");
 * SearchResult results = client.search("query");
 * </pre>
 */
public class UcotronClient {
    static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    static final String NAMESPACE_HEADER = "X-Ucotron-Namespace";

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final String baseUrl;
    private final ClientConfig config;

    /**
     * Create a client with default configuration.
     */
    public UcotronClient(String serverUrl) {
        this(serverUrl, ClientConfig.builder().build());
    }

    /**
     * Create a client with custom configuration.
     */
    public UcotronClient(String serverUrl, ClientConfig config) {
        this.baseUrl = serverUrl.replaceAll("/+$", "");
        this.config = config;
        this.gson = new Gson();

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(config.getTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(config.getTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .writeTimeout(config.getTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .connectionPool(new ConnectionPool(10, 90, TimeUnit.SECONDS));

        this.httpClient = builder.build();
    }

    // -----------------------------------------------------------------------
    // Health & Metrics
    // -----------------------------------------------------------------------

    /** Check server health. */
    public HealthResponse health() throws UcotronException {
        return executeGet("/api/v1/health", null, HealthResponse.class);
    }

    /** Get server metrics. */
    public MetricsResponse metrics() throws UcotronException {
        return executeGet("/api/v1/metrics", null, MetricsResponse.class);
    }

    // -----------------------------------------------------------------------
    // Memories CRUD
    // -----------------------------------------------------------------------

    /** Add a new memory. */
    public CreateMemoryResult addMemory(String text) throws UcotronException {
        return addMemory(text, null, null);
    }

    /** Add a new memory with namespace. */
    public CreateMemoryResult addMemory(String text, String namespace) throws UcotronException {
        return addMemory(text, namespace, null);
    }

    /** Add a new memory with namespace and metadata. */
    public CreateMemoryResult addMemory(String text, String namespace, Map<String, Object> metadata)
            throws UcotronException {
        JsonObject body = new JsonObject();
        body.addProperty("text", text);
        if (metadata != null && !metadata.isEmpty()) {
            body.add("metadata", gson.toJsonTree(metadata));
        }
        return executePost("/api/v1/memories", body, namespace, CreateMemoryResult.class);
    }

    /** Get a single memory by ID. */
    public MemoryResponse getMemory(long id) throws UcotronException {
        return getMemory(id, null);
    }

    /** Get a single memory by ID with namespace. */
    public MemoryResponse getMemory(long id, String namespace) throws UcotronException {
        return executeGet("/api/v1/memories/" + id, namespace, MemoryResponse.class);
    }

    /** List memories with default parameters. */
    public List<MemoryResponse> listMemories() throws UcotronException {
        return listMemories(null, null, null, null);
    }

    /** List memories with filters. */
    public List<MemoryResponse> listMemories(String nodeType, Integer limit, Integer offset, String namespace)
            throws UcotronException {
        StringBuilder path = new StringBuilder("/api/v1/memories");
        String sep = "?";
        if (nodeType != null) {
            path.append(sep).append("node_type=").append(nodeType);
            sep = "&";
        }
        if (limit != null) {
            path.append(sep).append("limit=").append(limit);
            sep = "&";
        }
        if (offset != null) {
            path.append(sep).append("offset=").append(offset);
        }
        Type listType = new TypeToken<List<MemoryResponse>>() {}.getType();
        return executeGet(path.toString(), namespace, listType);
    }

    /** Update a memory by ID. */
    public MemoryResponse updateMemory(long id, String content) throws UcotronException {
        return updateMemory(id, content, null, null);
    }

    /** Update a memory by ID with metadata and namespace. */
    public MemoryResponse updateMemory(long id, String content, Map<String, Object> metadata, String namespace)
            throws UcotronException {
        JsonObject body = new JsonObject();
        if (content != null) {
            body.addProperty("content", content);
        }
        if (metadata != null) {
            body.add("metadata", gson.toJsonTree(metadata));
        }
        return executePut("/api/v1/memories/" + id, body, namespace, MemoryResponse.class);
    }

    /** Delete a memory by ID. */
    public void deleteMemory(long id) throws UcotronException {
        deleteMemory(id, null);
    }

    /** Delete a memory by ID with namespace. */
    public void deleteMemory(long id, String namespace) throws UcotronException {
        executeDelete("/api/v1/memories/" + id, namespace);
    }

    // -----------------------------------------------------------------------
    // Search
    // -----------------------------------------------------------------------

    /** Search memories. */
    public SearchResult search(String query) throws UcotronException {
        return search(query, null, null, null);
    }

    /** Search memories with options. */
    public SearchResult search(String query, Integer limit, String nodeType, String namespace)
            throws UcotronException {
        JsonObject body = new JsonObject();
        body.addProperty("query", query);
        if (limit != null) {
            body.addProperty("limit", limit);
        }
        if (nodeType != null) {
            body.addProperty("node_type", nodeType);
        }
        return executePost("/api/v1/memories/search", body, namespace, SearchResult.class);
    }

    // -----------------------------------------------------------------------
    // Entities
    // -----------------------------------------------------------------------

    /** Get an entity by ID. */
    public EntityResponse getEntity(long id) throws UcotronException {
        return getEntity(id, null);
    }

    /** Get an entity by ID with namespace. */
    public EntityResponse getEntity(long id, String namespace) throws UcotronException {
        return executeGet("/api/v1/entities/" + id, namespace, EntityResponse.class);
    }

    /** List entities with default parameters. */
    public List<EntityResponse> listEntities() throws UcotronException {
        return listEntities(null, null, null);
    }

    /** List entities with filters. */
    public List<EntityResponse> listEntities(Integer limit, Integer offset, String namespace) throws UcotronException {
        StringBuilder path = new StringBuilder("/api/v1/entities");
        String sep = "?";
        if (limit != null) {
            path.append(sep).append("limit=").append(limit);
            sep = "&";
        }
        if (offset != null) {
            path.append(sep).append("offset=").append(offset);
        }
        Type listType = new TypeToken<List<EntityResponse>>() {}.getType();
        return executeGet(path.toString(), namespace, listType);
    }

    // -----------------------------------------------------------------------
    // Augment & Learn
    // -----------------------------------------------------------------------

    /** Augment context with relevant memories. */
    public AugmentResult augment(String context) throws UcotronException {
        return augment(context, null, null);
    }

    /** Augment context with options. */
    public AugmentResult augment(String context, Integer limit, String namespace) throws UcotronException {
        JsonObject body = new JsonObject();
        body.addProperty("context", context);
        if (limit != null) {
            body.addProperty("limit", limit);
        }
        return executePost("/api/v1/augment", body, namespace, AugmentResult.class);
    }

    /** Learn from agent output. */
    public LearnResult learn(String output) throws UcotronException {
        return learn(output, null, null);
    }

    /** Learn from agent output with options. */
    public LearnResult learn(String output, Map<String, Object> metadata, String namespace) throws UcotronException {
        JsonObject body = new JsonObject();
        body.addProperty("output", output);
        if (metadata != null && !metadata.isEmpty()) {
            body.add("metadata", gson.toJsonTree(metadata));
        }
        return executePost("/api/v1/learn", body, namespace, LearnResult.class);
    }

    // -----------------------------------------------------------------------
    // Accessors (package-private for testing)
    // -----------------------------------------------------------------------

    /** Get the base URL. */
    public String getBaseUrl() { return baseUrl; }

    /** Get the client configuration. */
    public ClientConfig getConfig() { return config; }

    /** Get the Gson instance (for testing). */
    Gson getGson() { return gson; }

    /** Get the OkHttpClient (for testing). */
    OkHttpClient getHttpClient() { return httpClient; }

    // -----------------------------------------------------------------------
    // Internal HTTP methods with retry
    // -----------------------------------------------------------------------

    private <T> T executeGet(String path, String namespace, Type responseType) throws UcotronException {
        Request request = buildRequest(path, namespace)
                .get()
                .build();
        return executeWithRetry(request, responseType);
    }

    private <T> T executePost(String path, JsonObject body, String namespace, Type responseType)
            throws UcotronException {
        RequestBody requestBody = RequestBody.create(gson.toJson(body), JSON_MEDIA_TYPE);
        Request request = buildRequest(path, namespace)
                .post(requestBody)
                .build();
        return executeWithRetry(request, responseType);
    }

    private <T> T executePut(String path, JsonObject body, String namespace, Type responseType)
            throws UcotronException {
        RequestBody requestBody = RequestBody.create(gson.toJson(body), JSON_MEDIA_TYPE);
        Request request = buildRequest(path, namespace)
                .put(requestBody)
                .build();
        return executeWithRetry(request, responseType);
    }

    private void executeDelete(String path, String namespace) throws UcotronException {
        Request request = buildRequest(path, namespace)
                .delete()
                .build();
        executeWithRetry(request, Void.class);
    }

    private Request.Builder buildRequest(String path, String namespace) {
        Request.Builder builder = new Request.Builder()
                .url(baseUrl + path);

        // Apply namespace header
        String ns = namespace != null ? namespace : config.getDefaultNamespace();
        if (ns != null) {
            builder.header(NAMESPACE_HEADER, ns);
        }

        // Apply API key
        if (config.getApiKey() != null) {
            builder.header("Authorization", "Bearer " + config.getApiKey());
        }

        return builder;
    }

    @SuppressWarnings("unchecked")
    private <T> T executeWithRetry(Request request, Type responseType) throws UcotronException {
        RetryConfig retryConfig = config.getRetryConfig();
        UcotronException lastException = null;

        for (int attempt = 0; attempt <= retryConfig.getMaxRetries(); attempt++) {
            if (attempt > 0) {
                long delay = computeRetryDelay(attempt - 1, retryConfig);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new UcotronConnectionException("Interrupted during retry delay", e);
                }
            }

            try {
                try (Response response = httpClient.newCall(request).execute()) {
                    int code = response.code();

                    // 4xx — client error, do NOT retry
                    if (code >= 400 && code < 500) {
                        String errorBody = response.body() != null ? response.body().string() : "";
                        throw new UcotronServerException(code, errorBody);
                    }

                    // 5xx — server error, retry
                    if (code >= 500) {
                        String errorBody = response.body() != null ? response.body().string() : "";
                        lastException = new UcotronServerException(code, errorBody);
                        continue;
                    }

                    // 2xx — success
                    if (responseType == Void.class) {
                        return null;
                    }
                    String responseBody = response.body() != null ? response.body().string() : "";
                    return gson.fromJson(responseBody, responseType);
                }
            } catch (UcotronServerException e) {
                // 4xx errors propagate immediately (already thrown above)
                if (e.getStatusCode() >= 400 && e.getStatusCode() < 500) {
                    throw e;
                }
                // 5xx errors continue to retry loop
                lastException = e;
            } catch (ConnectException | SocketTimeoutException e) {
                lastException = new UcotronConnectionException(e.getMessage(), e);
            } catch (IOException e) {
                lastException = new UcotronConnectionException(e.getMessage(), e);
            }
        }

        throw new UcotronRetriesExhaustedException(retryConfig.getMaxRetries() + 1, lastException);
    }

    private long computeRetryDelay(int attempt, RetryConfig retryConfig) {
        long delay = retryConfig.getBaseDelay().toMillis() * (1L << attempt);
        long maxDelay = retryConfig.getMaxDelay().toMillis();
        return Math.min(delay, maxDelay);
    }
}
