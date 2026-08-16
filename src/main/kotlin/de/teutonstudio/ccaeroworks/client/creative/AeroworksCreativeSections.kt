package de.teutonstudio.ccaeroworks.client.creative

import com.mred231.aeroworks.Aeroworks
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.createradar.CreateRadarCompat
import de.teutonstudio.ccaeroworks.mixin.client.CreativeModeInventoryScreenAccessor
import de.teutonstudio.ccaeroworks.mixin.client.CreativeModeTabAccessor
import de.teutonstudio.ccaeroworks.registry.CCItems
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModList
import net.neoforged.neoforge.client.event.ScreenEvent
import kotlin.math.roundToInt

object AeroworksCreativeSections {
    private const val COLUMNS = 9
    private const val VISIBLE_ROWS = 5
    private const val BANNER_WIDTH = 162
    private const val BANNER_HEIGHT = 18
    private val sectionRows = linkedMapOf<String, Int>()

    @JvmStatic
    fun arrange(tab: CreativeModeTab) {
        if (tab !== Aeroworks.MAIN_TAB.get()) return
        val createRadarLoaded = ModList.get().isLoaded(CreateRadarCompat.MOD_ID)
        val accessor = tab as CreativeModeTabAccessor
        accessor.ccaeroworks_getSearchTabDisplayItems().removeIf(::isGuideBook)
        if (!createRadarLoaded) {
            accessor.ccaeroworks_getSearchTabDisplayItems().removeIf(::isRadarDisplay)
        }

        val items = tab.displayItems
            .filterNot(ItemStack::isEmpty)
            .filterNot(::isGuideBook)
            .filterNot { !createRadarLoaded && isRadarDisplay(it) }
        val (registeredBridgeItems, registeredAeroworksItems) = items.partition {
            BuiltInRegistries.ITEM.getKey(it.item).namespace == CCAeroworks.MOD_ID && !isRadarDisplay(it)
        }
        val aeroworksItems = registeredAeroworksItems.toMutableList()
        val bridgeItems = registeredBridgeItems.toMutableList()
        appendMissing(bridgeItems, CCItems.COMPUTER_CONTROL_DESK.get().defaultInstance)
        appendMissing(bridgeItems, CCItems.ADVANCED_COMPUTER_CONTROL_DESK.get().defaultInstance)
        if (createRadarLoaded) {
            appendMissing(aeroworksItems, CCItems.SMALL_RADAR_DISPLAY.get().defaultInstance)
            appendMissing(aeroworksItems, CCItems.LARGE_RADAR_DISPLAY.get().defaultInstance)
        }

        val arranged = mutableListOf<ItemStack>()
        sectionRows.clear()
        appendSection(arranged, "aeroworks", aeroworksItems)
        appendSection(arranged, "cc_aeroworks", bridgeItems)
        accessor.ccaeroworks_setDisplayItems(arranged)
    }

    @SubscribeEvent
    fun renderSections(event: ScreenEvent.Render.Post) {
        val screen = event.screen as? CreativeModeInventoryScreen ?: return
        val tab = Aeroworks.MAIN_TAB.get()
        if (screen.menu.items != tab.displayItems.toList()) return
        val scroll = (screen as CreativeModeInventoryScreenAccessor).ccaeroworks_getScrollOffset()
        val scrollableRows = (Mth.positiveCeilDiv(screen.menu.items.size, COLUMNS) - VISIBLE_ROWS).coerceAtLeast(0)
        val currentRow = (scroll * scrollableRows).roundToInt().coerceAtLeast(0)
        val left = screen.guiLeft + 8
        val top = screen.guiTop + 17

        sectionRows.forEach { (id, absoluteRow) ->
            val visibleRow = absoluteRow - currentRow
            if (visibleRow !in 0 until VISIBLE_ROWS) return@forEach
            val y = top + visibleRow * BANNER_HEIGHT
            val background = if (id == "aeroworks") 0xFF5A3A20.toInt() else 0xFF1F4B59.toInt()
            val border = if (id == "aeroworks") 0xFFFFC66D.toInt() else 0xFF79D7EE.toInt()
            event.guiGraphics.fill(left, y, left + BANNER_WIDTH, y + BANNER_HEIGHT, background)
            event.guiGraphics.fill(left, y, left + 3, y + BANNER_HEIGHT, border)
            event.guiGraphics.drawString(
                screen.minecraft!!.font,
                Component.translatable("creative_section.cc_aeroworks.$id"),
                left + 7,
                y + 5,
                0xFFFFFFFF.toInt(),
                true
            )
        }
    }

    private fun isGuideBook(stack: ItemStack): Boolean = stack.item === CCItems.GUIDE_BOOK.get()

    private fun isRadarDisplay(stack: ItemStack): Boolean =
        stack.item === CCItems.SMALL_RADAR_DISPLAY.get() || stack.item === CCItems.LARGE_RADAR_DISPLAY.get()

    private fun appendMissing(target: MutableList<ItemStack>, stack: ItemStack) {
        if (target.none { ItemStack.isSameItemSameComponents(it, stack) }) target += stack
    }

    private fun appendSection(target: MutableList<ItemStack>, id: String, items: List<ItemStack>) {
        if (items.isEmpty()) return
        sectionRows[id] = target.size / COLUMNS
        repeat(COLUMNS) { target += ItemStack.EMPTY }
        target += items
        while (target.size % COLUMNS != 0) target += ItemStack.EMPTY
    }
}
