<?php

declare(strict_types=1);

namespace Ucotron\Sdk\Exceptions;

class UcotronServerException extends UcotronException
{
    public function __construct(
        public readonly int $statusCode,
        public readonly ?string $errorBody,
        string $message = '',
        ?\Throwable $previous = null,
    ) {
        $msg = $message ?: "Server returned HTTP {$statusCode}";
        if ($errorBody !== null) {
            $msg .= ": {$errorBody}";
        }
        parent::__construct($msg, $statusCode, $previous);
    }
}
