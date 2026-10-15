<?php
declare(strict_types=1);
require __DIR__ . '/../bootstrap.php';

// Called by the Android app: claims up to N pending messages.
require_key('device_key');
require_method('GET');

$limit    = max(1, min(25, (int)($_GET['limit'] ?? 10)));
$stale    = max(60, (int)$config['stale_sending_seconds']);
$maxTries = max(1, (int)$config['max_attempts']);

$pdo = db();
$pdo->beginTransaction();

// Messages claimed long ago but never reported: retry them, or give up after max attempts.
$pdo->exec("UPDATE sms_queue
            SET status = IF(attempts >= {$maxTries}, 'failed', 'pending'),
                error  = IF(attempts >= {$maxTries}, 'No result reported by device', error)
            WHERE status = 'sending'
              AND picked_at < (NOW() - INTERVAL {$stale} SECOND)");

$rows = $pdo->query("SELECT id, phone, message FROM sms_queue
                     WHERE status = 'pending'
                     ORDER BY id
                     LIMIT {$limit}
                     FOR UPDATE")->fetchAll();

if ($rows) {
    $ids = array_map('intval', array_column($rows, 'id'));
    $pdo->exec('UPDATE sms_queue
                SET status = \'sending\', picked_at = NOW(), attempts = attempts + 1
                WHERE id IN (' . implode(',', $ids) . ')');
}
$pdo->commit();

foreach ($rows as &$r) {
    $r['id'] = (int)$r['id'];
}
unset($r);

json_out(['ok' => true, 'messages' => $rows]);
