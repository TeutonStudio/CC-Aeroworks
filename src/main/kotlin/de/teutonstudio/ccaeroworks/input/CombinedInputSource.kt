package de.teutonstudio.ccaeroworks.input

import com.mred231.aeroworks.content.controls.ModuleTypes
import com.mred231.aeroworks.content.controls.MountedModule

object CombinedInputSource {
    const val ID: String = "cc_aeroworks.combined"
    const val LEVER_CHANNEL: String = "lever"

    private val supportedChannels: Map<String, List<String>> = mapOf(
        "aeroworks:lever" to listOf(LEVER_CHANNEL),
        "aeroworks:joystick" to listOf("x", "y"),
        "aeroworks:throttle_quadrant" to listOf("red", "amber", "green", "blue")
    )

    fun mouseAxis(channel: String): MouseAxis = if (channel == "x") MouseAxis.X else MouseAxis.Y

    fun channelsFor(moduleId: String): List<String> = supportedChannels[moduleId].orEmpty()

    fun channels(module: MountedModule): List<String> = channelsFor(ModuleTypes.idOf(module.type()).toString())

    fun supports(module: MountedModule): Boolean = channels(module).isNotEmpty()

    fun isCombined(module: MountedModule, channel: String): Boolean =
        channel in channels(module) && module.analogActiveFor(channel) && module.analogSourceFor(channel) == ID

    /** Aeroworks persists this through ChannelConfig. In combined mode it is the hold-to-control key. */
    fun activationBinding(module: MountedModule, channel: String): String =
        module.channelConfig(channel)?.negativeKey().orEmpty()

    enum class MouseAxis {
        X,
        Y
    }
}
