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
            CCItems.SMALL_DISPLAY.get() -> DeskDisplayType.TWO_DIGIT
            CCItems.LARGE_DISPLAY.get() -> DeskDisplayType.THREE_DIGIT
            CCItems.ADVANCED_SMALL_DISPLAY.get() -> DeskDisplayType.TWO_DIGIT
            CCItems.ADVANCED_LARGE_DISPLAY.get() -> DeskDisplayType.THREE_DIGIT
            else -> return
        }

        val ppb = de.teutonstudio.ccaeroworks.display.DisplayResolutionSettings.effectivePpb(
            de.teutonstudio.ccaeroworks.config.CCServerConfig.displayUseGlobalResolutionValue(),
            type.partsPerBlock,
            de.teutonstudio.ccaeroworks.config.CCServerConfig.displayMaxIndividualPpbValue(), null
        )
        event.toolTip.add(
            Component.literal("Pixel: ${type.pixelWidthAt(ppb)} × ${type.pixelHeightAt(ppb)}")
                .withStyle(ChatFormatting.DARK_GRAY)
        )
    }
}
