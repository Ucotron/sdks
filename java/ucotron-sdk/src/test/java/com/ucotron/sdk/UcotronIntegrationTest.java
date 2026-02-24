package com.ucotron.sdk;

import com.ucotron.sdk.types.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests that run against a live Ucotron server.
 * <p>
 * Set the UCOTRON_TEST_SERVER_URL environment variable to the server URL
 * (e.g., "http://127.0.0.1:8420") to enable these tests. If the env var
 * is not set, all tests in this class are skipped.
 */
@EnabledIfEnvironmentVariable(named = "UCOTRON_TEST_SERVER_URL", matches = ".+")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UcotronIntegrationTest {

    private static String serverUrl;
    private static UcotronClient client;
    private static UcotronAsync asyncClient;

    /** Unique namespace per JVM run to isolate test data. */
    private static final String TEST_NS = "java_integ_" + ProcessHandle.current().pid();
    private static final String TEST_NS_B = "java_integ_b_" + ProcessHandle.current().pid();

    @BeforeAll
    static void setup() {
        serverUrl = System.getenv("UCOTRON_TEST_SERVER_URL");
        assertNotNull(serverUrl, "UCOTRON_TEST_SERVER_URL must be set");

        ClientConfig config = ClientConfig.builder()
                .defaultNamespace(TEST_NS)
                .timeout(Duration.ofSeconds(30))
                .build();
        client = new UcotronClient(serverUrl, config);
        asyncClient = new UcotronAsync(serverUrl, config);
    }

    // -----------------------------------------------------------------------
    // 1. Health check
    // -----------------------------------------------------------------------

    @Test
    @Order(1)
    void testHealth() throws UcotronException {
        HealthResponse health = client.health();
        assertNotNull(health);
        assertEquals("ok", health.getStatus());
        assertNotNull(health.getVersion());
        assertNotNull(health.getInstanceId());
        assertNotNull(health.getInstanceRole());
        assertNotNull(health.getStorageMode());
    }

    // -----------------------------------------------------------------------
    // 2. Metrics
    // -----------------------------------------------------------------------

    @Test
    @Order(2)
    void testMetrics() throws UcotronException {
        MetricsResponse metrics = client.metrics();
        assertNotNull(metrics);
        assertNotNull(metrics.getInstanceId());
        assertTrue(metrics.getTotalRequests() >= 0);
        assertTrue(metrics.getUptimeSecs() >= 0);
    }

    // -----------------------------------------------------------------------
    // 3. Add memory
    // -----------------------------------------------------------------------

    @Test
    @Order(3)
    void testAddMemory() throws UcotronException {
        CreateMemoryResult result = client.addMemory(
                "Java SDK test: The Eiffel Tower is located in Paris, France.");
        assertNotNull(result);
        assertNotNull(result.getChunkNodeIds());
        assertNotNull(result.getEntityNodeIds());
        assertTrue(result.getEdgesCreated() >= 0);
        assertNotNull(result.getMetrics());
    }

    // -----------------------------------------------------------------------
    // 4. Add memory with metadata
    // -----------------------------------------------------------------------

    @Test
    @Order(4)
    void testAddMemoryWithMetadata() throws UcotronException {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "java-sdk-test");
        metadata.put("priority", 1);

        CreateMemoryResult result = client.addMemory(
                "Java SDK test: Tokyo is the capital of Japan and has a population of over 13 million.",
                TEST_NS,
                metadata);
        assertNotNull(result);
        assertNotNull(result.getChunkNodeIds());
        assertFalse(result.getChunkNodeIds().isEmpty());
    }

    // -----------------------------------------------------------------------
    // 5. Search
    // -----------------------------------------------------------------------

    @Test
    @Order(5)
    void testSearch() throws UcotronException {
        SearchResult result = client.search("Eiffel Tower Paris");
        assertNotNull(result);
        assertNotNull(result.getResults());
        assertNotNull(result.getQuery());
        assertTrue(result.getTotal() >= 0);
    }

    // -----------------------------------------------------------------------
    // 6. Augment
    // -----------------------------------------------------------------------

    @Test
    @Order(6)
    void testAugment() throws UcotronException {
        AugmentResult result = client.augment("Tell me about landmarks in Europe");
        assertNotNull(result);
        assertNotNull(result.getMemories());
        assertNotNull(result.getEntities());
        assertNotNull(result.getContextText());
    }

    // -----------------------------------------------------------------------
    // 7. Learn
    // -----------------------------------------------------------------------

    @Test
    @Order(7)
    void testLearn() throws UcotronException {
        LearnResult result = client.learn(
                "The user prefers IntelliJ IDEA and writes Java code professionally.");
        assertNotNull(result);
        assertTrue(result.getMemoriesCreated() >= 0);
        assertTrue(result.getEntitiesFound() >= 0);
        assertTrue(result.getConflictsFound() >= 0);
    }

    // -----------------------------------------------------------------------
    // 8. List memories
    // -----------------------------------------------------------------------

    @Test
    @Order(8)
    void testListMemories() throws UcotronException {
        List<MemoryResponse> memories = client.listMemories();
        assertNotNull(memories);
        // We should have at least the memories we added in earlier tests
    }

    // -----------------------------------------------------------------------
    // 9. List entities
    // -----------------------------------------------------------------------

    @Test
    @Order(9)
    void testListEntities() throws UcotronException {
        List<EntityResponse> entities = client.listEntities();
        assertNotNull(entities);
    }

    // -----------------------------------------------------------------------
    // 10. Full flow: addMemory → search → augment
    // -----------------------------------------------------------------------

    @Test
    @Order(10)
    void testFullFlow() throws UcotronException {
        // Step 1: Ingest a specific memory
        CreateMemoryResult ingestResult = client.addMemory(
                "Java full-flow test: Mount Fuji is the tallest mountain in Japan at 3776 meters.");
        assertNotNull(ingestResult);
        assertNotNull(ingestResult.getChunkNodeIds());

        // Step 2: Search for the ingested content
        SearchResult searchResult = client.search("Mount Fuji Japan");
        assertNotNull(searchResult);
        assertNotNull(searchResult.getResults());

        // Step 3: Augment with related context
        AugmentResult augmentResult = client.augment("What is the tallest mountain in Japan?");
        assertNotNull(augmentResult);
        assertNotNull(augmentResult.getContextText());
    }

    // -----------------------------------------------------------------------
    // 11. Multi-tenancy isolation
    // -----------------------------------------------------------------------

    @Test
    @Order(11)
    void testMultiTenancyIsolation() throws UcotronException {
        // Add memory to namespace A (default TEST_NS)
        CreateMemoryResult resultA = client.addMemory(
                "Multi-tenancy test NS-A: The Amazon River is in South America.");
        assertNotNull(resultA);

        // Add memory to namespace B (explicit override)
        CreateMemoryResult resultB = client.addMemory(
                "Multi-tenancy test NS-B: The Nile River is in Africa.",
                TEST_NS_B);
        assertNotNull(resultB);

        // Search in namespace B should not find namespace A content
        ClientConfig configB = ClientConfig.builder()
                .defaultNamespace(TEST_NS_B)
                .timeout(Duration.ofSeconds(30))
                .build();
        UcotronClient clientB = new UcotronClient(serverUrl, configB);
        SearchResult searchB = clientB.search("Amazon River South America");
        assertNotNull(searchB);
        // The search in namespace B should return results only from B
        // (Amazon River was added to namespace A, not B)
        for (SearchResultItem item : searchB.getResults()) {
            assertFalse(item.getContent().contains("Multi-tenancy test NS-A"),
                    "Namespace B search should not return namespace A content");
        }
    }

    // -----------------------------------------------------------------------
    // 12. Async full flow
    // -----------------------------------------------------------------------

    @Test
    @Order(12)
    void testAsyncFullFlow() throws Exception {
        // Async health check
        CompletableFuture<HealthResponse> healthFuture = asyncClient.healthAsync();
        HealthResponse health = healthFuture.get(10, TimeUnit.SECONDS);
        assertNotNull(health);
        assertEquals("ok", health.getStatus());

        // Async add memory
        CompletableFuture<CreateMemoryResult> addFuture = asyncClient.addMemoryAsync(
                "Async Java test: The Great Barrier Reef is located off the coast of Australia.");
        CreateMemoryResult addResult = addFuture.get(30, TimeUnit.SECONDS);
        assertNotNull(addResult);
        assertNotNull(addResult.getChunkNodeIds());

        // Async search
        CompletableFuture<SearchResult> searchFuture = asyncClient.searchAsync("Great Barrier Reef");
        SearchResult searchResult = searchFuture.get(30, TimeUnit.SECONDS);
        assertNotNull(searchResult);
        assertNotNull(searchResult.getResults());

        // Async augment
        CompletableFuture<AugmentResult> augFuture = asyncClient.augmentAsync("coral reefs in Australia");
        AugmentResult augResult = augFuture.get(30, TimeUnit.SECONDS);
        assertNotNull(augResult);
        assertNotNull(augResult.getContextText());
    }

    // -----------------------------------------------------------------------
    // 13. Learn with metadata
    // -----------------------------------------------------------------------

    @Test
    @Order(13)
    void testLearnWithMetadata() throws UcotronException {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("agent", "java-test-agent");
        metadata.put("session_id", "test-session-001");

        LearnResult result = client.learn(
                "The user mentioned they use Gradle for build automation.",
                metadata,
                TEST_NS);
        assertNotNull(result);
        assertTrue(result.getMemoriesCreated() >= 0);
    }

    // -----------------------------------------------------------------------
    // 14. Search with options
    // -----------------------------------------------------------------------

    @Test
    @Order(14)
    void testSearchWithOptions() throws UcotronException {
        SearchResult result = client.search("landmarks", 5, null, TEST_NS);
        assertNotNull(result);
        assertNotNull(result.getResults());
        assertTrue(result.getResults().size() <= 5);
    }
}
