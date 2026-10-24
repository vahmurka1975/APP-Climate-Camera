package com.example.climatecamera

import org.junit.Assert.*
import org.junit.Test

class FreshnessPolicyTest {
    @Test fun missingSampleIsStale() {
        assertTrue(FreshnessPolicy.stale(null, 100_000L, 20_000L))
        assertNull(FreshnessPolicy.ageSeconds(null, 100_000L))
    }
    @Test fun recentStatusIsFresh() {
        assertFalse(FreshnessPolicy.stale(50_000L, 69_999L, FreshnessPolicy.STATUS_MAX_AGE_MS))
    }
    @Test fun statusExpiresAtBoundary() {
        assertTrue(FreshnessPolicy.stale(50_000L, 70_000L, FreshnessPolicy.STATUS_MAX_AGE_MS))
    }
    @Test fun productListExpiresAtBoundary() {
        assertFalse(FreshnessPolicy.stale(1_000L, 60_999L, FreshnessPolicy.PRODUCTS_MAX_AGE_MS))
        assertTrue(FreshnessPolicy.stale(1_000L, 61_000L, FreshnessPolicy.PRODUCTS_MAX_AGE_MS))
    }
    @Test fun reversedClockIsNeverFresh() {
        assertTrue(FreshnessPolicy.stale(20_000L, 19_000L, 20_000L))
        assertEquals(0L, FreshnessPolicy.ageSeconds(20_000L, 19_000L)!!)
    }
    @Test fun ageUsesWholeSeconds() {
        assertEquals(2L, FreshnessPolicy.ageSeconds(1_000L, 3_999L)!!)
    }
}
