package com.example.climatecamera

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.ceil

internal data class Weighing(val date: String, val weight: Double)
internal data class Product(
    val id: Int,
    val active: Boolean,
    val initialWeight: Double,
    val targetLossPct: Double,
    val startDate: String,
    val history: List<Weighing>
) {
    val lastWeight: Double get() = history.lastOrNull()?.weight ?: initialWeight
    val lossPct: Double get() = if (initialWeight > 0) (initialWeight - lastWeight) / initialWeight * 100 else 0.0
    val targetWeight: Double get() = initialWeight * (1 - targetLossPct / 100)
    val remainingGrams: Double get() = (lastWeight - targetWeight).coerceAtLeast(0.0)
    val targetProgress: Double get() = if (targetLossPct > 0) (lossPct / targetLossPct).coerceIn(0.0, 1.0) else 0.0
    fun forecast(): String {
        if (history.isEmpty()) return "Нужно контрольное взвешивание"
        if (lastWeight <= targetWeight) return "Целевая усушка достигнута"
        val start = ProductDates.day(startDate) ?: return "Некорректная дата закладки"
        val last = ProductDates.day(history.last().date) ?: return "Некорректная дата замера"
        val lost = initialWeight - lastWeight
        if (last <= start || lost <= 0) return "Недостаточно данных о снижении веса"
        val remaining = ceil((lastWeight - targetWeight) / (lost / (last - start).toDouble())).toLong()
        if (remaining > 3650) return "Слишком низкая скорость усушки для прогноза"
        return "≈ ${ProductDates.displayDay(last + remaining)}"
    }
}

internal data class ProductsSnapshot(val today: String, val readOnly: Boolean, val products: List<Product>) {
    companion object {
        fun fromJson(data: JSONObject, status: JSONObject): ProductsSnapshot {
            val array = data.getJSONArray("products")
            val list = (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val h = item.optJSONArray("weighings")
                val history = (0 until (h?.length() ?: 0)).map { i ->
                    val record = h!!.getJSONObject(i)
                    Weighing(record.getString("date"), record.getDouble("weight"))
                }
                Product(item.getInt("id"), item.getBoolean("active"), item.optDouble("initialWeight", 0.0),
                    item.optDouble("targetLossPct", 38.0), item.optString("startDate"), history)
            }
            require(list.size == 30 && list.map { it.id }.toSet() == (1..30).toSet()) { "API вернул некорректный список 30 слотов" }
            return ProductsSnapshot(data.optString("today"), status.optBoolean("readOnly", true), list.sortedBy { it.id })
        }
    }
}

internal object ProductDates {
    private const val DAY = 86_400_000L
    private fun formatter(pattern: String) = SimpleDateFormat(pattern, Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
        isLenient = false
    }
    fun valid(iso: String): Boolean = day(iso) != null
    fun day(iso: String): Long? {
        if (!iso.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return null
        return try {
            val f = formatter("yyyy-MM-dd")
            val date = f.parse(iso) ?: return null
            if (f.format(date) != iso) null else date.time / DAY
        } catch (_: Exception) { null }
    }
    fun display(iso: String): String = day(iso)?.let { displayDay(it) } ?: "—"
    fun displayDay(day: Long): String = formatter("dd.MM.yyyy").format(Date(day * DAY))
}

internal fun parseWeight(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
    ?.takeIf { it.isFinite() && it in 1.0..100000.0 }

internal fun productsCsv(snapshot: ProductsSnapshot): String {
    fun cell(value: String) = "\"" + value.replace("\"", "\"\"") + "\""
    val lines = mutableListOf("product;kind;date;weight_g;loss_pct;target_loss_pct")
    for (p in snapshot.products.filter { it.active }) {
        lines += listOf(p.id.toString(), "initial", p.startDate, p.initialWeight.toString(), "0", p.targetLossPct.toString()).joinToString(";") { cell(it) }
        for (w in p.history) {
            val loss = if (p.initialWeight > 0) (p.initialWeight - w.weight) / p.initialWeight * 100 else 0.0
            lines += listOf(p.id.toString(), "control", w.date, w.weight.toString(), loss.toString(), p.targetLossPct.toString()).joinToString(";") { cell(it) }
        }
    }
    return "\uFEFF" + lines.joinToString("\r\n") + "\r\n"
}
