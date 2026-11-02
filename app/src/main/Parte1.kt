package dev.tiagotinel.meubolso

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
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
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// ===================== MeuBolsoApp.kt =====================

class MeuBolsoApp : Application() {
    val db: AppDatabase by lazy { AppDatabase.build(this) }
    val prefs: Prefs by lazy { Prefs(this) }

    override fun onCreate() {
        super.onCreate()
        Alerts.createChannel(this)
        DueWorker.schedule(this)
    }
}

// ===================== data/Data.kt =====================

/** Um gasto. Compras parceladas viram uma linha por parcela, ligadas pelo mesmo groupId. */
@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val description: String,
    val amountCents: Long,
    val category: String,
    val purchaseDay: Long,   // LocalDate.toEpochDay()
    val dueDay: Long,        // dia em que o dinheiro sai de fato
    val installment: Int = 1,
    val installments: Int = 1,
    val paid: Boolean = false,
    val groupId: String,
) {
    val label: String
        get() = if (installments > 1) "$description ($installment/$installments)" else description
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY dueDay DESC, id DESC")
    fun observeAll(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses")
    suspend fun getAll(): List<Expense>

    @Insert
    suspend fun insertAll(items: List<Expense>)

    @Update
    suspend fun update(item: Expense)

    @Query("DELETE FROM expenses WHERE groupId = :groupId")
    suspend fun deleteGroup(groupId: String)
}

@Database(entities = [Expense::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenses(): ExpenseDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "meubolso.db").build()
    }
}

data class Income(val salaryCents: Long, val payday: Int)

/** Salário e dia do pagamento, guardados nas preferências do app. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("meubolso", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        Income(sp.getLong("salary", 0L), sp.getInt("payday", 5))
    )
    val state: StateFlow<Income> = _state

    fun save(income: Income) {
        sp.edit().putLong("salary", income.salaryCents).putInt("payday", income.payday).apply()
        _state.value = income
    }
}

// ===================== data/Budget.kt =====================

/** Período entre um pagamento de salário e o próximo. */
data class Cycle(val start: LocalDate, val end: LocalDate) {
    fun contains(day: LocalDate): Boolean = !day.isBefore(start) && day.isBefore(end)
    fun contains(e: Expense): Boolean = contains(LocalDate.ofEpochDay(e.dueDay))
}

object Budget {
    fun cycle(today: LocalDate, payday: Int): Cycle {
        fun payAt(ym: YearMonth): LocalDate = ym.atDay(min(payday, ym.lengthOfMonth()))
        val month = YearMonth.from(today)
        val thisPay = payAt(month)
        return if (!today.isBefore(thisPay)) Cycle(thisPay, payAt(month.plusMonths(1)))
        else Cycle(payAt(month.minusMonths(1)), thisPay)
    }

    /** Quanto do salário deste ciclo já está comprometido (pago ou a pagar). */
    fun committed(list: List<Expense>, cycle: Cycle): Long =
        list.filter { cycle.contains(it) }.sumOf { it.amountCents }

    fun percent(part: Long, total: Long): Int =
        if (total <= 0) 0 else ((part * 100) / total).toInt()
}

object Money {
    private val fmt = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))

    fun format(cents: Long): String = fmt.format(cents / 100.0)

    /** Aceita "45,90", "1.234,50", "45.90" ou "R$ 45". Devolve centavos, ou null se inválido. */
    fun parse(text: String): Long? {
        val t = text.trim().replace("R$", "").replace(" ", "")
        val normalized = if (t.contains(',')) t.replace(".", "").replace(',', '.') else t
        return normalized.toBigDecimalOrNull()
            ?.movePointRight(2)
            ?.setScale(0, RoundingMode.HALF_UP)
            ?.toLong()
            ?.takeIf { it > 0 }
    }
}

object Dates {
    private val short = DateTimeFormatter.ofPattern("dd/MM")
    private val full = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    fun short(day: Long): String = LocalDate.ofEpochDay(day).format(short)
    fun short(d: LocalDate): String = d.format(short)
    fun full(d: LocalDate): String = d.format(full)
}

val CATEGORIES = listOf(
    "Alimentação", "Mercado", "Transporte", "Combustível", "Casa",
    "Contas", "Saúde", "Lazer", "Compras", "Outros",
)

// ===================== alerts/Alerts.kt =====================

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
            context, 0, Intent(context, MainActivity::class.java), PendingInt
