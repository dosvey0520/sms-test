<?php
// Edit these values, then keep this file out of version control.
return [
    'db' => [
        'dsn'  => 'mysql:host=localhost;dbname=sms_gateway;charset=utf8mb4',
        'user' => 'sms_user',
        'pass' => 'CHANGE_ME',
    ],

    // Two separate keys (use long random strings, e.g. `openssl rand -hex 32`):
    'sender_key' => 'CHANGE_ME_SENDER_KEY',   // used by YOUR PHP system to queue SMS (send.php, status.php)
    'device_key' => 'CHANGE_ME_DEVICE_KEY',   // used by the Android app (ping/pending/report)

    'max_attempts'          => 3,    // retries before a message is marked failed
    'stale_sending_seconds' => 300,  // a message claimed but never reported is re-queued after this
];
