<?php

declare(strict_types=1);

namespace Ucotron\Sdk\Exceptions;

class UcotronConnectionException extends UcotronException
{
    public function __construct(string $message = 'Connection failed', ?\Throwable $previous = null)
    {
        parent::__construct($message, 0, $previous);
    }
}
