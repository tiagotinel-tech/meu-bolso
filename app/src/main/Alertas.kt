package dev.tiagotinel.meubolso

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Alerts {
    private const val CHANNEL = "gastos"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Alertas de gastos", NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    fun notify(context: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    /** Avisa quando um novo gasto faz o ciclo cruzar 70%, 90% ou 100% do salário. */
    fun checkThreshold(context: Context, beforeCents: Long, afterCents: Long, salaryCents: Long) {
        if (salaryCents <= 0) return
        val before = beforeCents * 100 / salaryCents
        val after = afterCents * 100 / salaryCents
        val hit = listOf(100L, 90L, 70L).firstOrNull { before < it && after >= it } ?: return
        val left = salaryCents - afterCents
        val text = if (left >= 0) {
            "Você já comprometeu $after% do salário deste ciclo. Restam ${Money.format(left)}."
        } else {
            "Os gastos deste ciclo passaram do salário em ${Money.format(-left)}."
        }
        notify(context, 1000 + hit.toInt(), "Atenção: $hit% do salário", text)
    }
}
