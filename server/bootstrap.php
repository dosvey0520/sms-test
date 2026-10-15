<?php
declare(strict_types=1);

$config = require __DIR__ . '/config.php';

function json_out(array $data, int $code = 200)
{
    http_response_code($code);
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: no-store');
    echo json_encode($data, JSON_UNESCAPED_UNICODE);
    exit;
}

set_exception_handler(function ($e) {
    error_log('[sms-gateway] ' . $e);
    json_out(['ok' => false, 'error' => 'server_error'], 500);
});

function db(): PDO
{
    global $config;
    static $pdo = null;
    if ($pdo === null) {
        $pdo = new PDO($config['db']['dsn'], $config['db']['user'], $config['db']['pass'], [
            PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        ]);
    }
    return $pdo;
}

/** $which is 'sender_key' or 'device_key'. Key must be sent in the X-API-Key header. */
function require_key(string $which): void
{
    global $config;
    $expected = (string)($config[$which] ?? '');
    if ($expected === '' || strpos($expected, 'CHANGE_ME') === 0) {
        json_out(['ok' => false, 'error' => 'server_not_configured'], 500);
    }
    $given = (string)($_SERVER['HTTP_X_API_KEY'] ?? '');
    if (!hash_equals($expected, $given)) {
        json_out(['ok' => false, 'error' => 'unauthorized'], 401);
    }
}

function require_method(string $method): void
{
    if ($_SERVER['REQUEST_METHOD'] !== $method) {
        json_out(['ok' => false, 'error' => 'method_not_allowed'], 405);
    }
}

/** Reads a JSON body or a normal form post. */
function input(): array
{
    $ctype = (string)($_SERVER['CONTENT_TYPE'] ?? '');
    if (stripos($ctype, 'json') !== false) {
        $d = json_decode((string)file_get_contents('php://input'), true);
        return is_array($d) ? $d : [];
    }
    return $_POST;
}
