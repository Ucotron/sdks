<?php

declare(strict_types=1);

namespace Ucotron\Sdk\Laravel;

use Ucotron\Sdk\UcotronClient;

/**
 * Laravel facade for the Ucotron SDK.
 *
 * Provides static access to the UcotronClient singleton.
 *
 * Usage in config/app.php (Laravel < 11):
 *   'aliases' => [
 *       'Ucotron' => Ucotron\Sdk\Laravel\UcotronFacade::class,
 *   ],
 *
 * Auto-discovery (Laravel 5.5+):
 *   This facade is auto-discovered via composer.json extra.laravel.aliases.
 *
 * Example usage:
 *   use Ucotron\Sdk\Laravel\UcotronFacade as Ucotron;
 *
 *   $result = Ucotron::search('recent conversations');
 *   $memory = Ucotron::addMemory('User prefers dark mode');
 *   $health = Ucotron::health();
 *   $entities = Ucotron::listEntities();
 *
 * Available methods (proxied to UcotronClient):
 * @method static \Ucotron\Sdk\Types\CreateMemoryResult addMemory(string $content, ?array $metadata = null, ?string $namespace = null)
 * @method static \Ucotron\Sdk\Types\MemoryResponse getMemory(string $id, ?string $namespace = null)
 * @method static \Ucotron\Sdk\Types\MemoryResponse[] listMemories(?int $limit = null, ?int $offset = null, ?string $namespace = null)
 * @method static \Ucotron\Sdk\Types\MemoryResponse updateMemory(string $id, string $content, ?array $metadata = null, ?string $namespace = null)
 * @method static void deleteMemory(string $id, ?string $namespace = null)
 * @method static \Ucotron\Sdk\Types\SearchResponse search(string $query, ?int $topK = null, ?string $namespace = null)
 * @method static \Ucotron\Sdk\Types\EntityResponse getEntity(string $id, ?string $namespace = null)
 * @method static \Ucotron\Sdk\Types\EntityResponse[] listEntities(?int $limit = null, ?string $namespace = null)
 * @method static \Ucotron\Sdk\Types\AugmentResult augment(string $prompt, ?int $topK = null, ?string $namespace = null)
 * @method static \Ucotron\Sdk\Types\LearnResult learn(string $content, ?string $source = null, ?string $namespace = null)
 * @method static \Ucotron\Sdk\Types\HealthResponse health()
 * @method static \Ucotron\Sdk\Types\MetricsResponse metrics()
 */
class UcotronFacade
{
    /**
     * The resolved UcotronClient instance.
     */
    protected static ?UcotronClient $resolvedInstance = null;

    /**
     * The resolver callback for testing.
     *
     * @var (callable(): UcotronClient)|null
     */
    protected static $resolver = null;

    /**
     * Get the registered name of the component in the container.
     */
    protected static function getFacadeAccessor(): string
    {
        return 'ucotron';
    }

    /**
     * Resolve the facade instance from the container or resolver.
     */
    protected static function resolveFacadeInstance(): UcotronClient
    {
        if (static::$resolvedInstance !== null) {
            return static::$resolvedInstance;
        }

        if (static::$resolver !== null) {
            static::$resolvedInstance = (static::$resolver)();
            return static::$resolvedInstance;
        }

        throw new \RuntimeException(
            'UcotronFacade has not been resolved. Register UcotronServiceProvider or call Ucotron::swap().'
        );
    }

    /**
     * Set a custom resolver for the facade (useful for testing and non-Laravel usage).
     *
     * @param callable(): UcotronClient $resolver
     */
    public static function setResolver(callable $resolver): void
    {
        static::$resolver = $resolver;
        static::$resolvedInstance = null;
    }

    /**
     * Swap the underlying instance (useful for testing with mocks).
     */
    public static function swap(UcotronClient $instance): void
    {
        static::$resolvedInstance = $instance;
    }

    /**
     * Clear the resolved instance and resolver.
     */
    public static function clearResolvedInstance(): void
    {
        static::$resolvedInstance = null;
        static::$resolver = null;
    }

    /**
     * Handle dynamic static calls by proxying to the resolved UcotronClient.
     *
     * @param string $method
     * @param array<mixed> $args
     * @return mixed
     */
    public static function __callStatic(string $method, array $args): mixed
    {
        $instance = static::resolveFacadeInstance();
        return $instance->$method(...$args);
    }
}
