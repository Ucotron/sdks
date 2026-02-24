<?php

declare(strict_types=1);

namespace Ucotron\Sdk\Tests;

use GuzzleHttp\Client;
use GuzzleHttp\Handler\MockHandler;
use GuzzleHttp\HandlerStack;
use GuzzleHttp\Psr7\Response;
use GuzzleHttp\Psr7\Request;
use GuzzleHttp\Exception\ConnectException;
use Ucotron\Sdk\ClientConfig;
use Ucotron\Sdk\UcotronClient;
use Ucotron\Sdk\UcotronAsync;
use Ucotron\Sdk\RetryConfig;
use Ucotron\Sdk\RetryMiddleware;
use Ucotron\Sdk\Exceptions\UcotronServerException;
use Ucotron\Sdk\Exceptions\UcotronConnectionException;
use PHPUnit\Framework\TestCase;

class RetryMiddlewareTest extends TestCase
{
    // --- Unit tests for RetryMiddleware ---

    public function testCreateReturnsCallable(): void
    {
        $middleware = RetryMiddleware::create();
        $this->assertIsCallable($middleware);
    }

    public function testDefaultConfig(): void
    {
        $middleware = new RetryMiddleware();
        $config = $middleware->getConfig();
        $this->assertSame(3, $config->maxRetries);
        $this->assertSame(100, $config->baseDelayMs);
        $this->assertSame(5000, $config->maxDelayMs);
    }

    public function testCustomConfig(): void
    {
        $config = new RetryConfig(maxRetries: 5, baseDelayMs: 200, maxDelayMs: 10000);
        $middleware = new RetryMiddleware($config);
        $this->assertSame(5, $middleware->getConfig()->maxRetries);
        $this->assertSame(200, $middleware->getConfig()->baseDelayMs);
    }

    public function testCalculateDelayExponentialBackoff(): void
    {
        $config = new RetryConfig(baseDelayMs: 100, maxDelayMs: 10000);
        $middleware = new RetryMiddleware($config);

        // Attempt 1: 100 * 2^0 = 100 + jitter (0-25)
        $delay1 = $middleware->calculateDelay(1);
        $this->assertGreaterThanOrEqual(100, $delay1);
        $this->assertLessThanOrEqual(125, $delay1);

        // Attempt 2: 100 * 2^1 = 200 + jitter (0-50)
        $delay2 = $middleware->calculateDelay(2);
        $this->assertGreaterThanOrEqual(200, $delay2);
        $this->assertLessThanOrEqual(250, $delay2);

        // Attempt 3: 100 * 2^2 = 400 + jitter (0-100)
        $delay3 = $middleware->calculateDelay(3);
        $this->assertGreaterThanOrEqual(400, $delay3);
        $this->assertLessThanOrEqual(500, $delay3);
    }

    public function testCalculateDelayRespectsMaxDelay(): void
    {
        $config = new RetryConfig(baseDelayMs: 1000, maxDelayMs: 2000);
        $middleware = new RetryMiddleware($config);

        // Attempt 5: 1000 * 2^4 = 16000 → capped at 2000
        $delay = $middleware->calculateDelay(5);
        $this->assertLessThanOrEqual(2000, $delay);
    }

    // --- Integration: sync client retries 5xx ---

    public function testSyncClientRetries5xx(): void
    {
        $mock = new MockHandler([
            new Response(503, [], '{"error":"unavailable"}'),
            new Response(500, [], '{"error":"internal"}'),
            new Response(200, [], json_encode(['status' => 'ok'])),
        ]);

        $config = new ClientConfig(
            retryConfig: new RetryConfig(maxRetries: 3, baseDelayMs: 1, maxDelayMs: 1),
        );
        $client = $this->createSyncClient($mock, $config);
        $result = $client->health();
        $this->assertSame('ok', $result->status);
    }

    public function testSyncClientDoesNotRetry4xx(): void
    {
        $mock = new MockHandler([
            new Response(404, [], '{"error":"not found"}'),
            // If retried, this would succeed — but it should NOT be reached
            new Response(200, [], json_encode(['status' => 'ok'])),
        ]);

        $config = new ClientConfig(
            retryConfig: new RetryConfig(maxRetries: 3, baseDelayMs: 1, maxDelayMs: 1),
        );
        $client = $this->createSyncClient($mock, $config);

        $this->expectException(UcotronServerException::class);
        $client->health();
    }

    public function testSyncClientRetriesConnectionError(): void
    {
        $mock = new MockHandler([
            new ConnectException('Connection refused', new Request('GET', '/api/v1/health')),
            new Response(200, [], json_encode(['status' => 'ok'])),
        ]);

        $config = new ClientConfig(
            retryConfig: new RetryConfig(maxRetries: 3, baseDelayMs: 1, maxDelayMs: 1),
        );
        $client = $this->createSyncClient($mock, $config);
        $result = $client->health();
        $this->assertSame('ok', $result->status);
    }

