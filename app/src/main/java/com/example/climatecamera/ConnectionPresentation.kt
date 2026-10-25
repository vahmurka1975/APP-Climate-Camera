package com.example.climatecamera

/** Text only: polling must not insert or remove a progress bar in the layout. */
internal object ConnectionPresentation {
    fun detail(
        connected: Boolean,
        hasStatus: Boolean,
        hasError: Boolean,
        stale: Boolean,
        ageSeconds: Long?
    ): String {
        val age = ageSeconds?.let { "получены ${it.coerceAtLeast(0L)} с назад" }
            ?: "время получения неизвестно"
        return when {
            !connected && hasStatus -> "Обновление остановлено · последние данные $age"
            !connected -> "Укажите адрес камеры и подключитесь"
            hasError && hasStatus -> "Ошибка обновления · последние данные $age"
            hasError -> "Ошибка связи · первый ответ ESP32 не получен"
            !hasStatus -> "Подключение · ожидание первого ответа ESP32"
            stale -> "Данные устарели · $age"
            else -> "Данные актуальны · $age"
        }
    }
}
