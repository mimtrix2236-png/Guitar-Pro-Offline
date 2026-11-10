package com.mimtrix.guitarprooffline

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class Pitch(val frequency: Double, val noteName: String, val cents: Double, val midi: Int, val clarity: Double)

/** Median-based temporal filter: rejects transient octave jumps and smooths plucked-string decay. */
class PitchStabilizer(private val windowSize: Int = 5, private val maxJumpCents: Double = 1000.0) {
    private val history = ArrayDeque<Double>()
    private var lastFrequency: Double? = null

    fun push(pitch: Pitch): Pitch? {
        if (pitch.clarity < 0.68 || !pitch.frequency.isFinite()) return null
        val previous = lastFrequency
        if (previous != null) {
            val jump = abs(1200.0 * ln(pitch.frequency / previous) / ln(2.0))
            // A sudden octave/large jump is usually a pick transient or another string.
            if (jump > maxJumpCents && history.isNotEmpty()) return null
        }
        history.addLast(pitch.frequency)
        while (history.size > windowSize) history.removeFirst()
        val sorted = history.sorted()
        val median = sorted[sorted.size / 2]
        // Light smoothing after the robust median, without making the meter sluggish.
        val stable = if (previous == null) median else previous * 0.25 + median * 0.75
        lastFrequency = stable
        return TunerEngine.fromFrequency(stable, pitch.clarity)
    }

    fun reset() { history.clear(); lastFrequency = null }
}

object TunerEngine {
    private val sharpNames = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
    private val solfegeNames = arrayOf("Do", "Do♯", "Re", "Re♯", "Mi", "Fa", "Fa♯", "Sol", "Sol♯", "La", "La♯", "Si")

    fun detect(samples: ShortArray, sampleRate: Int, referenceA4: Double = 440.0, minFrequency: Double = 35.0, maxFrequency: Double = 1200.0): Pitch? {
        if (samples.size < 4096 || sampleRate <= 0 || referenceA4 !in 400.0..500.0) return null
        val x = DoubleArray(samples.size) { samples[it].toDouble() / 32768.0 }
        val mean = x.average()
        // Hann window reduces edge discontinuities; DC removal prevents false low notes.
        for (i in x.indices) {
            val hann = 0.5 - 0.5 * kotlin.math.cos(2.0 * Math.PI * i / (x.size - 1))
            x[i] = (x[i] - mean) * hann
        }
        val rms = sqrt(x.sumOf { it * it } / x.size)
        if (rms < 0.0025) return null

        val minLag = (sampleRate / maxFrequency).toInt().coerceAtLeast(2)
        val maxLag = (sampleRate / minFrequency).toInt().coerceAtMost(x.size / 2)
        if (maxLag <= minLag + 3) return null
        val diff = DoubleArray(maxLag + 1)
        for (lag in 1..maxLag) {
            var sum = 0.0
            val limit = x.size - lag
            for (i in 0 until limit) {
                val d = x[i] - x[i + lag]
                sum += d * d
            }
            diff[lag] = sum
        }
        val cmnd = DoubleArray(maxLag + 1)
        cmnd[0] = 1.0
        var running = 0.0
        for (lag in 1..maxLag) {
            running += diff[lag]
            cmnd[lag] = if (running <= 1e-15) 1.0 else diff[lag] * lag / running
        }

        // Choose the first convincing local minimum (YIN), avoiding octave-subharmonic locking.
        var tau = -1
        for (lag in minLag until maxLag) {
            if (cmnd[lag] < 0.13 && cmnd[lag] <= cmnd[lag + 1]) {
                tau = lag
                break
            }
        }
        if (tau < 0) {
            tau = (minLag..maxLag).minByOrNull { cmnd[it] } ?: return null
            if (cmnd[tau] > 0.24) return null
        }

        val refined = if (tau > minLag && tau < maxLag) {
            val a = cmnd[tau - 1]
            val b = cmnd[tau]
            val c = cmnd[tau + 1]
            val denominator = a - 2.0 * b + c
            if (abs(denominator) > 1e-12) tau + 0.5 * (a - c) / denominator else tau.toDouble()
        } else tau.toDouble()
        val hz = sampleRate / refined
        if (!hz.isFinite() || hz !in minFrequency..maxFrequency) return null
        return fromFrequency(hz, (1.0 - cmnd[tau]).coerceIn(0.0, 1.0), referenceA4)
    }

    fun fromFrequency(hz: Double, clarity: Double = 1.0, referenceA4: Double = 440.0): Pitch? {
        if (!hz.isFinite() || hz <= 0.0 || referenceA4 !in 400.0..500.0) return null
        val midi = (69.0 + 12.0 * ln(hz / referenceA4) / ln(2.0)).roundToInt()
        val target = frequencyForMidi(midi, referenceA4)
        val cents = 1200.0 * ln(hz / target) / ln(2.0)
        val octave = midi / 12 - 1
        return Pitch(hz, "${sharpNames[Math.floorMod(midi, 12)]}$octave", cents, midi, clarity)
    }

    fun frequencyForMidi(midi: Int, referenceA4: Double = 440.0): Double =
        referenceA4 * Math.pow(2.0, (midi - 69) / 12.0)

    fun noteForMidi(midi: Int, solfege: Boolean = false): String {
        val names = if (solfege) solfegeNames else sharpNames
        return "${names[Math.floorMod(midi, 12)]}${midi / 12 - 1}"
    }
}
