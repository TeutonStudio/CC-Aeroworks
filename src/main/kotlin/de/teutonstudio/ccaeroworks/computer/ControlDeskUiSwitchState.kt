package de.teutonstudio.ccaeroworks.computer

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import com.mred231.aeroworks.content.controls.ConsoleSocket
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes
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
 * Keeps the server-authoritative desk session used by ComputerControlDesk sub-pages and a
 * client-side return context for Aeroworks' configuration screens. UI switching itself carries the
 * current desk anchor explicitly, so it never depends on whichever right-click happened previously.
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
        sessions[player.uuid] = Session(event.level.dimension(), event.pos.immutable())
    }

    @JvmStatic
    fun rememberClientControls(holder: Any?) {
        val socket = holder as? ConsoleSocket
        if (socket == null || !socket.valid()) {
            clearClientControlsContext()
            return
        }
        setClientContext(ClientReturnMode.DETAIL, socket.be(), socket)
    }

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

    /** Resolve the ComputerControlDesk represented by the player's current saved desk session. */
    @JvmStatic
    fun activeComputerDesk(player: ServerPlayer): ComputerControlDeskBlockEntity? {
        val session = validSession(player) ?: return null
        return resolveOwner(player, session.pos)
    }

    /**
     * Switch using the exact desk anchor from the currently visible Aeroworks screen. Successful
     * validation also refreshes the server session used by channel/source snapshot requests.
     */
    @JvmStatic
    fun switchToComputer(player: ServerPlayer, anchorPos: BlockPos): Boolean {
        val owner = validateAnchorAndResolveOwner(player, anchorPos) ?: return false
        val level = player.serverLevel()
        sessions[player.uuid] = Session(level.dimension(), anchorPos.immutable())
        val direct = level.getBlockEntity(anchorPos) as? ComputerControlDeskBlockEntity
        return if (direct != null) direct.openTerminal(player, direct = true) else owner.openTerminal(player)
    }

    /** Legacy fallback retained for callers which already established a validated server session. */
    @JvmStatic
    fun switchToComputer(player: ServerPlayer): Boolean {
        val session = validSession(player) ?: return false
        return switchToComputer(player, session.pos)
    }

    private fun validateAnchorAndResolveOwner(
        player: ServerPlayer,
        anchorPos: BlockPos
    ): ComputerControlDeskBlockEntity? {
        val level = player.serverLevel()
        if (!level.hasChunkAt(anchorPos) || !level.mayInteract(player, anchorPos)) return null
        if (!AeroworksTypes.isControlDesk(level.getBlockState(anchorPos).block)) return null

        val snapshot = ConsoleMultiblockManager.resolve(level, anchorPos)
        val direct = level.getBlockEntity(anchorPos) as? ComputerControlDeskBlockEntity
        val owner = direct ?: snapshot.owner ?: return null
        if (direct == null && snapshot.state != ConsoleNetworkState.ACTIVE) return null

        val maximumDistance = player.blockInteractionRange() + 1.0
        val maxDistanceSqr = maximumDistance * maximumDistance
        val inRange = player.distanceToSqr(anchorPos.center) <= maxDistanceSqr ||
            snapshot.members.any { player.distanceToSqr(it.pos.center) <= maxDistanceSqr }
        if (!inRange) return null
        return owner
    }

    private fun resolveOwner(player: ServerPlayer, pos: BlockPos): ComputerControlDeskBlockEntity? {
        val level = player.serverLevel()
        (level.getBlockEntity(pos) as? ComputerControlDeskBlockEntity)?.let { return it }
        val snapshot = ConsoleMultiblockManager.resolve(level, pos)
        if (snapshot.state != ConsoleNetworkState.ACTIVE) return null
        return snapshot.owner
    }

    private fun validSession(player: ServerPlayer): Session? {
        val session = sessions[player.uuid] ?: return null
        val level = player.serverLevel()
        if (session.dimension != level.dimension()) {
            sessions.remove(player.uuid)
            return null
        }
        if (!level.hasChunkAt(session.pos) || !level.mayInteract(player, session.pos)) return null
        if (!AeroworksTypes.isControlDesk(level.getBlockState(session.pos).block)) return null

        val snapshot = ConsoleMultiblockManager.resolve(level, session.pos)
        val maximumDistance = player.blockInteractionRange() + 1.0
        val maxDistanceSqr = maximumDistance * maximumDistance
        if (player.distanceToSqr(session.pos.center) > maxDistanceSqr &&
            snapshot.members.none { player.distanceToSqr(it.pos.center) <= maxDistanceSqr }
        ) return null
        return session
    }
}
