<?php

declare(strict_types=1);

namespace Ucotron\Sdk\Types;

class ModelStatus
{
    public function __construct(
        public readonly string $name,
        public readonly bool $loaded,
        public readonly ?string $version = null,
    ) {}

    public static function fromArray(array $data): self
    {
        return new self(
            name: $data['name'] ?? '',
            loaded: (bool) ($data['loaded'] ?? false),
            version: $data['version'] ?? null,
        );
    }
}
