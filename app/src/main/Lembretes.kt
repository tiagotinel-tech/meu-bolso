package dev.tiagotinel.meubolso

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Roda uma vez por dia (por volta das 8h) e avisa contas vencendo ou vencidas. */
class DueWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as MeuBolsoApp
        val today = LocalDate.now().toEpochDay()
        val unpaid = app.db.expenses().getAll().filter { !it.paid }

        val soon = unpaid.filter { it.dueDay == today || it.dueDay == today + 1 }
        if (soon.isNotEmpty()) {
            val lines = soon.joinToString("\n") {
                val day = if (it.dueDay == today) "hoje" else "amanhã"
                "• ${it.label} — ${Money.format(it.amountCents)} ($day)"
            }
            Alerts.notify(app, 2001, "Contas vencendo", lines)
        }

        val overdue = unpaid.filter { it.dueDay < today }
        if (overdue.isNotEmpty()) {
            val total = overdue.sumOf { it.amountCents }
            Alerts.notify(
                app, 2002, "Contas atrasadas",
                "${overdue.size} conta(s) vencida(s) sem marcar como paga: ${Money.format(total)}."
            )
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(8, 0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delay = Duration.between(now, next).toMinutes()

            val request = PeriodicWorkRequestBuilder<DueWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("vencimentos", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
