package de.teutonstudio.ccaeroworks.computer

import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent

object ComputerConsoleInteractionHandler {
    @SubscribeEvent
    fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        if (event.hand != InteractionHand.MAIN_HAND ||
            !event.entity.isCrouching ||
            !event.itemStack.isEmpty ||
            !AeroworksTypes.isControlDesk(event.level.getBlockState(event.pos).block)
        ) {
            return
        }

        // The client only consumes the deliberately reserved interaction. Computer creation and
        // menu opening are server-only operations and are handled by the matching server event.
        if (event.level.isClientSide) {
            event.cancellationResult = InteractionResult.SUCCESS
            event.isCanceled = true
            return
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

        if (handled) {
            event.cancellationResult = InteractionResult.SUCCESS
            event.isCanceled = true
        }
    }
}
