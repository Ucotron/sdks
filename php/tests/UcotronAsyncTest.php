<?php

declare(strict_types=1);

namespace Ucotron\Sdk\Tests;

use GuzzleHttp\Client;
use GuzzleHttp\Handler\MockHandler;
use GuzzleHttp\HandlerStack;
use GuzzleHttp\Psr7\Response;
use GuzzleHttp\Exception\ConnectException;
use GuzzleHttp\Psr7\Request;
use Ucotron\Sdk\ClientConfig;
use Ucotron\Sdk\UcotronAsync;
use Ucotron\Sdk\Exceptions\UcotronConnectionException;
use Ucotron\Sdk\Exceptions\UcotronServerException;
use Ucotron\Sdk\Types\AugmentResult;
use Ucotron\Sdk\Types\CreateMemoryResult;
use Ucotron\Sdk\Types\EntityResponse;
use Ucotron\Sdk\Types\HealthResponse;
use Ucotron\Sdk\Types\LearnResult;
use Ucotron\Sdk\Types\MemoryResponse;
use Ucotron\Sdk\Types\MetricsResponse;
use Ucotron\Sdk\Types\SearchResponse;
use PHPUnit\Framework\TestCase;

class UcotronAsyncTest extends TestCase
{
    private function createClient(MockHandler $mock, ?ClientConfig $config = null): UcotronAsync
    {
        $handlerStack = HandlerStack::create($mock);
        $httpClient = new Client(['handler' => $handlerStack]);

        // Use reflection to inject the mock HTTP client
        $client = new UcotronAsync('http://localhost:8420', $config);
        $ref = new \ReflectionClass($client);
        $prop = $ref->getProperty('httpClient');
        $prop->setValue($client, $httpClient);

        return $client;
    }

    // --- Constructor ---

    public function testConstructorDefaults(): void
    {
        $client = new UcotronAsync('http://localhost:8420');
        $this->assertInstanceOf(UcotronAsync::class, $client);
        $config = $client->getConfig();
        $this->assertSame(30.0, $config->timeout);
        $this->assertNull($config->apiKey);
        $this->assertNull($config->namespace);
    }

    public function testConstructorWithConfig(): void
    {
        $config = new ClientConfig(
            timeout: 60.0,
            apiKey: 'test-key',
            namespace: 'test-ns',
        );
        $client = new UcotronAsync('http://localhost:8420', $config);
        $this->assertSame('test-key', $client->getConfig()->apiKey);
        $this->assertSame('test-ns', $client->getConfig()->namespace);
    }

