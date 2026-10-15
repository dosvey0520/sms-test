package com.citihardware.smsgateway

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var etUrl: EditText
    private lateinit var etKey: EditText
    private lateinit var etInterval: EditText
    private lateinit var tvState: TextView
    private lateinit var tvLog: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            tvLog.text = LogStore.text()
            tvState.text = if (Prefs.enabled(this@MainActivity)) "Status: RUNNING" else "Status: STOPPED"
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etUrl = findViewById(R.id.etUrl)
        etKey = findViewById(R.id.etKey)
        etInterval = findViewById(R.id.etInterval)
        tvState = findViewById(R.id.tvState)
        tvLog = findViewById(R.id.tvLog)

        etUrl.setText(Prefs.baseUrl(this))
        etKey.setText(Prefs.apiKey(this))
        etInterval.setText(Prefs.interval(this).toString())

        findViewById<Button>(R.id.btnStart).setOnClickListener { startGateway() }
        findViewById<Button>(R.id.btnStop).setOnClickListener { stopGateway() }
        findViewById<Button>(R.id.btnTest).setOnClickListener { testConnection() }
        findViewById<Button>(R.id.btnBattery).setOnClickListener {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    private fun startGateway() {
        val url = etUrl.text.toString().trim()
        val key = etKey.text.toString().trim()
        val interval = etInterval.text.toString().toIntOrNull()?.coerceIn(3, 3600) ?: 10

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            toast("Server URL must start with http:// or https://")
            return
        }
        if (key.isEmpty()) {
            toast("Enter the device API key")
            return
        }
        Prefs.save(this, url, key, interval)

        val need = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            need += Manifest.permission.SEND_SMS
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            need += Manifest.permission.POST_NOTIFICATIONS
        }
        if (need.isNotEmpty()) {
            requestPermissions(need.toTypedArray(), 1)
            toast("Allow the permissions, then tap Start again")
            return
        }

        Prefs.setEnabled(this, true)
        startForegroundService(Intent(this, SmsService::class.java))
        toast("Gateway started")
    }

    private fun stopGateway() {
        Prefs.setEnabled(this, false)
        stopService(Intent(this, SmsService::class.java))
        toast("Gateway stopped")
    }

    private fun testConnection() {
        val url = etUrl.text.toString().trim().trimEnd('/')
        val key = etKey.text.toString().trim()
        Thread {
            val msg = try {
                val res = Api.call(url, key, "ping.php")
                if (res.optBoolean("ok")) "Connection OK" else "Server replied: $res"
            } catch (e: Exception) {
                "Failed: ${e.message}"
            }
            runOnUiThread { toast(msg) }
        }.start()
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
