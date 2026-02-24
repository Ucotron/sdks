<?php

declare(strict_types=1);

namespace Ucotron\Sdk;

use Psr\Log\LoggerInterface;

class ClientConfig
{
    public function __construct(
        public readonly float $timeout = 30.0,
        public readonly ?string $apiKey = null,
        public readonly ?string $namespace = null,
        public readonly RetryConfig $retryConfig = new RetryConfig(),
        public readonly ?LoggerInterface $logger = null,
    ) {}
}
