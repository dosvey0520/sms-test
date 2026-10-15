<?php
declare(strict_types=1);
require __DIR__ . '/../bootstrap.php';

// Called by the Android app after each send attempt.
require_key('device_key');
require_method('POST');

$in     = input();
$id     = (int)($in['id'] ?? 0);
$status = (string)($in['status'] ?? '');
$error  = substr((string)($in['error'] ?? ''), 0, 255);

if ($id <= 0 || !in_array($status, ['sent', 'failed'], true)) {
    json_out(['ok' => false, 'error' => 'invalid_request'], 422);
}

$pdo = db();
if ($status === 'sent') {
    $st = $pdo->prepare("UPDATE sms_queue SET status = 'sent', sent_at = NOW(), error = NULL WHERE id = ?");
    $st->execute([$id]);
} else {
    // Retry until max attempts, then mark failed.
    $st = $pdo->prepare("UPDATE sms_queue
                         SET status = IF(attempts >= ?, 'failed', 'pending'), error = ?
                         WHERE id = ? AND status = 'sending'");
    $st->execute([(int)$config['max_attempts'], $error, $id]);
}

json_out(['ok' => true]);
