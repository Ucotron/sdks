<?php

declare(strict_types=1);

namespace Ucotron\Sdk\Types;

class NeighborResponse
{
    public function __construct(
        public readonly string $id,
        public readonly string $name,
        public readonly string $edge_type,
        public readonly float $weight,
    ) {}

    public static function fromArray(array $data): self
    {
        return new self(
            id: (string) ($data['id'] ?? ''),
            name: $data['name'] ?? '',
            edge_type: $data['edge_type'] ?? '',
            weight: (float) ($data['weight'] ?? 0.0),
        );
    }
}
