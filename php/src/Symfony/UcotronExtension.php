<?php

declare(strict_types=1);

namespace Ucotron\Sdk\Symfony;

use Ucotron\Sdk\ClientConfig;
use Ucotron\Sdk\UcotronClient;
use Ucotron\Sdk\RetryConfig;

/**
 * Symfony DI extension for the Ucotron SDK.
 *
 * Processes the `ucotron` configuration key and registers
 * UcotronClient as a service in the container.
 *
 * In Symfony, this is loaded automatically by UcotronBundle.
 * For standalone usage, call processConfig() directly.
 *
 * Configuration tree (ucotron.yaml):
 *
 *   ucotron:
 *     server_url: 'http://localhost:8420'   # Required
 *     api_key: ~                            # Optional
 *     namespace: ~                          # Optional
 *     timeout: 30.0                         # Optional (seconds)
 *     retry:
 *       max_retries: 3                      # Optional
 *       base_delay_ms: 100                  # Optional
 *       max_delay_ms: 5000                  # Optional
 */
class UcotronExtension
{
    /**
     * Default configuration values.
     */
    public const DEFAULTS = [
        'server_url' => 'http://localhost:8420',
        'api_key' => null,
        'namespace' => null,
        'timeout' => 30.0,
        'retry' => [
            'max_retries' => 3,
            'base_delay_ms' => 100,
            'max_delay_ms' => 5000,
        ],
    ];

    /**
     * Get the configuration alias (root key in YAML).
     */
    public function getAlias(): string
    {
        return 'ucotron';
    }

    /**
     * Process configuration and register services in a Symfony container.
     *
     * In Symfony, this is called by the framework with (array $configs, ContainerBuilder $container).
     * For standalone testing, use processConfig() instead.
     *
     * @param array<array<string, mixed>> $configs Merged config arrays
     * @param object $container Symfony ContainerBuilder (duck-typed)
     */
    public function load(array $configs, object $container): void
    {
        $config = $this->processConfig($configs);

        // Register RetryConfig
        $container->register('ucotron.retry_config', RetryConfig::class)
            ->setArguments([
                $config['retry']['max_retries'],
                $config['retry']['base_delay_ms'],
                $config['retry']['max_delay_ms'],
            ]);

        // Register ClientConfig
        $container->register('ucotron.client_config', ClientConfig::class)
            ->setArguments([
                $config['timeout'],
                $config['api_key'],
                $config['namespace'],
                null, // retryConfig — will be set via method call or constructor
            ]);

        // Register UcotronClient as the primary service
        $container->register('ucotron.client', UcotronClient::class)
            ->setArguments([
                $config['server_url'],
                null, // clientConfig — will be set by Symfony DI
            ])
            ->setPublic(true);

        // Alias UcotronClient class for autowiring
        $container->setAlias(UcotronClient::class, 'ucotron.client')
            ->setPublic(true);
    }

    /**
     * Process and merge multiple configuration arrays with defaults.
     *
     * @param array<array<string, mixed>> $configs Array of config arrays (from multiple config files)
     * @return array<string, mixed> Merged configuration
     */
    public function processConfig(array $configs): array
    {
        $merged = self::DEFAULTS;

        foreach ($configs as $config) {
            if (isset($config['server_url'])) {
                $merged['server_url'] = (string) $config['server_url'];
            }
            if (array_key_exists('api_key', $config)) {
                $merged['api_key'] = $config['api_key'];
            }
            if (array_key_exists('namespace', $config)) {
                $merged['namespace'] = $config['namespace'];
            }
            if (isset($config['timeout'])) {
                $merged['timeout'] = (float) $config['timeout'];
            }
            if (isset($config['retry'])) {
                $merged['retry'] = array_merge($merged['retry'], $config['retry']);
            }
        }

        return $merged;
    }

    /**
     * Create a UcotronClient directly from a configuration array.
     *
     * Useful for standalone (non-Symfony) usage:
     *
     *   $extension = new UcotronExtension();
     *   $client = $extension->createClient([
     *       ['server_url' => 'http://localhost:8420', 'api_key' => 'my-key'],
     *   ]);
     *
     * @param array<array<string, mixed>> $configs Config arrays to merge
     */
    public function createClient(array $configs = [[]]): UcotronClient
    {
        $config = $this->processConfig($configs);

        $retryConfig = new RetryConfig(
            maxRetries: (int) $config['retry']['max_retries'],
            baseDelayMs: (int) $config['retry']['base_delay_ms'],
            maxDelayMs: (int) $config['retry']['max_delay_ms'],
        );

        $clientConfig = new ClientConfig(
            timeout: (float) $config['timeout'],
            apiKey: $config['api_key'],
            namespace: $config['namespace'],
            retryConfig: $retryConfig,
        );

        return new UcotronClient($config['server_url'], $clientConfig);
    }
}
