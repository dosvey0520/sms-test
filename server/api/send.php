<?php
declare(strict_types=1);
require __DIR__ . '/../bootstrap.php';

// Called by YOUR PHP system to queue an SMS.
require_key('sender_key');
require_method('POST');

$in    = input();
$phone = preg_replace('/[\s\-\(\)\.]/', '', (string)($in['phone'] ?? ''));
$msg   = trim((string)($in['message'] ?? ''));
$ref   = substr((string)($in['ref'] ?? ''), 0, 64);

if (!preg_match('/^\+?[0-9]{7,15}$/', $phone)) {
    json_out(['ok' => false, 'error' => 'invalid_phone'], 422);
}
if ($msg === '' || strlen($msg) > 2000) {
    json_out(['ok' => false, 'error' => 'invalid_message'], 422);
}

$st = db()->prepare('INSERT INTO sms_queue (phone, message, ref) VALUES (?, ?, ?)');
$st->execute([$phone, $msg, $ref === '' ? null : $ref]);

json_out(['ok' => true, 'id' => (int)db()->lastInsertId(), 'status' => 'pending'], 201);
