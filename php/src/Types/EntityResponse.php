<?php

declare(strict_types=1);

namespace Ucotron\Sdk\Types;

class EntityResponse
{
    /**
     * @param NeighborResponse[] $neighbors
     */
    public function __construct(
        public readonly string $id,
        public readonly string $name,
        public readonly ?string $entity_type = null,
        public readonly ?array $metadata = null,
        public readonly array $neighbors = [],
    ) {}

    public static function fromArray(array $data): self
    {
        $neighbors = array_map(
            fn(array $item) => NeighborResponse::fromArray($item),
            $data['neighbors'] ?? [],
        );
        return new self(
            id: (string) ($data['id'] ?? ''),
            name: $data['name'] ?? '',
            entity_type: $data['entity_type'] ?? null,
            metadata: $data['metadata'] ?? null,
            neighbors: $neighbors,
        );
    }
}
