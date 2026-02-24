package com.ucotron.spring;

import com.ucotron.sdk.ClientConfig;
import com.ucotron.sdk.UcotronAsync;
import com.ucotron.sdk.UcotronClient;
import com.ucotron.sdk.RetryConfig;
import java.util.concurrent.ForkJoinPool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot auto-configuration for Ucotron client.
 * <p>
 * Automatically creates {@link UcotronClient} and {@link UcotronAsync} beans
 * when the {@code ucotron.enabled} property is {@code true} (default) and
 * the Ucotron SDK classes are on the classpath.
 * <p>
 * Conditional behavior:
 * <ul>
 *   <li>Disabled when {@code ucotron.enabled=false}</li>
 *   <li>Only activates when {@link UcotronClient} is on classpath</li>
 *   <li>Backs off if user defines their own {@link UcotronClient} bean</li>
 * </ul>
 * <p>
 * Usage in Spring Boot:
 * <pre>
 * &#64;Autowired
 * private UcotronClient ucotronClient;
 *
 * &#64;Autowired
 * private UcotronAsync ucotronAsync;
 * </pre>
 */
@Configuration
@ConditionalOnClass(UcotronClient.class)
@ConditionalOnProperty(prefix = "ucotron", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(UcotronProperties.class)
public class UcotronAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ClientConfig ucotronClientConfig(UcotronProperties properties) {
        RetryConfig retryConfig = new RetryConfig(
                properties.getRetry().getMaxRetries(),
                properties.getRetry().getBaseDelay(),
                properties.getRetry().getMaxDelay()
        );

        ClientConfig.Builder builder = ClientConfig.builder()
                .timeout(properties.getTimeout())
                .retryConfig(retryConfig);

        if (properties.getApiKey() != null) {
            builder.apiKey(properties.getApiKey());
        }
        if (properties.getNamespace() != null) {
            builder.defaultNamespace(properties.getNamespace());
        }

        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public UcotronClient ucotronClient(UcotronProperties properties, ClientConfig config) {
        return new UcotronClient(properties.getServerUrl(), config);
    }

    @Bean
    @ConditionalOnMissingBean
    public UcotronAsync ucotronAsync(UcotronClient client) {
        return new UcotronAsync(client, ForkJoinPool.commonPool());
    }
}
