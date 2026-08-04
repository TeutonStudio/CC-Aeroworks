package de.teutonstudio.ccaeroworks.computer

import com.mred231.aeroworks.content.controls.ModuleItem
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

        val heldItem = event.itemStack.item
        if (heldItem is WrenchItem) {
            if (event.face?.axis?.isHorizontal == true) {
                openControlConfiguration(event)
            } else {
                // Top and bottom retain normal Create wrench behaviour. Prevent a failed
                // wrench action from falling through into the control configuration UI.
                event.setUseBlock(TriState.FALSE)
            }
            return
        }

        if (event.itemStack.isEmpty) {
            // Bare interaction no longer opens the control configuration UI. Sneak + bare
            // main hand was handled above and remains reserved for the computer terminal.
            consume(event)
            return
        }

        if (heldItem !is ModuleItem) {
            // Preserve item-specific use while preventing unrelated held items from opening
            // the control configuration UI after their own interaction passes.
            event.setUseBlock(TriState.FALSE)
        }
    }

    private fun openControlConfiguration(event: PlayerInteractEvent.RightClickBlock) {
        // Aeroworks opens the configuration UI through the empty-hand block path. A held
        // wrench prevents vanilla from reaching that path, so invoke it explicitly and
        // consume the event before Create can rotate the desk.
        val result = event.level.getBlockState(event.pos).useWithoutItem(
            event.level,
            event.entity,
            event.hitVec
        )
        event.cancellationResult = if (result.consumesAction()) {
            result
        } else {
            InteractionResult.SUCCESS
        }
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
