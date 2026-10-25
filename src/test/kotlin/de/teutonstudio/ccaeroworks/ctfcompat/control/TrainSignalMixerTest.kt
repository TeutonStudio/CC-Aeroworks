package de.teutonstudio.ccaeroworks.ctfcompat.control

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrainSignalMixerTest {
    @Test
    fun `strongest analog magnitude wins`() {
        assertEquals(0.8f, TrainSignalMixer.strongest(0.2f, 0.8f))
        assertEquals(-0.8f, TrainSignalMixer.strongest(-0.8f, 0.2f))
    }

    @Test
    fun `equal opposite analog inputs neutralize`() {
        assertEquals(0.0f, TrainSignalMixer.strongest(-0.75f, 0.75f))
        assertEquals(0.0f, TrainSignalMixer.strongest(0.75f, -0.75f))
    }

    @Test
    fun `weaker later input cannot displace a strongest opposite tie`() {
        val merged = TrainSignalMixer.mergeAll(
            listOf(
                TrainSignal(throttle = 1.0f, steer = -1.0f),
                TrainSignal(throttle = -1.0f, steer = 1.0f),
                TrainSignal(throttle = 0.5f, steer = 0.5f)
            )
        )
        assertEquals(0.0f, merged.throttle)
        assertEquals(0.0f, merged.steer)
    }

    @Test
    fun `boolean inputs are merged with or`() {
        val merged = TrainSignalMixer.merge(
            TrainSignal(brake = true),
            TrainSignal(station = true)
        )
        assertTrue(merged.brake)
        assertTrue(merged.station)
        assertFalse(merged.throttle != 0.0f)
    }

    @Test
    fun `public steering convention is converted to ctf convention`() {
        assertEquals(1, TrainSignalMixer.ctfSteer(-1.0f))
        assertEquals(-1, TrainSignalMixer.ctfSteer(1.0f))
        assertEquals(0, TrainSignalMixer.ctfSteer(0.32f))
        assertEquals(-1, TrainSignalMixer.ctfSteer(1.0f / 3.0f))
    }
}
