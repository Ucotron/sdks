package com.ucotron.sdk.android

import com.ucotron.sdk.ClientConfig
import com.ucotron.sdk.RetryConfig
import java.time.Duration

/**
 * Lifecycle-aware extensions for integrating UcotronAndroid with Android components.
 *
 * Usage with LifecycleOwner (Activity/Fragment):
 * ```kotlin
 * class MainActivity : AppCompatActivity() {
 *     private val ucotron by lazy {
 *         UcotronAndroid("http://10.0.2.2:8420")
 *     }
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         lifecycleScope.launch {
 *             val health = ucotron.health()
 *             val results = ucotron.search("recent conversations")
 *         }
 *     }
 * }
 * ```
 *
 * Usage with ViewModel:
 * ```kotlin
 * class MemoryViewModel : ViewModel() {
 *     private val ucotron = UcotronAndroid("http://10.0.2.2:8420")
 *
 *     val memories = MutableLiveData<List<MemoryResponse>>()
 *
 *     fun loadMemories() {
 *         viewModelScope.launch {
 *             try {
 *                 memories.value = ucotron.listMemories()
 *             } catch (e: UcotronException) {
 *                 // handle error
 *             }
 *         }
 *     }
 * }
 * ```
 */

/**
 * Creates a [UcotronAndroid] instance configured for typical Android usage.
 *
 * Uses Dispatchers.IO for network calls and applies common Android defaults:
 * - 15 second timeout (shorter for mobile)
 * - 2 max retries (conserve battery)
 *
 * @param serverUrl The Ucotron server URL (use 10.0.2.2 for emulator localhost)
 * @param apiKey Optional API key for authentication
 * @param namespace Optional default namespace
 */
fun createUcotronClient(
    serverUrl: String,
    apiKey: String? = null,
    namespace: String? = null
): UcotronAndroid {
    val configBuilder = ClientConfig.builder()
    if (apiKey != null) {
        configBuilder.apiKey(apiKey)
    }
    if (namespace != null) {
        configBuilder.defaultNamespace(namespace)
    }
    configBuilder.timeout(Duration.ofSeconds(15))
    configBuilder.retryConfig(
        RetryConfig(2, Duration.ofMillis(200), Duration.ofSeconds(3))
    )
    return UcotronAndroid(serverUrl, configBuilder.build())
}
