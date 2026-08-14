package de.teutonstudio.ccaeroworks.computer

import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes
import net.minecraft.world.InteractionHand
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent

/**
 * Keeps enough desk context for the Aeroworks -> embedded-computer UI switch without taking
 * ownership of Aeroworks' world interaction.
 *
 * Aeroworks itself owns the ControlDesk controls:
 * - empty-hand right-click enters control mode,
 * - sneak + empty-hand right-click opens the native module/configuration UI,
 * - wrench interactions remain native Aeroworks/Create behaviour.
 *
 * The configuration screen's Computer button uses [ControlDeskUiSwitchState] to find the
 * ComputerControlDesk which owns the row, so remember the native configuration click on both
 * logical sides and otherwise leave the event completely untouched.
 */
object ComputerConsoleInteractionHandler {
    @SubscribeEvent
    fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        if (!AeroworksTypes.isControlDesk(event.level.getBlockState(event.pos).block)) return
        if (event.hand != InteractionHand.MAIN_HAND) return
        if (!event.entity.isCrouching || !event.itemStack.isEmpty) return

        ControlDeskUiSwitchState.remember(event)
        // Deliberately do not cancel the event or alter useBlock/useItem. Aeroworks must receive
        // the original sneak + right-click so its native configuration menu can open.
    }
}
