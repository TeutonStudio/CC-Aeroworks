package de.teutonstudio.ccaeroworks.compat.drivebywire

import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity
import de.teutonstudio.ccaeroworks.computer.wire.WireBackend
import de.teutonstudio.ccaeroworks.computer.wire.WireConnectionView
import edn.stratodonut.drivebywire.wire.WireNetworkManager
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction

/**
 * Optional Drive By Wire 0.2.9 backend.
 *
 * This class is loaded reflectively only when the drivebywire mod is present, so the core
 * ComputerControlDesk remains loadable without the optional dependency.
 */
class DriveByWireWireBackend(
    private val owner: ComputerControlDeskBlockEntity
) : WireBackend {
    override val name: String = "drivebywire"

    override fun setValue(channel: String, value: Int) {
        val level = owner.level ?: return
        WireNetworkManager.trySetSignalAt(level, owner.blockPos, channel, value)
    }

    override fun removeChannel(channel: String) {
        val level = owner.level ?: return
        WireNetworkManager.trySetSignalAt(level, owner.blockPos, channel, 0)
        val sinks = sinks(owner.blockPos, channel)
        sinks.forEach { sink ->
            WireNetworkManager.removeConnection(
                level,
                owner.blockPos,
                BlockPos.of(sink.position()),
                Direction.from3DDataValue(sink.direction()),
                channel
            )
        }
    }

    override fun renameChannel(oldName: String, newName: String, value: Int) {
        if (oldName == newName) return
        val level = owner.level ?: return
        val oldSinks = sinks(owner.blockPos, oldName)

        WireNetworkManager.trySetSignalAt(level, owner.blockPos, oldName, 0)
        oldSinks.forEach { sink ->
            WireNetworkManager.removeConnection(
                level,
                owner.blockPos,
                BlockPos.of(sink.position()),
                Direction.from3DDataValue(sink.direction()),
                oldName
            )
        }

        if (value > 0) {
            WireNetworkManager.trySetSignalAt(level, owner.blockPos, newName, value)
        }
        oldSinks.forEach { sink ->
            val result = WireNetworkManager.createConnection(
                level,
                owner.blockPos,
                BlockPos.of(sink.position()),
                Direction.from3DDataValue(sink.direction()),
                newName
            )
            if (!result.isSuccess) {
                CCAeroworks.LOGGER.warn(
                    "Failed to migrate Drive By Wire connection {} -> {} while renaming '{}' to '{}': {}",
                    owner.blockPos,
                    BlockPos.of(sink.position()),
                    oldName,
                    newName,
                    result.description
                )
            }
        }
    }

    override fun connectionCount(channel: String): Int = sinks(owner.blockPos, channel).size

    override fun connectionTargets(sourcePos: BlockPos, channel: String): List<WireConnectionView> =
        sinks(sourcePos, channel)
            .map { sink ->
                val pos = BlockPos.of(sink.position())
                WireConnectionView(
                    x = pos.x,
                    y = pos.y,
                    z = pos.z,
                    side = Direction.from3DDataValue(sink.direction()).name.lowercase()
                )
            }
            .sortedWith(compareBy(WireConnectionView::x, WireConnectionView::y, WireConnectionView::z, WireConnectionView::side))

    override fun clearSignals() {
        val level = owner.level ?: return
        WireNetworkManager.get(level).clearSourceSignals(level, owner.blockPos)
    }

    private fun sinks(sourcePos: BlockPos, channel: String) = owner.level
        ?.let(WireNetworkManager::get)
        ?.network
        ?.get(sourcePos.asLong())
        ?.get(channel)
        ?.toList()
        .orEmpty()
}
