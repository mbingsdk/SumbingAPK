package com.sdkdev.sumbingcompanion.core

import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TeamWebhookReporter(
    private val webhookUrl: String,
    private val appVersion: String
) {
    val enabled: Boolean
        get() = webhookUrl.isNotBlank()

    fun sendMonitorStart() {
        if (!enabled) return
        post(
            title = "🟢  Monitor Dimulai",
            color = 0x2ECC71,
            extraLines = emptyList()
        )
    }

    fun sendMonitorStop(durationSec: Int) {
        if (!enabled) return
        post(
            title = "🔴  Monitor Dihentikan",
            color = 0xE74C3C,
            extraLines = listOf("Durasi: \`\${formatDuration(durationSec)}\`")
        )
    }

    private fun post(
        title: String,
        color: Int,
        extraLines: List<String>
    ) {
        val now = Instant.now()
        val localTime = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(now)

        val description = buildList {
            add("Mode: \`TFLITE\`")
            add("Version: \`$appVersion\`")
            add("Device: \`\${Build.MANUFACTURER} \${Build.MODEL}\`")
            add("Android: \`\${Build.VERSION.RELEASE} (SDK \${Build.VERSION.SDK_INT})\`")
            addAll(extraLines)
            add("Waktu: \`$localTime\`")
        }.joinToString("\n")

        val embed = JSONObject().apply {
            put("title", title)
            put("color", color)
            put("description", description)
            put("footer", JSONObject().put("text", "SDK-Dev • Sumbing Companion Android"))
            put("timestamp", now.toString())
        }

        val payload = JSONObject().put(
            "embeds",
            JSONArray().put(embed)
        )

        val connection = (URL(webhookUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 6000
            readTimeout = 6000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "SumbingCompanion-Android/$appVersion")
        }

        try {
            connection.outputStream.bufferedWriter().use { writer ->
                writer.write(payload.toString())
            }
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    private fun formatDuration(sec: Int): String {
        if (sec <= 0) return "—"
        val hours = sec / 3600
        val minutes = (sec % 3600) / 60
        val seconds = sec % 60

        return when {
            hours > 0 -> "\${hours}j \${minutes}m \${seconds}d"
            minutes > 0 -> "\${minutes}m \${seconds}d"
            else -> "\${seconds}d"
        }
    }
}
