package com.mimtrix.guitarprooffline

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class TunerEngineTest {
    private fun sine(frequency: Double, rate: Int = 44100, size: Int = 8192): ShortArray =
        ShortArray(size) { i -> (sin(2.0 * PI * frequency * i / rate) * 12000).toInt().toShort() }

    @Test fun detectsConcertA() {
        val pitch = TunerEngine.detect(sine(440.0), 44100)
        assertNotNull(pitch)
        assertEquals(440.0, pitch!!.frequency, 1.5)
        assertEquals("A4", pitch.noteName)
    }

    @Test fun detectsLowBassE() {
        val pitch = TunerEngine.detect(sine(41.203), 44100, minFrequency = 30.0)
        assertNotNull(pitch)
        assertEquals(41.203, pitch!!.frequency, 1.5)
    }

    @Test fun silenceIsRejected() {
        assertNull(TunerEngine.detect(ShortArray(8192), 44100))
    }

    @Test fun referenceCalibrationChangesFrequency() {
        assertEquals(442.0, TunerEngine.frequencyForMidi(69, 442.0), 0.001)
    }

    @Test fun tuningCatalogHasExpectedPresets() {
        assertTrue(TuningCatalog.all.any { it.instrument == "Guitarra" && it.name == "DADGAD" })
        assertTrue(TuningCatalog.all.any { it.instrument == "Bajo" && it.strings.size == 6 })
        assertTrue(TuningCatalog.all.any { it.instrument == "Ukelele" })
    }
}
