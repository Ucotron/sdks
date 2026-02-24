package com.ucotron.sdk.android

import com.ucotron.sdk.ClientConfig
import com.ucotron.sdk.UcotronServerException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UcotronAndroidTest {

    private lateinit var server: MockWebServer
    private lateinit var ucotron: UcotronAndroid

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val config = ClientConfig.builder()
            .apiKey("test-key")
            .defaultNamespace("test-ns")
            .build()
        ucotron = UcotronAndroid(
            server.url("/").toString(),
            config,
            Dispatchers.Unconfined // Use Unconfined for tests
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `health returns HealthResponse`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody("""{"status":"ok","version":"0.1.0"}""")
                .setHeader("Content-Type", "application/json")
        )

        val health = ucotron.health()
        assertEquals("ok", health.status)
        assertEquals("0.1.0", health.version)

        val request = server.takeRequest()
        assertEquals("/api/v1/health", request.path)
        assertEquals("Bearer test-key", request.getHeader("Authorization"))
    }

    @Test
    fun `metrics returns MetricsResponse`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody("""{"request_count":42}""")
                .setHeader("Content-Type", "application/json")
        )

        val metrics = ucotron.metrics()
        assertNotNull(metrics)

        val request = server.takeRequest()
        assertEquals("/api/v1/metrics", request.path)
    }

    @Test
    fun `addMemory sends POST with text`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody("""{"id":1,"timestamp":1000}""")
                .setHeader("Content-Type", "application/json")
        )

        val result = ucotron.addMemory("hello world")
        assertNotNull(result)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/memories", request.path)
        assertTrue(request.body.readUtf8().contains("hello world"))
    }

    @Test
    fun `addMemory with namespace and metadata`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody("""{"id":2,"timestamp":2000}""")
                .setHeader("Content-Type", "application/json")
        )

        val meta = mapOf<String, Any>("source" to "android")
        val result = ucotron.addMemory("test", "custom-ns", meta)
        assertNotNull(result)

        val request = server.takeRequest()
        assertEquals("custom-ns", request.getHeader("X-Ucotron-Namespace"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("android"))
    }

    @Test
    fun `getMemory returns MemoryResponse`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody("""{"id":1,"content":"hello","node_type":"Entity","timestamp":1000}""")
                .setHeader("Content-Type", "application/json")
        )

        val memory = ucotron.getMemory(1)
        assertNotNull(memory)

        val request = server.takeRequest()
        assertEquals("/api/v1/memories/1", request.path)
        assertEquals("test-ns", request.getHeader("X-Ucotron-Namespace"))
    }

    @Test
    fun `listMemories returns list`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody("""[{"id":1,"content":"a"},{"id":2,"content":"b"}]""")
                .setHeader("Content-Type", "application/json")
        )

        val memories = ucotron.listMemories()
        assertEquals(2, memories.size)
    }

    @Test
    fun `listMemories with filters`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody("""[{"id":1,"content":"a"}]""")
                .setHeader("Content-Type", "application/json")
        )

        val memories = ucotron.listMemories(nodeType = "Entity", limit = 5, offset = 0)
        assertEquals(1, memories.size)

        val request = server.takeRequest()
        assertTrue(request.path!!.contains("node_type=Entity"))
        assertTrue(request.path!!.contains("limit=5"))
    }

    @Test
    fun `updateMemory sends PUT`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody("""{"id":1,"content":"updated"}""")
                .setHeader("Content-Type", "application/json")
        )

        val memory = ucotron.updateMemory(1, "updated")
        assertNotNull(memory)

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/memories/1", request.path)
    }

    @Test
    fun `deleteMemory sends DELETE`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        ucotron.deleteMemory(1)

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/memories/1", request.path)
    }

    @Test
    fun `search sends POST with query`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody("""{"results":[],"total":0,"query":"test"}""")
                .setHeader("Content-Type", "application/json")
        )

        val result = ucotron.search("test")
        assertNotNull(result)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/memories/search", request.path)
        assertTrue(request.body.readUtf8().contains("test"))
    }

    @Test
    fun `search with options`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody("""{"results":[],"total":0,"query":"q"}""")
                .setHeader("Content-Type", "application/json")
        )

        ucotron.search("q", limit = 5, nodeType = "Fact", namespace = "ns2")

        val request = server.takeRequest()
        assertEquals("ns2", request.getHeader("X-Ucotron-Namespace"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"limit\":5"))
    }

    @Test
    fun `getEntity returns EntityResponse`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody("""{"id":10,"node_type":"Entity"}""")
                .setHeader("Content-Type", "application/json")
        )

        val entity = ucotron.getEntity(10)
        assertNotNull(entity)

        val request = server.takeRequest()
        assertEquals("/api/v1/entities/10", request.path)
    }

    @Test
    fun `listEntities with filters`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody("""[{"id":1}]""")
                .setHeader("Content-Type", "application/json")
        )

        val entities = ucotron.listEntities(limit = 10, offset = 0, namespace = "ns3")
        assertEquals(1, entities.size)

        val request = server.takeRequest()
        assertEquals("ns3", request.getHeader("X-Ucotron-Namespace"))
    }

    @Test
    fun `augment sends POST with context`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody("""{"augmented_context":"enriched","memories_used":2}""")
                .setHeader("Content-Type", "application/json")
        )

        val result = ucotron.augment("user is asking about weather")
        assertNotNull(result)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/augment", request.path)
    }

    @Test
    fun `learn sends POST with output`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody("""{"nodes_created":3}""")
                .setHeader("Content-Type", "application/json")
        )

        val result = ucotron.learn("agent responded with facts")
        assertNotNull(result)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/learn", request.path)
    }

    @Test
    fun `server error throws UcotronServerException`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("internal error"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("internal error"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("internal error"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("internal error"))

        assertThrows<Exception> {
            ucotron.health()
        }
    }

    @Test
    fun `client error throws immediately without retry`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("""{"error":"not found"}""")
        )

        assertThrows<UcotronServerException> {
            ucotron.getMemory(999)
        }

        // Only 1 request made (no retry on 4xx)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `syncClient exposes underlying UcotronClient`() {
        assertNotNull(ucotron.syncClient)
        assertEquals("test-key", ucotron.syncClient.config.apiKey)
    }

    @Test
    fun `createUcotronClient factory with defaults`() {
        val client = createUcotronClient("http://localhost:8420")
        assertNotNull(client)
        assertNotNull(client.syncClient)
    }

    @Test
    fun `createUcotronClient factory with all options`() {
        val client = createUcotronClient(
            serverUrl = "http://localhost:8420",
            apiKey = "my-key",
            namespace = "my-ns"
        )
        assertNotNull(client)
        assertEquals("my-key", client.syncClient.config.apiKey)
        assertEquals("my-ns", client.syncClient.config.defaultNamespace)
    }
}
