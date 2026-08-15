package de.teutonstudio.ccaeroworks.client

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
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
 * Treats a ComputerControlDesk multiblock as one scrollable DBW source catalogue while preserving
 * each native channel's actual physical source position. User-defined channels live only on the
 * ComputerControlDesk owner position, matching WireChannelBank's server output source.
 */
object DriveByWireDeskSelectionResolver {
    fun resolve(level: Level, anyMember: BlockPos): DriveByWireDeskSelection? {
        val snapshot = ConsoleMultiblockManager.resolve(level, anyMember)
        if (snapshot.state != ConsoleNetworkState.ACTIVE) return null
        val owner = snapshot.owner ?: return null
        val endpoints = arrayListOf<DriveByWireDeskEndpoint>()
        val seen = linkedSetOf<String>()

        snapshot.members.forEach { member ->
            nativeChannels(member.desk, NativeDriveByWireChannels.channels(level, member.pos)).forEach { channel ->
                val key = "${member.pos.asLong()}|$channel"
                if (seen.add(key)) {
                    endpoints += DriveByWireDeskEndpoint(member.pos.immutable(), channel, false)
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

    private fun nativeChannels(desk: ConsoleBlockEntity, raw: List<String>): List<String> {
        if (raw.isEmpty()) return emptyList()
        var hasDisplayPointer = false
        var hasPhysicalContinuousControl = false
        for (socket in 0 until desk.socketCount()) {
            val module = desk.module(socket) ?: continue
            if (CombinedInputSource.isDisplayPointerModule(module)) {
                hasDisplayPointer = true
                continue
            }
            if (CombinedInputSource.channels(module).isNotEmpty()) {
                hasPhysicalContinuousControl = true
            }
        }

        // Large CC-Aeroworks displays are pointer-only modules. Their x/y motion is a local input
        // mechanism and must never appear as a DBW/redstone source channel.
        if (hasDisplayPointer && !hasPhysicalContinuousControl) return emptyList()
        return raw
    }
}
