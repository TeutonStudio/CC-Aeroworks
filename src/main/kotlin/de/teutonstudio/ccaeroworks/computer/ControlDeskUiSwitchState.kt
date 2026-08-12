package de.teutonstudio.ccaeroworks.computer

import com.mred231.aeroworks.content.controls.ConsoleSocket
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps the server-authoritative desk session used for switching into the embedded
 * computer, plus the exact client-side Aeroworks module socket needed for a lossless
 * return to the original ModuleScreen.
 */
object ControlDeskUiSwitchState {
    private data class Session(
        val dimension: ResourceKey<Level>,
        val pos: BlockPos
    )

    private val sessions = ConcurrentHashMap<UUID, Session>()

    @Volatile
    private var clientComputerAvailable: Boolean = false

    @Volatile
    private var clientControlsSocket: ConsoleSocket? = null

    fun remember(event: PlayerInteractEvent.RightClickBlock) {
        val snapshot = ConsoleMultiblockManager.resolve(event.level, event.pos)
        val computerAvailable = event.level.getBlockEntity(event.pos) is ComputerControlDeskBlockEntity ||
            (snapshot.state == ConsoleNetworkState.ACTIVE && snapshot.owner != null)

        if (event.level.isClientSide) {
            clientComputerAvailable = computerAvailable
            return
        }

        val player = event.entity as? ServerPlayer ?: return
        sessions[player.uuid] = Session(
            event.level.dimension(),
            event.pos.immutable()
        )
    }

    /**
     * Capture Aeroworks' exact native module context while ModuleScreen still owns it.
     * ModuleMenu.contentHolder is a ConsoleSocket for desk-mounted controls and carries
     * the physical desk, socket index and recursive subPath.
     */
    @JvmStatic
    fun rememberClientControls(holder: Any?) {
        val socket = holder as? ConsoleSocket
        if (socket == null || !socket.valid()) {
            clientControlsSocket = null
            clientComputerAvailable = false
            return
        }

        clientControlsSocket = socket
        val desk = socket.be()
        val level = desk.level
        if (level == null) {
            clientComputerAvailable = false
            return
        }

        val snapshot = ConsoleMultiblockManager.resolve(level, desk.blockPos)
        clientComputerAvailable = desk is ComputerControlDeskBlockEntity ||
            (snapshot.state == ConsoleNetworkState.ACTIVE && snapshot.owner != null)
    }

    @JvmStatic
    fun clearClientControlsContext() {
        clientControlsSocket = null
    }

    @JvmStatic
    fun clientCanSwitchToComputer(): Boolean = clientComputerAvailable

    @JvmStatic
    fun clientCanReturnToControls(): Boolean = clientControlsSocket?.valid() == true

    /**
     * Reopen precisely the Aeroworks ModuleScreen which preceded the computer screen.
     * ConsoleSocket.reopenModuleMenu() emits Aeroworks' native C2SOpenModuleMenu with
     * the original desk position, socket and subPath, so Aeroworks performs all normal
     * reachability, ownership and module-node validation itself.
     */
    @JvmStatic
    fun reopenClientControls(): Boolean {
        val socket = clientControlsSocket ?: return false
        if (!socket.valid()) {
            clientControlsSocket = null
            return false
        }
        socket.reopenModuleMenu()
        return true
    }

    @JvmStatic
    fun switchToComputer(player: ServerPlayer): Boolean {
        val session = validSession(player) ?: return false
        val level = player.serverLevel()
        val direct = level.getBlockEntity(session.pos) as? ComputerControlDeskBlockEntity
        if (direct != null) return direct.openTerminal(player, direct = true)

        val snapshot = ConsoleMultiblockManager.resolve(level, session.pos)
        if (snapshot.state != ConsoleNetworkState.ACTIVE) return false
        return snapshot.owner?.openTerminal(player) == true
    }

    private fun validSession(player: ServerPlayer): Session? {
        val session = sessions[player.uuid] ?: return null
        val level = player.serverLevel()
        if (session.dimension != level.dimension()) {
            sessions.remove(player.uuid)
            return null
        }
        if (!level.hasChunkAt(session.pos) || !level.mayInteract(player, session.pos)) return null
        if (!de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes.isControlDesk(
                level.getBlockState(session.pos).block
            )
        ) return null

        val maximumDistance = player.blockInteractionRange() + 1.0
        if (player.distanceToSqr(session.pos.center) > maximumDistance * maximumDistance) return null
        return session
    }
}
