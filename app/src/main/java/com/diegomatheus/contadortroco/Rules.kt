package com.diegomatheus.contadortroco

import java.text.Normalizer
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

const val REWARD_THRESHOLD_CENTS = 15_000L

data class Exchange(
    val id: String,
    val name: String,
    val date: String,
    val notes5: Int,
    val notes10: Int
) {
    val totalCents: Long get() = notes5.toLong() * 500L + notes10.toLong() * 1000L
}

data class Gift(val id: String, val name: String)

data class Award(
    val month: String,
    val personKey: String,
    val name: String,
    val giftId: String,
    val giftName: String,
    val at: Long,
    val delivered: Boolean = false
)

data class TrocoData(
    val exchanges: List<Exchange> = emptyList(),
    val gifts: List<Gift> = defaultGifts(),
    val awards: List<Award> = emptyList()
)

data class PersonRank(
    val key: String,
    val name: String,
    val totalCents: Long,
    val exchangeCount: Int,
    val award: Award?
) {
    val eligible: Boolean get() = totalCents >= REWARD_THRESHOLD_CENTS
}

fun defaultGifts() = listOf(
    "Caixa de bombom", "Trufa", "Bolo de pote", "Desodorante",
    "Kit sabonete", "Escova de dente"
).mapIndexed { index, name -> Gift("preset-${index + 1}", name) }

object TrocoRules {
    fun personKey(name: String): String = Normalizer.normalize(name.trim().replace(Regex("\\s+"), " "), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

    fun isValidName(name: String): Boolean = name.trim().isNotEmpty() && name.trim().length <= 80

    fun isValidExchange(name: String, date: String, notes5: Int, notes10: Int): Boolean =
        isValidName(name) && runCatching { LocalDate.parse(date) }.isSuccess &&
            notes5 >= 0 && notes10 >= 0 && notes5 <= 1_000_000 && notes10 <= 1_000_000 &&
            (notes5 > 0 || notes10 > 0)

    fun ranks(data: TrocoData, month: YearMonth): List<PersonRank> {
        val totals = linkedMapOf<String, Triple<String, Long, Int>>()
        for (exchange in data.exchanges) {
            if (!exchange.date.startsWith("$month-")) continue
            val key = personKey(exchange.name)
            val previous = totals[key]
            totals[key] = Triple(previous?.first ?: exchange.name,
                (previous?.second ?: 0L) + exchange.totalCents,
                (previous?.third ?: 0) + 1)
        }
        return totals.map { (key, total) ->
            PersonRank(key, total.first, total.second, total.third,
                data.awards.firstOrNull { it.month == month.toString() && it.personKey == key })
        }.sortedWith(compareByDescending<PersonRank> { it.totalCents }.thenBy { it.name.lowercase(Locale.ROOT) })
    }

    fun canDraw(data: TrocoData, month: YearMonth, key: String): Boolean =
        data.gifts.isNotEmpty() && ranks(data, month).any { it.key == key && it.eligible && it.award == null }
}
