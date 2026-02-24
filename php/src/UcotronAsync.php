<?php

declare(strict_types=1);

namespace Ucotron\Sdk;

use GuzzleHttp\Client;
use GuzzleHttp\Exception\ConnectException;
use GuzzleHttp\Exception\RequestException;
use GuzzleHttp\HandlerStack;
use GuzzleHttp\Promise\PromiseInterface;
use GuzzleHttp\Promise\Utils;
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

/**
 * Async Ucotron client using Guzzle promises for concurrent requests.
 *
 * All methods return PromiseInterface. Use ->wait() for blocking resolution
 * or ->then() for chaining. Use UcotronAsync::batch() for concurrent requests.
 *
 * Retry logic is handled by RetryMiddleware in the Guzzle handler stack,
 * automatically retrying 5xx errors and connection failures with exponential backoff.
 */
class UcotronAsync
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

    public function getConfig(): ClientConfig
    {
        return $this->config;
    }

    // --- Memory CRUD ---

    /**
     * @return PromiseInterface<CreateMemoryResult>
     */
    public function addMemoryAsync(string $content, ?array $metadata = null, ?string $namespace = null): PromiseInterface
    {
        $body = ['content' => $content];
        if ($metadata !== null) {
            $body['metadata'] = $metadata;
        }
        return $this->postAsync('/api/v1/memories', $body, $namespace)
            ->then(fn(array $data) => CreateMemoryResult::fromArray($data));
    }

    /**
     * @return PromiseInterface<MemoryResponse>
     */
    public function getMemoryAsync(string $id, ?string $namespace = null): PromiseInterface
    {
        return $this->getAsync("/api/v1/memories/{$id}", namespace: $namespace)
            ->then(fn(array $data) => MemoryResponse::fromArray($data));
    }

    /**
     * @return PromiseInterface<MemoryResponse[]>
     */
    public function listMemoriesAsync(?int $limit = null, ?int $offset = null, ?string $namespace = null): PromiseInterface
    {
        $query = [];
        if ($limit !== null) {
            $query['limit'] = $limit;
        }
        if ($offset !== null) {
            $query['offset'] = $offset;
        }
        return $this->getAsync('/api/v1/memories', $query, $namespace)
            ->then(fn(array $data) => array_map(
                fn(array $item) => MemoryResponse::fromArray($item),
                $data['memories'] ?? $data,
            ));
    }

    /**
     * @return PromiseInterface<MemoryResponse>
     */
    public function updateMemoryAsync(string $id, string $content, ?array $metadata = null, ?string $namespace = null): PromiseInterface
    {
        $body = ['content' => $content];
        if ($metadata !== null) {
            $body['metadata'] = $metadata;
        }
        return $this->putAsync("/api/v1/memories/{$id}", $body, $namespace)
            ->then(fn(array $data) => MemoryResponse::fromArray($data));
    }

    /**
     * @return PromiseInterface<void>
     */
    public function deleteMemoryAsync(string $id, ?string $namespace = null): PromiseInterface
    {
        return $this->deleteAsync("/api/v1/memories/{$id}", $namespace)
            ->then(fn() => null);
    }

    // --- Search ---

    /**
     * @return PromiseInterface<SearchResponse>
     */
    public function searchAsync(string $query, ?int $topK = null, ?string $namespace = null): PromiseInterface
    {
        $body = ['query' => $query];
        if ($topK !== null) {
            $body['top_k'] = $topK;
        }
        return $this->postAsync('/api/v1/memories/search', $body, $namespace)
            ->then(fn(array $data) => SearchResponse::fromArray($data));
    }

    // --- Entities ---

    /**
     * @return PromiseInterface<EntityResponse>
     */
    public function getEntityAsync(string $id, ?string $namespace = null): PromiseInterface
    {
        return $this->getAsync("/api/v1/entities/{$id}", namespace: $namespace)
            ->then(fn(array $data) => EntityResponse::fromArray($data));
    }

    /**
     * @return PromiseInterface<EntityResponse[]>
     */
    public function listEntitiesAsync(?int $limit = null, ?string $namespace = null): PromiseInterface
    {
        $query = [];
        if ($limit !== null) {
            $query['limit'] = $limit;
        }
        return $this->getAsync('/api/v1/entities', $query, $namespace)
            ->then(fn(array $data) => array_map(
                fn(array $item) => EntityResponse::fromArray($item),
                $data['entities'] ?? $data,
            ));
    }

    // --- Augment & Learn ---

    /**
     * @return PromiseInterface<AugmentResult>
     */
    public function augmentAsync(string $prompt, ?int $topK = null, ?string $namespace = null): PromiseInterface
    {
        $body = ['prompt' => $prompt];
        if ($topK !== null) {
            $body['top_k'] = $topK;
        }
        return $this->postAsync('/api/v1/augment', $body, $namespace)
            ->then(fn(array $data) => AugmentResult::fromArray($data));
    }

    /**
     * @return PromiseInterface<LearnResult>
     */
    public function learnAsync(string $content, ?string $source = null, ?string $namespace = null): PromiseInterface
    {
        $body = ['content' => $content];
        if ($source !== null) {
            $body['source'] = $source;
        }
        return $this->postAsync('/api/v1/learn', $body, $namespace)
            ->then(fn(array $data) => LearnResult::fromArray($data));
    }

    // --- Health & Metrics ---

    /**
     * @return PromiseInterface<HealthResponse>
     */
    public function healthAsync(): PromiseInterface
    {
        return $this->getAsync('/api/v1/health')
            ->then(fn(array $data) => HealthResponse::fromArray($data));
    }

    /**
     * @return PromiseInterface<MetricsResponse>
     */
    public function metricsAsync(): PromiseInterface
    {
        return $this->getAsync('/api/v1/metrics')
            ->then(fn(array $data) => MetricsResponse::fromArray($data));
    }

    // --- Concurrent request batching ---

    /**
     * Execute multiple promises concurrently and return all results.
     *
     * @param array<string, PromiseInterface> $promises Named promises
     * @return array<string, mixed> Results keyed by promise name
     * @throws \Throwable If any promise rejects
     */
    public static function batch(array $promises): array
    {
        return Utils::unwrap($promises);
    }

    /**
     * Settle all promises and return results with state information.
     *
     * Unlike batch(), this does not throw on rejection. Each result is an array
     * with 'state' ('fulfilled' or 'rejected') and 'value' or 'reason'.
     *
     * @param array<string, PromiseInterface> $promises Named promises
     * @return array<string, array{state: string, value?: mixed, reason?: \Throwable}>
     */
    public static function settle(array $promises): array
    {
        return Utils::settle($promises)->wait();
    }

    // --- Async HTTP helpers ---

    /**
     * @return PromiseInterface<array<string, mixed>>
     */
    private function getAsync(string $path, array $query = [], ?string $namespace = null): PromiseInterface
    {
        $options = [
            RequestOptions::HEADERS => $this->buildHeaders($namespace),
        ];
        if (!empty($query)) {
            $options[RequestOptions::QUERY] = $query;
        }
        return $this->requestAsync('GET', $path, $options);
    }

    /**
     * @return PromiseInterface<array<string, mixed>>
     */
    private function postAsync(string $path, array $body, ?string $namespace = null): PromiseInterface
    {
        return $this->requestAsync('POST', $path, [
            RequestOptions::HEADERS => $this->buildHeaders($namespace),
            RequestOptions::JSON => $body,
        ]);
    }

    /**
     * @return PromiseInterface<array<string, mixed>>
     */
    private function putAsync(string $path, array $body, ?string $namespace = null): PromiseInterface
    {
        return $this->requestAsync('PUT', $path, [
            RequestOptions::HEADERS => $this->buildHeaders($namespace),
            RequestOptions::JSON => $body,
        ]);
    }

    /**
     * @return PromiseInterface<array<string, mixed>>
     */
    private function deleteAsync(string $path, ?string $namespace = null): PromiseInterface
    {
        return $this->requestAsync('DELETE', $path, [
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
     * @return PromiseInterface<array<string, mixed>>
     */
    private function requestAsync(string $method, string $path, array $options): PromiseInterface
    {
        return $this->httpClient->requestAsync($method, $path, $options)
            ->then(
                function ($response) {
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
                },
                function ($reason) {
                    if ($reason instanceof ConnectException) {
                        throw new UcotronConnectionException($reason->getMessage(), $reason);
                    }
                    if ($reason instanceof RequestException) {
                        $response = $reason->getResponse();
                        if ($response !== null) {
                            throw new UcotronServerException(
                                $response->getStatusCode(),
                                (string) $response->getBody(),
                            );
                        }
                        throw new UcotronConnectionException($reason->getMessage(), $reason);
                    }
                    if ($reason instanceof \Throwable) {
                        throw new UcotronConnectionException($reason->getMessage(), $reason);
                    }
                    throw new UcotronConnectionException('Request failed');
                }
            );
    }
}
