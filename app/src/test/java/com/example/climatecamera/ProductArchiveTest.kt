package com.example.climatecamera

import org.junit.Assert.*
import org.junit.Test

class ProductArchiveTest {
    private fun product() = Product(1, true, 1000.0, 38.0, "2026-10-20",
        listOf(Weighing("2026-10-21", 900.0), Weighing("2026-10-22", 800.0)))
    private fun entry() = ArchivePolicy.pending(ArchivePolicy.cameraKey("http://camera"), product())

    @Test fun everyBatchHasIndependentId() {
        assertNotEquals(entry().archiveId, entry().archiveId)
    }
    @Test fun sourceNormalization() {
        assertEquals(ArchivePolicy.cameraKey("http://CAMERA/api/status/"), ArchivePolicy.cameraKey("http://camera:80/"))
        assertEquals(ArchivePolicy.cameraKey("https://camera/"), ArchivePolicy.cameraKey("https://camera:443/api/status"))
    }
    @Test fun differentSourcesStaySeparate() {
        assertNotEquals(ArchivePolicy.cameraKey("http://camera"), ArchivePolicy.cameraKey("https://camera"))
        assertNotEquals(ArchivePolicy.cameraKey("http://camera:81"), ArchivePolicy.cameraKey("http://camera:80"))
    }
    @Test fun credentialsAndQueriesAreRejected() {
        for (address in listOf("http://user:password@camera", "http://camera?token=secret", "http://camera#token", "ftp://camera")) {
            try { ArchivePolicy.cameraKey(address); fail(address) } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun snapshotDoesNotShareMutableHistory() {
        val history = product().history.toMutableList()
        val saved = ArchivePolicy.pending("camera", product().copy(history = history))
        history.clear()
        assertEquals(2, saved.product.history.size)
    }
    @Test fun codecRoundTripPreservesProduct() {
        assertEquals(product(), ArchiveCodec.decode(ArchiveCodec.encode(product())))
    }
    @Test fun emptySlotIsObservationNotAcknowledgement() {
        assertEquals(ArchiveState.EMPTY_OBSERVED, ArchivePolicy.observed(product(), product().copy(active = false)))
    }
    @Test fun unchangedAndChangedSlots() {
        assertEquals(ArchiveState.STILL_PRESENT, ArchivePolicy.observed(product(), product()))
        assertEquals(ArchiveState.SLOT_CHANGED, ArchivePolicy.observed(product(), product().copy(initialWeight = 1100.0)))
    }
    @Test fun pendingRejectsInvalidProduct() {
        for (p in listOf(product().copy(active = false), product().copy(initialWeight = Double.NaN),
            product().copy(startDate = "2026-02-30"), product().copy(id = 31), product().copy(targetLossPct = 0.0))) {
            try { ArchivePolicy.pending("camera", p); fail("Invalid product accepted") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun csvHasBomHistoryAndEscapedCells() {
        val csv = archiveCsv(listOf(entry().copy(name = "Сыр; \"тест\"", notes = "строка 1\nстрока 2")))
        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("initial"))
        assertTrue(csv.contains("control"))
        assertTrue(csv.contains("900.0"))
        assertTrue(csv.contains("\"\"тест\"\""))
    }
    @Test fun csvNeutralizesFormulaCells() {
        for (name in listOf("=1+1", " +1", "-2+3", "@SUM(A1)", "\t=1")) {
            val csv = archiveCsv(listOf(entry().copy(name = name)))
            assertTrue(csv.contains("\"'" + name))
        }
    }
    @Test fun csvIsNotASecretContainer() {
        val csv = archiveCsv(listOf(entry()))
        assertFalse(csv.contains("http://camera"))
        assertFalse(csv.contains("password"))
        assertEquals(64, entry().cameraKey.length)
    }
}
