package com.ucotron.spring;

import com.ucotron.sdk.ClientConfig;
import com.ucotron.sdk.UcotronAsync;
import com.ucotron.sdk.UcotronClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link UcotronAutoConfiguration}.
 */
class UcotronAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(UcotronAutoConfiguration.class));

    @Test
    void defaultBeansCreated() {
        contextRunner.run(context -> {
            assertTrue(context.containsBean("ucotronClient"));
            assertTrue(context.containsBean("ucotronAsync"));
            assertTrue(context.containsBean("ucotronClientConfig"));
            assertNotNull(context.getBean(UcotronClient.class));
            assertNotNull(context.getBean(UcotronAsync.class));
            assertNotNull(context.getBean(ClientConfig.class));
        });
    }

    @Test
    void defaultServerUrl() {
        contextRunner.run(context -> {
            UcotronClient client = context.getBean(UcotronClient.class);
            assertEquals("http://localhost:8420", client.getBaseUrl());
        });
    }

    @Test
    void customServerUrl() {
        contextRunner
                .withPropertyValues("ucotron.server-url=http://ucotron.example.com:9000")
                .run(context -> {
                    UcotronClient client = context.getBean(UcotronClient.class);
                    assertEquals("http://ucotron.example.com:9000", client.getBaseUrl());
                });
    }

    @Test
    void customApiKey() {
        contextRunner
                .withPropertyValues("ucotron.api-key=test-key-123")
                .run(context -> {
                    ClientConfig config = context.getBean(ClientConfig.class);
                    assertEquals("test-key-123", config.getApiKey());
                });
    }

    @Test
    void customNamespace() {
        contextRunner
                .withPropertyValues("ucotron.namespace=my-tenant")
                .run(context -> {
                    ClientConfig config = context.getBean(ClientConfig.class);
                    assertEquals("my-tenant", config.getDefaultNamespace());
                });
    }

    @Test
    void customTimeout() {
        contextRunner
                .withPropertyValues("ucotron.timeout=15s")
                .run(context -> {
                    ClientConfig config = context.getBean(ClientConfig.class);
                    assertEquals(Duration.ofSeconds(15), config.getTimeout());
                });
    }

    @Test
    void customRetryConfig() {
        contextRunner
                .withPropertyValues(
                        "ucotron.retry.max-retries=5",
                        "ucotron.retry.base-delay=200ms",
                        "ucotron.retry.max-delay=10s"
                )
                .run(context -> {
                    ClientConfig config = context.getBean(ClientConfig.class);
                    assertEquals(5, config.getRetryConfig().getMaxRetries());
                    assertEquals(Duration.ofMillis(200), config.getRetryConfig().getBaseDelay());
                    assertEquals(Duration.ofSeconds(10), config.getRetryConfig().getMaxDelay());
                });
    }

    @Test
    void disabledWhenPropertyFalse() {
        contextRunner
                .withPropertyValues("ucotron.enabled=false")
                .run(context -> {
                    assertFalse(context.containsBean("ucotronClient"));
                    assertFalse(context.containsBean("ucotronAsync"));
                });
    }

    @Test
    void backsOffWhenUserDefinesClient() {
        contextRunner
                .withUserConfiguration(CustomClientConfig.class)
                .run(context -> {
                    UcotronClient client = context.getBean(UcotronClient.class);
                    assertEquals("http://custom:1234", client.getBaseUrl());
                });
    }

    @Test
    void allPropertiesTogether() {
        contextRunner
                .withPropertyValues(
                        "ucotron.server-url=http://prod:8420",
                        "ucotron.api-key=prod-key",
                        "ucotron.namespace=production",
                        "ucotron.timeout=60s",
                        "ucotron.retry.max-retries=10"
                )
                .run(context -> {
                    UcotronClient client = context.getBean(UcotronClient.class);
                    ClientConfig config = context.getBean(ClientConfig.class);
                    assertEquals("http://prod:8420", client.getBaseUrl());
                    assertEquals("prod-key", config.getApiKey());
                    assertEquals("production", config.getDefaultNamespace());
                    assertEquals(Duration.ofSeconds(60), config.getTimeout());
                    assertEquals(10, config.getRetryConfig().getMaxRetries());
                });
    }

    @Configuration
    static class CustomClientConfig {
        @Bean
        public UcotronClient ucotronClient() {
            return new UcotronClient("http://custom:1234");
        }
    }
}
