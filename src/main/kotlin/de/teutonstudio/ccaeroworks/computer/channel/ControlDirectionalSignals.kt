package de.teutonstudio.ccaeroworks.computer.channel

import de.teutonstudio.ccaeroworks.compat.drivebywire.NativeDriveByWireChannel

data class DirectionalControlSignal(
    val direction: String,
    val label: String,
    val value: Int,
    /** Native Aeroworks axis sign represented by this redstone-facing channel. */
    val sign: Int,
    val wireChannel: String?
)

/**
 * Aeroworks continuous controls are signed internally (-15..15), while their physical redstone/DBW
 * outputs are separate directional channels. Neutral therefore means 0 on both outputs, not a fake
 * midpoint of 8 on one channel.
 */
object ControlDirectionalSignals {
    fun split(
        moduleId: String,
        socket: Int,
        channel: String,
        nativeValue: Int,
        availableWireChannels: List<NativeDriveByWireChannel>
    ): List<DirectionalControlSignal> {
        val (negative, positive) = directions(moduleId, channel)
        val value = nativeValue.coerceIn(-15, 15)
        return listOf(
            DirectionalControlSignal(
                direction = negative,
                label = displayLabel(channel, negative),
                value = (-value).coerceIn(0, 15),
                sign = -1,
                wireChannel = availableWireChannels.firstOrNull {
                    it.socket == socket && it.channelId == channel && it.sign < 0
                }?.id
            ),
            DirectionalControlSignal(
                direction = positive,
                label = displayLabel(channel, positive),
                value = value.coerceIn(0, 15),
                sign = 1,
                wireChannel = availableWireChannels.firstOrNull {
                    it.socket == socket && it.channelId == channel && it.sign > 0
                }?.id
            )
        )
    }

    private fun directions(moduleId: String, channel: String): Pair<String, String> {
        val module = moduleId.substringAfter(':')
        return when {
            channel == "x" -> "left" to "right"
            channel == "y" -> "forward" to "back"
            channel == "wheel" -> "left" to "right"
            channel == "turn" -> "left" to "right"
            channel == "pitch" -> "forward" to "back"
            channel == "lever" -> "forward" to "back"
            module == "throttle_quadrant" -> "forward" to "back"
            else -> "negative" to "positive"
        }
    }

    private fun displayLabel(channel: String, direction: String): String = when (channel) {
        "x", "y", "wheel", "turn", "pitch", "lever" -> direction
        else -> "$channel $direction"
    }
}
