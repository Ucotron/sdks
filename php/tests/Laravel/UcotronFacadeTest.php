<?php

declare(strict_types=1);

namespace Ucotron\Sdk\Tests\Laravel;

use Ucotron\Sdk\Laravel\UcotronFacade;
use Ucotron\Sdk\UcotronClient;
use Ucotron\Sdk\Types\HealthResponse;
use Ucotron\Sdk\Types\SearchResponse;
use PHPUnit\Framework\TestCase;

class UcotronFacadeTest extends TestCase
{
    protected function tearDown(): void
    {
        UcotronFacade::clearResolvedInstance();
    }

    public function testSwapAllowsMockInjection(): void
    {
        $mock = $this->createMock(UcotronClient::class);
        $mock->method('health')->willReturn(
            HealthResponse::fromArray(['status' => 'ok', 'version' => '1.0.0'])
        );

        UcotronFacade::swap($mock);

        $health = UcotronFacade::health();
        $this->assertSame('ok', $health->status);
        $this->assertSame('1.0.0', $health->version);
    }

    public function testSearchViaFacade(): void
    {
        $mock = $this->createMock(UcotronClient::class);
        $mock->method('search')->willReturn(
            SearchResponse::fromArray(['results' => []])
        );

        UcotronFacade::swap($mock);

        $result = UcotronFacade::search('test query');
        $this->assertInstanceOf(SearchResponse::class, $result);
        $this->assertEmpty($result->results);
    }

    public function testSetResolverUsesCallback(): void
    {
        $mock = $this->createMock(UcotronClient::class);
        $mock->method('health')->willReturn(
            HealthResponse::fromArray(['status' => 'healthy'])
        );

        $resolverCalled = false;
        UcotronFacade::setResolver(function () use ($mock, &$resolverCalled) {
            $resolverCalled = true;
            return $mock;
        });

        $health = UcotronFacade::health();
        $this->assertTrue($resolverCalled);
        $this->assertSame('healthy', $health->status);
    }

    public function testResolverCachesInstance(): void
    {
        $callCount = 0;
        $mock = $this->createMock(UcotronClient::class);

        UcotronFacade::setResolver(function () use ($mock, &$callCount) {
            $callCount++;
            return $mock;
        });

        UcotronFacade::health();
        UcotronFacade::health();

        $this->assertSame(1, $callCount); // Resolver called only once
    }

    public function testClearResolvedInstanceResetsState(): void
    {
        $mock = $this->createMock(UcotronClient::class);
        UcotronFacade::swap($mock);

        UcotronFacade::clearResolvedInstance();

        $this->expectException(\RuntimeException::class);
        $this->expectExceptionMessage('UcotronFacade has not been resolved');
        UcotronFacade::health();
    }

    public function testUnresolvedFacadeThrowsRuntimeException(): void
    {
        $this->expectException(\RuntimeException::class);
        $this->expectExceptionMessage('UcotronFacade has not been resolved');
        UcotronFacade::health();
    }

    public function testSetResolverOverridesSwap(): void
    {
        $mock1 = $this->createMock(UcotronClient::class);
        $mock1->method('health')->willReturn(
            HealthResponse::fromArray(['status' => 'first'])
        );

        $mock2 = $this->createMock(UcotronClient::class);
        $mock2->method('health')->willReturn(
            HealthResponse::fromArray(['status' => 'second'])
        );

        UcotronFacade::swap($mock1);
        $this->assertSame('first', UcotronFacade::health()->status);

        // setResolver clears the resolved instance
        UcotronFacade::setResolver(fn () => $mock2);
        $this->assertSame('second', UcotronFacade::health()->status);
    }

    public function testFacadeProxiesMethodArguments(): void
    {
        $mock = $this->createMock(UcotronClient::class);
        $mock->expects($this->once())
            ->method('search')
            ->with('my query', 10, 'my-ns')
            ->willReturn(SearchResponse::fromArray(['results' => []]));

        UcotronFacade::swap($mock);
        UcotronFacade::search('my query', 10, 'my-ns');
    }
}
