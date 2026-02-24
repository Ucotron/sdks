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
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class UcotronAsyncTest {
    private MockWebServer server;
    private UcotronAsync asyncClient;
    private Gson gson;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        asyncClient = new UcotronAsync(server.url("/").toString());
        gson = new Gson();
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
        UcotronAsync a = new UcotronAsync("http://localhost:8420");
        assertNotNull(a.getSyncClient());
        assertNotNull(a.getExecutor());
    }

    @Test
    void testConstructorWithConfig() {
        ClientConfig config = ClientConfig.builder()
                .defaultNamespace("test-ns")
                .build();
        UcotronAsync a = new UcotronAsync("http://localhost:8420", config);
        assertEquals("test-ns", a.getSyncClient().getConfig().getDefaultNamespace());
    }

    @Test
    void testConstructorWithCustomExecutor() {
        ExecutorService customExecutor = Executors.newFixedThreadPool(2);
        try {
            UcotronAsync a = new UcotronAsync("http://localhost:8420",
                    ClientConfig.builder().build(), customExecutor);
            assertSame(customExecutor, a.getExecutor());
        } finally {
            customExecutor.shutdown();
        }
    }

    @Test
    void testConstructorWithExistingSyncClient() {
        UcotronClient syncClient = new UcotronClient("http://localhost:8420");
        ExecutorService customExecutor = Executors.newSingleThreadExecutor();
        try {
            UcotronAsync a = new UcotronAsync(syncClient, customExecutor);
            assertSame(syncClient, a.getSyncClient());
            assertSame(customExecutor, a.getExecutor());
        } finally {
            customExecutor.shutdown();
        }
    }

    // -----------------------------------------------------------------------
    // Health & Metrics
    // -----------------------------------------------------------------------

    @Test
    void testHealthAsync() throws Exception {
        String json = "{\"status\":\"ok\",\"version\":\"0.1.0\",\"instance_id\":\"inst1\"}";
        server.enqueue(new MockResponse().setBody(json));

        HealthResponse result = asyncClient.healthAsync().get(5, TimeUnit.SECONDS);
        assertEquals("ok", result.getStatus());
        assertEquals("0.1.0", result.getVersion());

        RecordedRequest req = server.takeRequest();
        assertEquals("GET", req.getMethod());
        assertTrue(req.getPath().endsWith("/api/v1/health"));
    }

    @Test
    void testMetricsAsync() throws Exception {
        String json = "{\"instance_id\":\"inst1\",\"total_requests\":42,\"total_ingestions\":10,"
                + "\"total_searches\":5,\"uptime_secs\":3600}";
        server.enqueue(new MockResponse().setBody(json));

        MetricsResponse result = asyncClient.metricsAsync().get(5, TimeUnit.SECONDS);
        assertEquals(42, result.getTotalRequests());
        assertEquals(3600, result.getUptimeSecs());
    }

    // -----------------------------------------------------------------------
    // Memories CRUD
    // -----------------------------------------------------------------------

    @Test
    void testAddMemoryAsync() throws Exception {
        String json = "{\"chunk_node_ids\":[100],\"entity_node_ids\":[200],\"edges_created\":1}";
        server.enqueue(new MockResponse().setBody(json));

        CreateMemoryResult result = asyncClient.addMemoryAsync("hello world")
                .get(5, TimeUnit.SECONDS);
        assertEquals(1, result.getChunkNodeIds().size());
        assertEquals(100L, result.getChunkNodeIds().get(0));

        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertTrue(req.getBody().readUtf8().contains("hello world"));
    }

    @Test
    void testAddMemoryAsyncWithNamespace() throws Exception {
        String json = "{\"chunk_node_ids\":[101],\"entity_node_ids\":[],\"edges_created\":0}";
        server.enqueue(new MockResponse().setBody(json));

        asyncClient.addMemoryAsync("text", "custom-ns").get(5, TimeUnit.SECONDS);

        RecordedRequest req = server.takeRequest();
        assertEquals("custom-ns", req.getHeader("X-Ucotron-Namespace"));
    }

    @Test
    void testAddMemoryAsyncWithMetadata() throws Exception {
        String json = "{\"chunk_node_ids\":[102],\"entity_node_ids\":[],\"edges_created\":0}";
        server.enqueue(new MockResponse().setBody(json));

        Map<String, Object> meta = new HashMap<>();
        meta.put("source", "test");
        asyncClient.addMemoryAsync("text", null, meta).get(5, TimeUnit.SECONDS);

        RecordedRequest req = server.takeRequest();
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("\"source\""));
    }

    @Test
    void testGetMemoryAsync() throws Exception {
        String json = "{\"id\":42,\"content\":\"hello\",\"node_type\":\"Entity\",\"timestamp\":1000}";
        server.enqueue(new MockResponse().setBody(json));

        MemoryResponse result = asyncClient.getMemoryAsync(42).get(5, TimeUnit.SECONDS);
        assertEquals(42, result.getId());
        assertEquals("hello", result.getContent());
    }

    @Test
    void testGetMemoryAsyncWithNamespace() throws Exception {
        String json = "{\"id\":42,\"content\":\"hello\",\"node_type\":\"Entity\",\"timestamp\":1000}";
        server.enqueue(new MockResponse().setBody(json));

        asyncClient.getMemoryAsync(42, "ns1").get(5, TimeUnit.SECONDS);

        RecordedRequest req = server.takeRequest();
        assertEquals("ns1", req.getHeader("X-Ucotron-Namespace"));
    }

    @Test
    void testListMemoriesAsync() throws Exception {
        String json = "[{\"id\":1,\"content\":\"a\",\"node_type\":\"Entity\",\"timestamp\":100}]";
        server.enqueue(new MockResponse().setBody(json));

        List<MemoryResponse> result = asyncClient.listMemoriesAsync().get(5, TimeUnit.SECONDS);
        assertEquals(1, result.size());
        assertEquals("a", result.get(0).getContent());
    }

    @Test
    void testListMemoriesAsyncWithFilters() throws Exception {
        String json = "[]";
        server.enqueue(new MockResponse().setBody(json));

        asyncClient.listMemoriesAsync("Entity", 10, 0, "ns1").get(5, TimeUnit.SECONDS);

        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().contains("node_type=Entity"));
        assertTrue(req.getPath().contains("limit=10"));
    }

    @Test
    void testUpdateMemoryAsync() throws Exception {
        String json = "{\"id\":42,\"content\":\"updated\",\"node_type\":\"Entity\",\"timestamp\":1000}";
        server.enqueue(new MockResponse().setBody(json));

        MemoryResponse result = asyncClient.updateMemoryAsync(42, "updated")
                .get(5, TimeUnit.SECONDS);
        assertEquals("updated", result.getContent());

        RecordedRequest req = server.takeRequest();
        assertEquals("PUT", req.getMethod());
    }

    @Test
    void testUpdateMemoryAsyncWithMetadataAndNamespace() throws Exception {
        String json = "{\"id\":42,\"content\":\"updated\",\"node_type\":\"Entity\",\"timestamp\":1000}";
        server.enqueue(new MockResponse().setBody(json));

        Map<String, Object> meta = new HashMap<>();
        meta.put("key", "value");
        asyncClient.updateMemoryAsync(42, "updated", meta, "ns2").get(5, TimeUnit.SECONDS);

        RecordedRequest req = server.takeRequest();
        assertEquals("ns2", req.getHeader("X-Ucotron-Namespace"));
    }

    @Test
    void testDeleteMemoryAsync() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));

        asyncClient.deleteMemoryAsync(42).get(5, TimeUnit.SECONDS);

        RecordedRequest req = server.takeRequest();
        assertEquals("DELETE", req.getMethod());
        assertTrue(req.getPath().endsWith("/api/v1/memories/42"));
    }

    @Test
    void testDeleteMemoryAsyncWithNamespace() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));

        asyncClient.deleteMemoryAsync(42, "ns1").get(5, TimeUnit.SECONDS);

        RecordedRequest req = server.takeRequest();
        assertEquals("ns1", req.getHeader("X-Ucotron-Namespace"));
    }

    // -----------------------------------------------------------------------
    // Search (acceptance: searchAsync().get() returns correct results)
    // -----------------------------------------------------------------------

    @Test
    void testSearchAsync() throws Exception {
        String json = "{\"results\":[{\"id\":1,\"content\":\"found\",\"node_type\":\"Entity\","
                + "\"score\":0.95,\"vector_sim\":0.9,\"graph_centrality\":0.5,\"recency\":0.8}],"
                + "\"total\":1,\"query\":\"test\"}";
        server.enqueue(new MockResponse().setBody(json));

        SearchResult result = asyncClient.searchAsync("test").get(5, TimeUnit.SECONDS);
        assertEquals(1, result.getTotal());
        assertEquals("test", result.getQuery());
        assertEquals(1, result.getResults().size());
        assertEquals(0.95f, result.getResults().get(0).getScore(), 0.01f);

        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertTrue(req.getBody().readUtf8().contains("\"query\":\"test\""));
    }

    @Test
    void testSearchAsyncWithOptions() throws Exception {
        String json = "{\"results\":[],\"total\":0,\"query\":\"test\"}";
        server.enqueue(new MockResponse().setBody(json));

        asyncClient.searchAsync("test", 5, "Entity", "ns1").get(5, TimeUnit.SECONDS);

        RecordedRequest req = server.takeRequest();
        assertEquals("ns1", req.getHeader("X-Ucotron-Namespace"));
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("\"limit\":5"));
        assertTrue(body.contains("\"node_type\":\"Entity\""));
    }

    // -----------------------------------------------------------------------
    // Entities
    // -----------------------------------------------------------------------

    @Test
    void testGetEntityAsync() throws Exception {
        String json = "{\"id\":10,\"content\":\"entity\",\"node_type\":\"Entity\",\"timestamp\":500}";
        server.enqueue(new MockResponse().setBody(json));

        EntityResponse result = asyncClient.getEntityAsync(10).get(5, TimeUnit.SECONDS);
        assertEquals(10, result.getId());
        assertEquals("entity", result.getContent());
    }

    @Test
    void testGetEntityAsyncWithNamespace() throws Exception {
        String json = "{\"id\":10,\"content\":\"entity\",\"node_type\":\"Entity\",\"timestamp\":500}";
        server.enqueue(new MockResponse().setBody(json));

        asyncClient.getEntityAsync(10, "ns1").get(5, TimeUnit.SECONDS);
        assertEquals("ns1", server.takeRequest().getHeader("X-Ucotron-Namespace"));
    }

    @Test
    void testListEntitiesAsync() throws Exception {
        String json = "[]";
        server.enqueue(new MockResponse().setBody(json));

        List<EntityResponse> result = asyncClient.listEntitiesAsync().get(5, TimeUnit.SECONDS);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testListEntitiesAsyncWithFilters() throws Exception {
        String json = "[]";
        server.enqueue(new MockResponse().setBody(json));

        asyncClient.listEntitiesAsync(10, 5, "ns1").get(5, TimeUnit.SECONDS);

        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().contains("limit=10"));
        assertTrue(req.getPath().contains("offset=5"));
    }

    // -----------------------------------------------------------------------
    // Augment & Learn
    // -----------------------------------------------------------------------

    @Test
    void testAugmentAsync() throws Exception {
        String json = "{\"memories\":[],\"entities\":[],\"context_text\":\"augmented\"}";
        server.enqueue(new MockResponse().setBody(json));

        AugmentResult result = asyncClient.augmentAsync("context").get(5, TimeUnit.SECONDS);
        assertEquals("augmented", result.getContextText());

        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
    }

    @Test
    void testAugmentAsyncWithOptions() throws Exception {
        String json = "{\"memories\":[],\"entities\":[],\"context_text\":\"augmented\"}";
        server.enqueue(new MockResponse().setBody(json));

        asyncClient.augmentAsync("context", 5, "ns1").get(5, TimeUnit.SECONDS);

        RecordedRequest req = server.takeRequest();
        assertEquals("ns1", req.getHeader("X-Ucotron-Namespace"));
    }

    @Test
    void testLearnAsync() throws Exception {
        String json = "{\"memories_created\":2,\"entities_found\":1,\"conflicts_found\":0}";
        server.enqueue(new MockResponse().setBody(json));

        LearnResult result = asyncClient.learnAsync("output text").get(5, TimeUnit.SECONDS);
        assertEquals(2, result.getMemoriesCreated());
        assertEquals(1, result.getEntitiesFound());
    }

    @Test
    void testLearnAsyncWithOptions() throws Exception {
        String json = "{\"memories_created\":1,\"entities_found\":0,\"conflicts_found\":0}";
        server.enqueue(new MockResponse().setBody(json));

        Map<String, Object> meta = new HashMap<>();
        meta.put("source", "agent");
        asyncClient.learnAsync("output", meta, "ns1").get(5, TimeUnit.SECONDS);

        RecordedRequest req = server.takeRequest();
        assertEquals("ns1", req.getHeader("X-Ucotron-Namespace"));
    }

    // -----------------------------------------------------------------------
    // Error handling in async context
    // -----------------------------------------------------------------------

    @Test
    void testAsyncServerErrorPropagates() {
        server.enqueue(new MockResponse().setResponseCode(404).setBody("not found"));

        ExecutionException ex = assertThrows(ExecutionException.class, () ->
                asyncClient.healthAsync().get(5, TimeUnit.SECONDS));

        Throwable cause = ex.getCause();
        assertInstanceOf(UcotronServerException.class, cause);
        assertEquals(404, ((UcotronServerException) cause).getStatusCode());
    }

    @Test
    void testAsync5xxRetriesAndFails() {
        // 3 retries + 1 initial = 4 attempts total, all fail
        ClientConfig config = ClientConfig.builder()
                .retryConfig(new RetryConfig(3, java.time.Duration.ofMillis(10),
                        java.time.Duration.ofMillis(50)))
                .build();
        UcotronAsync retryClient = new UcotronAsync(server.url("/").toString(), config);

        for (int i = 0; i < 4; i++) {
            server.enqueue(new MockResponse().setResponseCode(500).setBody("error"));
        }

        ExecutionException ex = assertThrows(ExecutionException.class, () ->
                retryClient.healthAsync().get(10, TimeUnit.SECONDS));

        assertInstanceOf(UcotronRetriesExhaustedException.class, ex.getCause());
    }

    // -----------------------------------------------------------------------
    // Custom executor tests
    // -----------------------------------------------------------------------

    @Test
    void testCustomExecutorIsUsed() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);
        Executor countingExecutor = runnable -> {
            callCount.incrementAndGet();
            ForkJoinPool.commonPool().execute(runnable);
        };

        UcotronAsync customClient = new UcotronAsync(
                new UcotronClient(server.url("/").toString()), countingExecutor);

        server.enqueue(new MockResponse().setBody("{\"status\":\"ok\"}"));
        customClient.healthAsync().get(5, TimeUnit.SECONDS);

        assertEquals(1, callCount.get());
    }

    @Test
    void testConcurrentAsyncCalls() throws Exception {
        // Enqueue 5 responses
        for (int i = 0; i < 5; i++) {
            server.enqueue(new MockResponse().setBody(
                    "{\"status\":\"ok\",\"version\":\"0.1.0\"}"));
        }

        // Fire 5 concurrent health checks
        List<CompletableFuture<HealthResponse>> futures = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            futures.add(asyncClient.healthAsync());
        }

        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(10, TimeUnit.SECONDS);

        // All should succeed
        for (CompletableFuture<HealthResponse> future : futures) {
            assertEquals("ok", future.get().getStatus());
        }
    }
}
