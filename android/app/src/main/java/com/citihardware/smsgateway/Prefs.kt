package com.citihardware.smsgateway

import android.content.Context

object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("gateway", Context.MODE_PRIVATE)

    fun baseUrl(c: Context): String = p(c).getString("baseUrl", "") ?: ""
    fun apiKey(c: Context): String = p(c).getString("apiKey", "") ?: ""
    fun interval(c: Context): Int = p(c).getInt("interval", 10)
    fun enabled(c: Context): Boolean = p(c).getBoolean("enabled", false)

    fun save(c: Context, url: String, key: String, intervalSec: Int) {
        p(c).edit()
            .putString("baseUrl", url.trim().trimEnd('/'))
            .putString("apiKey", key.trim())
            .putInt("interval", intervalSec)
            .apply()
    }

    fun setEnabled(c: Context, value: Boolean) {
        p(c).edit().putBoolean("enabled", value).apply()
    }
}
