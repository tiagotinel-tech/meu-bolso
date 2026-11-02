package dev.tiagotinel.meubolso

import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

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