    public function testConstructorStripsTrailingSlash(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode(['status' => 'ok'])),
        ]);
        $client = $this->createClient($mock);
        $result = $client->healthAsync()->wait();
        $this->assertInstanceOf(HealthResponse::class, $result);
    }

    // --- addMemoryAsync ---

    public function testAddMemoryAsync(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode([
                'id' => 'mem-123',
                'metrics' => [
                    'chunks_created' => 2,
                    'entities_extracted' => 3,
                    'relations_extracted' => 1,
                    'processing_time_ms' => 45.2,
                ],
            ])),
        ]);
        $client = $this->createClient($mock);
        $result = $client->addMemoryAsync('test content', ['source' => 'test'])->wait();
        $this->assertInstanceOf(CreateMemoryResult::class, $result);
        $this->assertSame('mem-123', $result->id);
    }

    // --- getMemoryAsync ---

    public function testGetMemoryAsync(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode([
                'id' => 'mem-123',
                'content' => 'hello world',
                'namespace' => 'default',
            ])),
        ]);
        $client = $this->createClient($mock);
        $result = $client->getMemoryAsync('mem-123')->wait();
        $this->assertInstanceOf(MemoryResponse::class, $result);
        $this->assertSame('mem-123', $result->id);
        $this->assertSame('hello world', $result->content);
    }

    // --- listMemoriesAsync ---

    public function testListMemoriesAsync(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode([
                'memories' => [
                    ['id' => 'mem-1', 'content' => 'first'],
                    ['id' => 'mem-2', 'content' => 'second'],
                ],
            ])),
        ]);
        $client = $this->createClient($mock);
        $result = $client->listMemoriesAsync(limit: 10, offset: 0)->wait();
        $this->assertCount(2, $result);
        $this->assertInstanceOf(MemoryResponse::class, $result[0]);
        $this->assertSame('mem-1', $result[0]->id);
    }

    // --- updateMemoryAsync ---

    public function testUpdateMemoryAsync(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode([
                'id' => 'mem-123',
                'content' => 'updated content',
            ])),
        ]);
        $client = $this->createClient($mock);
        $result = $client->updateMemoryAsync('mem-123', 'updated content', ['key' => 'val'])->wait();
        $this->assertInstanceOf(MemoryResponse::class, $result);
        $this->assertSame('updated content', $result->content);
    }

    // --- deleteMemoryAsync ---

    public function testDeleteMemoryAsync(): void
    {
        $mock = new MockHandler([
            new Response(204, [], ''),
        ]);
        $client = $this->createClient($mock);
        $result = $client->deleteMemoryAsync('mem-123')->wait();
        $this->assertNull($result);
    }

    // --- searchAsync ---

    public function testSearchAsync(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode([
                'results' => [
                    ['id' => 'mem-1', 'content' => 'relevant result', 'score' => 0.95],
                ],
            ])),
        ]);
        $client = $this->createClient($mock);
        $result = $client->searchAsync('test query', topK: 5)->wait();
        $this->assertInstanceOf(SearchResponse::class, $result);
        $this->assertCount(1, $result->results);
        $this->assertSame('mem-1', $result->results[0]->id);
    }

    // --- getEntityAsync ---

    public function testGetEntityAsync(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode([
                'id' => 'ent-1',
                'name' => 'Alice',
                'entity_type' => 'Person',
                'neighbors' => [],
            ])),
        ]);
        $client = $this->createClient($mock);
        $result = $client->getEntityAsync('ent-1')->wait();
        $this->assertInstanceOf(EntityResponse::class, $result);
        $this->assertSame('Alice', $result->name);
    }

    // --- listEntitiesAsync ---

    public function testListEntitiesAsync(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode([
                'entities' => [
                    ['id' => 'ent-1', 'name' => 'Alice', 'neighbors' => []],
                    ['id' => 'ent-2', 'name' => 'Bob', 'neighbors' => []],
                ],
            ])),
        ]);
        $client = $this->createClient($mock);
        $result = $client->listEntitiesAsync(limit: 10)->wait();
        $this->assertCount(2, $result);
        $this->assertInstanceOf(EntityResponse::class, $result[0]);
    }

    // --- augmentAsync ---

    public function testAugmentAsync(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode([
                'augmented_prompt' => 'Context: ... Prompt: test',
                'memories' => [
                    ['id' => 'mem-1', 'content' => 'context', 'score' => 0.9],
                ],
            ])),
        ]);
        $client = $this->createClient($mock);
        $result = $client->augmentAsync('test', topK: 3)->wait();
        $this->assertInstanceOf(AugmentResult::class, $result);
        $this->assertStringContainsString('Context', $result->augmented_prompt);
    }

    // --- learnAsync ---

    public function testLearnAsync(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode([
                'entities_extracted' => 5,
                'relations_extracted' => 3,
                'memories_stored' => 2,
            ])),
        ]);
        $client = $this->createClient($mock);
        $result = $client->learnAsync('some content', source: 'test')->wait();
        $this->assertInstanceOf(LearnResult::class, $result);
        $this->assertSame(5, $result->entities_extracted);
    }

    // --- healthAsync ---

    public function testHealthAsync(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode([
                'status' => 'ok',
                'version' => '0.1.0',
                'uptime_seconds' => 123.4,
            ])),
        ]);
        $client = $this->createClient($mock);
        $result = $client->healthAsync()->wait();
        $this->assertInstanceOf(HealthResponse::class, $result);
        $this->assertSame('ok', $result->status);
    }

    // --- metricsAsync ---

    public function testMetricsAsync(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode([
                'total_memories' => 100,
                'total_entities' => 50,
                'total_namespaces' => 3,
                'models' => [],
            ])),
        ]);
        $client = $this->createClient($mock);
        $result = $client->metricsAsync()->wait();
        $this->assertInstanceOf(MetricsResponse::class, $result);
        $this->assertSame(100, $result->total_memories);
    }

    // --- Error handling ---

    public function testServerErrorRejects(): void
    {
        $mock = new MockHandler([
            new Response(404, [], '{"error":"not found"}'),
        ]);
        $client = $this->createClient($mock);

        $this->expectException(UcotronServerException::class);
        $client->getMemoryAsync('nonexistent')->wait();
    }

    public function testServerError500Rejects(): void
    {
        $mock = new MockHandler([
            new Response(500, [], '{"error":"internal"}'),
        ]);
        $client = $this->createClient($mock);

        $this->expectException(UcotronServerException::class);
        $client->healthAsync()->wait();
    }

    public function testConnectionErrorRejects(): void
    {
        $mock = new MockHandler([
            new ConnectException('Connection refused', new Request('GET', '/api/v1/health')),
        ]);
        $client = $this->createClient($mock);

        $this->expectException(UcotronConnectionException::class);
        $client->healthAsync()->wait();
    }

    // --- Namespace forwarding ---

    public function testNamespaceFromConfig(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode(['status' => 'ok'])),
        ]);
        $config = new ClientConfig(namespace: 'config-ns');
        $client = $this->createClient($mock, $config);
        // This should not throw — namespace sent via header
        $result = $client->healthAsync()->wait();
        $this->assertInstanceOf(HealthResponse::class, $result);
    }

    public function testNamespaceOverride(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode([
                'id' => 'mem-1',
                'content' => 'test',
            ])),
        ]);
        $config = new ClientConfig(namespace: 'default-ns');
        $client = $this->createClient($mock, $config);
        // Override namespace at call level
        $result = $client->getMemoryAsync('mem-1', namespace: 'custom-ns')->wait();
        $this->assertInstanceOf(MemoryResponse::class, $result);
    }

    // --- Concurrent request batching ---

    public function testBatchConcurrentRequests(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode(['status' => 'ok', 'version' => '0.1.0'])),
            new Response(200, [], json_encode([
                'total_memories' => 42,
                'total_entities' => 10,
                'total_namespaces' => 1,
                'models' => [],
            ])),
        ]);
        $client = $this->createClient($mock);

        $results = UcotronAsync::batch([
            'health' => $client->healthAsync(),
            'metrics' => $client->metricsAsync(),
        ]);

        $this->assertArrayHasKey('health', $results);
        $this->assertArrayHasKey('metrics', $results);
        $this->assertInstanceOf(HealthResponse::class, $results['health']);
        $this->assertInstanceOf(MetricsResponse::class, $results['metrics']);
        $this->assertSame(42, $results['metrics']->total_memories);
    }

    public function testSettleConcurrentRequests(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode(['status' => 'ok'])),
            new Response(500, [], '{"error":"internal"}'),
        ]);
        $client = $this->createClient($mock);

        $results = UcotronAsync::settle([
            'health' => $client->healthAsync(),
            'fail' => $client->metricsAsync(),
        ]);

        $this->assertSame('fulfilled', $results['health']['state']);
        $this->assertSame('rejected', $results['fail']['state']);
        $this->assertInstanceOf(HealthResponse::class, $results['health']['value']);
        $this->assertInstanceOf(UcotronServerException::class, $results['fail']['reason']);
    }

    // --- Promise chaining ---

    public function testPromiseChaining(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode([
                'results' => [
                    ['id' => 'mem-1', 'content' => 'result', 'score' => 0.9],
                ],
            ])),
        ]);
        $client = $this->createClient($mock);

        $count = $client->searchAsync('test')
            ->then(fn(SearchResponse $response) => count($response->results))
            ->wait();

        $this->assertSame(1, $count);
    }

    // --- API key forwarding ---

    public function testApiKeyForwarding(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode(['status' => 'ok'])),
        ]);
        $config = new ClientConfig(apiKey: 'secret-key');
        $client = $this->createClient($mock, $config);
        $result = $client->healthAsync()->wait();
        $this->assertInstanceOf(HealthResponse::class, $result);
    }

    // --- Empty response handling ---

    public function testEmpty204Response(): void
    {
        $mock = new MockHandler([
            new Response(204, [], ''),
        ]);
        $client = $this->createClient($mock);
        $result = $client->deleteMemoryAsync('mem-1')->wait();
        $this->assertNull($result);
    }
}
