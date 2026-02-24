<?php

declare(strict_types=1);

namespace Ucotron\Sdk\Tests\Laravel;

use Ucotron\Sdk\Laravel\UcotronServiceProvider;
use Ucotron\Sdk\UcotronClient;
use PHPUnit\Framework\TestCase;

/**
 * Tests for UcotronServiceProvider using a minimal Application mock.
 *
 * These tests validate the service provider's registration logic
 * without requiring the full Laravel framework.
 */
class UcotronServiceProviderTest extends TestCase
{
    public function testRegisterBindsUcotronClientSingleton(): void
    {
        $app = new FakeApplication([
            'ucotron.server_url' => 'http://localhost:8420',
            'ucotron.api_key' => 'test-key',
            'ucotron.namespace' => 'test-ns',
            'ucotron.timeout' => 15.0,
            'ucotron.retry.max_retries' => 2,
            'ucotron.retry.base_delay_ms' => 200,
            'ucotron.retry.max_delay_ms' => 3000,
        ]);

        $provider = new UcotronServiceProvider($app);
        $provider->register();

        $this->assertTrue($app->hasSingleton(UcotronClient::class));
        $this->assertTrue($app->hasAlias('ucotron'));
    }

    public function testResolvesClientWithConfig(): void
    {
        $app = new FakeApplication([
            'ucotron.server_url' => 'http://my-server:9000',
            'ucotron.api_key' => 'my-api-key',
            'ucotron.namespace' => 'my-ns',
            'ucotron.timeout' => 20.0,
            'ucotron.retry.max_retries' => 5,
            'ucotron.retry.base_delay_ms' => 500,
            'ucotron.retry.max_delay_ms' => 10000,
        ]);

        $provider = new UcotronServiceProvider($app);
        $provider->register();

        $client = $app->resolve(UcotronClient::class);
        $this->assertInstanceOf(UcotronClient::class, $client);
    }

    public function testSingletonReturnsSameInstance(): void
    {
        $app = new FakeApplication([
            'ucotron.server_url' => 'http://localhost:8420',
        ]);

        $provider = new UcotronServiceProvider($app);
        $provider->register();

        $client1 = $app->resolve(UcotronClient::class);
        $client2 = $app->resolve(UcotronClient::class);
        $this->assertSame($client1, $client2);
    }

    public function testDefaultConfigValues(): void
    {
        $app = new FakeApplication([]);

        $provider = new UcotronServiceProvider($app);
        $provider->register();

        // Should resolve with defaults from config.php
        $client = $app->resolve(UcotronClient::class);
        $this->assertInstanceOf(UcotronClient::class, $client);
    }

    public function testBootDoesNotThrow(): void
    {
        $app = new FakeApplication([]);

        $provider = new UcotronServiceProvider($app);
        $provider->register();
        $provider->boot();

        $this->assertTrue(true); // No exception means success
    }

    public function testMergeConfigAppliesDefaults(): void
    {
        $app = new FakeApplication([]);

        $provider = new UcotronServiceProvider($app);
        $provider->register();

        // After register, config should have defaults from config.php
        $config = $app->make('config');
        $this->assertSame('http://localhost:8420', $config->get('ucotron.server_url'));
        $this->assertSame(30.0, $config->get('ucotron.timeout'));
        $this->assertSame(3, $config->get('ucotron.retry.max_retries'));
    }

    public function testUserConfigOverridesDefaults(): void
    {
        $app = new FakeApplication([
            'ucotron.server_url' => 'http://custom:9999',
            'ucotron.timeout' => 60.0,
        ]);

        $provider = new UcotronServiceProvider($app);
        $provider->register();

        $config = $app->make('config');
        $this->assertSame('http://custom:9999', $config->get('ucotron.server_url'));
        $this->assertSame(60.0, $config->get('ucotron.timeout'));
    }

    public function testCreateFromConfigReturnsClient(): void
    {
        $client = UcotronServiceProvider::createFromConfig([
            'server_url' => 'http://localhost:8420',
            'api_key' => 'my-key',
            'namespace' => 'my-ns',
            'timeout' => 15.0,
            'retry' => [
                'max_retries' => 2,
                'base_delay_ms' => 200,
                'max_delay_ms' => 3000,
            ],
        ]);

        $this->assertInstanceOf(UcotronClient::class, $client);
    }

    public function testCreateFromConfigWithDefaults(): void
    {
        $client = UcotronServiceProvider::createFromConfig([]);
        $this->assertInstanceOf(UcotronClient::class, $client);
    }
}

/**
 * Minimal fake Application that provides the container interface
 * methods used by UcotronServiceProvider (singleton, alias, make).
 *
 * No dependency on Laravel/Illuminate packages.
 */
class FakeApplication
{
    /** @var array<string, callable> */
    private array $singletons = [];

    /** @var array<string, mixed> */
    private array $resolved = [];

    /** @var array<string, string> */
    private array $aliases = [];

    private FakeConfigRepository $configRepo;

    public function __construct(array $config = [])
    {
        $this->configRepo = new FakeConfigRepository($config);
    }

    public function hasSingleton(string $abstract): bool
    {
        return isset($this->singletons[$abstract]);
    }

    public function hasAlias(string $name): bool
    {
        return isset($this->aliases[$name]);
    }

    public function resolve(string $abstract): mixed
    {
        if (isset($this->resolved[$abstract])) {
            return $this->resolved[$abstract];
        }

        if (isset($this->singletons[$abstract])) {
            $this->resolved[$abstract] = ($this->singletons[$abstract])($this);
            return $this->resolved[$abstract];
        }

        throw new \RuntimeException("Cannot resolve: $abstract");
    }

    public function singleton(string $abstract, callable $concrete): void
    {
        $this->singletons[$abstract] = $concrete;
    }

    public function alias(string $abstract, string $alias): void
    {
        $this->aliases[$alias] = $abstract;
    }

    public function make(string $abstract, array $parameters = []): mixed
    {
        if ($abstract === 'config') {
            return $this->configRepo;
        }
        return $this->resolve($abstract);
    }
}

/**
 * Minimal config repository for testing.
 */
class FakeConfigRepository
{
    /** @var array<string, mixed> */
    private array $items;

    public function __construct(array $items = [])
    {
        $this->items = $items;
    }

    public function get(string $key, mixed $default = null): mixed
    {
        return $this->items[$key] ?? $default;
    }

    public function set(string $key, mixed $value): void
    {
        $this->items[$key] = $value;
    }

    public function has(string $key): bool
    {
        return isset($this->items[$key]);
    }
}
