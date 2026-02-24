<?php

declare(strict_types=1);

namespace Ucotron\Sdk\Types;

class SearchResponse
{
    /**
     * @param SearchResultItem[] $results
     */
    public function __construct(
        public readonly array $results,
    ) {}

    public static function fromArray(array $data): self
    {
        $results = array_map(
            fn(array $item) => SearchResultItem::fromArray($item),
            $data['results'] ?? [],
        );
        return new self(results: $results);
    }
}
