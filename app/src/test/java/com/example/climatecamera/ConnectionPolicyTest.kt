package com.example.climatecamera

import org.junit.Assert.*
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ConnectionPolicyTest {
    @Test fun reportsInitialConnection() {
        assertEquals("Подключение к камере…", ConnectionPolicy.label(true, false, false, true))
    }
    @Test fun failureTakesPriorityOverOldData() {
        assertEquals("Ошибка связи или доступа", ConnectionPolicy.label(true, true, true, true))
    }
    @Test fun stoppedConnectionCannotLookLive() {
        assertEquals("Обновление остановлено", ConnectionPolicy.label(false, true, false, false))
    }
    @Test fun freshDataLooksLive() {
        assertEquals("Камера доступна", ConnectionPolicy.label(true, true, false, false))
    }
    @Test fun expiredDataIsMarked() {
        assertEquals("Данные не обновлены", ConnectionPolicy.label(true, true, false, true))
    }
    @Test fun detectsNetworkFailures() {
        assertTrue(ConnectionPolicy.errorMessage(SocketTimeoutException()).contains("вовремя"))
        assertTrue(ConnectionPolicy.errorMessage(UnknownHostException()).contains("Адрес"))
    }
    @Test fun separatesHttpErrors() {
        assertTrue(ConnectionPolicy.httpFallback(401).contains("пароль"))
        assertTrue(ConnectionPolicy.httpFallback(503).contains("сервера"))
    }
    @Test fun confirmsOnlyFiniteIncrease() {
        assertTrue(ConnectionPolicy.needsWeightConfirmation(1001.0, 1000.0))
        assertFalse(ConnectionPolicy.needsWeightConfirmation(1000.0, 1000.0))
        assertFalse(ConnectionPolicy.needsWeightConfirmation(900.0, 1000.0))
        assertFalse(ConnectionPolicy.needsWeightConfirmation(Double.NaN, 1000.0))
    }
}
