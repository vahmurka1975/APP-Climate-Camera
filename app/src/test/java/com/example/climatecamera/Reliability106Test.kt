package com.example.climatecamera

import org.junit.Assert.*
import org.junit.Test
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLException

class Reliability106Test {
    @Test fun pollingBackoff() {
        assertEquals(listOf(5000L, 5000L, 10000L, 20000L, 30000L, 30000L),
            (0..5).map { ConnectionPolicy.pollDelayMs(it) })
    }
    @Test fun backoffIsBounded() {
        assertEquals(30000L, ConnectionPolicy.pollDelayMs(Int.MAX_VALUE))
        assertEquals(5000L, ConnectionPolicy.pollDelayMs(-1))
    }
    @Test fun numericFormattingDoesNotCauseMismatch() {
        assertTrue(ReadbackPolicy.differences(mapOf("x" to "12.50"), mapOf("x" to "12,5")).isEmpty())
    }
    @Test fun floatRoundingIsTolerated() {
        assertTrue(ReadbackPolicy.differences(mapOf("x" to "0.1"), mapOf("x" to "0.10000001")).isEmpty())
    }
    @Test fun changedValuesAreReported() {
        assertEquals(listOf("x"), ReadbackPolicy.differences(mapOf("x" to "12"), mapOf("x" to "13")))
    }
    @Test fun absentValuesAreReported() {
        assertEquals(listOf("x"), ReadbackPolicy.differences(mapOf("x" to "12"), emptyMap()))
    }
    @Test fun invalidNumbersNeverPass() {
        for (bad in listOf("NaN", "Infinity", "oops", "")) {
            assertEquals(listOf("x"), ReadbackPolicy.differences(mapOf("x" to "12"), mapOf("x" to bad)))
            assertEquals(listOf("x"), ReadbackPolicy.differences(mapOf("x" to bad), mapOf("x" to "12")))
        }
    }
    @Test fun unrelatedValuesAreIgnored() {
        assertTrue(ReadbackPolicy.differences(mapOf("x" to "1"), mapOf("x" to "1", "y" to "9")).isEmpty())
    }
    @Test fun everyEditGateIsRequired() {
        for (v in listOf(false, true)) for (r in listOf(false, true)) for (b in listOf(false, true)) {
            assertEquals(v && !r && !b, ReadbackPolicy.canEdit(v, r, b))
        }
    }
    @Test fun readbackHasSmallFiniteRetryBudget() {
        assertEquals(3, ReadbackPolicy.MAX_ATTEMPTS)
        assertEquals(500L, ReadbackPolicy.RETRY_DELAY_MS)
    }
    @Test fun certificateErrorsHaveSpecificMessage() {
        assertTrue(ConnectionPolicy.errorMessage(SSLHandshakeException("x")).contains("сертификат"))
        assertTrue(ConnectionPolicy.errorMessage(SSLPeerUnverifiedException("x")).contains("адрес"))
    }
    @Test fun tlsFailureIsNotReportedAsWrongPassword() {
        assertTrue(ConnectionPolicy.errorMessage(SSLException("x")).contains("HTTPS"))
    }
}
