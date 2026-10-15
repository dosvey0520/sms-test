package com.citihardware.smsgateway

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.telephony.SmsManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Foreground service: polls the PHP API for queued messages, sends each one
 * through the phone's SIM, then reports sent/failed back to the server.
 */
class SmsService : Service() {

    @Volatile private var running = false
    private var worker: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // Results that could not be reported yet (server unreachable). Retried every loop
    // so a message that WAS sent is not re-queued and sent twice.
    private val outbox = ConcurrentLinkedQueue<JSONObject>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        if (!running) {
            running = true
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "smsgateway:poll").apply { acquire() }
            worker = Thread { loop() }.also { it.start() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        worker?.interrupt()
        wakeLock?.let { if (it.isHeld) it.release() }
        LogStore.add("Service stopped")
        super.onDestroy()
    }

    // ---------------------------------------------------------------- polling loop

    private fun loop() {
        LogStore.add("Service started")
        while (running) {
            var handled = 0
            try {
                val base = Prefs.baseUrl(this)
                val key = Prefs.apiKey(this)

                if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                    LogStore.add("SEND_SMS permission not granted - open the app and allow SMS")
                } else {
                    flushOutbox(base, key)
                    val res = Api.call(base, key, "pending.php?limit=10")
                    val arr = res.optJSONArray("messages") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        if (!running) break
                        val m = arr.getJSONObject(i)
                        val id = m.getLong("id")
                        val phone = m.getString("phone")
                        val (ok, err) = sendSms(id, phone, m.getString("message"))
                        LogStore.add(if (ok) "#$id sent to $phone" else "#$id FAILED ($phone): $err")
                        outbox.add(
                            JSONObject()
                                .put("id", id)
                                .put("status", if (ok) "sent" else "failed")
                                .put("error", err ?: "")
                        )
                        try {
                            flushOutbox(base, key)
                        } catch (e: Exception) {
                            LogStore.add("Report failed, will retry: ${e.message}")
                        }
                        handled++
                        Thread.sleep(1500) // pace sends to stay under carrier limits
                    }
                }
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                LogStore.add("Error: ${e.message}")
            }

            if (handled > 0) continue // drain the queue before sleeping
            try {
                Thread.sleep(Prefs.interval(this).coerceIn(3, 3600) * 1000L)
            } catch (e: InterruptedException) {
                break
            }
        }
    }

    private fun flushOutbox(base: String, key: String) {
        while (true) {
            val item = outbox.peek() ?: return
            Api.call(base, key, "report.php", "POST", item)
            outbox.poll()
        }
    }

    // ---------------------------------------------------------------- SMS sending

    private fun smsManager(): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

    /** Sends one SMS (multipart if long) and waits for the radio's result. Returns (ok, error). */
    private fun sendSms(id: Long, phone: String, text: String): Pair<Boolean, String?> {
        val action = "$packageName.SENT.$id"
        val sm = try {
            smsManager()
        } catch (e: Exception) {
            return Pair(false, "SMS unavailable: ${e.message}")
        }
        val parts = try {
            sm.divideMessage(text)
        } catch (e: Exception) {
            return Pair(false, "Invalid message text")
        }

        val latch = CountDownLatch(parts.size)
        var error: String? = null
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (resultCode != Activity.RESULT_OK && error == null) error = errorText(resultCode)
                latch.countDown()
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, IntentFilter(action))
        }

        try {
            val sentIntents = ArrayList<PendingIntent>()
            for (n in parts.indices) {
                sentIntents.add(
                    PendingIntent.getBroadcast(
                        this,
                        (id * 100 + n).toInt(),
                        Intent(action).setPackage(packageName),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
            }
            if (parts.size == 1) {
                sm.sendTextMessage(phone, null, text, sentIntents[0], null)
            } else {
                sm.sendMultipartTextMessage(phone, null, parts, sentIntents, null)
            }
            if (!latch.await(45, TimeUnit.SECONDS)) return Pair(false, "Timed out waiting for radio")
            return if (error == null) Pair(true, null) else Pair(false, error)
        } catch (e: SecurityException) {
            return Pair(false, "SEND_SMS permission denied")
        } catch (e: IllegalArgumentException) {
            return Pair(false, "Invalid number or text")
        } catch (e: Exception) {
            return Pair(false, e.message ?: e.javaClass.simpleName)
        } finally {
            try {
                unregisterReceiver(receiver)
            } catch (e: Exception) {
                // already unregistered
            }
        }
    }

    private fun errorText(code: Int): String = when (code) {
        1 -> "Generic failure (check SIM load/signal)"
        2 -> "Radio off (airplane mode?)"
        3 -> "Null PDU"
        4 -> "No service"
        5 -> "Send limit exceeded"
        else -> "Error code $code"
    }

    // ---------------------------------------------------------------- notification

    private fun startInForeground() {
        val channelId = "sms_gateway"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, "SMS Gateway", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("SMS Gateway is running")
            .setContentText("Sending queued messages from your server")
            .setSmallIcon(R.drawable.ic_stat_sms)
            .setContentIntent(open)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }
}
