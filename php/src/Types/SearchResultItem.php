<?php

declare(strict_types=1);

namespace Ucotron\Sdk\Types;

class SearchResultItem
{
    public function __construct(
        public readonly string $id,
        public readonly string $content,
        public readonly float $score,
        public readonly ?string $node_type = null,
        public readonly ?int $timestamp = null,
        public readonly ?array $metadata = null,
    ) {}

    public static function fromArray(array $data): self
    {
        return new self(
            id: (string) ($data['id'] ?? ''),
            content: $data['content'] ?? '',
            score: (float) ($data['score'] ?? 0.0),
            node_type: $data['node_type'] ?? null,
            timestamp: isset($data['timestamp']) ? (int) $data['timestamp'] : null,
            metadata: $data['metadata'] ?? null,
        );
    }
}
