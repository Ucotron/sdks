<?php

declare(strict_types=1);

namespace Ucotron\Sdk;

use GuzzleHttp\Exception\ConnectException;
use GuzzleHttp\Promise\Create;
use GuzzleHttp\Promise\PromiseInterface;
use Psr\Http\Message\RequestInterface;
use Psr\Http\Message\ResponseInterface;

/**
 * Guzzle middleware that retries requests on 5xx errors and connection failures.
 *
 * Uses exponential backoff with jitter. 4xx errors are never retried.
 * Integrates into the Guzzle handler stack for both sync and async clients.
 */
class RetryMiddleware
{
    private RetryConfig $config;

    public function __construct(?RetryConfig $config = null)
    {
        $this->config = $config ?? new RetryConfig();
    }

    /**
     * Create the middleware callable for the Guzzle handler stack.
     *
     * Usage:
     *   $stack = HandlerStack::create();
     *   $stack->push(RetryMiddleware::create($retryConfig));
     */
    public static function create(?RetryConfig $config = null): callable
    {
        $middleware = new self($config);
        return $middleware->__invoke(...);
    }

    /**
     * Guzzle middleware invocation: returns a handler wrapper.
     */
    public function __invoke(callable $handler): callable
    {
        return function (RequestInterface $request, array $options) use ($handler): PromiseInterface {
            return $this->retry($handler, $request, $options, 0);
        };
    }

    private function retry(callable $handler, RequestInterface $request, array $options, int $attempt): PromiseInterface
    {
        return $handler($request, $options)->then(
            function (ResponseInterface $response) use ($handler, $request, $options, $attempt) {
                $statusCode = $response->getStatusCode();

                // 2xx-3xx: success — pass through
                if ($statusCode < 500) {
                    return $response;
                }

                // 5xx: retryable
                if ($attempt < $this->config->maxRetries) {
                    $this->sleep($attempt + 1);
                    return $this->retry($handler, $request, $options, $attempt + 1);
                }

                // Exhausted retries — return the last 5xx response as-is
                return $response;
            },
            function ($reason) use ($handler, $request, $options, $attempt) {
                // Connection errors are retryable
                if ($reason instanceof ConnectException && $attempt < $this->config->maxRetries) {
                    $this->sleep($attempt + 1);
                    return $this->retry($handler, $request, $options, $attempt + 1);
                }

                // Non-retryable or retries exhausted — re-reject
                return Create::rejectionFor($reason);
            }
        );
    }

    private function sleep(int $attempt): void
    {
        $delay = $this->calculateDelay($attempt);
        if ($delay > 0) {
            usleep($delay * 1000);
        }
    }

    /**
     * Exponential backoff with jitter.
     *
     * delay = min(baseDelay * 2^(attempt-1) + jitter, maxDelay)
     * jitter is 0-25% of the base delay for that attempt.
     */
    public function calculateDelay(int $attempt): int
    {
        $delay = $this->config->baseDelayMs * (2 ** ($attempt - 1));
        $jitter = (int) ($delay * 0.25 * (mt_rand() / mt_getrandmax()));
        $delay += $jitter;
        return min($delay, $this->config->maxDelayMs);
    }

    public function getConfig(): RetryConfig
    {
        return $this->config;
    }
}
