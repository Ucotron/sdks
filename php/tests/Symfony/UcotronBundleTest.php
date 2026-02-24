<?php

declare(strict_types=1);

namespace Ucotron\Sdk\Tests\Symfony;

use Ucotron\Sdk\UcotronClient;
use Ucotron\Sdk\Symfony\UcotronBundle;
use Ucotron\Sdk\Symfony\UcotronExtension;
use PHPUnit\Framework\TestCase;

class UcotronBundleTest extends TestCase
{
    public function testGetContainerExtensionReturnsUcotronExtension(): void
    {
        $bundle = new UcotronBundle();
        $extension = $bundle->getContainerExtension();

        $this->assertInstanceOf(UcotronExtension::class, $extension);
    }

    public function testGetAliasReturnsUcotron(): void
    {
        $bundle = new UcotronBundle();
        $this->assertSame('ucotron', $bundle->getAlias());
    }

    public function testExtensionGetAliasReturnsUcotron(): void
    {
        $extension = new UcotronExtension();
        $this->assertSame('ucotron', $extension->getAlias());
    }

    public function testProcessConfigWithDefaults(): void
    {
        $extension = new UcotronExtension();
        $config = $extension->processConfig([[]]);

        $this->assertSame('http://localhost:8420', $config['server_url']);
        $this->assertNull($config['api_key']);
        $this->assertNull($config['namespace']);
        $this->assertSame(30.0, $config['timeout']);
        $this->assertSame(3, $config['retry']['max_retries']);
        $this->assertSame(100, $config['retry']['base_delay_ms']);
        $this->assertSame(5000, $config['retry']['max_delay_ms']);
    }

    public function testProcessConfigOverridesDefaults(): void
    {
        $extension = new UcotronExtension();
        $config = $extension->processConfig([
            [
                'server_url' => 'http://custom:9000',
                'api_key' => 'my-key',
                'namespace' => 'my-ns',
                'timeout' => 15.0,
                'retry' => [
                    'max_retries' => 5,
                    'base_delay_ms' => 200,
                    'max_delay_ms' => 10000,
                ],
            ],
        ]);

        $this->assertSame('http://custom:9000', $config['server_url']);
        $this->assertSame('my-key', $config['api_key']);
        $this->assertSame('my-ns', $config['namespace']);
        $this->assertSame(15.0, $config['timeout']);
        $this->assertSame(5, $config['retry']['max_retries']);
        $this->assertSame(200, $config['retry']['base_delay_ms']);
        $this->assertSame(10000, $config['retry']['max_delay_ms']);
    }

    public function testProcessConfigMergesMultipleConfigs(): void
    {
        $extension = new UcotronExtension();
        $config = $extension->processConfig([
            ['server_url' => 'http://first:8000', 'api_key' => 'key-1'],
            ['api_key' => 'key-2', 'timeout' => 60.0],
        ]);

        // Last config wins for overlapping keys
        $this->assertSame('http://first:8000', $config['server_url']);
        $this->assertSame('key-2', $config['api_key']);
        $this->assertSame(60.0, $config['timeout']);
    }

    public function testProcessConfigPartialRetryOverride(): void
    {
        $extension = new UcotronExtension();
        $config = $extension->processConfig([
            ['retry' => ['max_retries' => 10]],
        ]);

        $this->assertSame(10, $config['retry']['max_retries']);
        // Other retry values keep defaults
        $this->assertSame(100, $config['retry']['base_delay_ms']);
        $this->assertSame(5000, $config['retry']['max_delay_ms']);
    }

    public function testProcessConfigExplicitNullApiKey(): void
    {
        $extension = new UcotronExtension();
        $config = $extension->processConfig([
            ['api_key' => null],
        ]);

        $this->assertNull($config['api_key']);
    }

    public function testLoadRegistersServicesInContainer(): void
    {
        $extension = new UcotronExtension();
        $container = new FakeContainerBuilder();

        $extension->load([
            ['server_url' => 'http://test:8420', 'api_key' => 'test-key'],
        ], $container);

        $this->assertTrue($container->hasDefinition('ucotron.retry_config'));
        $this->assertTrue($container->hasDefinition('ucotron.client_config'));
        $this->assertTrue($container->hasDefinition('ucotron.client'));
        $this->assertTrue($container->hasAlias(UcotronClient::class));
    }

