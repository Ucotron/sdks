# Ucotron PHP SDK

PHP client for [Ucotron](https://github.com/Ucotron/ucotron) — cognitive trust infrastructure for AI.

Includes integrations for **Laravel** and **Symfony**.

## Install

```bash
composer require ucotron/sdk
```

Requires PHP 8.1+.

## Usage

```php
use Ucotron\UcotronClient;

$client = new UcotronClient('http://localhost:8420');

// Augment
$result = $client->augment('Tell me about the user');
echo $result->contextText;

// Learn
$client->learn('User prefers dark mode and speaks Spanish.');

// Search
$results = $client->search('preferences');
foreach ($results->results as $item) {
    echo "{$item->content} (score: {$item->score})\n";
}
```

### Laravel

Publish the config:

```bash
php artisan vendor:publish --tag=ucotron-config
```

```php
// config/ucotron.php
return [
    'server_url' => env('UCOTRON_URL', 'http://localhost:8420'),
    'namespace' => env('UCOTRON_NAMESPACE', 'default'),
];
```

```php
use Ucotron\Laravel\UcotronFacade as Ucotron;

$result = Ucotron::augment('context');
```

### Symfony

Register the bundle:

```php
// config/bundles.php
return [
    Ucotron\Symfony\UcotronBundle::class => ['all' => true],
];
```

## License

MIT
