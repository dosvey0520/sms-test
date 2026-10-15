<?php
declare(strict_types=1);
require __DIR__ . '/../bootstrap.php';

// Called by YOUR PHP system: GET status.php?id=123  or  status.php?ref=INV-1001
require_key('sender_key');
require_method('GET');

$pdo = db();
if (isset($_GET['id'])) {
    $st = $pdo->prepare('SELECT id, phone, ref, status, attempts, error, created_at, sent_at FROM sms_queue WHERE id = ?');
    $st->execute([(int)$_GET['id']]);
} elseif (isset($_GET['ref']) && $_GET['ref'] !== '') {
    $st = $pdo->prepare('SELECT id, phone, ref, status, attempts, error, created_at, sent_at
                         FROM sms_queue WHERE ref = ? ORDER BY id DESC LIMIT 20');
    $st->execute([(string)$_GET['ref']]);
} else {
    json_out(['ok' => false, 'error' => 'id_or_ref_required'], 422);
}

json_out(['ok' => true, 'messages' => $st->fetchAll()]);
