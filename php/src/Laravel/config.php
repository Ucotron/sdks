<?php

// Polyfill env() for use outside Laravel
if (!function_exists('env')) {
    function env(string $key, mixed $default = null): mixed
    {
        $value = getenv($key);
        if ($value === false) {
            return $default;
        }
        // Convert common string representations
        return match (strtolower($value)) {
            'true', '(true)' => true,
            'false', '(false)' => false,
            'null', '(null)' => null,
            default => $value,
        };
    }
}

return [
    /*
    |--------------------------------------------------------------------------
    | Ucotron Server URL
    |--------------------------------------------------------------------------
    |
    | The base URL of your Ucotron server instance.
    |
    */
    'server_url' => env('UCOTRON_SERVER_URL', 'http://localhost:8420'),

    /*
    |--------------------------------------------------------------------------
    | API Key
    |--------------------------------------------------------------------------
    |
    | Optional API key for authenticating with the Ucotron server.
    | Set via UCOTRON_API_KEY environment variable.
    |
    */
    'api_key' => env('UCOTRON_API_KEY'),

    /*
    |--------------------------------------------------------------------------
    | Default Namespace
    |--------------------------------------------------------------------------
    |
    | Default namespace for multi-tenant memory isolation.
    | Can be overridden per-request.
    |
    */
    'namespace' => env('UCOTRON_NAMESPACE'),

    /*
    |--------------------------------------------------------------------------
    | Timeout
    |--------------------------------------------------------------------------
    |
    | HTTP request timeout in seconds.
    |
    */
    'timeout' => (float) env('UCOTRON_TIMEOUT', 30.0),

    /*
    |--------------------------------------------------------------------------
    | Retry Configuration
    |--------------------------------------------------------------------------
    |
    | Configure retry behavior for failed requests.
    | - max_retries: Maximum number of retry attempts for 5xx errors
    | - base_delay_ms: Initial delay in milliseconds (doubles each retry)
    | - max_delay_ms: Maximum delay cap in milliseconds
    |
    */
    'retry' => [
        'max_retries' => (int) env('UCOTRON_RETRY_MAX', 3),
        'base_delay_ms' => (int) env('UCOTRON_RETRY_BASE_DELAY', 100),
        'max_delay_ms' => (int) env('UCOTRON_RETRY_MAX_DELAY', 5000),
    ],
];
