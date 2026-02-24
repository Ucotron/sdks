<?php

declare(strict_types=1);

namespace Ucotron\Sdk\Types;

class AugmentResult
{
    /**
     * @param SearchResultItem[] $memories
     */
    public function __construct(
        public readonly string $augmented_prompt,
        public readonly array $memories = [],
    ) {}

    public static function fromArray(array $data): self
    {
        $memories = array_map(
            fn(array $item) => SearchResultItem::fromArray($item),
            $data['memories'] ?? [],
        );
        return new self(
            augmented_prompt: $data['augmented_prompt'] ?? '',
            memories: $memories,
        );
    }
}
