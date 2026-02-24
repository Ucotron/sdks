<?php

declare(strict_types=1);

namespace Ucotron\Sdk\Tests;

use GuzzleHttp\Client;
use GuzzleHttp\Handler\MockHandler;
use GuzzleHttp\HandlerStack;
use GuzzleHttp\Middleware;
use GuzzleHttp\Psr7\Response;
use GuzzleHttp\Psr7\Request;
use GuzzleHttp\Exception\ConnectException;
use Ucotron\Sdk\ClientConfig;
use Ucotron\Sdk\UcotronClient;
use Ucotron\Sdk\RetryConfig;
use Ucotron\Sdk\RetryMiddleware;
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

class UcotronClientTest extends TestCase
{
    /** @var array<int, array> */
    private array $requestHistory = [];

    private function createClient(MockHandler $mock, ?ClientConfig $config = null): UcotronClient
    {
        $config = $config ?? new ClientConfig(
            retryConfig: new RetryConfig(maxRetries: 0),
        );

        $stack = HandlerStack::create($mock);
        $stack->push(RetryMiddleware::create($config->retryConfig), 'retry');
        $history = Middleware::history($this->requestHistory);
        $stack->push($history, 'history');
        $httpClient = new Client([
            'handler' => $stack,
            'http_errors' => false,
        ]);

        $client = new UcotronClient('http://localhost:8420/', $config);
        $ref = new \ReflectionClass($client);
        $prop = $ref->getProperty('httpClient');
        $prop->setValue($client, $httpClient);

        return $client;
    }

    // === Constructor ===

    public function testConstructorDefaults(): void
    {
        $client = new UcotronClient('http://localhost:8420');
        $this->assertInstanceOf(UcotronClient::class, $client);
    }

    public function testConstructorWithConfig(): void
    {
        $config = new ClientConfig(
            timeout: 60.0,
            apiKey: 'test-key',
            namespace: 'test-ns',
        );
        $client = new UcotronClient('http://localhost:8420', $config);
        $this->assertInstanceOf(UcotronClient::class, $client);
    }

    // === addMemory ===

