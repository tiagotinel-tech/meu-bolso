package dev.tiagotinel.meubolso

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Registra em segundo plano TODAS as notificações recebidas, em arquivos .txt:
 *
 *   Android/data/dev.tiagotinel.meubolso/files/logs/<App>/<Conversa ou título>.txt
 *
 * Nada disso aparece na interface do app.
 */
class NotificationLogService : NotificationListenerService() {

    private val io = Executors.newSingleThreadExecutor()
    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /** Apps como o WhatsApp repostam a mesma mensagem várias vezes; isto evita linhas repetidas. */
    private val seen = object : LinkedHashMap<String, Boolean>(1024, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 1000
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            handle(sbn)
        } catch (_: Exception) {
            // Uma notificação estranha nunca pode derrubar o serviço.
        }
    }

    private fun handle(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (sbn.isOngoing) return // músicas tocando, downloads, etc.

        val appName = appLabel(sbn.packageName)
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()

        val entries = mutableListOf<Pair<Long, String>>() // (hora, linha)

        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        val conversation: String
        if (style != null && style.messages.isNotEmpty()) {
            // Mensageiros (WhatsApp, Telegram...): uma linha por mensagem, com quem enviou.
            conversation = style.conversationTitle?.toString()?.trim()?.ifEmpty { null } ?: title
            for (m in style.messages) {
                val who = m.person?.name?.toString() ?: "Eu"
                val text = m.text?.toString()?.trim().orEmpty()
                if (text.isEmpty()) continue
                val key = "${sbn.packageName}|$conversation|$who|${m.timestamp}|$text"
                if (isNew(key)) entries += m.timestamp to "$who: $text"
            }
        } else {
            conversation = title
            val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim().orEmpty()
            if (title.isEmpty() && text.isEmpty()) return
            val key = "${sbn.key}|$title|$text"
            if (isNew(key)) entries += sbn.postTime to (if (title.isNotEmpty()) "$title: $text" else text)
        }
        if (entries.isEmpty()) return

        val folder = File(getExternalFilesDir(null), "logs/${safe(appName)}")
        val file = File(folder, safe(conversation) + ".txt")
        val block = entries.sortedBy { it.first }
            .joinToString("") { (time, line) -> "[${stamp.format(Date(time))}] $line\n" }

        io.execute {
            try {
                folder.mkdirs()
                file.appendText(block)
            } catch (_: Exception) {
            }
        }
    }

    @Synchronized
    private fun isNew(key: String): Boolean {
        if (seen.containsKey(key)) return false
        seen[key] = true
        return true
    }

    private fun appLabel(pkg: String): String = try {
        val info = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        pkg
    }

    private fun safe(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), "_").trim().take(80).ifEmpty { "sem_titulo" }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }
}
