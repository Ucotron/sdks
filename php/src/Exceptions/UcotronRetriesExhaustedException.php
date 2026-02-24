<?php

declare(strict_types=1);

namespace Ucotron\Sdk\Exceptions;

class UcotronRetriesExhaustedException extends UcotronException
{
    public function __construct(
        public readonly int $attempts,
        string $message = '',
        ?\Throwable $previous = null,
    ) {
        $msg = $message ?: "All {$attempts} retry attempts exhausted";
        parent::__construct($msg, 0, $previous);
    }
}
