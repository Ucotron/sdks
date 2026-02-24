<?php

declare(strict_types=1);

namespace Ucotron\Sdk\Tests;

use Ucotron\Sdk\Exceptions\UcotronConnectionException;
use Ucotron\Sdk\Exceptions\UcotronException;
use Ucotron\Sdk\Exceptions\UcotronRetriesExhaustedException;
use Ucotron\Sdk\Exceptions\UcotronServerException;
use PHPUnit\Framework\TestCase;

class ExceptionsTest extends TestCase
{
    public function testUcotronExceptionIsRuntimeException(): void
    {
        $e = new UcotronException('test error');
        $this->assertInstanceOf(\RuntimeException::class, $e);
        $this->assertSame('test error', $e->getMessage());
    }

    public function testServerExceptionContainsStatusCode(): void
    {
        $e = new UcotronServerException(404, '{"error":"not found"}');
        $this->assertSame(404, $e->statusCode);
        $this->assertSame('{"error":"not found"}', $e->errorBody);
        $this->assertStringContainsString('404', $e->getMessage());
        $this->assertInstanceOf(UcotronException::class, $e);
    }

    public function testServerExceptionWithCustomMessage(): void
    {
        $e = new UcotronServerException(500, null, 'Custom error');
        $this->assertSame(500, $e->statusCode);
        $this->assertSame('Custom error', $e->getMessage());
    }

    public function testConnectionException(): void
    {
        $e = new UcotronConnectionException('Connection refused');
        $this->assertSame('Connection refused', $e->getMessage());
        $this->assertInstanceOf(UcotronException::class, $e);
    }

    public function testConnectionExceptionDefaultMessage(): void
    {
        $e = new UcotronConnectionException();
        $this->assertSame('Connection failed', $e->getMessage());
    }

    public function testRetriesExhaustedException(): void
    {
        $e = new UcotronRetriesExhaustedException(3);
        $this->assertSame(3, $e->attempts);
        $this->assertStringContainsString('3', $e->getMessage());
        $this->assertInstanceOf(UcotronException::class, $e);
    }

    public function testExceptionChaining(): void
    {
        $cause = new UcotronConnectionException('timeout');
        $e = new UcotronRetriesExhaustedException(3, previous: $cause);
        $this->assertSame($cause, $e->getPrevious());
    }
}
