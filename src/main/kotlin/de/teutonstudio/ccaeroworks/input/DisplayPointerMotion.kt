package de.teutonstudio.ccaeroworks.input

import kotlin.math.exp
import kotlin.math.hypot

/**
 * Tracks the most recent pointer velocity in normalized display-surface coordinates.
 *
 * Raw mouse deltas are transformed into the actual U/V movement applied to the virtual finger
 * before they arrive here. Velocity is measured per second and smoothed with a time-domain
 * exponential filter, then split into a normalized direction and a resolution-independent speed.
 * A zero-motion observation terminates the smoothing chain but deliberately keeps the last
 * published direction/speed so a draw-end event can still describe the motion before release.
 */
class DisplayPointerMotion {
    companion object {
        /** Roughly preserves the previous 0.65-per-frame response at 60 Hz. */
        private const val SMOOTHING_TIME_SECONDS = 0.016
        private const val EPSILON = 1.0e-12
    }

    private var velocityU: Double = 0.0
    private var velocityV: Double = 0.0
    private var hasVelocity: Boolean = false

    var directionU: Double = 0.0
        private set
    var directionV: Double = 0.0
        private set
    var speed: Double = 0.0
        private set

    fun observe(deltaU: Double, deltaV: Double, deltaSeconds: Double) {
        if (!deltaU.isFinite() || !deltaV.isFinite() || !deltaSeconds.isFinite() || deltaSeconds <= EPSILON) return
        val distance = hypot(deltaU, deltaV)
        if (distance <= EPSILON) {
            velocityU = 0.0
            velocityV = 0.0
            hasVelocity = false
            return
        }

        val sampleVelocityU = deltaU / deltaSeconds
        val sampleVelocityV = deltaV / deltaSeconds
        if (!sampleVelocityU.isFinite() || !sampleVelocityV.isFinite()) return

        if (!hasVelocity) {
            velocityU = sampleVelocityU
            velocityV = sampleVelocityV
            hasVelocity = true
        } else {
            val alpha = 1.0 - exp(-deltaSeconds / SMOOTHING_TIME_SECONDS)
            velocityU += alpha * (sampleVelocityU - velocityU)
            velocityV += alpha * (sampleVelocityV - velocityV)
        }

        val magnitude = hypot(velocityU, velocityV)
        if (magnitude <= EPSILON || !magnitude.isFinite()) return

        speed = magnitude
        directionU = velocityU / magnitude
        directionV = velocityV / magnitude
    }

    fun reset() {
        velocityU = 0.0
        velocityV = 0.0
        hasVelocity = false
        directionU = 0.0
        directionV = 0.0
        speed = 0.0
    }
}
