package de.teutonstudio.ccaeroworks.computer

import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers the physical control desk interaction which opened one of the two
 * native configuration screens. The screens themselves stay owned by
 * CC:Tweaked and Aeroworks; this object only supplies the server-authoritative
 * jump between them.
 */
object ControlDeskUiSwitchState {
    private data class Session(
        val dimension: ResourceKey<Level>,
        val pos: BlockPos,
        val location: Vec3,
        val face: Direction,
        val inside: Boolean
    )

    private val sessions = ConcurrentHashMap<UUID, Session>()

    @Volatile
    private var clientComputerAvailable: Boolean = false

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
            event.pos.immutable(),
            event.hitVec.location,
            event.hitVec.direction,
            event.hitVec.isInside
        )
    }

    @JvmStatic
    fun clientCanSwitchToComputer(): Boolean = clientComputerAvailable

    @JvmStatic
    fun prepareClientControlsScreen() {
        // A controls screen opened from our computer button necessarily belongs
        // to the same computer-capable desk session.
        clientComputerAvailable = true
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

    @JvmStatic
    fun switchToControls(player: ServerPlayer): Boolean {
        val session = validSession(player) ?: return false
        val level = player.serverLevel()
        val state = level.getBlockState(session.pos)
        if (!AeroworksTypes.isControlDesk(state.block)) return false

        val wrench = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "wrench"))
        if (wrench === Items.AIR) return false

        val hit = BlockHitResult(
            session.location,
            session.face,
            session.pos,
            session.inside
        )
        val wasShiftDown = player.isShiftKeyDown
        try {
            player.setShiftKeyDown(true)
            state.useItemOn(
                ItemStack(wrench),
                level,
                player,
                InteractionHand.MAIN_HAND,
                hit
            )
        } finally {
            player.setShiftKeyDown(wasShiftDown)
        }
        return true
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

        val maximumDistance = player.blockInteractionRange() + 1.0
        if (player.distanceToSqr(session.pos.center) > maximumDistance * maximumDistance) return null
        return session
    }
}
