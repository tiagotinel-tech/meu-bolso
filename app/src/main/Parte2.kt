package dev.tiagotinel.meubolso

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List as ListIcon
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ===================== MainViewModel.kt =====================

enum class PayMode(val title: String) {
    A_VISTA("À vista"),
    A_PRAZO("A prazo"),
    PARCELADO("Parcelado"),
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MeuBolsoApp
    private val dao = app.db.expenses()

    val expenses: StateFlow<List<Expense>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val income: StateFlow<Income> = app.prefs.state

    fun saveIncome(income: Income) = app.prefs.save(income)

    fun add(
        description: String,
        amountCents: Long,
        category: String,
        purchase: LocalDate,
        mode: PayMode,
        days: Int,
        installments: Int,
    ) = viewModelScope.launch {
        val group = UUID.randomUUID().toString()
        fun item(cents: Long, due: LocalDate, n: Int = 1, total: Int = 1, paid: Boolean = false) = Expense(
            description = description,
            amountCents = cents,
            category = category,
            purchaseDay = purchase.toEpochDay(),
            dueDay = due.toEpochDay(),
            installment = n,
            installments = total,
            paid = paid,
            groupId = group,
        )

        val items = when (mode) {
            PayMode.A_VISTA -> listOf(item(amountCents, purchase, paid = true))
            PayMode.A_PRAZO -> listOf(item(amountCents, purchase.plusDays(days.toLong())))
            PayMode.PARCELADO -> {
                val n = installments.coerceAtLeast(2)
                val base = amountCents / n
                val rest = amountCents - base * n // centavos que sobram vão na 1ª parcela
                (1..n).map { i ->
                    item(if (i == 1) base + rest else base, purchase.plusMonths(i.toLong()), i, n)
                }
            }
        }

        val inc = income.value
        val cycle = Budget.cycle(LocalDate.now(), inc.payday)
        val before = Budget.committed(dao.getAll(), cycle)
        dao.insertAll(items)
        val after = before + items.filter { cycle.contains(it) }.sumOf { it.amountCents }
        Alerts.checkThreshold(app, before, after, inc.salaryCents)
    }

    fun togglePaid(e: Expense) = viewModelScope.launch { dao.update(e.copy(paid = !e.paid)) }

    fun delete(e: Expense) = viewModelScope.launch { dao.deleteGroup(e.groupId) }
}

// ===================== MainActivity.kt =====================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                App()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: MainViewModel = viewModel()) {
    val expenses by vm.expenses.collectAsState()
    val income by vm.income.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(income.salaryCents == 0L) }

    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meu Bolso") },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Configurações")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("Resumo") },
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.ListIcon, contentDescription = null) },
                    label = { Text("Gastos") },
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Novo gasto")
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            if (tab == 0) SummaryScreen(expenses, income, onTogglePaid = vm::togglePaid)
            else ExpensesScreen(expenses, onTogglePaid = vm::togglePaid, onDelete = vm::delete)
        }
    }

    if (showAdd) {
        AddExpenseDialog(
            onDismiss = { showAdd = false },
            onSave = { desc, cents, cat, date, mode, days, parcels ->
                vm.add(desc, cents, cat, date, mode, days, parcels)
                showAdd = false
            },
        )
    }
    if (showSettings) {
        SettingsDialog(
            income = income,
            onDismiss = { showSettings = false },
            onSave = { vm.saveIncome(it); showSettings = false },
        )
    }
}
