<?php

declare(strict_types=1);

namespace Ucotron\Sdk\Types;

class HealthResponse
{
    public function __construct(
        public readonly string $status,
        public readonly ?string $version = null,
        public readonly ?float $uptime_seconds = null,
    ) {}

    public static function fromArray(array $data): self
    {
        return new self(
            status: $data['status'] ?? 'unknown',
            version: $data['version'] ?? null,
            uptime_seconds: isset($data['uptime_seconds']) ? (float) $data['uptime_seconds'] : null,
        );
    }
}
