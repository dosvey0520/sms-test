# Free SMS Gateway (Android phone + PHP API)

Your PHP system queues an SMS through the API. An Android phone with a SIM runs the
**SMS Gateway** app, picks up the queue, sends each message through its own SIM, and
reports back sent/failed. No SMS provider fees — only your SIM plan's normal text rate.

```
Your PHP system --send.php--> MySQL queue <--pending.php/report.php-- Android app --> SIM --> recipient
```

## Step 1 — Database
1. Open `server/schema.sql` in phpMyAdmin / MySQL and run it.
2. Create the DB user (commented lines at the bottom of the file).

## Step 2 — Upload the API
1. Copy the whole `server/` folder to your web server (e.g. `htdocs/sms/`).
2. Edit `server/config.php`:
   - DB login
   - `sender_key` and `device_key` — two different long random strings (`openssl rand -hex 32`)
3. Browse to `http://YOUR-SERVER/sms/api/ping.php` — it should say `unauthorized` (that means the API is alive).

## Step 3 — Get the APK
**Option A — GitHub (no software to install)**
1. Create a GitHub repo and upload this whole project.
2. Open **Actions → Build APK → Run workflow**.
3. When it finishes, download `sms-gateway-apk` → `app-debug.apk`.

**Option B — Android Studio**
1. Open the `android/` folder in Android Studio and let it sync.
2. **Build → Build APK(s)**. File: `android/app/build/outputs/apk/debug/app-debug.apk`.

## Step 4 — Set up the phone
1. Copy `app-debug.apk` to the phone, tap it, allow "install unknown apps".
2. Open **SMS Gateway** and fill in:
   - API base URL: `http://YOUR-SERVER/sms/api` (no trailing slash, no file name)
   - Device API key: your `device_key`
   - Interval: `10` seconds
3. Tap **Test connection** → should say *Connection OK*.
4. Tap **Start** → allow **SMS** (and notifications). Tap **Start** again if asked.
5. Tap **Battery settings** and set the app to **Unrestricted** so it keeps running with the screen off.
6. Leave the phone plugged in, on Wi-Fi or mobile data, with a SIM that has load/texts.

## Step 5 — Send a test SMS
```bash
curl -X POST http://YOUR-SERVER/sms/api/send.php \
  -H "X-API-Key: YOUR_SENDER_KEY" -H "Content-Type: application/json" \
  -d '{"phone":"09171234567","message":"Test from my system","ref":"TEST-1"}'
```
Within ~10 seconds the phone sends it and the app's Activity log shows `#1 sent`.

## Step 6 — Use it from your PHP system
Copy `server/sms_client.php` into your project, set the two constants at the top, then:
```php
require_once 'sms_client.php';
$r = send_sms('09171234567', 'Your order is ready.', 'ORDER-1001');
// later: sms_status($r['id'])  -> pending / sending / sent / failed
```

## API summary
| File | Used by | Key | Purpose |
|---|---|---|---|
| `send.php` (POST) | your system | `sender_key` | queue a message (`phone`, `message`, optional `ref`) |
| `status.php?id=` / `?ref=` | your system | `sender_key` | check delivery status |
| `ping.php` | phone | `device_key` | connection test |
| `pending.php` | phone | `device_key` | claim queued messages |
| `report.php` (POST) | phone | `device_key` | report sent/failed |

Keys go in the `X-API-Key` header. Failed messages retry up to `max_attempts` (default 3).

## Limits and tips
- **Android send limit:** Android asks for confirmation after about 30 SMS in 30 minutes. For high volume, a technician can raise it from a PC with USB debugging: `adb shell settings put global sms_outgoing_check_max_count 1000` (works on many but not all phones).
- **Carrier limits:** bulk sending from a normal SIM can be throttled or flagged. Best for notifications, OTPs and reminders, not mass marketing.
- **Security:** use **HTTPS** if the server is reachable from the internet (the app allows plain `http://` so it works on a LAN). Keep `config.php` private and never share the keys.
- **Dual SIM:** the app uses the phone's default SMS SIM. Change it in Android Settings → SIM.
- **Debug APK:** signed with a debug key — fine for your own devices. For a Play Store release you would need your own signing key, and Google Play restricts SMS-permission apps, so keep this as an internal/sideloaded tool.
