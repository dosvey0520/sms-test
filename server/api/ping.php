<?php
declare(strict_types=1);
require __DIR__ . '/../bootstrap.php';

require_key('device_key');
db()->query('SELECT 1');
json_out(['ok' => true, 'time' => date('c')]);
