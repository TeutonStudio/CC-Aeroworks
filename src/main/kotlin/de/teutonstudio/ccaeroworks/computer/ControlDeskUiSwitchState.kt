package de.teutonstudio.ccaeroworks.computer

import dan200.computercraft.shared.computer.menu.ComputerMenu
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
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
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
        // The terminal may have been opened by clicking any face of the desk. Aeroworks'
        // definition interaction, however, is deliberately reserved for horizontal desk
        // faces. Replaying the terminal's original UP/DOWN hit therefore cannot open the
        // definition screen. Always synthesize the same horizontal interaction used by the
        // real wrench handler, while keeping the original physical desk as the target.
        val session = validSession(player) ?: sessionFromOpenComputer(player) ?: return false
        val level = player.serverLevel()
        val state = level.getBlockState(session.pos)
        if (!AeroworksTypes.isControlDesk(state.block)) return false

        val wrench = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "wrench"))
        if (wrench === Items.AIR) return false

        val hit = definitionHit(state, session.pos)
        val previousMenu = player.containerMenu
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

        // Menu opening is synchronous on the server. Returning whether Aeroworks actually
        // replaced the CC menu keeps failures observable instead of reporting a decorative
        // button press as success.
        return player.containerMenu !== previousMenu
    }

    private fun sessionFromOpenComputer(player: ServerPlayer): Session? {
        val computerMenu = player.containerMenu as? ComputerMenu ?: return null
        val computer = runCatching { computerMenu.computer }.getOrNull() ?: return null
        val level = player.serverLevel()
        if (computer.level !== level) return null

        val pos = computer.position
        if (!level.hasChunkAt(pos) || !level.mayInteract(player, pos)) return null
        val state = level.getBlockState(pos)
        if (!AeroworksTypes.isControlDesk(state.block)) return null

        val hit = definitionHit(state, pos)
        val session = Session(
            level.dimension(),
            pos.immutable(),
            hit.location,
            hit.direction,
            hit.isInside
        )
        sessions[player.uuid] = session
        return validSession(player)
    }

    private fun definitionHit(state: BlockState, pos: BlockPos): BlockHitResult {
        val face = if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            state.getValue(BlockStateProperties.HORIZONTAL_FACING)
        } else {
            Direction.NORTH
        }
        val center = pos.center
        return BlockHitResult(
            center.add(face.stepX * 0.5, 0.0, face.stepZ * 0.5),
            face,
            pos,
            false
        )
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