    public function testAddMemory(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode([
                'id' => 'mem-1',
                'metrics' => [
                    'chunks_created' => 2,
                    'entities_extracted' => 3,
                    'relations_extracted' => 1,
                    'processing_time_ms' => 42.5,
                ],
            ])),
        ]);
        $client = $this->createClient($mock);
        $result = $client->addMemory('test content', ['source' => 'unit-test']);

        $this->assertInstanceOf(CreateMemoryResult::class, $result);
        $this->assertSame('mem-1', $result->id);
        $this->assertSame(2, $result->metrics->chunks_created);
        $this->assertSame(3, $result->metrics->entities_extracted);
    }

    public function testAddMemorySendsCorrectRequest(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode(['id' => 'mem-1'])),
        ]);
        $client = $this->createClient($mock);
        $client->addMemory('hello world', ['key' => 'val']);

        $request = $this->requestHistory[0]['request'];
        $this->assertSame('POST', $request->getMethod());
        $this->assertStringContainsString('/api/v1/memories', (string) $request->getUri());
        $body = json_decode((string) $request->getBody(), true);
        $this->assertSame('hello world', $body['content']);
        $this->assertSame(['key' => 'val'], $body['metadata']);
    }

    public function testAddMemoryWithoutMetadata(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode(['id' => 'mem-2'])),
        ]);
        $client = $this->createClient($mock);
        $result = $client->addMemory('content only');

        $this->assertSame('mem-2', $result->id);
        $body = json_decode((string) $this->requestHistory[0]['request']->getBody(), true);
        $this->assertArrayNotHasKey('metadata', $body);
    }

    // === getMemory ===

    public function testGetMemory(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode([
                'id' => 'mem-42',
                'content' => 'stored memory',
                'namespace' => 'default',
                'node_type' => 'Entity',
                'timestamp' => 1700000000,
                'metadata' => ['confidence' => 0.9],
            ])),
        ]);
        $client = $this->createClient($mock);
        $result = $client->getMemory('mem-42');

        $this->assertInstanceOf(MemoryResponse::class, $result);
        $this->assertSame('mem-42', $result->id);
        $this->assertSame('stored memory', $result->content);
        $this->assertSame('Entity', $result->node_type);
        $this->assertSame(1700000000, $result->timestamp);
    }

    public function testGetMemorySendsCorrectPath(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode(['id' => 'mem-99', 'content' => 'x'])),
        ]);
        $client = $this->createClient($mock);
        $client->getMemory('mem-99');

        $request = $this->requestHistory[0]['request'];
        $this->assertSame('GET', $request->getMethod());
        $this->assertStringContainsString('/api/v1/memories/mem-99', (string) $request->getUri());
    }

    // === listMemories ===

    public function testListMemories(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode([
                'memories' => [
                    ['id' => 'mem-1', 'content' => 'first'],
                    ['id' => 'mem-2', 'content' => 'second'],
                    ['id' => 'mem-3', 'content' => 'third'],
                ],
            ])),
        ]);
        $client = $this->createClient($mock);
        $result = $client->listMemories(limit: 10, offset: 0);

        $this->assertCount(3, $result);
        $this->assertInstanceOf(MemoryResponse::class, $result[0]);
        $this->assertSame('mem-1', $result[0]->id);
        $this->assertSame('third', $result[2]->content);
    }

    public function testListMemoriesSendsQueryParams(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode(['memories' => []])),
        ]);
        $client = $this->createClient($mock);
        $client->listMemories(limit: 25, offset: 50);

        $request = $this->requestHistory[0]['request'];
        $query = $request->getUri()->getQuery();
        $this->assertStringContainsString('limit=25', $query);
        $this->assertStringContainsString('offset=50', $query);
    }

    // === updateMemory ===

    public function testUpdateMemory(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode([
                'id' => 'mem-1',
                'content' => 'updated content',
                'namespace' => 'default',
            ])),
        ]);
        $client = $this->createClient($mock);
        $result = $client->updateMemory('mem-1', 'updated content', ['ver' => '2']);

        $this->assertInstanceOf(MemoryResponse::class, $result);
        $this->assertSame('updated content', $result->content);
    }

    public function testUpdateMemorySendsCorrectRequest(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode(['id' => 'mem-1', 'content' => 'new'])),
        ]);
        $client = $this->createClient($mock);
        $client->updateMemory('mem-1', 'new content', ['tag' => 'test']);

        $request = $this->requestHistory[0]['request'];
        $this->assertSame('PUT', $request->getMethod());
        $this->assertStringContainsString('/api/v1/memories/mem-1', (string) $request->getUri());
        $body = json_decode((string) $request->getBody(), true);
        $this->assertSame('new content', $body['content']);
        $this->assertSame(['tag' => 'test'], $body['metadata']);
    }

    // === deleteMemory ===

    public function testDeleteMemory(): void
    {
        $mock = new MockHandler([
            new Response(204, [], ''),
        ]);
        $client = $this->createClient($mock);
        $client->deleteMemory('mem-1');

        $request = $this->requestHistory[0]['request'];
        $this->assertSame('DELETE', $request->getMethod());
        $this->assertStringContainsString('/api/v1/memories/mem-1', (string) $request->getUri());
    }

    // === search ===

    public function testSearch(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode([
                'results' => [
                    ['id' => 'mem-1', 'content' => 'result 1', 'score' => 0.95],
                    ['id' => 'mem-2', 'content' => 'result 2', 'score' => 0.80],
                ],
            ])),
        ]);
        $client = $this->createClient($mock);
        $result = $client->search('find this', topK: 5);

        $this->assertInstanceOf(SearchResponse::class, $result);
        $this->assertCount(2, $result->results);
        $this->assertSame('mem-1', $result->results[0]->id);
        $this->assertSame(0.95, $result->results[0]->score);
    }

    public function testSearchSendsCorrectBody(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode(['results' => []])),
        ]);
        $client = $this->createClient($mock);
        $client->search('query text', topK: 10);

        $request = $this->requestHistory[0]['request'];
        $this->assertSame('POST', $request->getMethod());
        $body = json_decode((string) $request->getBody(), true);
        $this->assertSame('query text', $body['query']);
        $this->assertSame(10, $body['top_k']);
    }

    public function testSearchWithoutTopK(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode(['results' => []])),
        ]);
        $client = $this->createClient($mock);
        $client->search('query');

        $body = json_decode((string) $this->requestHistory[0]['request']->getBody(), true);
        $this->assertSame('query', $body['query']);
        $this->assertArrayNotHasKey('top_k', $body);
    }

    // === getEntity ===

    public function testGetEntity(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode([
                'id' => 'ent-10',
                'name' => 'Alice',
                'entity_type' => 'Person',
                'metadata' => ['role' => 'engineer'],
                'neighbors' => [
                    ['id' => 'ent-11', 'name' => 'Bob', 'edge_type' => 'RELATES_TO', 'weight' => 0.9],
                ],
            ])),
        ]);
        $client = $this->createClient($mock);
        $result = $client->getEntity('ent-10');

        $this->assertInstanceOf(EntityResponse::class, $result);
        $this->assertSame('Alice', $result->name);
        $this->assertSame('Person', $result->entity_type);
        $this->assertCount(1, $result->neighbors);
        $this->assertSame('Bob', $result->neighbors[0]->name);
        $this->assertSame(0.9, $result->neighbors[0]->weight);
    }

    // === listEntities ===

    public function testListEntities(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode([
                'entities' => [
                    ['id' => 'e-1', 'name' => 'Alice', 'neighbors' => []],
                    ['id' => 'e-2', 'name' => 'Bob', 'neighbors' => []],
                ],
            ])),
        ]);
        $client = $this->createClient($mock);
        $result = $client->listEntities(limit: 10);

        $this->assertCount(2, $result);
        $this->assertInstanceOf(EntityResponse::class, $result[0]);
        $this->assertSame('Alice', $result[0]->name);
    }

    public function testListEntitiesSendsQueryParam(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode(['entities' => []])),
        ]);
        $client = $this->createClient($mock);
        $client->listEntities(limit: 50);

        $query = $this->requestHistory[0]['request']->getUri()->getQuery();
        $this->assertStringContainsString('limit=50', $query);
    }

    // === augment ===

    public function testAugment(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode([
                'augmented_prompt' => 'Context: relevant info\n\nQuery: test prompt',
                'memories' => [
                    ['id' => 'mem-1', 'content' => 'relevant context', 'score' => 0.92],
                ],
            ])),
        ]);
        $client = $this->createClient($mock);
        $result = $client->augment('test prompt', topK: 3);

        $this->assertInstanceOf(AugmentResult::class, $result);
        $this->assertStringContainsString('Context', $result->augmented_prompt);
        $this->assertCount(1, $result->memories);
    }

    public function testAugmentSendsCorrectBody(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode([
                'augmented_prompt' => 'test',
                'memories' => [],
            ])),
        ]);
        $client = $this->createClient($mock);
        $client->augment('my prompt', topK: 7);

        $body = json_decode((string) $this->requestHistory[0]['request']->getBody(), true);
        $this->assertSame('my prompt', $body['prompt']);
        $this->assertSame(7, $body['top_k']);
    }

    // === learn ===

    public function testLearn(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode([
                'entities_extracted' => 5,
                'relations_extracted' => 3,
                'memories_stored' => 2,
            ])),
        ]);
        $client = $this->createClient($mock);
        $result = $client->learn('some output from agent', source: 'gpt-4');

        $this->assertInstanceOf(LearnResult::class, $result);
        $this->assertSame(5, $result->entities_extracted);
        $this->assertSame(3, $result->relations_extracted);
        $this->assertSame(2, $result->memories_stored);
    }

    public function testLearnSendsCorrectBody(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode([
                'entities_extracted' => 0,
                'relations_extracted' => 0,
                'memories_stored' => 0,
            ])),
        ]);
        $client = $this->createClient($mock);
        $client->learn('agent output', source: 'claude');

        $body = json_decode((string) $this->requestHistory[0]['request']->getBody(), true);
        $this->assertSame('agent output', $body['content']);
        $this->assertSame('claude', $body['source']);
    }

    public function testLearnWithoutSource(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode([
                'entities_extracted' => 1,
                'relations_extracted' => 0,
                'memories_stored' => 1,
            ])),
        ]);
        $client = $this->createClient($mock);
        $client->learn('content');

        $body = json_decode((string) $this->requestHistory[0]['request']->getBody(), true);
        $this->assertArrayNotHasKey('source', $body);
    }

    // === health ===

    public function testHealth(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode([
                'status' => 'ok',
                'version' => '0.2.0',
                'uptime_seconds' => 7200.5,
            ])),
        ]);
        $client = $this->createClient($mock);
        $result = $client->health();

        $this->assertInstanceOf(HealthResponse::class, $result);
        $this->assertSame('ok', $result->status);
        $this->assertSame('0.2.0', $result->version);
        $this->assertSame(7200.5, $result->uptime_seconds);
    }

    // === metrics ===

    public function testMetrics(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode([
                'total_memories' => 500,
                'total_entities' => 120,
                'total_namespaces' => 5,
                'models' => [
                    ['name' => 'all-MiniLM-L6-v2', 'loaded' => true, 'version' => '1.0'],
                    ['name' => 'gliner', 'loaded' => false],
                ],
            ])),
        ]);
        $client = $this->createClient($mock);
        $result = $client->metrics();

        $this->assertInstanceOf(MetricsResponse::class, $result);
        $this->assertSame(500, $result->total_memories);
        $this->assertSame(120, $result->total_entities);
        $this->assertSame(5, $result->total_namespaces);
        $this->assertCount(2, $result->models);
        $this->assertTrue($result->models[0]->loaded);
        $this->assertFalse($result->models[1]->loaded);
    }

    // === Error Handling ===

    public function testThrowsServerExceptionOn404(): void
    {
        $mock = new MockHandler([
            new Response(404, [], '{"error":"Memory not found"}'),
        ]);
        $client = $this->createClient($mock);

        $this->expectException(UcotronServerException::class);
        $client->getMemory('nonexistent');
    }

    public function testThrowsServerExceptionOn500(): void
    {
        $mock = new MockHandler([
            new Response(500, [], '{"error":"Internal server error"}'),
        ]);
        $client = $this->createClient($mock);

        $this->expectException(UcotronServerException::class);
        $client->health();
    }

    public function testServerExceptionContainsStatusAndBody(): void
    {
        $mock = new MockHandler([
            new Response(422, [], '{"error":"Validation failed","details":"content required"}'),
        ]);
        $client = $this->createClient($mock);

        try {
            $client->addMemory('');
            $this->fail('Expected UcotronServerException');
        } catch (UcotronServerException $e) {
            $this->assertSame(422, $e->statusCode);
            $this->assertStringContainsString('Validation failed', $e->errorBody);
        }
    }

    public function testThrowsConnectionExceptionOnNetworkError(): void
    {
        $mock = new MockHandler([
            new ConnectException('Connection refused', new Request('GET', '/api/v1/health')),
        ]);
        $client = $this->createClient($mock);

        $this->expectException(UcotronConnectionException::class);
        $client->health();
    }

    public function testThrowsServerExceptionOn403(): void
    {
        $mock = new MockHandler([
            new Response(403, [], '{"error":"Forbidden"}'),
        ]);
        $client = $this->createClient($mock);

        $this->expectException(UcotronServerException::class);
        $client->search('test');
    }

    public function testThrowsServerExceptionOn401(): void
    {
        $mock = new MockHandler([
            new Response(401, [], '{"error":"Unauthorized"}'),
        ]);
        $client = $this->createClient($mock);

        $this->expectException(UcotronServerException::class);
        $client->addMemory('content');
    }

    // === Namespace Handling ===

    public function testNamespaceFromConfig(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode(['status' => 'ok'])),
        ]);
        $config = new ClientConfig(
            namespace: 'prod',
            retryConfig: new RetryConfig(maxRetries: 0),
        );
        $client = $this->createClient($mock, $config);
        $client->health();

        $request = $this->requestHistory[0]['request'];
        $this->assertSame('prod', $request->getHeaderLine('X-Ucotron-Namespace'));
    }

    public function testNamespaceOverridePerCall(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode(['id' => 'mem-1', 'content' => 'x'])),
        ]);
        $config = new ClientConfig(
            namespace: 'default-ns',
            retryConfig: new RetryConfig(maxRetries: 0),
        );
        $client = $this->createClient($mock, $config);
        $client->getMemory('mem-1', namespace: 'override-ns');

        $request = $this->requestHistory[0]['request'];
        $this->assertSame('override-ns', $request->getHeaderLine('X-Ucotron-Namespace'));
    }

    public function testNoNamespaceHeaderWhenNotConfigured(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode(['status' => 'ok'])),
        ]);
        $client = $this->createClient($mock);
        $client->health();

        $request = $this->requestHistory[0]['request'];
        $this->assertFalse($request->hasHeader('X-Ucotron-Namespace'));
    }

    // === API Key ===

    public function testApiKeyIsSentAsBearer(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode(['status' => 'ok'])),
        ]);
        $config = new ClientConfig(
            apiKey: 'my-secret-key',
            retryConfig: new RetryConfig(maxRetries: 0),
        );
        $client = $this->createClient($mock, $config);
        $client->health();

        $request = $this->requestHistory[0]['request'];
        $this->assertSame('Bearer my-secret-key', $request->getHeaderLine('Authorization'));
    }

    public function testNoAuthHeaderWithoutApiKey(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode(['status' => 'ok'])),
        ]);
        $client = $this->createClient($mock);
        $client->health();

        $request = $this->requestHistory[0]['request'];
        $this->assertFalse($request->hasHeader('Authorization'));
    }

    // === Retry Integration ===

    public function testRetriesOn5xxThenSucceeds(): void
    {
        $mock = new MockHandler([
            new Response(503, [], '{"error":"unavailable"}'),
            new Response(200, [], json_encode(['status' => 'ok'])),
        ]);
        $config = new ClientConfig(
            retryConfig: new RetryConfig(maxRetries: 3, baseDelayMs: 1, maxDelayMs: 1),
        );
        $client = $this->createClient($mock, $config);
        $result = $client->health();

        $this->assertSame('ok', $result->status);
    }

    public function testDoesNotRetry4xx(): void
    {
        $mock = new MockHandler([
            new Response(400, [], '{"error":"bad request"}'),
            new Response(200, [], json_encode(['status' => 'ok'])),
        ]);
        $config = new ClientConfig(
            retryConfig: new RetryConfig(maxRetries: 3, baseDelayMs: 1, maxDelayMs: 1),
        );
        $client = $this->createClient($mock, $config);

        $this->expectException(UcotronServerException::class);
        $client->health();
    }

    public function testRetriesConnectionErrorThenSucceeds(): void
    {
        $mock = new MockHandler([
            new ConnectException('timeout', new Request('GET', '/api/v1/health')),
            new Response(200, [], json_encode(['status' => 'ok'])),
        ]);
        $config = new ClientConfig(
            retryConfig: new RetryConfig(maxRetries: 3, baseDelayMs: 1, maxDelayMs: 1),
        );
        $client = $this->createClient($mock, $config);
        $result = $client->health();

        $this->assertSame('ok', $result->status);
    }

    // === Empty / Edge Cases ===

    public function testDeleteReturnsVoidOn204(): void
    {
        $mock = new MockHandler([
            new Response(204, [], ''),
        ]);
        $client = $this->createClient($mock);
        // Should not throw
        $client->deleteMemory('mem-to-delete');
        $this->assertTrue(true); // reached without exception
    }

    public function testListMemoriesEmpty(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode(['memories' => []])),
        ]);
        $client = $this->createClient($mock);
        $result = $client->listMemories();

        $this->assertIsArray($result);
        $this->assertCount(0, $result);
    }

    public function testListEntitiesEmpty(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode(['entities' => []])),
        ]);
        $client = $this->createClient($mock);
        $result = $client->listEntities();

        $this->assertIsArray($result);
        $this->assertCount(0, $result);
    }

    public function testSearchEmptyResults(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode(['results' => []])),
        ]);
        $client = $this->createClient($mock);
        $result = $client->search('obscure query');

        $this->assertInstanceOf(SearchResponse::class, $result);
        $this->assertCount(0, $result->results);
    }

    // === Content-Type / Accept Headers ===

    public function testAcceptHeaderIsSent(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode(['status' => 'ok'])),
        ]);
        $client = $this->createClient($mock);
        $client->health();

        $request = $this->requestHistory[0]['request'];
        $this->assertSame('application/json', $request->getHeaderLine('Accept'));
    }

    public function testPostRequestHasJsonContentType(): void
    {
        $mock = new MockHandler([
            new Response(200, [], json_encode(['results' => []])),
        ]);
        $client = $this->createClient($mock);
        $client->search('query text');

        $request = $this->requestHistory[0]['request'];
        $this->assertStringContainsString('application/json', $request->getHeaderLine('Content-Type'));
    }
}
