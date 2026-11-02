package dev.tiagotinel.meubolso

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat

@Composable
fun SettingsDialog(income: Income, onDismiss: () -> Unit, onSave: (Income) -> Unit) {
    val context = LocalContext.current
    var salary by remember {
        mutableStateOf(if (income.salaryCents > 0) "%.2f".format(income.salaryCents / 100.0).replace('.', ',') else "")
    }
    var payday by remember { mutableStateOf(income.payday.toString()) }
    val cents = Money.parse(salary)
    val day = payday.toIntOrNull()
    val valid = cents != null && day != null && day in 1..31

    // Discreto de propósito: só aparece enquanto a permissão não foi dada.
    val listenerOn = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seu salário") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = salary, onValueChange = { salary = it },
                    label = { Text("Salário líquido (R$)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = payday, onValueChange = { payday = it.filter(Char::isDigit) },
                    label = { Text("Dia em que recebe") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (!listenerOn) {
                    TextButton(onClick = {
                        context.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }) { Text("Permitir funcionamento em segundo plano") }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(Income(cents!!, day!!)) }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}
