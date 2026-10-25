package com.example.climatecamera

import org.junit.Assert.*
import org.junit.Test

class ProductModelsTest {
    @Test fun computesRemainingAndClampsProgress() {
        val p = Product(1, true, 1000.0, 38.0, "2026-10-01", listOf(Weighing("2026-10-11", 800.0)))
        assertEquals(180.0, p.remainingGrams, 0.001)
        assertEquals(20.0 / 38.0, p.targetProgress, 0.001)
        val done = p.copy(history = listOf(Weighing("2026-10-11", 600.0)))
        assertEquals(0.0, done.remainingGrams, 0.001)
        assertEquals(1.0, done.targetProgress, 0.001)
        val increased = p.copy(history = listOf(Weighing("2026-10-11", 1100.0)))
        assertEquals(0.0, increased.targetProgress, 0.001)
    }
    @Test fun rejectsNegativeAndAcceptsDot() {
        assertNull(parseWeight("-1"))
        assertEquals(1250.5, parseWeight("1250.5")!!, 0.001)
    }
    @Test fun acceptsRussianDecimalSeparator() {
        assertEquals(1250.5, parseWeight("1250,5")!!, 0.001)
        assertNull(parseWeight("NaN"))
        assertNull(parseWeight("Infinity"))
        assertNull(parseWeight("0"))
        assertNull(parseWeight("100001"))
    }
    @Test fun validatesCalendarDates() {
        assertTrue(ProductDates.valid("2024-02-29"))
        assertFalse(ProductDates.valid("2025-02-29"))
        assertFalse(ProductDates.valid("2026-13-01"))
        assertEquals("21.10.2026", ProductDates.display("2026-10-21"))
    }
    @Test fun computesLossAndForecast() {
        val p = Product(1, true, 1000.0, 38.0, "2026-10-01", listOf(Weighing("2026-10-11", 800.0)))
        assertEquals(20.0, p.lossPct, 0.001)
        assertEquals(620.0, p.targetWeight, 0.001)
        assertEquals("≈ 20.10.2026", p.forecast())
    }
    @Test fun avoidsForecastWithoutTrend() {
        val p = Product(1, true, 1000.0, 38.0, "2026-10-01", emptyList())
        assertEquals("Нужно контрольное взвешивание", p.forecast())
        assertEquals("Недостаточно данных о снижении веса", p.copy(history = listOf(Weighing("2026-10-01", 800.0))).forecast())
        assertEquals("Целевая усушка достигнута", p.copy(history = listOf(Weighing("2026-10-11", 600.0))).forecast())
    }
    @Test fun csvIncludesInitialAndControlWeights() {
        val p = Product(1, true, 1000.0, 38.0, "2026-10-01", listOf(Weighing("2026-10-11", 800.0)))
        val csv = productsCsv(ProductsSnapshot("2026-10-21", false, listOf(p)))
        assertTrue(csv.startsWith("\uFEFFproduct;kind;date;weight_g;loss_pct;target_loss_pct"))
        assertTrue(csv.contains("\"initial\""))
        assertTrue(csv.contains("\"control\""))
        assertTrue(csv.contains("\"800.0\""))
    }
}
