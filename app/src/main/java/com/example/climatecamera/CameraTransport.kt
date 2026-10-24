package com.example.climatecamera

import android.util.Base64
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** All HTTP requests, including climate polling, share one lock. No automatic POST retry. */
internal object CameraTransport {
    private val lock = Any()

    fun status(settings: ConnectionSettings): JSONObject = synchronized(lock) {
        request(settings, "/api/status")
    }

    fun products(settings: ConnectionSettings): ProductsSnapshot = synchronized(lock) {
        val status = request(settings, "/api/status")
        val data = request(settings, "/api/products")
        ProductsSnapshot.fromJson(data, status)
    }

    fun mutate(settings: ConnectionSettings, action: String, fields: Map<String, String>): String = synchronized(lock) {
        require(action in setOf("add", "weigh", "remove"))
        val status = request(settings, "/api/status")
        if (status.optBoolean("readOnly", true)) throw IOException("Для изменения данных войдите как администратор")
        val token = status.optString("token")
        if (token.isBlank()) throw IOException("Контроллер не выдал токен. Проверьте версию прошивки")
        val today = status.optString("isoDate")
        if (action != "remove" && !ProductDates.valid(today)) {
            throw IOException("Время ESP32 не синхронизировано. Дождитесь NTP; запись веса отменена")
        }
        if (action == "add" && (!ProductDates.valid(fields["startDate"].orEmpty()) || fields.getValue("startDate") > today)) {
            throw IOException("Дата закладки должна быть корректной и не позже текущей даты ESP32")
        }
        if (action == "weigh") {
            // Protect against the firmware's fallback to the product start date without NTP.
            val snapshot = ProductsSnapshot.fromJson(request(settings, "/api/products"), status)
            val product = snapshot.products.firstOrNull { it.id.toString() == fields["id"] && it.active }
                ?: throw IOException("Продукт уже извлечён. Обновите список")
            if (product.startDate > today || product.history.any { it.date > today }) {
                throw IOException("Дата ESP32 раньше даты закладки или последнего замера. Проверьте часы")
            }
            if (product.history.size >= 32) {
                throw IOException("В памяти уже 32 взвешивания. Прошивка удалит самое старое: сначала экспортируйте историю и доработайте хранилище. Приложение не перезаписывает её автоматически")
            }
        }
        val data = request(settings, "/api/products/$action", fields, token)
        if (!data.optBoolean("ok", false)) throw IOException(data.optString("message", "Операция отклонена"))
        data.optString("message", "Операция выполнена")
    }

    private fun request(settings: ConnectionSettings, path: String, fields: Map<String, String>? = null, token: String = ""): JSONObject {
        val base = settings.address.trim().trimEnd('/').removeSuffix("/api/status")
        val url = try { URL(base + path) } catch (_: Exception) {
            throw IOException("Проверьте адрес контроллера")
        }
        if (url.protocol !in setOf("http", "https") || url.host.isBlank() || url.userInfo != null || url.query != null || url.ref != null) {
            throw IOException("Укажите адрес вида http://адрес:порт без логина, параметров и фрагмента")
        }
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 6000
            connection.readTimeout = 6000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            if (settings.username.isNotBlank() || settings.password.isNotEmpty()) {
                val basic = Base64.encodeToString("${settings.username}:${settings.password}".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                connection.setRequestProperty("Authorization", "Basic $basic")
            }
            if (fields != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("X-Climate-Token", token)
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                val body = fields.entries.joinToString("&") {
                    URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
                }.toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                val buffer = CharArray(4096)
                val result = StringBuilder()
                while (true) {
                    val n = reader.read(buffer)
                    if (n < 0) break
                    if (result.length + n > 512_000) throw IOException("Слишком большой ответ контроллера")
                    result.append(buffer, 0, n)
                }
                result.toString()
            }.orEmpty()
            if (code !in 200..299) {
                val detail = runCatching { JSONObject(text).optString("message") }.getOrDefault("")
                val fallback = when (code) {
                    401 -> "Неверный логин или пароль"
                    403 -> "Доступ запрещён или токен устарел"
                    404 -> "API не найден. Нужна прошивка с поддержкой продуктов"
                    429 -> "Слишком много попыток входа. Подождите 60 секунд"
                    in 300..399 -> "Перенаправление запрещено; укажите конечный адрес"
                    else -> "Ошибка контроллера"
                }
                throw IOException("${detail.ifBlank { fallback }} (HTTP $code)")
            }
            return try { JSONObject(text) } catch (_: Exception) {
                throw IOException("Вместо JSON получена другая страница. Проверьте адрес API")
            }
        } finally { connection.disconnect() }
    }
}
