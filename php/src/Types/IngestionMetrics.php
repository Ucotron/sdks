<?php

declare(strict_types=1);

namespace Ucotron\Sdk\Types;

class IngestionMetrics
{
    public function __construct(
        public readonly int $chunks_created,
        public readonly int $entities_extracted,
        public readonly int $relations_extracted,
        public readonly float $processing_time_ms,
    ) {}

    public static function fromArray(array $data): self
    {
        return new self(
            chunks_created: (int) ($data['chunks_created'] ?? 0),
            entities_extracted: (int) ($data['entities_extracted'] ?? 0),
            relations_extracted: (int) ($data['relations_extracted'] ?? 0),
            processing_time_ms: (float) ($data['processing_time_ms'] ?? 0.0),
        );
    }
}
