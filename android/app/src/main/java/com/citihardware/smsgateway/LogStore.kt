package com.citihardware.smsgateway

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Small in-memory activity log shown on the main screen (newest first). */
object LogStore {
    private val lines = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Synchronized
    fun add(msg: String) {
        lines.addFirst("${fmt.format(Date())}  $msg")
        while (lines.size > 100) lines.removeLast()
    }

    @Synchronized
    fun text(): String = lines.joinToString("\n")
}
