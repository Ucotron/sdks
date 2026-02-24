<?php

declare(strict_types=1);

namespace Ucotron\Sdk\Symfony;

/**
 * Symfony bundle for the Ucotron SDK.
 *
 * Registers UcotronClient as a service in the Symfony DI container.
 *
 * Configuration via config/packages/ucotron.yaml:
 *
 *   ucotron:
 *     server_url: '%env(UCOTRON_SERVER_URL)%'
 *     api_key: '%env(UCOTRON_API_KEY)%'
 *     namespace: ~
 *     timeout: 30.0
 *     retry:
 *       max_retries: 3
 *       base_delay_ms: 100
 *       max_delay_ms: 5000
 *
 * Usage:
 *   // In a Symfony controller or service
 *   public function __construct(private UcotronClient $ucotron) {}
 *
 *   // Or via service container
 *   $ucotron = $container->get('ucotron.client');
 *
 * Registration in config/bundles.php:
 *   return [
 *       // ...
 *       Ucotron\Sdk\Symfony\UcotronBundle::class => ['all' => true],
 *   ];
 *
 * This class does not extend Symfony\Component\HttpKernel\Bundle\Bundle
 * to avoid requiring symfony packages as compile-time dependencies.
 * When used in Symfony, the framework handles bundle instantiation.
 */
class UcotronBundle
{
    /**
     * Returns the bundle extension that handles configuration loading.
     *
     * In a real Symfony app, this would return a ContainerExtension.
     * Here we return a UcotronExtension instance.
     */
    public function getContainerExtension(): UcotronExtension
    {
        return new UcotronExtension();
    }

    /**
     * Get the bundle alias used as the configuration root key.
     */
    public function getAlias(): string
    {
        return 'ucotron';
    }
}
