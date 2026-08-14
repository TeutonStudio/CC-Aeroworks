package de.teutonstudio.ccaeroworks.computer

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
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
 * computer and a client-side return context for Aeroworks' configuration screens.
 *
 * Aeroworks has two native entry shapes:
 * - OVERVIEW: ConsoleScreen, only when ConsoleScreenOpener.hasOverview() is true.
 * - DETAIL: ModuleScreen for one exact ConsoleSocket. With exactly one control Aeroworks
 *   skips the overview completely, so DETAIL must work without an OVERVIEW ever existing.
 */
object ControlDeskUiSwitchState {
    private data class Session(
        val dimension: ResourceKey<Level>,
        val pos: BlockPos
    )

    private enum class ClientReturnMode {
        NONE,
        OVERVIEW,
        DETAIL
    }

    private val sessions = ConcurrentHashMap<UUID, Session>()

    @Volatile
    private var clientComputerAvailable: Boolean = false

    @Volatile
    private var clientReturnMode: ClientReturnMode = ClientReturnMode.NONE

    @Volatile
    private var clientControlsSocket: ConsoleSocket? = null

    @Volatile
    private var clientControlsConsole: ConsoleBlockEntity? = null

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
     * Capture Aeroworks' exact native detail context while ModuleScreen still owns it.
     * ModuleMenu.contentHolder is a ConsoleSocket for desk-mounted controls and carries
     * the physical desk, socket index and recursive subPath.
     */
    @JvmStatic
    fun rememberClientControls(holder: Any?) {
        val socket = holder as? ConsoleSocket
        if (socket == null || !socket.valid()) {
            clearClientControlsContext()
            return
        }

        setClientContext(ClientReturnMode.DETAIL, socket.be(), socket)
    }

    /**
     * Capture ConsoleScreen as an overview return target. This method is never called in
     * Aeroworks' one-control path because ConsoleScreenOpener skips ConsoleScreen there.
     */
    @JvmStatic
    fun rememberClientOverview(console: ConsoleBlockEntity) {
        if (console.isRemoved) {
            clearClientControlsContext()
            return
        }

        setClientContext(ClientReturnMode.OVERVIEW, console, null)
    }

    private fun setClientContext(
        mode: ClientReturnMode,
        console: ConsoleBlockEntity,
        socket: ConsoleSocket?
    ) {
        clientReturnMode = mode
        clientControlsConsole = console
        clientControlsSocket = socket

        val level = console.level
        if (level == null) {
            clientComputerAvailable = false
            return
        }

        val snapshot = ConsoleMultiblockManager.resolve(level, console.blockPos)
        clientComputerAvailable = console is ComputerControlDeskBlockEntity ||
            (snapshot.state == ConsoleNetworkState.ACTIVE && snapshot.owner != null)
    }

    @JvmStatic
    fun clearClientControlsContext() {
        clientComputerAvailable = false
        clientReturnMode = ClientReturnMode.NONE
        clientControlsSocket = null
        clientControlsConsole = null
    }

    @JvmStatic
    fun clientCanSwitchToComputer(): Boolean = clientComputerAvailable

    @JvmStatic
    fun clientCanReturnToControls(): Boolean {
        if (clientReturnMode == ClientReturnMode.NONE) return false
        if (clientReturnMode == ClientReturnMode.DETAIL && clientControlsSocket?.valid() == true) return true
        return clientControlsConsole?.isRemoved == false
    }

    /**
     * Reopen an exact ModuleScreen when possible. OVERVIEW, or a detail which became invalid
     * while the computer was open, is intentionally left to the client navigation helper so
     * Aeroworks' ConsoleScreenOpener can re-evaluate whether the current group has 0, 1 or many
     * controls.
     */
    @JvmStatic
    fun reopenExactClientControls(): Boolean {
        if (clientReturnMode != ClientReturnMode.DETAIL) return false
        val socket = clientControlsSocket ?: return false
        if (!socket.valid()) return false
        socket.reopenModuleMenu()
        return true
    }

    @JvmStatic
    fun clientReturnConsole(): ConsoleBlockEntity? =
        clientControlsConsole?.takeUnless { it.isRemoved }

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
