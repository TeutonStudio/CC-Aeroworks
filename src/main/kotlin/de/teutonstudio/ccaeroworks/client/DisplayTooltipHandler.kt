package de.teutonstudio.ccaeroworks.client

import de.teutonstudio.ccaeroworks.display.DeskDisplayType
import de.teutonstudio.ccaeroworks.registry.CCItems
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent

object DisplayTooltipHandler {
    @SubscribeEvent
    fun onItemTooltip(event: ItemTooltipEvent) {
        if (!event.flags.isAdvanced) return

        val type = when (event.itemStack.item) {
            CCItems.TWO_DIGIT_DISPLAY.get() -> DeskDisplayType.TWO_DIGIT
            CCItems.THREE_DIGIT_DISPLAY.get() -> DeskDisplayType.THREE_DIGIT
            else -> return
        }

        event.toolTip.add(
            Component.literal("Pixel: ${type.pixelWidth} × ${type.pixelHeight}")
                .withStyle(ChatFormatting.DARK_GRAY)
        )
    }
}
