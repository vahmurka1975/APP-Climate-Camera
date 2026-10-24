package com.example.climatecamera

/** Monotonic time only: changing the phone clock must not make data fresh. */
internal object FreshnessPolicy {
    const val STATUS_MAX_AGE_MS = 20_000L
    const val PRODUCTS_MAX_AGE_MS = 60_000L

    fun ageSeconds(receivedAtMs: Long?, nowMs: Long): Long? =
        receivedAtMs?.let { ((nowMs - it).coerceAtLeast(0L)) / 1000L }

    fun stale(receivedAtMs: Long?, nowMs: Long, maxAgeMs: Long): Boolean =
        receivedAtMs == null || nowMs - receivedAtMs < 0L || nowMs - receivedAtMs >= maxAgeMs
}
