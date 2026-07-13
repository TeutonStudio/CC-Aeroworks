package de.teutonstudio.ccaeroworks.client

import de.teutonstudio.ccaeroworks.registry.CCItems
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionResult
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent

object GuideBookClientHandler {
    @SubscribeEvent
    fun openGuide(event: PlayerInteractEvent.RightClickItem) {
        if (!event.level.isClientSide || !event.itemStack.`is`(CCItems.GUIDE_BOOK.get())) return
        Minecraft.getInstance().setScreen(GuideBookScreen())
        event.cancellationResult = InteractionResult.SUCCESS
        event.isCanceled = true
    }
}
