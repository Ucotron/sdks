package com.ucotron.sdk;

import com.google.gson.Gson;
import com.ucotron.sdk.types.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UcotronClientTest {
    private MockWebServer server;
    private UcotronClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new UcotronClient(server.url("/").toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    // -----------------------------------------------------------------------
    // Constructor tests
    // -----------------------------------------------------------------------

    @Test
    void testDefaultConstructor() {
        UcotronClient c = new UcotronClient("http://localhost:8420");
        assertEquals("http://localhost:8420", c.getBaseUrl());
        assertNotNull(c.getConfig());
    }

    @Test
    void testTrailingSlashStripped() {
        UcotronClient c = new UcotronClient("http://localhost:8420/");
        assertEquals("http://localhost:8420", c.getBaseUrl());
    }

    @Test
    void testMultipleTrailingSlashes() {
        UcotronClient c = new UcotronClient("http://localhost:8420///");
        assertEquals("http://localhost:8420", c.getBaseUrl());
    }

    @Test
    void testCustomConfig() {
        ClientConfig config = ClientConfig.builder()
                .apiKey("test-key")
                .defaultNamespace("test-ns")
                .timeout(Duration.ofSeconds(60))
                .retryConfig(new RetryConfig(5, Duration.ofMillis(200), Duration.ofSeconds(10)))
                .build();

        UcotronClient c = new UcotronClient("http://localhost:8420", config);
        assertEquals("test-key", c.getConfig().getApiKey());
        assertEquals("test-ns", c.getConfig().getDefaultNamespace());
        assertEquals(Duration.ofSeconds(60), c.getConfig().getTimeout());
        assertEquals(5, c.getConfig().getRetryConfig().getMaxRetries());
    }

    @Test
    void testRetryConfigDefaults() {
        RetryConfig config = RetryConfig.defaults();
        assertEquals(3, config.getMaxRetries());
        assertEquals(Duration.ofMillis(100), config.getBaseDelay());
        assertEquals(Duration.ofSeconds(5), config.getMaxDelay());
    }

    @Test
    void testClientConfigDefaults() {
        ClientConfig config = ClientConfig.builder().build();
        assertEquals(Duration.ofSeconds(30), config.getTimeout());
        assertNull(config.getApiKey());
        assertNull(config.getDefaultNamespace());
        assertNotNull(config.getRetryConfig());
    }

    // -----------------------------------------------------------------------
    // Health endpoint
    // -----------------------------------------------------------------------

    @Test
    void testHealth() throws Exception {
        String json = "{\"status\":\"ok\",\"version\":\"0.1.0\",\"instance_id\":\"abc\"," +
                "\"instance_role\":\"writer\",\"storage_mode\":\"embedded\"," +
                "\"vector_backend\":\"helix\",\"graph_backend\":\"helix\"," +
                "\"models\":{\"embedder_loaded\":true,\"embedding_model\":\"all-MiniLM-L6-v2\"," +
                "\"ner_loaded\":false,\"relation_extractor_loaded\":false,\"transcriber_loaded\":false}}";

        server.enqueue(new MockResponse().setBody(json).setResponseCode(200));

        HealthResponse health = client.health();
        assertEquals("ok", health.getStatus());
        assertEquals("0.1.0", health.getVersion());
        assertEquals("abc", health.getInstanceId());
        assertTrue(health.getModels().isEmbedderLoaded());

        RecordedRequest req = server.takeRequest();
        assertEquals("GET", req.getMethod());
        assertEquals("/api/v1/health", req.getPath());
    }

    // -----------------------------------------------------------------------
    // Metrics endpoint
    // -----------------------------------------------------------------------

    @Test
    void testMetrics() throws Exception {
        String json = "{\"instance_id\":\"test\",\"total_requests\":100," +
                "\"total_ingestions\":50,\"total_searches\":30,\"uptime_secs\":3600}";

        server.enqueue(new MockResponse().setBody(json).setResponseCode(200));

        MetricsResponse metrics = client.metrics();
        assertEquals("test", metrics.getInstanceId());
        assertEquals(100, metrics.getTotalRequests());
        assertEquals(3600, metrics.getUptimeSecs());

        RecordedRequest req = server.takeRequest();
        assertEquals("GET", req.getMethod());
        assertEquals("/api/v1/metrics", req.getPath());
    }

    // -----------------------------------------------------------------------
    // Add memory
    // -----------------------------------------------------------------------

    @Test
    void testAddMemory() throws Exception {
        String json = "{\"chunk_node_ids\":[1,2],\"entity_node_ids\":[3]," +
                "\"edges_created\":5,\"metrics\":{\"chunks_processed\":2," +
                "\"entities_extracted\":1,\"relations_extracted\":3," +
                "\"contradictions_detected\":0,\"total_us\":12345}}";

        server.enqueue(new MockResponse().setBody(json).setResponseCode(200));

        CreateMemoryResult result = client.addMemory("Hello world");
        assertEquals(2, result.getChunkNodeIds().size());
        assertEquals(5, result.getEdgesCreated());
        assertEquals(2, result.getMetrics().getChunksProcessed());

        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertEquals("/api/v1/memories", req.getPath());
        assertTrue(req.getBody().readUtf8().contains("\"text\":\"Hello world\""));
    }

    @Test
    void testAddMemoryWithNamespace() throws Exception {
        String json = "{\"chunk_node_ids\":[1],\"entity_node_ids\":[]," +
                "\"edges_created\":0,\"metrics\":{\"chunks_processed\":1," +
                "\"entities_extracted\":0,\"relations_extracted\":0," +
                "\"contradictions_detected\":0,\"total_us\":100}}";

        server.enqueue(new MockResponse().setBody(json).setResponseCode(200));

        client.addMemory("test", "custom-ns");

        RecordedRequest req = server.takeRequest();
        assertEquals("custom-ns", req.getHeader("X-Ucotron-Namespace"));
    }

    @Test
    void testAddMemoryWithMetadata() throws Exception {
        String json = "{\"chunk_node_ids\":[1],\"entity_node_ids\":[]," +
                "\"edges_created\":0,\"metrics\":{\"chunks_processed\":1," +
                "\"entities_extracted\":0,\"relations_extracted\":0," +
                "\"contradictions_detected\":0,\"total_us\":100}}";

        server.enqueue(new MockResponse().setBody(json).setResponseCode(200));

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "test");
        client.addMemory("text", null, metadata);

        RecordedRequest req = server.takeRequest();
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("\"source\":\"test\""));
    }

    // -----------------------------------------------------------------------
    // Get memory
    // -----------------------------------------------------------------------

    @Test
    void testGetMemory() throws Exception {
        String json = "{\"id\":42,\"content\":\"hello\",\"node_type\":\"Entity\"," +
                "\"timestamp\":1000000,\"metadata\":{}}";

        server.enqueue(new MockResponse().setBody(json).setResponseCode(200));

        MemoryResponse memory = client.getMemory(42);
        assertEquals(42, memory.getId());
        assertEquals("hello", memory.getContent());
        assertEquals("Entity", memory.getNodeType());

        RecordedRequest req = server.takeRequest();
        assertEquals("GET", req.getMethod());
        assertEquals("/api/v1/memories/42", req.getPath());
    }

    // -----------------------------------------------------------------------
    // List memories
    // -----------------------------------------------------------------------

    @Test
    void testListMemories() throws Exception {
        String json = "[{\"id\":1,\"content\":\"a\",\"node_type\":\"Entity\"," +
                "\"timestamp\":100,\"metadata\":{}}," +
                "{\"id\":2,\"content\":\"b\",\"node_type\":\"Event\"," +
                "\"timestamp\":200,\"metadata\":{}}]";

        server.enqueue(new MockResponse().setBody(json).setResponseCode(200));

        List<MemoryResponse> memories = client.listMemories();
        assertEquals(2, memories.size());
        assertEquals("a", memories.get(0).getContent());

        RecordedRequest req = server.takeRequest();
        assertEquals("GET", req.getMethod());
        assertEquals("/api/v1/memories", req.getPath());
    }

    @Test
    void testListMemoriesWithFilters() throws Exception {
        server.enqueue(new MockResponse().setBody("[]").setResponseCode(200));

        client.listMemories("Entity", 10, 5, "ns1");

        RecordedRequest req = server.takeRequest();
        assertEquals("ns1", req.getHeader("X-Ucotron-Namespace"));
        String path = req.getPath();
        assertTrue(path.contains("node_type=Entity"));
        assertTrue(path.contains("limit=10"));
        assertTrue(path.contains("offset=5"));
    }

    // -----------------------------------------------------------------------
    // Update memory
    // -----------------------------------------------------------------------

    @Test
    void testUpdateMemory() throws Exception {
        String json = "{\"id\":42,\"content\":\"updated\",\"node_type\":\"Entity\"," +
                "\"timestamp\":1000000,\"metadata\":{}}";

        server.enqueue(new MockResponse().setBody(json).setResponseCode(200));

        MemoryResponse result = client.updateMemory(42, "updated");
        assertEquals("updated", result.getContent());

        RecordedRequest req = server.takeRequest();
        assertEquals("PUT", req.getMethod());
        assertEquals("/api/v1/memories/42", req.getPath());
        assertTrue(req.getBody().readUtf8().contains("\"content\":\"updated\""));
    }

    // -----------------------------------------------------------------------
    // Delete memory
    // -----------------------------------------------------------------------

    @Test
    void testDeleteMemory() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));

        client.deleteMemory(42);

        RecordedRequest req = server.takeRequest();
        assertEquals("DELETE", req.getMethod());
        assertEquals("/api/v1/memories/42", req.getPath());
    }

    @Test
    void testDeleteMemoryWithNamespace() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));

        client.deleteMemory(42, "custom-ns");

        RecordedRequest req = server.takeRequest();
        assertEquals("custom-ns", req.getHeader("X-Ucotron-Namespace"));
    }

    // -----------------------------------------------------------------------
    // Search
    // -----------------------------------------------------------------------

    @Test
    void testSearch() throws Exception {
        String json = "{\"results\":[{\"id\":1,\"content\":\"hello\",\"node_type\":\"Entity\"," +
                "\"score\":0.95,\"vector_sim\":0.9,\"graph_centrality\":0.5,\"recency\":0.8}]," +
                "\"total\":1,\"query\":\"test\"}";

        server.enqueue(new MockResponse().setBody(json).setResponseCode(200));

        SearchResult result = client.search("test");
        assertEquals(1, result.getTotal());
        assertEquals("test", result.getQuery());
        assertEquals(1, result.getResults().size());
        assertEquals(0.95f, result.getResults().get(0).getScore(), 0.01);

        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertEquals("/api/v1/memories/search", req.getPath());
        assertTrue(req.getBody().readUtf8().contains("\"query\":\"test\""));
    }

    @Test
    void testSearchWithOptions() throws Exception {
        String json = "{\"results\":[],\"total\":0,\"query\":\"q\"}";

        server.enqueue(new MockResponse().setBody(json).setResponseCode(200));

        client.search("q", 5, "Entity", "ns1");

        RecordedRequest req = server.takeRequest();
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("\"limit\":5"));
        assertTrue(body.contains("\"node_type\":\"Entity\""));
        assertEquals("ns1", req.getHeader("X-Ucotron-Namespace"));
    }

    // -----------------------------------------------------------------------
    // Entities
    // -----------------------------------------------------------------------

    @Test
    void testGetEntity() throws Exception {
        String json = "{\"id\":42,\"content\":\"Apple Inc.\",\"node_type\":\"Entity\"," +
                "\"timestamp\":1000000,\"metadata\":{},\"neighbors\":[" +
                "{\"node_id\":43,\"content\":\"Tim Cook\",\"edge_type\":\"RELATES_TO\",\"weight\":0.9}]}";

        server.enqueue(new MockResponse().setBody(json).setResponseCode(200));

        EntityResponse entity = client.getEntity(42);
        assertEquals(42, entity.getId());
        assertEquals("Apple Inc.", entity.getContent());
        assertEquals(1, entity.getNeighbors().size());
        assertEquals("Tim Cook", entity.getNeighbors().get(0).getContent());

        RecordedRequest req = server.takeRequest();
        assertEquals("GET", req.getMethod());
        assertEquals("/api/v1/entities/42", req.getPath());
    }

    @Test
    void testListEntities() throws Exception {
        String json = "[{\"id\":1,\"content\":\"Entity A\",\"node_type\":\"Entity\"," +
                "\"timestamp\":100,\"metadata\":{}}]";

        server.enqueue(new MockResponse().setBody(json).setResponseCode(200));

        List<EntityResponse> entities = client.listEntities();
        assertEquals(1, entities.size());
        assertEquals("Entity A", entities.get(0).getContent());

        RecordedRequest req = server.takeRequest();
        assertEquals("GET", req.getMethod());
        assertEquals("/api/v1/entities", req.getPath());
    }

    @Test
    void testListEntitiesWithFilters() throws Exception {
        server.enqueue(new MockResponse().setBody("[]").setResponseCode(200));

        client.listEntities(10, 5, "ns1");

        RecordedRequest req = server.takeRequest();
        String path = req.getPath();
        assertTrue(path.contains("limit=10"));
        assertTrue(path.contains("offset=5"));
        assertEquals("ns1", req.getHeader("X-Ucotron-Namespace"));
    }

    // -----------------------------------------------------------------------
    // Augment
    // -----------------------------------------------------------------------

    @Test
    void testAugment() throws Exception {
        String json = "{\"memories\":[],\"entities\":[],\"context_text\":\"relevant context\"}";

        server.enqueue(new MockResponse().setBody(json).setResponseCode(200));

        AugmentResult result = client.augment("some context");
        assertEquals("relevant context", result.getContextText());
        assertTrue(result.getMemories().isEmpty());

        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertEquals("/api/v1/augment", req.getPath());
        assertTrue(req.getBody().readUtf8().contains("\"context\":\"some context\""));
    }

    @Test
    void testAugmentWithOptions() throws Exception {
        String json = "{\"memories\":[],\"entities\":[],\"context_text\":\"\"}";

        server.enqueue(new MockResponse().setBody(json).setResponseCode(200));

        client.augment("ctx", 5, "ns1");

        RecordedRequest req = server.takeRequest();
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("\"limit\":5"));
        assertEquals("ns1", req.getHeader("X-Ucotron-Namespace"));
    }

    // -----------------------------------------------------------------------
    // Learn
    // -----------------------------------------------------------------------

    @Test
    void testLearn() throws Exception {
        String json = "{\"memories_created\":3,\"entities_found\":2,\"conflicts_found\":1}";

        server.enqueue(new MockResponse().setBody(json).setResponseCode(200));

        LearnResult result = client.learn("agent output text");
        assertEquals(3, result.getMemoriesCreated());
        assertEquals(2, result.getEntitiesFound());
        assertEquals(1, result.getConflictsFound());

        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertEquals("/api/v1/learn", req.getPath());
        assertTrue(req.getBody().readUtf8().contains("\"output\":\"agent output text\""));
    }

    @Test
    void testLearnWithMetadata() throws Exception {
        String json = "{\"memories_created\":1,\"entities_found\":0,\"conflicts_found\":0}";

        server.enqueue(new MockResponse().setBody(json).setResponseCode(200));

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("agent", "gpt-4");
        client.learn("output", metadata, "ns1");

        RecordedRequest req = server.takeRequest();
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("\"agent\":\"gpt-4\""));
        assertEquals("ns1", req.getHeader("X-Ucotron-Namespace"));
    }

    // -----------------------------------------------------------------------
    // Namespace handling
    // -----------------------------------------------------------------------

    @Test
    void testDefaultNamespaceHeader() throws Exception {
        ClientConfig config = ClientConfig.builder()
                .defaultNamespace("default-ns")
                .build();
        UcotronClient nsClient = new UcotronClient(server.url("/").toString(), config);

        server.enqueue(new MockResponse().setBody("{\"status\":\"ok\",\"version\":\"0.1.0\"," +
                "\"instance_id\":\"a\",\"instance_role\":\"writer\",\"storage_mode\":\"embedded\"," +
                "\"vector_backend\":\"helix\",\"graph_backend\":\"helix\"," +
                "\"models\":{\"embedder_loaded\":false,\"embedding_model\":\"\"," +
                "\"ner_loaded\":false,\"relation_extractor_loaded\":false,\"transcriber_loaded\":false}}")
                .setResponseCode(200));

        nsClient.health();

        RecordedRequest req = server.takeRequest();
        assertEquals("default-ns", req.getHeader("X-Ucotron-Namespace"));
    }

    @Test
    void testPerRequestNamespaceOverridesDefault() throws Exception {
        ClientConfig config = ClientConfig.builder()
                .defaultNamespace("default-ns")
                .build();
        UcotronClient nsClient = new UcotronClient(server.url("/").toString(), config);

        server.enqueue(new MockResponse().setBody("{\"id\":1,\"content\":\"a\",\"node_type\":\"Entity\"," +
                "\"timestamp\":100,\"metadata\":{}}").setResponseCode(200));

        nsClient.getMemory(1, "override-ns");

        RecordedRequest req = server.takeRequest();
        assertEquals("override-ns", req.getHeader("X-Ucotron-Namespace"));
    }

    @Test
    void testNoNamespaceWhenNotSet() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"status\":\"ok\",\"version\":\"0.1.0\"," +
                "\"instance_id\":\"a\",\"instance_role\":\"writer\",\"storage_mode\":\"embedded\"," +
                "\"vector_backend\":\"helix\",\"graph_backend\":\"helix\"," +
                "\"models\":{\"embedder_loaded\":false,\"embedding_model\":\"\"," +
                "\"ner_loaded\":false,\"relation_extractor_loaded\":false,\"transcriber_loaded\":false}}")
                .setResponseCode(200));

        client.health();

        RecordedRequest req = server.takeRequest();
        assertNull(req.getHeader("X-Ucotron-Namespace"));
    }

    // -----------------------------------------------------------------------
    // API key authentication
    // -----------------------------------------------------------------------

    @Test
    void testApiKeyHeader() throws Exception {
        ClientConfig config = ClientConfig.builder()
                .apiKey("my-secret-key")
                .build();
        UcotronClient authClient = new UcotronClient(server.url("/").toString(), config);

        server.enqueue(new MockResponse().setBody("{\"status\":\"ok\",\"version\":\"0.1.0\"," +
                "\"instance_id\":\"a\",\"instance_role\":\"writer\",\"storage_mode\":\"embedded\"," +
                "\"vector_backend\":\"helix\",\"graph_backend\":\"helix\"," +
                "\"models\":{\"embedder_loaded\":false,\"embedding_model\":\"\"," +
                "\"ner_loaded\":false,\"relation_extractor_loaded\":false,\"transcriber_loaded\":false}}")
                .setResponseCode(200));

        authClient.health();

        RecordedRequest req = server.takeRequest();
        assertEquals("Bearer my-secret-key", req.getHeader("Authorization"));
    }

    // -----------------------------------------------------------------------
    // Error handling
    // -----------------------------------------------------------------------

    @Test
    void testServerError4xxNotRetried() {
        server.enqueue(new MockResponse().setBody("Not Found").setResponseCode(404));

        UcotronServerException ex = assertThrows(UcotronServerException.class, () -> {
            client.getMemory(999);
        });
        assertEquals(404, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("404"));
        // Only 1 request — no retries on 4xx
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void testServerError5xxRetried() {
        ClientConfig config = ClientConfig.builder()
                .retryConfig(new RetryConfig(2, Duration.ofMillis(10), Duration.ofMillis(50)))
                .build();
        UcotronClient retryClient = new UcotronClient(server.url("/").toString(), config);

        // 3 attempts total (1 original + 2 retries), all fail with 500
        server.enqueue(new MockResponse().setBody("error").setResponseCode(500));
        server.enqueue(new MockResponse().setBody("error").setResponseCode(500));
        server.enqueue(new MockResponse().setBody("error").setResponseCode(500));

        UcotronRetriesExhaustedException ex = assertThrows(UcotronRetriesExhaustedException.class, () -> {
            retryClient.health();
        });
        assertEquals(3, ex.getAttempts());
        assertEquals(3, server.getRequestCount());
    }

    @Test
    void testServerError5xxRetriedThenSucceeds() throws Exception {
        ClientConfig config = ClientConfig.builder()
                .retryConfig(new RetryConfig(2, Duration.ofMillis(10), Duration.ofMillis(50)))
                .build();
        UcotronClient retryClient = new UcotronClient(server.url("/").toString(), config);

        // First attempt fails, second succeeds
        server.enqueue(new MockResponse().setBody("error").setResponseCode(500));
        server.enqueue(new MockResponse().setBody("{\"instance_id\":\"test\",\"total_requests\":0," +
                "\"total_ingestions\":0,\"total_searches\":0,\"uptime_secs\":0}").setResponseCode(200));

        MetricsResponse metrics = retryClient.metrics();
        assertEquals("test", metrics.getInstanceId());
        assertEquals(2, server.getRequestCount());
    }

    // -----------------------------------------------------------------------
    // Exception hierarchy
    // -----------------------------------------------------------------------

    @Test
    void testExceptionHierarchy() {
        UcotronServerException serverEx = new UcotronServerException(500, "Internal Server Error");
        assertTrue(serverEx instanceof UcotronException);
        assertEquals(500, serverEx.getStatusCode());
        assertTrue(serverEx.getMessage().contains("500"));

        UcotronConnectionException connEx = new UcotronConnectionException("Connection refused", new RuntimeException());
        assertTrue(connEx instanceof UcotronException);

        UcotronRetriesExhaustedException retryEx = new UcotronRetriesExhaustedException(3, new RuntimeException());
        assertTrue(retryEx instanceof UcotronException);
        assertEquals(3, retryEx.getAttempts());
    }

    // -----------------------------------------------------------------------
    // Gson deserialization
    // -----------------------------------------------------------------------

    @Test
    void testMemoryResponseDeserialization() {
        String json = "{\"id\":1,\"content\":\"test\",\"node_type\":\"Entity\"," +
                "\"timestamp\":12345,\"metadata\":{\"key\":\"value\"}}";

        Gson gson = new Gson();
        MemoryResponse memory = gson.fromJson(json, MemoryResponse.class);
        assertEquals(1, memory.getId());
        assertEquals("test", memory.getContent());
        assertEquals("Entity", memory.getNodeType());
        assertEquals("value", memory.getMetadata().get("key"));
    }

    @Test
    void testLearnResultDeserialization() {
        String json = "{\"memories_created\":3,\"entities_found\":2,\"conflicts_found\":1}";

        Gson gson = new Gson();
        LearnResult result = gson.fromJson(json, LearnResult.class);
        assertEquals(3, result.getMemoriesCreated());
        assertEquals(2, result.getEntitiesFound());
        assertEquals(1, result.getConflictsFound());
    }

    @Test
    void testAugmentResultDeserialization() {
        String json = "{\"memories\":[],\"entities\":[],\"context_text\":\"ctx\"}";

        Gson gson = new Gson();
        AugmentResult result = gson.fromJson(json, AugmentResult.class);
        assertEquals("ctx", result.getContextText());
        assertTrue(result.getMemories().isEmpty());
    }
}
