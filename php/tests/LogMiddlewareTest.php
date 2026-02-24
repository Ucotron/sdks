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
use Ucotron\Sdk\LogMiddleware;
use Ucotron\Sdk\UcotronClient;
use Ucotron\Sdk\UcotronAsync;
use Ucotron\Sdk\RetryConfig;
use Ucotron\Sdk\RetryMiddleware;
use Ucotron\Sdk\Exceptions\UcotronServerException;
use Ucotron\Sdk\Exceptions\UcotronConnectionException;
use PHPUnit\Framework\TestCase;
use Psr\Log\AbstractLogger;
use Psr\Log\LoggerInterface;
use Psr\Log\LogLevel;

/**
 * In-memory PSR-3 logger for testing. Captures all log entries.
 */
class TestLogger extends AbstractLogger
{
    /** @var array<int, array{level: string, message: string, context: array}> */
    public array $logs = [];

    public function log($level, string|\Stringable $message, array $context = []): void
    {
        $this->logs[] = [
            'level' => (string) $level,
            'message' => (string) $message,
            'context' => $context,
        ];
    }

    public function hasLogWithMessage(string $message): bool
    {
        foreach ($this->logs as $log) {
            if (str_contains($log['message'], $message)) {
                return true;
            }
        }
        return false;
    }

    public function getLogsWithMessage(string $message): array
    {
        return array_values(array_filter(
            $this->logs,
            fn(array $log) => str_contains($log['message'], $message),
        ));
    }
}

class LogMiddlewareTest extends TestCase
{
    public function testCreateReturnsCallable(): void
    {
        $logger = new TestLogger();
        $middleware = LogMiddleware::create($logger);
        $this->assertIsCallable($middleware);
    }

    public function testLogsSuccessfulRequest(): void
    {
        $logger = new TestLogger();
        $mock = new MockHandler([
            new Response(200, [], json_encode(['status' => 'ok'])),
        ]);

        $stack = HandlerStack::create($mock);
        $stack->push(LogMiddleware::create($logger), 'log');

        $client = new Client(['handler' => $stack, 'http_errors' => false]);
        $client->get('http://localhost:8420/api/v1/health');

        // Should have debug (request) + info (response)
        $this->assertTrue($logger->hasLogWithMessage('Ucotron SDK request'));
        $this->assertTrue($logger->hasLogWithMessage('Ucotron SDK response'));

        $responseLogs = $logger->getLogsWithMessage('Ucotron SDK response');
        $this->assertNotEmpty($responseLogs);
        $ctx = $responseLogs[0]['context'];
        $this->assertSame('GET', $ctx['method']);
        $this->assertStringContainsString('/api/v1/health', $ctx['uri']);
        $this->assertSame(200, $ctx['status']);
        $this->assertArrayHasKey('duration_ms', $ctx);
        $this->assertIsFloat($ctx['duration_ms']);
    }

    public function testLogsRequestMethodAndUri(): void
    {
        $logger = new TestLogger();
        $mock = new MockHandler([
            new Response(200, [], '{}'),
        ]);

        $stack = HandlerStack::create($mock);
        $stack->push(LogMiddleware::create($logger), 'log');

        $client = new Client(['handler' => $stack, 'http_errors' => false]);
        $client->post('http://localhost:8420/api/v1/memories', [
            'json' => ['content' => 'test'],
        ]);

        $requestLogs = $logger->getLogsWithMessage('Ucotron SDK request');
        $this->assertNotEmpty($requestLogs);
        $this->assertSame('POST', $requestLogs[0]['context']['method']);
        $this->assertStringContainsString('/api/v1/memories', $requestLogs[0]['context']['uri']);
    }

    public function testLogsErrorResponseWithErrorLevel(): void
    {
        $logger = new TestLogger();
        $mock = new MockHandler([
            new Response(500, [], '{"error":"internal"}'),
        ]);

        $stack = HandlerStack::create($mock);
        $stack->push(LogMiddleware::create($logger), 'log');

        $client = new Client(['handler' => $stack, 'http_errors' => false]);
        $client->get('http://localhost:8420/api/v1/health');

        $errorLogs = $logger->getLogsWithMessage('Ucotron SDK response error');
        $this->assertNotEmpty($errorLogs);
        $this->assertSame(LogLevel::ERROR, $errorLogs[0]['level']);
        $this->assertSame(500, $errorLogs[0]['context']['status']);
    }

    public function testLogs4xxAsError(): void
    {
        $logger = new TestLogger();
        $mock = new MockHandler([
            new Response(404, [], '{"error":"not found"}'),
        ]);

        $stack = HandlerStack::create($mock);
        $stack->push(LogMiddleware::create($logger), 'log');

        $client = new Client(['handler' => $stack, 'http_errors' => false]);
        $client->get('http://localhost:8420/api/v1/memories/999');

        $errorLogs = $logger->getLogsWithMessage('Ucotron SDK response error');
        $this->assertNotEmpty($errorLogs);
        $this->assertSame(404, $errorLogs[0]['context']['status']);
    }

