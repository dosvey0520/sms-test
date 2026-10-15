package com.citihardware.smsgateway

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Api {
    class ApiException(message: String) : Exception(message)

    /** Calls `<base>/<path>` with the device API key and returns the parsed JSON body. */
    fun call(
        base: String,
        key: String,
        path: String,
        method: String = "GET",
        body: JSONObject? = null
    ): JSONObject {
        val conn = URL("$base/$path").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.setRequestProperty("X-API-Key", key)
            conn.setRequestProperty("Accept", "application/json")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw ApiException("HTTP $code ${text.take(120)}")
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }
}
