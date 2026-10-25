package com.example.climatecamera

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal object ConnectionPolicy {
    fun errorMessage(error: Exception): String = when (error) {
        is UnknownHostException -> "Адрес камеры не найден. Проверьте адрес и подключение к сети камеры."
        is SocketTimeoutException -> "Камера не ответила вовремя. Проверьте связь с ESP32."
        is ConnectException -> "Не удалось подключиться к камере. Проверьте питание, адрес и сеть."
        else -> error.message ?: "Не удалось получить данные камеры"
    }

    fun httpFallback(code: Int): String = when (code) {
        401 -> "Неверный логин или пароль"
        403 -> "Доступ запрещён или токен устарел"
        404 -> "API не найден. Проверьте адрес и версию прошивки"
        429 -> "Слишком много запросов. Подождите перед повторной попыткой"
        in 300..399 -> "Перенаправление запрещено; укажите конечный адрес"
        in 500..599 -> "Ошибка сервера ESP32. Повторите чтение данных позже"
        else -> "Ошибка контроллера"
    }

    fun label(connected: Boolean, hasData: Boolean, failed: Boolean, stale: Boolean): String = when {
        !connected -> "Обновление остановлено"
        failed -> "Ошибка связи или доступа"
        !hasData -> "Подключение к камере…"
        stale -> "Данные не обновлены"
        else -> "Камера доступна"
    }

    fun needsWeightConfirmation(weight: Double, previous: Double): Boolean =
        weight.isFinite() && previous.isFinite() && weight > previous
}
