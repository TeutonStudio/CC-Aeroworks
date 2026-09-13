package de.teutonstudio.ccaeroworks.input

import com.mred231.aeroworks.content.controls.channel.ControlChannel
import com.mred231.aeroworks.content.controls.module.ModuleType
import com.mred231.aeroworks.content.controls.module.ModuleTypes
import com.mred231.aeroworks.content.controls.module.MountedModule

object CombinedInputSource {
    const val ID: String = "cc_aeroworks.combined"
    private val displayPointerModules: Set<String> = setOf(
        "cc_aeroworks:three_digit_display",
        "cc_aeroworks:large_radar_display"
    )

    /**
     * MountedModule assembles child-module channels and prefixes their IDs with the complete module
     * path. Selecting Axis records here therefore covers pedals, copper variants and composed
     * modules without losing the ID Aeroworks uses for configuration and network updates. Binary
     * button channels remain absent because mouse motion has no continuous value to map onto them.
     */
    fun axisChannelIds(channels: Iterable<ControlChannel>): List<String> = channels
        .filterIsInstance<ControlChannel.Axis>()
        .map(ControlChannel.Axis::id)

    fun mouseAxis(channel: ControlChannel.Axis): MouseAxis =
        if (channel.horizontalHud()) MouseAxis.X else MouseAxis.Y

    fun mouseAxis(module: MountedModule, channelId: String): MouseAxis = module.channels()
        .filterIsInstance<ControlChannel.Axis>()
        .firstOrNull { it.id() == channelId }
        ?.let(::mouseAxis)
        ?: MouseAxis.Y

    fun moduleId(module: MountedModule): String = ModuleTypes.idOf(module.type()).toString()

    fun moduleId(moduleType: ModuleType): String = ModuleTypes.idOf(moduleType).toString()

    fun channels(module: MountedModule): List<String> = axisChannelIds(module.channels())

    fun supports(module: MountedModule): Boolean = channels(module).isNotEmpty()

    /**
     * Display pointer modules keep real Aeroworks ControlChannels so ModuleScreen can configure
     * their independent X/Y activation keys. They are nevertheless a separate semantic kind from
     * vehicle controls: their channels exist only to drive the local pseudo-finger interaction.
     */
    fun isDisplayPointerModule(module: MountedModule): Boolean = moduleId(module) in displayPointerModules

    fun isDisplayPointerModule(moduleType: ModuleType): Boolean = moduleId(moduleType) in displayPointerModules

    fun isCombinedOnly(module: MountedModule): Boolean = isDisplayPointerModule(module)

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
