package de.teutonstudio.ccaeroworks.computer

import com.simibubi.create.content.equipment.wrench.WrenchItem
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.util.TriState
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent

object ComputerConsoleInteractionHandler {
    @SubscribeEvent
    fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        if (!AeroworksTypes.isControlDesk(event.level.getBlockState(event.pos).block)) {
            return
        }

        if (event.hand == InteractionHand.MAIN_HAND &&
            event.entity.isCrouching &&
            event.itemStack.isEmpty &&
            openComputerTerminal(event)
        ) {
            return
        }

        if (event.itemStack.item is WrenchItem) {
            if (event.face?.axis?.isHorizontal == true) {
                openControlDefinition(event)
            } else {
                // Top and bottom retain the normal Create wrench rotation. Prevent the
                // Aeroworks block interaction from consuming the click before the wrench runs.
                event.setUseBlock(TriState.FALSE)
            }
            return
        }

        // All other right-clicks, including Data Link use, empty-hand control interaction and
        // module installation/removal, are native item or Aeroworks behaviour.
    }

    private fun openControlDefinition(event: PlayerInteractEvent.RightClickBlock) {
        // Aeroworks opens the console overview/configuration from its held-item block path
        // when the player is sneaking. Reserve that exact native path for a horizontal wrench
        // right-click, without changing the player's persistent crouch state.
        val player = event.entity
        val wasShiftDown = player.isShiftKeyDown
        try {
            player.setShiftKeyDown(true)
            event.level.getBlockState(event.pos).useItemOn(
                event.itemStack,
                event.level,
                player,
                event.hand,
                event.hitVec
            )
        } finally {
            player.setShiftKeyDown(wasShiftDown)
        }

        // BlockState.useItemOn returns ItemInteractionResult in Minecraft 1.21.1, while
        // RightClickBlock cancellation expects InteractionResult. The native call has already
        // performed the menu-opening side effect, so consume the original event explicitly.
        event.cancellationResult = InteractionResult.SUCCESS
        event.isCanceled = true
    }

    private fun openComputerTerminal(event: PlayerInteractEvent.RightClickBlock): Boolean {
        // The client consumes the deliberately reserved interaction. Computer creation and
        // menu opening are server-only operations and are handled by the matching server event.
        if (event.level.isClientSide) {
            consume(event)
            return true
        }

        val snapshot = ConsoleMultiblockManager.resolve(event.level, event.pos)
        val direct = event.level.getBlockEntity(event.pos) as? ComputerControlDeskBlockEntity
        val handled = when {
            direct != null -> direct.openTerminal(event.entity, direct = true)
            snapshot.state == ConsoleNetworkState.ACTIVE -> snapshot.owner?.openTerminal(event.entity) == true
            snapshot.state == ConsoleNetworkState.CONFLICT -> {
                event.entity.displayClientMessage(
                    Component.translatable("message.cc_aeroworks.console_conflict"),
                    true
                )
                true
            }
            snapshot.state == ConsoleNetworkState.TOO_LARGE -> {
                event.entity.displayClientMessage(
                    Component.translatable("message.cc_aeroworks.console_too_large"),
                    true
                )
                true
            }
            snapshot.state == ConsoleNetworkState.PARTIALLY_LOADED -> {
                event.entity.displayClientMessage(
                    Component.translatable("message.cc_aeroworks.console_partially_loaded"),
                    true
                )
                true
            }
            else -> false
        }

        if (handled) consume(event)
        return handled
    }

    private fun consume(event: PlayerInteractEvent.RightClickBlock) {
        event.cancellationResult = InteractionResult.SUCCESS
        event.isCanceled = true
    }
}
