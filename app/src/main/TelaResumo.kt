package dev.tiagotinel.meubolso

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate

val Red = Color(0xFFD32F2F)
val Orange = Color(0xFFF57C00)

@Composable
fun SummaryScreen(expenses: List<Expense>, income: Income, onTogglePaid: (Expense) -> Unit) {
    val today = LocalDate.now()
    val todayDay = today.toEpochDay()
    val cycle = Budget.cycle(today, income.payday)
    val inCycle = expenses.filter { cycle.contains(it) }
    val committed = inCycle.sumOf { it.amountCents }
    val left = income.salaryCents - committed
    val pct = Budget.percent(committed, income.salaryCents)
    val overdue = expenses.filter { !it.paid && it.dueDay < todayDay }.sortedBy { it.dueDay }
    val upcoming = expenses.filter { !it.paid && it.dueDay in todayDay..(todayDay + 7) }.sortedBy { it.dueDay }
    val byCategory = inCycle.groupBy { it.category }
        .mapValues { (_, list) -> list.sumOf { it.amountCents } }
        .toList().sortedByDescending { it.second }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Ciclo ${Dates.short(cycle.start)} a ${Dates.short(cycle.end.minusDays(1))}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (income.salaryCents == 0L) {
                        Text("Toque na engrenagem e informe seu salário para ver quanto ainda pode gastar.")
                    } else {
                        Text(
                            if (left >= 0) Money.format(left) else "-" + Money.format(-left),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (left < 0) Red else MaterialTheme.colorScheme.onSurface,
                        )
                        Text("disponível de ${Money.format(income.salaryCents)}")
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { (pct.coerceAtMost(100)) / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = when {
                                pct >= 90 -> Red
                                pct >= 70 -> Orange
                                else -> MaterialTheme.colorScheme.primary
                            },
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("$pct% comprometido (${Money.format(committed)})", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (overdue.isNotEmpty()) {
            item { SectionTitle("Atrasadas", Red) }
            items(overdue, key = { it.id }) { ExpenseRow(it, onTogglePaid) }
        }

        item { SectionTitle("Vencem nos próximos 7 dias") }
        if (upcoming.isEmpty()) item { Text("Nada vencendo. 👍", style = MaterialTheme.typography.bodyMedium) }
        items(upcoming, key = { "u" + it.id }) { ExpenseRow(it, onTogglePaid) }

        item { SectionTitle("Onde você está gastando neste ciclo") }
        if (byCategory.isEmpty()) item { Text("Nenhum gasto neste ciclo ainda.") }
        items(byCategory, key = { it.first }) { (cat, cents) ->
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(cat)
                    Text(Money.format(cents), fontWeight = FontWeight.SemiBold)
                }
                LinearProgressIndicator(
                    progress = { if (committed > 0) cents.toFloat() / committed else 0f },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
        item { Spacer(Modifier.height(72.dp)) } // espaço para o botão +
    }
}
