package de.teutonstudio.ccaeroworks.computer.channel

data class DirectionalControlSignal(
    val direction: String,
    val label: String,
    val value: Int,
    val wireChannel: String?
)

/**
 * Aeroworks continuous controls are signed internally (-15..15), but their physical redstone/DBW
 * outputs are two independent 0..15 directions which share zero. The unified channel view mirrors
 * that physical model instead of inventing a fake midpoint value.
 */
object ControlDirectionalSignals {
    fun split(
        moduleId: String,
        channel: String,
        nativeValue: Int,
        availableWireChannels: List<String>
    ): List<DirectionalControlSignal> {
        val (negative, positive) = directions(moduleId, channel)
        val value = nativeValue.coerceIn(-15, 15)
        return listOf(
            DirectionalControlSignal(
                direction = negative,
                label = displayLabel(channel, negative),
                value = (-value).coerceIn(0, 15),
                wireChannel = matchWireChannel(availableWireChannels, channel, negative)
            ),
            DirectionalControlSignal(
                direction = positive,
                label = displayLabel(channel, positive),
                value = value.coerceIn(0, 15),
                wireChannel = matchWireChannel(availableWireChannels, channel, positive)
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

    /**
     * Aeroworks owns the exact DBW channel IDs. Prefer its actual names and only use semantic
     * matching to associate those IDs with the signed axis direction shown in our UI.
     */
    private fun matchWireChannel(
        available: List<String>,
        channel: String,
        direction: String
    ): String? {
        if (available.isEmpty()) return null
        val normalizedDirection = normalize(direction)
        val normalizedChannel = normalize(channel)

        available.firstOrNull { normalize(it) == normalizedDirection }?.let { return it }

        val directional = available.filter { candidate ->
            tokens(candidate).contains(normalizedDirection)
        }
        if (directional.size == 1) return directional.single()

        directional.firstOrNull { candidate ->
            tokens(candidate).contains(normalizedChannel)
        }?.let { return it }

        return available.firstOrNull { candidate ->
            val normalized = normalize(candidate)
            normalized.contains(normalizedChannel) && normalized.contains(normalizedDirection)
        }
    }

    private fun normalize(value: String): String = value
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')

    private fun tokens(value: String): Set<String> = normalize(value)
        .split('_')
        .filter(String::isNotBlank)
        .toSet()
}
