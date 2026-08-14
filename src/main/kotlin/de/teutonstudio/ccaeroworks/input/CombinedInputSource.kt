package de.teutonstudio.ccaeroworks.input

import com.mred231.aeroworks.content.controls.ModuleType
import com.mred231.aeroworks.content.controls.ModuleTypes
import com.mred231.aeroworks.content.controls.MountedModule

object CombinedInputSource {
    const val ID: String = "cc_aeroworks.combined"
    const val ID_X: String = "cc_aeroworks.combined_x"
    const val ID_Y: String = "cc_aeroworks.combined_y"
    const val LEVER_CHANNEL: String = "lever"
    const val X_CHANNEL: String = "x"
    const val Y_CHANNEL: String = "y"

    private val displayPointerModules: Set<String> = setOf(
        "cc_aeroworks:three_digit_display",
        "cc_aeroworks:large_radar_display"
    )

    private val supportedChannels: Map<String, List<String>> = mapOf(
        "aeroworks:lever" to listOf(LEVER_CHANNEL),
        "aeroworks:joystick" to listOf(X_CHANNEL, Y_CHANNEL),
        "aeroworks:wheel" to listOf("wheel"),
        "aeroworks:yoke" to listOf("turn", "pitch"),
        "aeroworks:throttle_quadrant" to listOf("red", "amber", "green", "blue"),
        "cc_aeroworks:three_digit_display" to listOf(X_CHANNEL, Y_CHANNEL),
        "cc_aeroworks:large_radar_display" to listOf(X_CHANNEL, Y_CHANNEL)
    )

    private val horizontalChannels: Set<String> = setOf(
        X_CHANNEL,
        "wheel",
        "turn"
    )

    private val combinedSourceIds: Set<String> = setOf(ID, ID_X, ID_Y)

    fun channelsFor(moduleId: String): List<String> = supportedChannels[moduleId].orEmpty()

    fun moduleId(module: MountedModule): String = ModuleTypes.idOf(module.type()).toString()

    fun moduleId(moduleType: ModuleType): String = ModuleTypes.idOf(moduleType).toString()

    fun channels(module: MountedModule): List<String> = channelsFor(moduleId(module))

    fun supports(module: MountedModule): Boolean = channels(module).isNotEmpty()

    fun topology(module: MountedModule): Topology = when (moduleId(module)) {
        "aeroworks:lever",
        "aeroworks:wheel",
        "aeroworks:throttle_quadrant" -> Topology.ONE_D

        "aeroworks:joystick",
        "aeroworks:yoke" -> Topology.TWO_D

        in displayPointerModules -> Topology.DISPLAY_POINTER
        else -> Topology.NONE
    }

    fun supportsAxisSelection(module: MountedModule, channel: String): Boolean =
        topology(module) == Topology.ONE_D && channel in channels(module)

    fun defaultMouseAxis(channel: String): MouseAxis =
        if (channel in horizontalChannels) MouseAxis.X else MouseAxis.Y

    fun mouseAxis(module: MountedModule, channel: String): MouseAxis {
        if (!supportsAxisSelection(module, channel)) return defaultMouseAxis(channel)
        return when (module.analogSourceFor(channel)) {
            ID_X -> MouseAxis.X
            ID_Y -> MouseAxis.Y
            else -> defaultMouseAxis(channel)
        }
    }

    fun sourceForAxis(axis: MouseAxis): String = when (axis) {
        MouseAxis.X -> ID_X
        MouseAxis.Y -> ID_Y
    }

    fun isCombinedSource(source: String): Boolean = source in combinedSourceIds

    fun isDisplayPointerModule(module: MountedModule): Boolean = moduleId(module) in displayPointerModules

    fun isDisplayPointerModule(moduleType: ModuleType): Boolean = moduleId(moduleType) in displayPointerModules

    fun isCombinedOnly(module: MountedModule): Boolean = isDisplayPointerModule(module)

    fun isCombined(module: MountedModule, channel: String): Boolean =
        channel in channels(module) &&
            module.analogActiveFor(channel) &&
            isCombinedSource(module.analogSourceFor(channel))

    /** Aeroworks persists this through ChannelConfig. In combined mode it is the hold-to-control key. */
    fun activationBinding(module: MountedModule, channel: String): String =
        module.channelConfig(channel)?.negativeKey().orEmpty()

    enum class Topology {
        NONE,
        ONE_D,
        TWO_D,
        DISPLAY_POINTER
    }

    enum class MouseAxis {
        X,
        Y
    }
}
