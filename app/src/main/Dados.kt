package dev.tiagotinel.meubolso

import android.app.Application
import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MeuBolsoApp : Application() {
    val db: AppDatabase by lazy { AppDatabase.build(this) }
    val prefs: Prefs by lazy { Prefs(this) }

    override fun onCreate() {
        super.onCreate()
        Alerts.createChannel(this)
        DueWorker.schedule(this)
    }
}

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
