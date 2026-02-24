<?php

declare(strict_types=1);

namespace Ucotron\Sdk;

class RetryConfig
{
    public function __construct(
        public readonly int $maxRetries = 3,
        public readonly int $baseDelayMs = 100,
        public readonly int $maxDelayMs = 5000,
    ) {}
}
