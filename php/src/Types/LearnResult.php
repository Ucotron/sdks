<?php

declare(strict_types=1);

namespace Ucotron\Sdk\Types;

class LearnResult
{
    public function __construct(
        public readonly int $entities_extracted,
        public readonly int $relations_extracted,
        public readonly int $memories_stored,
    ) {}

    public static function fromArray(array $data): self
    {
        return new self(
            entities_extracted: (int) ($data['entities_extracted'] ?? 0),
            relations_extracted: (int) ($data['relations_extracted'] ?? 0),
            memories_stored: (int) ($data['memories_stored'] ?? 0),
        );
    }
}