    public function testSyncClientExhaustsRetries5xx(): void
    {
        $mock = new MockHandler([
            new Response(500, [], '{"error":"internal"}'),
            new Response(500, [], '{"error":"internal"}'),
            new Response(500, [], '{"error":"internal"}'),
            new Response(500, [], '{"error":"internal"}'),
        ]);

        $config = new ClientConfig(
            retryConfig: new RetryConfig(maxRetries: 3, baseDelayMs: 1, maxDelayMs: 1),
        );
        $client = $this->createSyncClient($mock, $config);

        // After 1 initial + 3 retries = 4 attempts, gets the last 500 response
        $this->expectException(UcotronServerException::class);
        $client->health();
    }

    public function testSyncClientExhaustsRetriesConnectionError(): void
    {
        $mock = new MockHandler([
            new ConnectException('Connection refused', new Request('GET', '/api/v1/health')),
            new ConnectException('Connection refused', new Request('GET', '/api/v1/health')),
            new ConnectException('Connection refused', new Request('GET', '/api/v1/health')),
            new ConnectException('Connection refused', new Request('GET', '/api/v1/health')),
        ]);

        $config = new ClientConfig(
            retryConfig: new RetryConfig(maxRetries: 3, baseDelayMs: 1, maxDelayMs: 1),
        );
        $client = $this->createSyncClient($mock, $config);

        $this->expectException(UcotronConnectionException::class);
        $client->health();
    }

    public function testSyncClientNoRetriesWhenMaxIsZero(): void
    {
        $mock = new MockHandler([
            new Response(500, [], '{"error":"internal"}'),
            // Should not be reached
            new Response(200, [], json_encode(['status' => 'ok'])),
        ]);

        $config = new ClientConfig(
            retryConfig: new RetryConfig(maxRetries: 0),
        );
        $client = $this->createSyncClient($mock, $config);

        $this->expectException(UcotronServerException::class);
        $client->health();
    }

    // --- Integration: async client retries 5xx ---

    public function testAsyncClientRetries5xx(): void
    {
        $mock = new MockHandler([
            new Response(502, [], '{"error":"bad gateway"}'),
            new Response(200, [], json_encode(['status' => 'ok'])),
        ]);

        $config = new ClientConfig(
            retryConfig: new RetryConfig(maxRetries: 3, baseDelayMs: 1, maxDelayMs: 1),
        );
        $client = $this->createAsyncClient($mock, $config);
        $result = $client->healthAsync()->wait();
        $this->assertSame('ok', $result->status);
    }

    public function testAsyncClientDoesNotRetry4xx(): void
    {
        $mock = new MockHandler([
            new Response(403, [], '{"error":"forbidden"}'),
            new Response(200, [], json_encode(['status' => 'ok'])),
        ]);

        $config = new ClientConfig(
            retryConfig: new RetryConfig(maxRetries: 3, baseDelayMs: 1, maxDelayMs: 1),
        );
        $client = $this->createAsyncClient($mock, $config);

        $this->expectException(UcotronServerException::class);
        $client->healthAsync()->wait();
    }

    public function testAsyncClientRetriesConnectionError(): void
    {
        $mock = new MockHandler([
            new ConnectException('Connection reset', new Request('GET', '/api/v1/health')),
            new Response(200, [], json_encode(['status' => 'ok'])),
        ]);

        $config = new ClientConfig(
            retryConfig: new RetryConfig(maxRetries: 3, baseDelayMs: 1, maxDelayMs: 1),
        );
        $client = $this->createAsyncClient($mock, $config);
        $result = $client->healthAsync()->wait();
        $this->assertSame('ok', $result->status);
    }

    // --- Helpers ---

    private function createSyncClient(MockHandler $mock, ClientConfig $config): UcotronClient
    {
        // Build a handler stack with the retry middleware already injected
        $stack = HandlerStack::create($mock);
        $stack->push(RetryMiddleware::create($config->retryConfig), 'retry');
        $httpClient = new Client([
            'handler' => $stack,
            'http_errors' => false,
        ]);

        $client = new UcotronClient('http://localhost:8420', $config);
        $ref = new \ReflectionClass($client);
        $prop = $ref->getProperty('httpClient');
        $prop->setValue($client, $httpClient);

        return $client;
    }

    private function createAsyncClient(MockHandler $mock, ClientConfig $config): UcotronAsync
    {
        $stack = HandlerStack::create($mock);
        $stack->push(RetryMiddleware::create($config->retryConfig), 'retry');
        $httpClient = new Client([
            'handler' => $stack,
            'http_errors' => false,
        ]);

        $client = new UcotronAsync('http://localhost:8420', $config);
        $ref = new \ReflectionClass($client);
        $prop = $ref->getProperty('httpClient');
        $prop->setValue($client, $httpClient);

        return $client;
    }
}
