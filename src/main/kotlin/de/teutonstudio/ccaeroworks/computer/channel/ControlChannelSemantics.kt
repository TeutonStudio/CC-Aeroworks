package de.teutonstudio.ccaeroworks.computer.channel

import com.mred231.aeroworks.content.controls.MountedModule
import de.teutonstudio.ccaeroworks.input.CombinedInputSource

enum class ControlChannelKind {
    VEHICLE_CONTROL,
    DISPLAY_POINTER
}

data class ControlChannelCapabilities(
    val kind: ControlChannelKind,
    val combinedInput: Boolean,
    val driveByWire: Boolean,
    val computerOverride: Boolean
)

/**
 * Central semantic classification for Aeroworks ControlChannels exposed by CC-Aeroworks.
 *
 * Interactive displays deliberately use real Aeroworks x/y channels so their activation bindings
 * live in the normal ModuleScreen. Those channels are local pointer state, however, and must never
 * become vehicle DBW outputs or ComputerControlDesk control overrides.
 */
object ControlChannelSemantics {
    fun kind(module: MountedModule): ControlChannelKind =
        if (CombinedInputSource.isDisplayPointerModule(module)) {
            ControlChannelKind.DISPLAY_POINTER
        } else {
            ControlChannelKind.VEHICLE_CONTROL
        }

    fun capabilities(module: MountedModule, channel: String): ControlChannelCapabilities {
        val supported = channel in CombinedInputSource.channels(module) &&
            module.channels().any { it.id() == channel }
        val kind = kind(module)
        return ControlChannelCapabilities(
            kind = kind,
            combinedInput = supported,
            driveByWire = supported && kind == ControlChannelKind.VEHICLE_CONTROL,
            computerOverride = supported && kind == ControlChannelKind.VEHICLE_CONTROL
        )
    }

    fun isDriveByWireExposed(module: MountedModule, channel: String): Boolean =
        capabilities(module, channel).driveByWire

    fun isComputerOverrideAllowed(module: MountedModule, channel: String): Boolean =
        capabilities(module, channel).computerOverride
}
