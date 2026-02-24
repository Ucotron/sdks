<?php

declare(strict_types=1);

namespace Ucotron\Sdk;

use GuzzleHttp\Exception\ConnectException;
use GuzzleHttp\Promise\Create;
use GuzzleHttp\Promise\PromiseInterface;
use Psr\Http\Message\RequestInterface;
use Psr\Http\Message\ResponseInterface;
use Psr\Log\LoggerInterface;
use Psr\Log\LogLevel;

/**
 * Guzzle middleware that logs HTTP requests and responses using PSR-3 LoggerInterface.
 *
 * Logs request method, URI, response status code, and duration.
 * Integrates with any PSR-3 logger (e.g., Monolog).
 *
 * Usage:
 *   $stack = HandlerStack::create();
 *   $stack->push(LogMiddleware::create($logger));
 */
class LogMiddleware
{
    private LoggerInterface $logger;
    private string $successLevel;
    private string $errorLevel;

    public function __construct(
        LoggerInterface $logger,
        string $successLevel = LogLevel::INFO,
        string $errorLevel = LogLevel::ERROR,
    ) {
        $this->logger = $logger;
        $this->successLevel = $successLevel;
        $this->errorLevel = $errorLevel;
    }

    /**
     * Create the middleware callable for the Guzzle handler stack.
     */
    public static function create(
        LoggerInterface $logger,
        string $successLevel = LogLevel::INFO,
        string $errorLevel = LogLevel::ERROR,
    ): callable {
        $middleware = new self($logger, $successLevel, $errorLevel);
        return $middleware->__invoke(...);
    }

    /**
     * Guzzle middleware invocation: returns a handler wrapper.
     */
    public function __invoke(callable $handler): callable
    {
        return function (RequestInterface $request, array $options) use ($handler): PromiseInterface {
            $startTime = hrtime(true);
            $method = $request->getMethod();
            $uri = (string) $request->getUri();

            $this->logger->debug('Ucotron SDK request', [
                'method' => $method,
                'uri' => $uri,
            ]);

            return $handler($request, $options)->then(
                function (ResponseInterface $response) use ($startTime, $method, $uri) {
                    $durationMs = $this->calculateDurationMs($startTime);
                    $statusCode = $response->getStatusCode();

                    $context = [
                        'method' => $method,
                        'uri' => $uri,
                        'status' => $statusCode,
                        'duration_ms' => $durationMs,
                    ];

                    if ($statusCode >= 400) {
                        $this->logger->log($this->errorLevel, 'Ucotron SDK response error', $context);
                    } else {
                        $this->logger->log($this->successLevel, 'Ucotron SDK response', $context);
                    }

                    return $response;
                },
                function ($reason) use ($startTime, $method, $uri) {
                    $durationMs = $this->calculateDurationMs($startTime);

                    $context = [
                        'method' => $method,
                        'uri' => $uri,
                        'duration_ms' => $durationMs,
                        'error' => $reason instanceof \Throwable ? $reason->getMessage() : 'Unknown error',
                    ];

                    $this->logger->log($this->errorLevel, 'Ucotron SDK request failed', $context);

                    return Create::rejectionFor($reason);
                }
            );
        };
    }

    private function calculateDurationMs(int $startNano): float
    {
        return round((hrtime(true) - $startNano) / 1_000_000, 2);
    }
}
