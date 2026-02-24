<?php

declare(strict_types=1);

namespace Ucotron\Sdk;

use GuzzleHttp\Client;
use GuzzleHttp\HandlerStack;
use GuzzleHttp\RequestOptions;
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
use GuzzleHttp\Exception\ConnectException;
use GuzzleHttp\Exception\RequestException;

class UcotronClient
{
    private Client $httpClient;
    private ClientConfig $config;
    private string $baseUrl;

    public function __construct(string $serverUrl, ?ClientConfig $config = null)
    {
        $this->baseUrl = rtrim($serverUrl, '/');
        $this->config = $config ?? new ClientConfig();

        $stack = HandlerStack::create();
        $stack->push(RetryMiddleware::create($this->config->retryConfig), 'retry');
        if ($this->config->logger !== null) {
            $stack->push(LogMiddleware::create($this->config->logger), 'log');
        }

        $this->httpClient = new Client([
            'handler' => $stack,
            'base_uri' => $this->baseUrl,
            'timeout' => $this->config->timeout,
            'connect_timeout' => $this->config->timeout,
            'http_errors' => false,
        ]);
    }

    // --- Memory CRUD ---

    public function addMemory(string $content, ?array $metadata = null, ?string $namespace = null): CreateMemoryResult
    {
        $body = ['content' => $content];
        if ($metadata !== null) {
            $body['metadata'] = $metadata;
        }
        $data = $this->post('/api/v1/memories', $body, $namespace);
        return CreateMemoryResult::fromArray($data);
    }

    public function getMemory(string $id, ?string $namespace = null): MemoryResponse
    {
        $data = $this->get("/api/v1/memories/{$id}", namespace: $namespace);
        return MemoryResponse::fromArray($data);
    }

    /**
     * @return MemoryResponse[]
     */
    public function listMemories(?int $limit = null, ?int $offset = null, ?string $namespace = null): array
    {
        $query = [];
        if ($limit !== null) {
            $query['limit'] = $limit;
        }
        if ($offset !== null) {
            $query['offset'] = $offset;
        }
        $data = $this->get('/api/v1/memories', $query, $namespace);
        return array_map(
            fn(array $item) => MemoryResponse::fromArray($item),
            $data['memories'] ?? $data,
        );
    }

    public function updateMemory(string $id, string $content, ?array $metadata = null, ?string $namespace = null): MemoryResponse
    {
        $body = ['content' => $content];
        if ($metadata !== null) {
            $body['metadata'] = $metadata;
        }
        $data = $this->put("/api/v1/memories/{$id}", $body, $namespace);
        return MemoryResponse::fromArray($data);
    }

    public function deleteMemory(string $id, ?string $namespace = null): void
    {
        $this->delete("/api/v1/memories/{$id}", $namespace);
    }

    // --- Search ---

    public function search(string $query, ?int $topK = null, ?string $namespace = null): SearchResponse
    {
        $body = ['query' => $query];
        if ($topK !== null) {
            $body['top_k'] = $topK;
        }
        $data = $this->post('/api/v1/memories/search', $body, $namespace);
        return SearchResponse::fromArray($data);
    }

    // --- Entities ---

    public function getEntity(string $id, ?string $namespace = null): EntityResponse
    {
        $data = $this->get("/api/v1/entities/{$id}", namespace: $namespace);
        return EntityResponse::fromArray($data);
    }

    /**
     * @return EntityResponse[]
     */
    public function listEntities(?int $limit = null, ?string $namespace = null): array
    {
        $query = [];
        if ($limit !== null) {
            $query['limit'] = $limit;
        }
        $data = $this->get('/api/v1/entities', $query, $namespace);
        return array_map(
            fn(array $item) => EntityResponse::fromArray($item),
            $data['entities'] ?? $data,
        );
    }

    // --- Augment & Learn ---

    public function augment(string $prompt, ?int $topK = null, ?string $namespace = null): AugmentResult
    {
        $body = ['prompt' => $prompt];
        if ($topK !== null) {
            $body['top_k'] = $topK;
        }
        $data = $this->post('/api/v1/augment', $body, $namespace);
        return AugmentResult::fromArray($data);
    }

    public function learn(string $content, ?string $source = null, ?string $namespace = null): LearnResult
    {
        $body = ['content' => $content];
        if ($source !== null) {
            $body['source'] = $source;
        }
        $data = $this->post('/api/v1/learn', $body, $namespace);
        return LearnResult::fromArray($data);
    }

    // --- Health & Metrics ---

    public function health(): HealthResponse
    {
        $data = $this->get('/api/v1/health');
        return HealthResponse::fromArray($data);
    }

    public function metrics(): MetricsResponse
    {
        $data = $this->get('/api/v1/metrics');
        return MetricsResponse::fromArray($data);
    }

    // --- HTTP helpers ---

    /**
     * @return array<string, mixed>
     */
    private function get(string $path, array $query = [], ?string $namespace = null): array
    {
        $options = [
            RequestOptions::HEADERS => $this->buildHeaders($namespace),
        ];
        if (!empty($query)) {
            $options[RequestOptions::QUERY] = $query;
        }
        return $this->request('GET', $path, $options);
    }

    /**
     * @return array<string, mixed>
     */
    private function post(string $path, array $body, ?string $namespace = null): array
    {
        return $this->request('POST', $path, [
            RequestOptions::HEADERS => $this->buildHeaders($namespace),
            RequestOptions::JSON => $body,
        ]);
    }

    /**
     * @return array<string, mixed>
     */
    private function put(string $path, array $body, ?string $namespace = null): array
    {
        return $this->request('PUT', $path, [
            RequestOptions::HEADERS => $this->buildHeaders($namespace),
            RequestOptions::JSON => $body,
        ]);
    }

    private function delete(string $path, ?string $namespace = null): void
    {
        $this->request('DELETE', $path, [
            RequestOptions::HEADERS => $this->buildHeaders($namespace),
        ]);
    }

    /**
     * @return array<string, string>
     */
    private function buildHeaders(?string $namespace = null): array
    {
        $headers = [
            'Accept' => 'application/json',
        ];
        $ns = $namespace ?? $this->config->namespace;
        if ($ns !== null) {
            $headers['X-Ucotron-Namespace'] = $ns;
        }
        if ($this->config->apiKey !== null) {
            $headers['Authorization'] = "Bearer {$this->config->apiKey}";
        }
        return $headers;
    }

    /**
     * Execute an HTTP request. Retry logic is handled by the RetryMiddleware
     * in the Guzzle handler stack.
     *
     * @return array<string, mixed>
     */
    private function request(string $method, string $path, array $options): array
    {
        try {
            $response = $this->httpClient->request($method, $path, $options);
        } catch (ConnectException $e) {
            throw new UcotronConnectionException($e->getMessage(), $e);
        } catch (RequestException $e) {
            throw new UcotronConnectionException($e->getMessage(), $e);
        }

        $statusCode = $response->getStatusCode();
        $body = (string) $response->getBody();

        if ($statusCode >= 200 && $statusCode < 300) {
            if ($statusCode === 204 || $body === '') {
                return [];
            }
            $decoded = json_decode($body, true);
            if (json_last_error() !== JSON_ERROR_NONE) {
                return ['raw' => $body];
            }
            return $decoded;
        }

        throw new UcotronServerException($statusCode, $body);
    }
}
