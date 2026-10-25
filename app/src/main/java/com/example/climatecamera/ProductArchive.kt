package com.example.climatecamera

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest
import java.util.UUID

internal enum class ArchiveState(val label: String) {
    PENDING("Результат извлечения не подтверждён"),
    ACKNOWLEDGED("ESP32 подтвердил извлечение"),
    EMPTY_OBSERVED("При проверке слот пуст"),
    STILL_PRESENT("При проверке продукт остался в слоте"),
    SLOT_CHANGED("Слот изменился; результат исходной команды неизвестен")
}

internal data class ArchivedProduct(
    val archiveId: String,
    val cameraKey: String,
    val archivedAt: Long,
    val product: Product,
    val state: ArchiveState,
    val name: String,
    val notes: String
)

internal object ArchivePolicy {
    // Endpoint fingerprint, never credentials. Different addresses of one device are separate sources.
    fun cameraKey(address: String): String {
        val uri = URI(address.trim().trimEnd('/').removeSuffix("/api/status"))
        require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() &&
            uri.userInfo == null && uri.query == null && uri.fragment == null)
        val port = if (uri.port >= 0) uri.port else if (uri.scheme == "https") 443 else 80
        val normalized = "${uri.scheme}://${uri.host.lowercase(java.util.Locale.ROOT)}:$port${uri.path.orEmpty().trimEnd('/')}"
        return MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    fun pending(cameraKey: String, product: Product): ArchivedProduct {
        require(product.active && product.id in 1..30)
        require(product.initialWeight.isFinite() && product.initialWeight in 1.0..100000.0)
        require(product.targetLossPct.isFinite() && product.targetLossPct in 5.0..80.0)
        require(ProductDates.valid(product.startDate))
        require(product.history.all { ProductDates.valid(it.date) && it.weight.isFinite() && it.weight in 1.0..100000.0 })
        return ArchivedProduct(UUID.randomUUID().toString(), cameraKey, System.currentTimeMillis(),
            product.copy(history = product.history.toList()), ArchiveState.PENDING, "Продукт №${product.id}", "")
    }

    fun observed(saved: Product, current: Product): ArchiveState = when {
        !current.active -> ArchiveState.EMPTY_OBSERVED
        current == saved -> ArchiveState.STILL_PRESENT
        else -> ArchiveState.SLOT_CHANGED
    }
}

internal object ArchiveCodec {
    fun encode(p: Product): String {
        val history = JSONArray()
        p.history.forEach { history.put(JSONObject().put("date", it.date).put("weight", it.weight)) }
        return JSONObject().put("id", p.id).put("active", p.active).put("initialWeight", p.initialWeight)
            .put("targetLossPct", p.targetLossPct).put("startDate", p.startDate).put("history", history).toString()
    }
    fun decode(text: String): Product {
        val o = JSONObject(text)
        val h = o.getJSONArray("history")
        return Product(o.getInt("id"), o.getBoolean("active"), o.getDouble("initialWeight"),
            o.getDouble("targetLossPct"), o.getString("startDate"), (0 until h.length()).map {
                val w = h.getJSONObject(it)
                Weighing(w.getString("date"), w.getDouble("weight"))
            })
    }
}

/** Formula-leading user cells are neutralized even when quoted (Excel/LibreOffice). */
internal fun archiveCsv(entries: List<ArchivedProduct>): String {
    fun cell(value: String): String {
        val trimmed = value.trimStart()
        val safe = if (trimmed.firstOrNull() in setOf('=', '+', '-', '@') ||
            value.firstOrNull() in setOf('\t', '\r', '\n')) "'" + value else value
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }
    val rows = mutableListOf(listOf("archive_id", "camera_key", "archived_at_utc", "state", "name", "notes",
        "slot", "start_date", "initial_weight_g", "target_loss_pct", "record_type", "date", "weight_g", "loss_pct"))
    val date = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }
    for (e in entries) {
        val p = e.product
        val prefix = listOf(e.archiveId, e.cameraKey, date.format(java.util.Date(e.archivedAt)), e.state.label,
            e.name, e.notes, p.id.toString(), p.startDate, p.initialWeight.toString(), p.targetLossPct.toString())
        rows += prefix + listOf("initial", p.startDate, p.initialWeight.toString(), "0.0")
        for (w in p.history) rows += prefix + listOf("control", w.date, w.weight.toString(),
            ((p.initialWeight - w.weight) / p.initialWeight * 100).toString())
    }
    return "\uFEFF" + rows.joinToString("\r\n") { row -> row.joinToString(";") { cell(it) } } + "\r\n"
}
