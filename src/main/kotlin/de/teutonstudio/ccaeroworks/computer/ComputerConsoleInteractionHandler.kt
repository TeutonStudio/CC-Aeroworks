package de.teutonstudio.ccaeroworks.computer

import com.simibubi.create.content.equipment.wrench.WrenchItem
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes
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

        // Sneak + right-click is Aeroworks' native configuration gesture. Do not reserve it for
        // the embedded computer: doing so prevents ModuleScreen from opening at all. The computer
        // remains reachable from the Computer button injected into Aeroworks' ModuleScreen.
        if (event.entity.isCrouching && event.itemStack.isEmpty) {
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

        // Display touches are deliberately not handled here anymore. Large displays and large
        // radar displays accept programmable input only through the held display-combined key.
        // All other right-clicks remain native Aeroworks behaviour.
    }

    private fun openControlDefinition(event: PlayerInteractEvent.RightClickBlock) {
        ControlDeskUiSwitchState.remember(event)

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
}
