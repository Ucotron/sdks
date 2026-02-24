<?php

declare(strict_types=1);

namespace Ucotron\Sdk\Types;

class CreateMemoryResult
{
    public function __construct(
        public readonly string $id,
        public readonly ?IngestionMetrics $metrics = null,
    ) {}

    public static function fromArray(array $data): self
    {
        return new self(
            id: (string) ($data['id'] ?? ''),
            metrics: isset($data['metrics']) ? IngestionMetrics::fromArray($data['metrics']) : null,
        );
    }
}
