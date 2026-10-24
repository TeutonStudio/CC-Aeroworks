package de.teutonstudio.ccaeroworks.computer

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import com.mred231.aeroworks.content.controls.socket.ConsoleSocket
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
import de.TeutonStudio.TeutonLib.api.network.PlayerSessionState
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import java.util.UUID

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

    private const val SESSION_TTL_TICKS = 6_000L
    private val sessions = PlayerSessionState<UUID, Session>({ it }, SESSION_TTL_TICKS)

    @Volatile
    private var clientDeviceAvailable: Boolean = false

    @Volatile
    private var clientReturnMode: ClientReturnMode = ClientReturnMode.NONE

    @Volatile
    private var clientControlsSocket: ConsoleSocket? = null

    @Volatile
    private var clientControlsConsole: ConsoleBlockEntity? = null

    fun remember(event: PlayerInteractEvent.RightClickBlock) {
        val snapshot = ConsoleMultiblockManager.resolve(event.level, event.pos)
        val directHost = event.level.getBlockEntity(event.pos) as? ConsoleBlockEntity
        val deviceAvailable = directHost?.let(ConsoleDevices::get) != null ||
            snapshot.deviceHost != null

        if (event.level.isClientSide) {
            clientDeviceAvailable = deviceAvailable
            return
        }

        val player = event.entity as? ServerPlayer ?: return
        sessions.put(player.uuid, Session(event.level.dimension(), event.pos.immutable()), event.level.gameTime)
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
            clientDeviceAvailable = false
            return
        }

        val snapshot = ConsoleMultiblockManager.resolve(level, console.blockPos)
        clientDeviceAvailable = ConsoleDevices.get(console) != null || snapshot.deviceHost != null
    }

    @JvmStatic
    fun clearClientControlsContext() {
        clientDeviceAvailable = false
        clientReturnMode = ClientReturnMode.NONE
        clientControlsSocket = null
        clientControlsConsole = null
    }

    @JvmStatic
    fun clientCanSwitchToComputer(): Boolean = clientDeviceAvailable

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
    fun activeComputerDesk(player: ServerPlayer): ConsoleComputer? {
        val menu = player.containerMenu as? dan200.computercraft.shared.computer.inventory.AbstractComputerMenu
        val computer = menu?.computer
        if (computer != null && menu.stillValid(player) && computer.checkUsable(player)) {
            val pos = computer.position
            val host = computer.level.chunkSource.getChunkNow(pos.x shr 4, pos.z shr 4)?.blockEntities?.get(pos)
            ConsoleComputers.get(host)?.takeIf { it.getServerComputer() === computer }?.let { return it }
        }
        val session = validSession(player) ?: return null
        return resolveOwner(player, session.pos)
    }

    /**
     * Switch using the exact desk anchor from the currently visible Aeroworks screen. Successful
     * validation also refreshes the server session used by channel/source snapshot requests.
     */
    @JvmStatic
    fun switchToComputer(player: ServerPlayer, anchorPos: BlockPos): Boolean {
        val host = validateAnchorAndResolveOwner(player, anchorPos) ?: return false
        val level = player.serverLevel()
        sessions.put(player.uuid, Session(level.dimension(), anchorPos.immutable()), level.gameTime)
        val directHost = level.getBlockEntity(anchorPos) as? ConsoleBlockEntity
        val directComputer = ConsoleComputers.get(directHost)
        if (directComputer != null) return directComputer.openTerminal(player, direct = true)
        val directTerminal = ConsoleTerminals.get(directHost)
        if (directTerminal != null) return directTerminal.open(player, direct = true)
        return ConsoleComputers.get(host)?.openTerminal(player) ?: ConsoleTerminals.get(host)?.open(player) ?: false
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
    ): ConsoleBlockEntity? {
        val level = player.serverLevel()
        if (!level.hasChunkAt(anchorPos) || !level.mayInteract(player, anchorPos)) return null
        if (!AeroworksTypes.isControlDesk(level.getBlockState(anchorPos).block)) return null

        val snapshot = ConsoleMultiblockManager.resolve(level, anchorPos)
        val direct = level.getBlockEntity(anchorPos) as? ConsoleBlockEntity
        val host = direct?.takeIf { ConsoleDevices.get(it) != null } ?: snapshot.deviceHost ?: return null
        if (host !== direct && snapshot.state !in listOf(ConsoleNetworkState.ACTIVE, ConsoleNetworkState.TERMINAL)) return null

        val maximumDistance = player.blockInteractionRange() + 1.0
        val maxDistanceSqr = maximumDistance * maximumDistance
        val inRange = player.distanceToSqr(anchorPos.center) <= maxDistanceSqr ||
            snapshot.members.any { player.distanceToSqr(it.pos.center) <= maxDistanceSqr }
        if (!inRange) return null
        return host
    }

    private fun resolveOwner(player: ServerPlayer, pos: BlockPos): ConsoleComputer? {
        val level = player.serverLevel()
        (ConsoleComputers.get(level.getBlockEntity(pos)))?.let { return it }
        val snapshot = ConsoleMultiblockManager.resolve(level, pos)
        if (snapshot.state != ConsoleNetworkState.ACTIVE) return null
        return snapshot.owner
    }

    private fun validSession(player: ServerPlayer): Session? {
        val level = player.serverLevel()
        val session = sessions.touch(player.uuid, level.gameTime) ?: return null
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

    internal fun clearPlayerSession(playerId: UUID) {
        sessions.remove(playerId)
    }

    internal fun expireSessions(tick: Long) {
        sessions.expire(tick)
    }

    internal fun clearAllSessions() {
        sessions.clear()
    }
}
