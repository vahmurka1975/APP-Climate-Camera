package com.example.climatecamera

import kotlin.math.abs

/** Comparison is about live values, not persistence in NVS. */
internal object ReadbackPolicy {
    const val MAX_ATTEMPTS = 3
    const val RETRY_DELAY_MS = 500L

    fun differences(expected: Map<String, String>, actual: Map<String, String>): List<String> =
        expected.keys.filter { key ->
            val wanted = expected[key]?.replace(',', '.')?.toDoubleOrNull()
            val received = actual[key]?.replace(',', '.')?.toDoubleOrNull()
            wanted == null || received == null || !wanted.isFinite() || !received.isFinite() ||
                abs(wanted - received) > 0.00001
        }

    fun canEdit(verified: Boolean, readOnly: Boolean, busy: Boolean): Boolean =
        verified && !readOnly && !busy
}
