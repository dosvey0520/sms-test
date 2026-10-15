<?php
/**
 * Drop this into YOUR existing PHP system and call send_sms() wherever you need a text message.
 *
 *   require_once 'sms_client.php';
 *   $r = send_sms('09171234567', 'Your order #1001 is ready for pick-up.', 'ORDER-1001');
 *   if ($r['ok']) { ... $r['id'] is the queue id ... }
 */
const SMS_API_URL    = 'http://192.168.1.10/sms/api';   // folder that holds send.php
const SMS_SENDER_KEY = 'CHANGE_ME_SENDER_KEY';           // same as 'sender_key' in config.php

function sms_api(string $path, ?array $body = null): array
{
    $ch = curl_init(SMS_API_URL . '/' . $path);
    $opts = [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_TIMEOUT        => 10,
        CURLOPT_HTTPHEADER     => ['X-API-Key: ' . SMS_SENDER_KEY, 'Content-Type: application/json'],
    ];
    if ($body !== null) {
        $opts[CURLOPT_POST]       = true;
        $opts[CURLOPT_POSTFIELDS] = json_encode($body);
    }
    curl_setopt_array($ch, $opts);
    $out = curl_exec($ch);
    $err = curl_error($ch);
    curl_close($ch);

    if ($out === false) {
        return ['ok' => false, 'error' => $err ?: 'request_failed'];
    }
    $data = json_decode($out, true);
    return is_array($data) ? $data : ['ok' => false, 'error' => 'bad_response'];
}

/** Queue one SMS. $ref is optional (your invoice/order number) so you can look it up later. */
function send_sms(string $phone, string $message, string $ref = ''): array
{
    return sms_api('send.php', ['phone' => $phone, 'message' => $message, 'ref' => $ref]);
}

/** Check delivery status by queue id. */
function sms_status(int $id): array
{
    return sms_api('status.php?id=' . $id);
}
