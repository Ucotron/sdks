package com.ucotron.sdk.android

import com.ucotron.sdk.ClientConfig
import com.ucotron.sdk.RetryConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UcotronSyncWorkerTest {

    private lateinit var server: MockWebServer
    private lateinit var ucotron: UcotronAndroid
    private lateinit var worker: UcotronSyncWorker

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val config = ClientConfig.builder()
            .apiKey("test-key")
            .defaultNamespace("test-ns")
            .retryConfig(RetryConfig(0, Duration.ofMillis(1), Duration.ofMillis(1)))
            .build()
        ucotron = UcotronAndroid(
            server.url("/").toString(),
            config,
            Dispatchers.Unconfined
        )
        worker = UcotronSyncWorker(
            client = ucotron,
            config = SyncConfig(batchSize = 10, maxRetries = 1),
            dispatcher = Dispatchers.Unconfined
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `doSync with empty list returns success`() = runTest {
        val result = worker.doSync(emptyList())
        assertEquals(SyncStatus.SUCCESS, result.status)
        assertEquals(0, result.itemsSynced)
        assertTrue(result.errors.isEmpty())
        assertTrue(result.isSuccess)
    }

    @Test
    fun `doSync syncs single item successfully`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody("""{"chunk_node_ids":[42],"entity_node_ids":[],"edges_created":0}""")
                .setHeader("Content-Type", "application/json")
        )

        val items = listOf(PendingMemory(text = "hello world"))
        val result = worker.doSync(items)

        assertEquals(SyncStatus.SUCCESS, result.status)
        assertEquals(1, result.itemsSynced)
        assertTrue(result.errors.isEmpty())

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/memories", request.path)
        assertTrue(request.body.readUtf8().contains("hello world"))
    }

    @Test
    fun `doSync syncs multiple items`() = runTest {
        repeat(3) {
            server.enqueue(
                MockResponse()
                    .setBody("""{"chunk_node_ids":[${it + 1}],"entity_node_ids":[],"edges_created":0}""")
                    .setHeader("Content-Type", "application/json")
            )
        }

        val items = listOf(
            PendingMemory(text = "item 1"),
            PendingMemory(text = "item 2"),
            PendingMemory(text = "item 3")
        )
        val result = worker.doSync(items)

        assertEquals(SyncStatus.SUCCESS, result.status)
        assertEquals(3, result.itemsSynced)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `doSync respects batchSize limit`() = runTest {
        val smallBatchWorker = UcotronSyncWorker(
            client = ucotron,
            config = SyncConfig(batchSize = 2, maxRetries = 1),
            dispatcher = Dispatchers.Unconfined
        )

        repeat(2) {
            server.enqueue(
                MockResponse()
                    .setBody("""{"id":${it + 1},"timestamp":1000}""")
                    .setHeader("Content-Type", "application/json")
            )
        }

        val items = listOf(
            PendingMemory(text = "item 1"),
            PendingMemory(text = "item 2"),
            PendingMemory(text = "item 3"),
            PendingMemory(text = "item 4")
        )
        val result = smallBatchWorker.doSync(items)

        assertEquals(SyncStatus.SUCCESS, result.status)
        assertEquals(2, result.itemsSynced)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `doSync with namespace and metadata`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody("""{"chunk_node_ids":[1],"entity_node_ids":[],"edges_created":0}""")
                .setHeader("Content-Type", "application/json")
        )

        val items = listOf(
            PendingMemory(
                text = "important note",
                namespace = "custom-ns",
                metadata = mapOf("source" to "android", "priority" to "high")
            )
        )
        val result = worker.doSync(items)

        assertEquals(SyncStatus.SUCCESS, result.status)
        val request = server.takeRequest()
        assertEquals("custom-ns", request.getHeader("X-Ucotron-Namespace"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("android"))
    }

    @Test
    fun `doSync partial success returns PARTIAL status`() = runTest {
        // First item succeeds
        server.enqueue(
            MockResponse()
                .setBody("""{"chunk_node_ids":[1],"entity_node_ids":[],"edges_created":0}""")
                .setHeader("Content-Type", "application/json")
        )
        // Second item fails with server error (no retry since maxRetries=1)
        server.enqueue(MockResponse().setResponseCode(500).setBody("error"))

        val items = listOf(
            PendingMemory(text = "good item"),
            PendingMemory(text = "bad item")
        )
        val result = worker.doSync(items)

        assertEquals(SyncStatus.PARTIAL, result.status)
        assertEquals(1, result.itemsSynced)
        assertEquals(1, result.errors.size)
        assertFalse(result.isSuccess)
    }

    @Test
    fun `doSync all failures returns FAILURE status`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("error"))

        val items = listOf(PendingMemory(text = "bad item"))
        val result = worker.doSync(items)

        assertEquals(SyncStatus.FAILURE, result.status)
        assertEquals(0, result.itemsSynced)
        assertEquals(1, result.errors.size)
    }

    @Test
    fun `doSync calls callback on success`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody("""{"chunk_node_ids":[42],"entity_node_ids":[],"edges_created":0}""")
                .setHeader("Content-Type", "application/json")
        )

        var startCalled = false
        var syncedItem: PendingMemory? = null
        var syncedNodeIds: List<Long>? = null
        var completedResult: SyncResult? = null

        val callback = object : SyncCallback {
            override fun onSyncStart(): Boolean {
                startCalled = true
                return true
            }
            override fun onItemSynced(item: PendingMemory, chunkNodeIds: List<Long>) {
                syncedItem = item
                syncedNodeIds = chunkNodeIds
            }
            override fun onSyncComplete(result: SyncResult) {
                completedResult = result
            }
        }

        val callbackWorker = UcotronSyncWorker(
            client = ucotron,
            config = SyncConfig(maxRetries = 1),
            callback = callback,
            dispatcher = Dispatchers.Unconfined
        )

        val item = PendingMemory(text = "test")
        callbackWorker.doSync(listOf(item))

        assertTrue(startCalled)
        assertEquals(item, syncedItem)
        assertEquals(listOf(42L), syncedNodeIds)
        assertEquals(SyncStatus.SUCCESS, completedResult?.status)
    }

    @Test
    fun `doSync calls callback on failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("error"))

        var failedItem: PendingMemory? = null
        var failedError: String? = null

        val callback = object : SyncCallback {
            override fun onItemFailed(item: PendingMemory, error: String) {
                failedItem = item
                failedError = error
            }
        }

        val callbackWorker = UcotronSyncWorker(
            client = ucotron,
            config = SyncConfig(maxRetries = 1),
            callback = callback,
            dispatcher = Dispatchers.Unconfined
        )

        callbackWorker.doSync(listOf(PendingMemory(text = "fail")))

        assertEquals("fail", failedItem?.text)
        assertTrue(failedError != null)
    }

    @Test
    fun `doSync skips when callback returns false`() = runTest {
        val callback = object : SyncCallback {
            override fun onSyncStart(): Boolean = false
        }

        val callbackWorker = UcotronSyncWorker(
            client = ucotron,
            config = SyncConfig(),
            callback = callback,
            dispatcher = Dispatchers.Unconfined
        )

        val result = callbackWorker.doSync(listOf(PendingMemory(text = "skip me")))

        assertEquals(SyncStatus.SKIPPED, result.status)
        assertEquals(0, result.itemsSynced)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `SyncConfig defaults are reasonable`() {
        val config = SyncConfig()
        assertEquals(Duration.ofMinutes(30), config.syncInterval)
        assertEquals(50, config.batchSize)
        assertEquals(3, config.maxRetries)
        assertTrue(config.requireNetwork)
        assertFalse(config.requireUnmetered)
        assertFalse(config.requireCharging)
    }

    @Test
    fun `constraintsMap returns CONNECTED for network required`() {
        val w = UcotronSyncWorker(
            client = ucotron,
            config = SyncConfig(requireNetwork = true, requireUnmetered = false)
        )
        val map = w.constraintsMap()
        assertEquals("CONNECTED", map["networkType"])
        assertEquals("false", map["requiresCharging"])
    }

    @Test
    fun `constraintsMap returns UNMETERED for wifi required`() {
        val w = UcotronSyncWorker(
            client = ucotron,
            config = SyncConfig(requireUnmetered = true)
        )
        assertEquals("UNMETERED", w.constraintsMap()["networkType"])
    }

    @Test
    fun `constraintsMap returns NOT_REQUIRED when no network constraint`() {
        val w = UcotronSyncWorker(
            client = ucotron,
            config = SyncConfig(requireNetwork = false)
        )
        assertEquals("NOT_REQUIRED", w.constraintsMap()["networkType"])
    }

    @Test
    fun `constraintsMap returns charging when required`() {
        val w = UcotronSyncWorker(
            client = ucotron,
            config = SyncConfig(requireCharging = true)
        )
        assertEquals("true", w.constraintsMap()["requiresCharging"])
    }

    @Test
    fun `PendingMemory has correct defaults`() {
        val item = PendingMemory(text = "hello")
        assertEquals("hello", item.text)
        assertEquals(null, item.namespace)
        assertEquals(null, item.metadata)
        assertTrue(item.createdAt > 0)
    }

    @Test
    fun `SyncResult isSuccess only for SUCCESS status`() {
        assertTrue(SyncResult(SyncStatus.SUCCESS, 1, emptyList()).isSuccess)
        assertFalse(SyncResult(SyncStatus.PARTIAL, 1, listOf("err")).isSuccess)
        assertFalse(SyncResult(SyncStatus.FAILURE, 0, listOf("err")).isSuccess)
        assertFalse(SyncResult(SyncStatus.RETRY, 0, listOf("err")).isSuccess)
        assertFalse(SyncResult(SyncStatus.SKIPPED, 0, emptyList()).isSuccess)
    }

    @Test
    fun `doSync records duration`() = runTest {
        val result = worker.doSync(emptyList())
        assertTrue(result.durationMs >= 0)
        assertTrue(result.timestamp > 0)
    }
}
