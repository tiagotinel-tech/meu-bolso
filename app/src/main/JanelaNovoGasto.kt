package dev.tiagotinel.meubolso

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddExpenseDialog(
  onDismiss: () -> Unit,
  onSave: (String, Long, String, LocalDate, PayMode, Int, Int) -> Unit,
) {
  var description by remember { mutableStateOf("") }
  var amount by remember { mutableStateOf("") }
  var category by remember { mutableStateOf(CATEGORIES.first()) }
  var date by remember { mutableStateOf(LocalDate.now()) }
  var mode by remember { mutableStateOf(PayMode.A_VISTA) }
  var days by remember { mutableStateOf("30") }
  var parcels by remember { mutableStateOf("2") }
  var pickDate by remember { mutableStateOf(false) }

  val cents = Money.parse(amount)
  val daysN = days.toIntOrNull()
  val parcelsN = parcels.toIntOrNull()
  val valid = description.isNotBlank() && cents != null && when (mode) {
    PayMode.A_VISTA -> true
    PayMode.A_PRAZO -> daysN != null && daysN in 1..365
    PayMode.PARCELADO -> parcelsN != null && parcelsN in 2..48
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Novo gasto") },
    text = {
      Column(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = description, onValueChange = { description = it },
          label = { Text("O que comprou?") }, singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = amount, onValueChange = { amount = it },
          label = { Text("Valor total (R$)") }, singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth(),
        )

        Text("Categoria", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          CATEGORIES.forEach { c ->
            FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
          }
        }

        OutlinedButton(onClick = { pickDate = true }) {
          Text("Data da compra: ${Dates.full(date)}")
        }

        Text("Pagamento", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          PayMode.entries.forEach { m ->
            FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.title) })
          }
        }
        when (mode) {
          PayMode.A_PRAZO -> OutlinedTextField(
            value = days, onValueChange = { days = it.filter(Char::isDigit) },
            label = { Text("Vence em quantos dias?") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = { daysN?.let { Text("Vencimento: ${Dates.full(date.plusDays(it.toLong()))}") } },
          )
          PayMode.PARCELADO -> OutlinedTextField(
            value = parcels, onValueChange = { parcels = it.filter(Char::isDigit) },
            label = { Text("Quantas parcelas?") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = {
              if (cents != null && parcelsN != null && parcelsN >= 2) {
                Text("${parcelsN}x de ~${Money.format(cents / parcelsN)}, 1ª em ${Dates.full(date.plusMonths(1))}")
              }
            },
          )
          PayMode.A_VISTA -> Unit
        }
      }
    },
    confirmButton = {
      TextButton(
        enabled = valid,
        onClick = { onSave(description.trim(), cents!!, category, date, mode, daysN ?: 30, parcelsN ?: 2) },
      ) { Text("Salvar") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
  )

  if (pickDate) {
    val state = rememberDatePickerState(
      initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    )
    DatePickerDialog(
      onDismissRequest = { pickDate = false },
      confirmButton = {
        TextButton(onClick = {
          state.selectedDateMillis?.let {
            date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
          }
          pickDate = false
        }) { Text("OK") }
      },
      dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancelar") } },
    ) {
      DatePicker(state = state)
    }
  }
}
