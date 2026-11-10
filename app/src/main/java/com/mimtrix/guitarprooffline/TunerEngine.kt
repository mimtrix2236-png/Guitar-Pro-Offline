package com.mimtrix.guitarprooffline

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class Pitch(val frequency: Double, val note: String, val cents: Double)

object TunerEngine {
    private val names = arrayOf("Do", "Do♯", "Re", "Re♯", "Mi", "Fa", "Fa♯", "Sol", "Sol♯", "La", "La♯", "Si")

    fun detect(samples: ShortArray, sampleRate: Int): Pitch? {
        if (samples.size < 1024) return null
        val x = DoubleArray(samples.size) { samples[it].toDouble() / 32768.0 }
        val mean = x.average()
        for (i in x.indices) x[i] -= mean
        val rms = sqrt(x.sumOf { it * it } / x.size)
        if (rms < 0.008) return null

        val minHz = 55.0
        val maxHz = 1200.0
        val minLag = (sampleRate / maxHz).toInt().coerceAtLeast(2)
        val maxLag = (sampleRate / minHz).toInt().coerceAtMost(x.size / 2)
        val diff = DoubleArray(maxLag + 1)
        for (lag in 1..maxLag) {
            var sum = 0.0
            for (i in 0 until x.size - lag) {
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
            cmnd[lag] = if (running == 0.0) 1.0 else diff[lag] * lag / running
        }
        var tau = -1
        for (lag in minLag until maxLag) {
            if (cmnd[lag] < 0.18 && cmnd[lag] <= cmnd[lag + 1]) { tau = lag; break }
        }
        if (tau < 0) {
            tau = (minLag..maxLag).minByOrNull { cmnd[it] } ?: return null
            if (cmnd[tau] > 0.35) return null
        }
        if (tau > minLag && tau < maxLag) {
            val a = cmnd[tau - 1]; val b = cmnd[tau]; val c = cmnd[tau + 1]
            val denom = a - 2 * b + c
            if (abs(denom) > 1e-12) tau = (tau + 0.5 * (a - c) / denom).roundToInt()
        }
        val hz = sampleRate.toDouble() / tau
        if (!hz.isFinite() || hz !in minHz..maxHz) return null
        val midi = (69 + 12 * (ln(hz / 440.0) / ln(2.0))).roundToInt()
        val target = 440.0 * Math.pow(2.0, (midi - 69) / 12.0)
        val cents = 1200.0 * (ln(hz / target) / ln(2.0))
        val octave = midi / 12 - 1
        return Pitch(hz, "${names[Math.floorMod(midi, 12)]}$octave", cents)
    }
}
