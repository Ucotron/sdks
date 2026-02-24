<?php

declare(strict_types=1);

namespace Ucotron\Sdk\Tests;

use Ucotron\Sdk\Types\AugmentResult;
use Ucotron\Sdk\Types\CreateMemoryResult;
use Ucotron\Sdk\Types\EntityResponse;
use Ucotron\Sdk\Types\HealthResponse;
use Ucotron\Sdk\Types\IngestionMetrics;
use Ucotron\Sdk\Types\LearnResult;
use Ucotron\Sdk\Types\MemoryResponse;
use Ucotron\Sdk\Types\MetricsResponse;
use Ucotron\Sdk\Types\ModelStatus;
use Ucotron\Sdk\Types\NeighborResponse;
use Ucotron\Sdk\Types\SearchResponse;
use Ucotron\Sdk\Types\SearchResultItem;
use PHPUnit\Framework\TestCase;

class TypesTest extends TestCase
{
    public function testMemoryResponseFromArray(): void
    {
        $data = [
            'id' => '42',
            'content' => 'Test memory',
            'namespace' => 'default',
            'node_type' => 'Entity',
            'timestamp' => 1700000000,
            'metadata' => ['source' => 'test'],
        ];
        $response = MemoryResponse::fromArray($data);
        $this->assertSame('42', $response->id);
        $this->assertSame('Test memory', $response->content);
        $this->assertSame('default', $response->namespace);
        $this->assertSame('Entity', $response->node_type);
        $this->assertSame(1700000000, $response->timestamp);
        $this->assertSame(['source' => 'test'], $response->metadata);
    }

    public function testCreateMemoryResultFromArray(): void
    {
        $data = [
            'id' => '1',
            'metrics' => [
                'chunks_created' => 3,
                'entities_extracted' => 2,
                'relations_extracted' => 1,
                'processing_time_ms' => 150.5,
            ],
        ];
        $result = CreateMemoryResult::fromArray($data);
        $this->assertSame('1', $result->id);
        $this->assertNotNull($result->metrics);
        $this->assertSame(3, $result->metrics->chunks_created);
        $this->assertSame(2, $result->metrics->entities_extracted);
    }

    public function testSearchResponseFromArray(): void
    {
        $data = [
            'results' => [
                ['id' => '1', 'content' => 'result 1', 'score' => 0.95],
                ['id' => '2', 'content' => 'result 2', 'score' => 0.80],
            ],
        ];
        $response = SearchResponse::fromArray($data);
        $this->assertCount(2, $response->results);
        $this->assertSame('1', $response->results[0]->id);
        $this->assertSame(0.95, $response->results[0]->score);
    }

    public function testSearchResultItemFromArray(): void
    {
        $data = [
            'id' => '5',
            'content' => 'semantic result',
            'score' => 0.92,
            'node_type' => 'Fact',
            'timestamp' => 1700001000,
            'metadata' => ['confidence' => 0.8],
        ];
        $item = SearchResultItem::fromArray($data);
        $this->assertSame('5', $item->id);
        $this->assertSame('semantic result', $item->content);
        $this->assertSame(0.92, $item->score);
        $this->assertSame('Fact', $item->node_type);
    }

    public function testEntityResponseFromArray(): void
    {
        $data = [
            'id' => '10',
            'name' => 'Juan',
            'entity_type' => 'Person',
            'neighbors' => [
                ['id' => '11', 'name' => 'Madrid', 'edge_type' => 'LOCATION', 'weight' => 1.0],
            ],
        ];
        $entity = EntityResponse::fromArray($data);
        $this->assertSame('10', $entity->id);
        $this->assertSame('Juan', $entity->name);
        $this->assertCount(1, $entity->neighbors);
        $this->assertSame('Madrid', $entity->neighbors[0]->name);
    }

    public function testNeighborResponseFromArray(): void
    {
        $data = ['id' => '3', 'name' => 'Test', 'edge_type' => 'RELATES_TO', 'weight' => 0.75];
        $neighbor = NeighborResponse::fromArray($data);
        $this->assertSame('3', $neighbor->id);
        $this->assertSame(0.75, $neighbor->weight);
    }

    public function testAugmentResultFromArray(): void
    {
        $data = [
            'augmented_prompt' => 'Context: ... \n\nQuery: test',
            'memories' => [
                ['id' => '1', 'content' => 'relevant', 'score' => 0.9],
            ],
        ];
        $result = AugmentResult::fromArray($data);
        $this->assertStringContainsString('Context', $result->augmented_prompt);
        $this->assertCount(1, $result->memories);
    }

    public function testLearnResultFromArray(): void
    {
        $data = [
            'entities_extracted' => 5,
            'relations_extracted' => 3,
            'memories_stored' => 2,
        ];
        $result = LearnResult::fromArray($data);
        $this->assertSame(5, $result->entities_extracted);
        $this->assertSame(3, $result->relations_extracted);
        $this->assertSame(2, $result->memories_stored);
    }

    public function testHealthResponseFromArray(): void
    {
        $data = ['status' => 'ok', 'version' => '0.1.0', 'uptime_seconds' => 3600.0];
        $response = HealthResponse::fromArray($data);
        $this->assertSame('ok', $response->status);
        $this->assertSame('0.1.0', $response->version);
        $this->assertSame(3600.0, $response->uptime_seconds);
    }

    public function testMetricsResponseFromArray(): void
    {
        $data = [
            'total_memories' => 1000,
            'total_entities' => 200,
            'total_namespaces' => 3,
            'models' => [
                ['name' => 'all-MiniLM-L6-v2', 'loaded' => true, 'version' => '1.0'],
            ],
        ];
        $response = MetricsResponse::fromArray($data);
        $this->assertSame(1000, $response->total_memories);
        $this->assertCount(1, $response->models);
        $this->assertTrue($response->models[0]->loaded);
    }

    public function testModelStatusFromArray(): void
    {
        $data = ['name' => 'gliner', 'loaded' => false];
        $status = ModelStatus::fromArray($data);
        $this->assertSame('gliner', $status->name);
        $this->assertFalse($status->loaded);
        $this->assertNull($status->version);
    }

    public function testIngestionMetricsFromArray(): void
    {
        $data = [
            'chunks_created' => 10,
            'entities_extracted' => 5,
            'relations_extracted' => 8,
            'processing_time_ms' => 250.0,
        ];
        $metrics = IngestionMetrics::fromArray($data);
        $this->assertSame(10, $metrics->chunks_created);
        $this->assertSame(250.0, $metrics->processing_time_ms);
    }
}
