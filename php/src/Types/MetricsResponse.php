<?php

declare(strict_types=1);

namespace Ucotron\Sdk\Types;

class MetricsResponse
{
    /**
     * @param ModelStatus[] $models
     */
    public function __construct(
        public readonly int $total_memories,
        public readonly int $total_entities,
        public readonly int $total_namespaces,
        public readonly array $models = [],
    ) {}

    public static function fromArray(array $data): self
    {
        $models = array_map(
            fn(array $item) => ModelStatus::fromArray($item),
            $data['models'] ?? [],
        );
        return new self(
            total_memories: (int) ($data['total_memories'] ?? 0),
            total_entities: (int) ($data['total_entities'] ?? 0),
            total_namespaces: (int) ($data['total_namespaces'] ?? 0),
            models: $models,
        );
    }
}