    public function testLogsConnectionError(): void
    {
        $logger = new TestLogger();
        $mock = new MockHandler([
            new ConnectException('Connection refused', new Request('GET', '/api/v1/health')),
        ]);

        $stack = HandlerStack::create($mock);
        $stack->push(LogMiddleware::create($logger), 'log');

        $client = new Client(['handler' => $stack, 'http_errors' => false]);
        try {
            $client->get('http://localhost:8420/api/v1/health');
        } catch (\Throwable) {
            // Expected
        }

        $errorLogs = $logger->getLogsWithMessage('Ucotron SDK request failed');
        $this->assertNotEmpty($errorLogs);
        $this->assertSame(LogLevel::ERROR, $errorLogs[0]['level']);
        $this->assertStringContainsString('Connection refused', $errorLogs[0]['context']['error']);
        $this->assertArrayHasKey('duration_ms', $errorLogs[0]['context']);
    }

    public function testCustomLogLevels(): void
    {
        $logger = new TestLogger();
        $mock = new MockHandler([
            new Response(200, [], '{}'),
        ]);

        $stack = HandlerStack::create($mock);
        $stack->push(LogMiddleware::create($logger, LogLevel::DEBUG, LogLevel::CRITICAL), 'log');

        $client = new Client(['handler' => $stack, 'http_errors' => false]);
        $client->get('http://localhost:8420/api/v1/health');

        $responseLogs = $logger->getLogsWithMessage('Ucotron SDK response');
        $this->assertNotEmpty($responseLogs);
        $this->assertSame(LogLevel::DEBUG, $responseLogs[0]['level']);
    }

    public function testCustomErrorLogLevel(): void
    {
        $logger = new TestLogger();
        $mock = new MockHandler([
            new Response(503, [], '{}'),
        ]);

        $stack = HandlerStack::create($mock);
        $stack->push(LogMiddleware::create($logger, LogLevel::DEBUG, LogLevel::CRITICAL), 'log');

        $client = new Client(['handler' => $stack, 'http_errors' => false]);
        $client->get('http://localhost:8420/api/v1/health');

        $errorLogs = $logger->getLogsWithMessage('Ucotron SDK response error');
        $this->assertNotEmpty($errorLogs);
        $this->assertSame(LogLevel::CRITICAL, $errorLogs[0]['level']);
    }

    public function testDurationIsPositive(): void
    {
        $logger = new TestLogger();
        $mock = new MockHandler([
            new Response(200, [], '{}'),
        ]);

        $stack = HandlerStack::create($mock);
        $stack->push(LogMiddleware::create($logger), 'log');

        $client = new Client(['handler' => $stack, 'http_errors' => false]);
        $client->get('http://localhost:8420/api/v1/health');

        $responseLogs = $logger->getLogsWithMessage('Ucotron SDK response');
        $this->assertGreaterThanOrEqual(0.0, $responseLogs[0]['context']['duration_ms']);
    }

    // --- Integration: LogMiddleware in UcotronClient ---

    public function testSyncClientLogsWithMonolog(): void
    {
        $logger = new TestLogger();
        $mock = new MockHandler([
            new Response(200, [], json_encode(['status' => 'ok'])),
        ]);

        $config = new ClientConfig(
            logger: $logger,
            retryConfig: new RetryConfig(maxRetries: 0),
        );
        $client = $this->createSyncClient($mock, $config);
        $result = $client->health();

        $this->assertSame('ok', $result->status);
        $this->assertTrue($logger->hasLogWithMessage('Ucotron SDK request'));
        $this->assertTrue($logger->hasLogWithMessage('Ucotron SDK response'));
    }

    public function testAsyncClientLogsWithMonolog(): void
    {
        $logger = new TestLogger();
        $mock = new MockHandler([
            new Response(200, [], json_encode(['status' => 'ok'])),
        ]);

        $config = new ClientConfig(
            logger: $logger,
            retryConfig: new RetryConfig(maxRetries: 0),
        );
        $client = $this->createAsyncClient($mock, $config);
        $result = $client->healthAsync()->wait();

        $this->assertSame('ok', $result->status);
        $this->assertTrue($logger->hasLogWithMessage('Ucotron SDK request'));
        $this->assertTrue($logger->hasLogWithMessage('Ucotron SDK response'));
    }

    public function testNoLoggerMeansNoLogging(): void
    {
        // Config without logger — should not crash
        $mock = new MockHandler([
            new Response(200, [], json_encode(['status' => 'ok'])),
        ]);

        $config = new ClientConfig(
            retryConfig: new RetryConfig(maxRetries: 0),
        );
        $client = $this->createSyncClient($mock, $config);
        $result = $client->health();

        $this->assertSame('ok', $result->status);
    }

    public function testSyncClientLogsErrorResponse(): void
    {
        $logger = new TestLogger();
        $mock = new MockHandler([
            new Response(500, [], '{"error":"internal"}'),
        ]);

        $config = new ClientConfig(
            logger: $logger,
            retryConfig: new RetryConfig(maxRetries: 0),
        );
        $client = $this->createSyncClient($mock, $config);

        try {
            $client->health();
        } catch (UcotronServerException) {
            // Expected
        }

        $this->assertTrue($logger->hasLogWithMessage('Ucotron SDK response error'));
        $errorLogs = $logger->getLogsWithMessage('Ucotron SDK response error');
        $this->assertSame(500, $errorLogs[0]['context']['status']);
    }

    // --- Helpers ---

    private function createSyncClient(MockHandler $mock, ClientConfig $config): UcotronClient
    {
        $stack = HandlerStack::create($mock);
        $stack->push(RetryMiddleware::create($config->retryConfig), 'retry');
        if ($config->logger !== null) {
            $stack->push(LogMiddleware::create($config->logger), 'log');
        }
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
        if ($config->logger !== null) {
            $stack->push(LogMiddleware::create($config->logger), 'log');
        }
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
