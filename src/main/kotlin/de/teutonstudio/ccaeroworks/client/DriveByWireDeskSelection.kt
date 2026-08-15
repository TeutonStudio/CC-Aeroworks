package de.teutonstudio.ccaeroworks.client

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.compat.drivebywire.NativeDriveByWireChannel
import de.teutonstudio.ccaeroworks.compat.drivebywire.NativeDriveByWireChannels
import de.teutonstudio.ccaeroworks.input.CombinedInputSource
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level

data class DriveByWireDeskEndpoint(
    val sourcePos: BlockPos,
    val channel: String,
    val userDefined: Boolean
)

data class DriveByWireDeskSelection(
    val anchor: BlockPos,
    val ownerPos: BlockPos,
    val memberPositions: Set<BlockPos>,
    val endpoints: List<DriveByWireDeskEndpoint>
) {
    fun startAt(clicked: BlockPos): DriveByWireDeskEndpoint? =
        endpoints.firstOrNull { it.sourcePos == clicked }
            ?: endpoints.firstOrNull { it.userDefined }
            ?: endpoints.firstOrNull()

    fun contains(sourcePos: BlockPos, channel: String): Boolean =
        endpoints.any { it.sourcePos == sourcePos && it.channel == channel }

    fun next(sourcePos: BlockPos, channel: String, forward: Boolean): DriveByWireDeskEndpoint? {
        if (endpoints.isEmpty()) return null
        val current = endpoints.indexOfFirst { it.sourcePos == sourcePos && it.channel == channel }
        if (current < 0) return endpoints.first()
        val step = if (forward) 1 else -1
        return endpoints[Math.floorMod(current + step, endpoints.size)]
    }
}

/**
 * One scrollable DBW catalogue for the complete active ControlDesk multiblock.
 *
 * Native entries come straight from Aeroworks ConsoleWireChannels and retain their physical member
 * position. Display-pointer x/y channels are removed individually by resolving their socket back to
 * the mounted module. User-defined ComputerControlDesk channels are appended exactly once at the
 * owner position, matching WireChannelBank's server-side signal source.
 */
object DriveByWireDeskSelectionResolver {
    fun resolve(level: Level, anyMember: BlockPos): DriveByWireDeskSelection? {
        val snapshot = ConsoleMultiblockManager.resolve(level, anyMember)
        if (snapshot.state != ConsoleNetworkState.ACTIVE) return null
        val owner = snapshot.owner ?: return null
        val endpoints = arrayListOf<DriveByWireDeskEndpoint>()
        val seen = linkedSetOf<String>()

        snapshot.members.forEach { member ->
            physicalChannels(member.desk, NativeDriveByWireChannels.channels(member.desk)).forEach { channel ->
                val key = "${member.pos.asLong()}|${channel.id}"
                if (seen.add(key)) {
                    endpoints += DriveByWireDeskEndpoint(member.pos.immutable(), channel.id, false)
                }
            }

            if (member.pos == owner.blockPos) {
                owner.wireChannelNames().forEach { channel ->
                    val key = "${owner.blockPos.asLong()}|$channel"
                    if (seen.add(key)) {
                        endpoints += DriveByWireDeskEndpoint(owner.blockPos.immutable(), channel, true)
                    }
                }
            }
        }

        return DriveByWireDeskSelection(
            anchor = snapshot.anchor.immutable(),
            ownerPos = owner.blockPos.immutable(),
            memberPositions = snapshot.members.mapTo(linkedSetOf()) { it.pos.immutable() },
            endpoints = endpoints
        )
    }

    private fun physicalChannels(
        desk: ConsoleBlockEntity,
        channels: List<NativeDriveByWireChannel>
    ): List<NativeDriveByWireChannel> = channels.filter { channel ->
        val module = channel.socket
            .takeIf { it in 0 until desk.socketCount() }
            ?.let(desk::module)
            ?: return@filter false
        !CombinedInputSource.isDisplayPointerModule(module)
    }
}
