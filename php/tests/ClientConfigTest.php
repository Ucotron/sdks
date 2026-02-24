<?php

declare(strict_types=1);

namespace Ucotron\Sdk\Tests;

use Ucotron\Sdk\ClientConfig;
use Ucotron\Sdk\RetryConfig;
use PHPUnit\Framework\TestCase;

class ClientConfigTest extends TestCase
{
    public function testDefaultConfig(): void
    {
        $config = new ClientConfig();
        $this->assertSame(30.0, $config->timeout);
        $this->assertNull($config->apiKey);
        $this->assertNull($config->namespace);
        $this->assertInstanceOf(RetryConfig::class, $config->retryConfig);
    }

    public function testCustomConfig(): void
    {
        $config = new ClientConfig(
            timeout: 60.0,
            apiKey: 'test-key-123',
            namespace: 'my-namespace',
            retryConfig: new RetryConfig(maxRetries: 5, baseDelayMs: 200, maxDelayMs: 10000),
        );
        $this->assertSame(60.0, $config->timeout);
        $this->assertSame('test-key-123', $config->apiKey);
        $this->assertSame('my-namespace', $config->namespace);
        $this->assertSame(5, $config->retryConfig->maxRetries);
        $this->assertSame(200, $config->retryConfig->baseDelayMs);
        $this->assertSame(10000, $config->retryConfig->maxDelayMs);
    }

    public function testDefaultRetryConfig(): void
    {
        $config = new RetryConfig();
        $this->assertSame(3, $config->maxRetries);
        $this->assertSame(100, $config->baseDelayMs);
        $this->assertSame(5000, $config->maxDelayMs);
    }
}
