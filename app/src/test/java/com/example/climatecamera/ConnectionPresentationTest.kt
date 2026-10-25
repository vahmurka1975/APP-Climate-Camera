package com.example.climatecamera

import org.junit.Assert.*
import org.junit.Test

class ConnectionPresentationTest {
    @Test fun freshDataIncludesAge() {
        assertEquals("Данные актуальны · получены 3 с назад",
            ConnectionPresentation.detail(true, true, false, false, 3L))
    }
    @Test fun expiredDataIsNeverCalledFresh() {
        assertEquals("Данные устарели · получены 20 с назад",
            ConnectionPresentation.detail(true, true, false, true, 20L))
    }
    @Test fun failureOverridesFreshness() {
        assertEquals("Ошибка обновления · последние данные получены 1 с назад",
            ConnectionPresentation.detail(true, true, true, false, 1L))
    }
    @Test fun stoppedPollingIsNotLiveEvenWithRecentData() {
        assertEquals("Обновление остановлено · последние данные получены 0 с назад",
            ConnectionPresentation.detail(false, true, false, false, 0L))
    }
    @Test fun initialConnectionWaitsForRealData() {
        assertEquals("Подключение · ожидание первого ответа ESP32",
            ConnectionPresentation.detail(true, false, false, true, null))
    }
    @Test fun disconnectedWithoutDataOffersSetup() {
        assertEquals("Укажите адрес камеры и подключитесь",
            ConnectionPresentation.detail(false, false, false, true, null))
    }
    @Test fun initialFailureHasNoFakeAge() {
        assertEquals("Ошибка связи · первый ответ ESP32 не получен",
            ConnectionPresentation.detail(true, false, true, true, null))
    }
    @Test fun missingAgeIsNotInvented() {
        assertEquals("Данные устарели · время получения неизвестно",
            ConnectionPresentation.detail(true, true, false, true, null))
    }
    @Test fun negativeAgeIsClamped() {
        assertEquals("Данные актуальны · получены 0 с назад",
            ConnectionPresentation.detail(true, true, false, false, -1L))
    }
}
