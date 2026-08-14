package de.teutonstudio.ccaeroworks.computer

import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes
import de.teutonstudio.ccaeroworks.network.RequestDeskIoOverviewPayload
import net.minecraft.world.InteractionHand
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.network.PacketDistributor

/**
 * Routes desk configuration into the unified I/O overview whenever the desk network has an
 * embedded ComputerControlDesk. Networks without one keep Aeroworks' native configuration flow.
 *
 * Normal control operation, module mounting and wrench behaviour remain untouched.
 */
object ComputerConsoleInteractionHandler {
    @SubscribeEvent
    fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        if (!AeroworksTypes.isControlDesk(event.level.getBlockState(event.pos).block)) return
        if (event.hand != InteractionHand.MAIN_HAND) return
        if (!event.entity.isCrouching || !event.itemStack.isEmpty) return

        ControlDeskUiSwitchState.remember(event)
        if (!event.level.isClientSide) return
        if (!ControlDeskUiSwitchState.clientCanSwitchToComputer()) return

        // Own only the configuration click. The server performs the authoritative reach/network
        // validation again before returning the compact I/O snapshot.
        PacketDistributor.sendToServer(RequestDeskIoOverviewPayload(event.pos.immutable()))
        event.isCanceled = true
    }
}
