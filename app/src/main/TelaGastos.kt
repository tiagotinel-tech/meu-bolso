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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
fun ExpensesScreen(
    expenses: List<Expense>,
    onTogglePaid: (Expense) -> Unit,
    onDelete: (Expense) -> Unit,
) {
    var toDelete by remember { mutableStateOf<Expense?>(null) }

    if (expenses.isEmpty()) {
        Text("Nenhum gasto lançado. Toque no + para começar.", Modifier.padding(16.dp))
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(expenses, key = { it.id }) { e ->
            ExpenseRow(e, onTogglePaid, onDelete = { toDelete = e })
        }
        item { Spacer(Modifier.height(72.dp)) }
    }

    toDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Excluir gasto?") },
            text = {
                Text(
                    if (e.installments > 1) "Todas as ${e.installments} parcelas de \"${e.description}\" serão excluídas."
                    else "\"${e.description}\" será excluído."
                )
            },
            confirmButton = { TextButton(onClick = { onDelete(e); toDelete = null }) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
fun SectionTitle(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = color,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
fun ExpenseRow(e: Expense, onTogglePaid: (Expense) -> Unit, onDelete: (() -> Unit)? = null) {
    val late = !e.paid && e.dueDay < LocalDate.now().toEpochDay()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = e.paid, onCheckedChange = { onTogglePaid(e) })
        Column(Modifier.weight(1f)) {
            Text(e.label, fontWeight = FontWeight.Medium)
            Text(
                "${e.category} · vence ${Dates.short(e.dueDay)}" + if (e.paid) " · pago" else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (late) Red else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(Money.format(e.amountCents), fontWeight = FontWeight.SemiBold)
        if (onDelete != null) {
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Excluir") }
        }
    }
}
