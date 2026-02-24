<?php

declare(strict_types=1);

namespace Ucotron\Sdk\Laravel;

use Ucotron\Sdk\ClientConfig;
use Ucotron\Sdk\UcotronClient;
use Ucotron\Sdk\RetryConfig;

/**
 * Laravel service provider for the Ucotron SDK.
 *
 * Registers UcotronClient as a singleton in the service container
 * and publishes the ucotron.php configuration file.
 *
 * Usage in config/app.php (Laravel < 11):
 *   'providers' => [
 *       Ucotron\Sdk\Laravel\UcotronServiceProvider::class,
 *   ],
 *
 * Auto-discovery (Laravel 5.5+):
 *   This provider is auto-discovered via composer.json extra.laravel.providers.
 *
 * Publish config:
 *   php artisan vendor:publish --tag=ucotron-config
 *
 * This class uses duck-typing (object type hints) to avoid requiring
 * illuminate/support as a compile-time dependency. When used inside
 * Laravel, the Application container is passed automatically.
 */
class UcotronServiceProvider
{
    /**
     * The application instance (Laravel Container).
     */
    protected object $app;

    public function __construct(object $app)
    {
        $this->app = $app;
    }

    /**
     * Register services into the container.
     */
    public function register(): void
    {
        $this->mergeConfig();

        $this->app->singleton(UcotronClient::class, function (object $app) {
            $config = $app->make('config');

            $retryConfig = new RetryConfig(
                maxRetries: (int) $config->get('ucotron.retry.max_retries', 3),
                baseDelayMs: (int) $config->get('ucotron.retry.base_delay_ms', 100),
                maxDelayMs: (int) $config->get('ucotron.retry.max_delay_ms', 5000),
            );

            $clientConfig = new ClientConfig(
                timeout: (float) $config->get('ucotron.timeout', 30.0),
                apiKey: $config->get('ucotron.api_key'),
                namespace: $config->get('ucotron.namespace'),
                retryConfig: $retryConfig,
            );

            return new UcotronClient(
                $config->get('ucotron.server_url', 'http://localhost:8420'),
                $clientConfig,
            );
        });

        $this->app->alias(UcotronClient::class, 'ucotron');
    }

    /**
     * Boot services after all providers are registered.
     */
    public function boot(): void
    {
        // In a full Laravel app with Illuminate\Support\ServiceProvider,
        // this would publish the config file:
        // $this->publishes([__DIR__ . '/config.php' => config_path('ucotron.php')], 'ucotron-config');
    }

    /**
     * Merge the package default config with the application config.
     */
    protected function mergeConfig(): void
    {
        $configPath = __DIR__ . '/config.php';
        $defaults = require $configPath;
        $configRepo = $this->app->make('config');

        // Apply defaults for keys not already set by user
        foreach ($defaults as $key => $value) {
            $fullKey = "ucotron.{$key}";
            if (is_array($value)) {
                foreach ($value as $subKey => $subValue) {
                    $subFullKey = "{$fullKey}.{$subKey}";
                    if (!$configRepo->has($subFullKey)) {
                        $configRepo->set($subFullKey, $subValue);
                    }
                }
            } elseif (!$configRepo->has($fullKey)) {
                $configRepo->set($fullKey, $value);
            }
        }
    }

    /**
     * Create a UcotronClient from an array of config values.
     *
     * Useful for standalone (non-Laravel) usage:
     *
     *   $client = UcotronServiceProvider::createFromConfig([
     *       'server_url' => 'http://localhost:8420',
     *       'api_key' => 'my-key',
     *       'namespace' => 'my-ns',
     *   ]);
     */
    public static function createFromConfig(array $config): UcotronClient
    {
        $retryConfig = new RetryConfig(
            maxRetries: (int) ($config['retry']['max_retries'] ?? 3),
            baseDelayMs: (int) ($config['retry']['base_delay_ms'] ?? 100),
            maxDelayMs: (int) ($config['retry']['max_delay_ms'] ?? 5000),
        );

        $clientConfig = new ClientConfig(
            timeout: (float) ($config['timeout'] ?? 30.0),
            apiKey: $config['api_key'] ?? null,
            namespace: $config['namespace'] ?? null,
            retryConfig: $retryConfig,
        );

        return new UcotronClient(
            $config['server_url'] ?? 'http://localhost:8420',
            $clientConfig,
        );
    }
}