    public function testLoadRegistersClientAsPublic(): void
    {
        $extension = new UcotronExtension();
        $container = new FakeContainerBuilder();

        $extension->load([[]], $container);

        $definition = $container->getDefinition('ucotron.client');
        $this->assertTrue($definition->isPublic);
        $this->assertSame(UcotronClient::class, $definition->class);
    }

    public function testLoadPassesConfigToServiceArguments(): void
    {
        $extension = new UcotronExtension();
        $container = new FakeContainerBuilder();

        $extension->load([
            ['server_url' => 'http://custom:9000', 'timeout' => 20.0],
        ], $container);

        $clientDef = $container->getDefinition('ucotron.client');
        $this->assertSame('http://custom:9000', $clientDef->arguments[0]);

        $configDef = $container->getDefinition('ucotron.client_config');
        $this->assertSame(20.0, $configDef->arguments[0]);
    }

    public function testCreateClientReturnsInstance(): void
    {
        $extension = new UcotronExtension();
        $client = $extension->createClient([
            ['server_url' => 'http://localhost:8420'],
        ]);

        $this->assertInstanceOf(UcotronClient::class, $client);
    }

    public function testCreateClientWithFullConfig(): void
    {
        $extension = new UcotronExtension();
        $client = $extension->createClient([
            [
                'server_url' => 'http://prod:9000',
                'api_key' => 'prod-key',
                'namespace' => 'prod-ns',
                'timeout' => 10.0,
                'retry' => ['max_retries' => 1],
            ],
        ]);

        $this->assertInstanceOf(UcotronClient::class, $client);
    }

    public function testCreateClientWithDefaults(): void
    {
        $extension = new UcotronExtension();
        $client = $extension->createClient();

        $this->assertInstanceOf(UcotronClient::class, $client);
    }

    public function testDefaultsConstantMatchesExpected(): void
    {
        $defaults = UcotronExtension::DEFAULTS;

        $this->assertSame('http://localhost:8420', $defaults['server_url']);
        $this->assertNull($defaults['api_key']);
        $this->assertNull($defaults['namespace']);
        $this->assertSame(30.0, $defaults['timeout']);
        $this->assertIsArray($defaults['retry']);
    }
}

/**
 * Minimal fake ContainerBuilder for testing service registration
 * without requiring symfony/dependency-injection.
 */
class FakeContainerBuilder
{
    /** @var array<string, FakeDefinition> */
    private array $definitions = [];

    /** @var array<string, FakeAlias> */
    private array $aliases = [];

    public function register(string $id, string $class): FakeDefinition
    {
        $definition = new FakeDefinition($class);
        $this->definitions[$id] = $definition;
        return $definition;
    }

    public function setAlias(string $alias, string $id): FakeAlias
    {
        $aliasObj = new FakeAlias($id);
        $this->aliases[$alias] = $aliasObj;
        return $aliasObj;
    }

    public function hasDefinition(string $id): bool
    {
        return isset($this->definitions[$id]);
    }

    public function getDefinition(string $id): FakeDefinition
    {
        return $this->definitions[$id];
    }

    public function hasAlias(string $alias): bool
    {
        return isset($this->aliases[$alias]);
    }
}

class FakeDefinition
{
    public string $class;
    public array $arguments = [];
    public bool $isPublic = false;

    public function __construct(string $class)
    {
        $this->class = $class;
    }

    public function setArguments(array $arguments): self
    {
        $this->arguments = $arguments;
        return $this;
    }

    public function setPublic(bool $public): self
    {
        $this->isPublic = $public;
        return $this;
    }
}

class FakeAlias
{
    public string $id;
    public bool $isPublic = false;

    public function __construct(string $id)
    {
        $this->id = $id;
    }

    public function setPublic(bool $public): self
    {
        $this->isPublic = $public;
        return $this;
    }
}
