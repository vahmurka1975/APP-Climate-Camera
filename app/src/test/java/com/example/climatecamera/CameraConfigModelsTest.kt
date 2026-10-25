package com.example.climatecamera

import org.junit.Assert.*
import org.junit.Test

class CameraConfigModelsTest {
    @Test fun boundsAndDecimals() {
        val t = CameraConfigFields.all.first { it.key == "tempTarget" }
        assertEquals("12.5", t.normalized("12,5"))
        assertEquals("2.0", t.normalized("2"))
        assertNull(t.normalized("1.9"))
        assertNull(t.normalized("NaN"))
        assertNull(t.normalized("Infinity"))
    }
    @Test fun integerAndZeroPause() {
        val f = CameraConfigFields.all.first { it.key == "fanPauseMin" }
        assertEquals("0", f.normalized("0"))
        assertNull(f.normalized("1.5"))
        assertNull(f.normalized("-1"))
    }
    @Test fun keysAreUnique() {
        assertEquals(11, CameraConfigFields.all.size)
        assertEquals(11, CameraConfigFields.all.map { it.key }.toSet().size)
    }
}
