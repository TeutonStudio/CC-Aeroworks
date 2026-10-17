package de.teutonstudio.ccaeroworks.client

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import com.mred231.aeroworks.content.controls.console.ConsoleScreen
import com.mred231.aeroworks.foundation.gui.AeroworksGuiTextures
import de.teutonstudio.ccaeroworks.computer.ConsoleInventoryMenu
import de.teutonstudio.ccaeroworks.computer.ControlDeskUiSwitchState
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

/** Native module overview plus a real, synchronized row slot and player inventory. */
class ConsoleInventoryScreen(menu: ConsoleInventoryMenu, inventory: Inventory, title: Component) :
    AbstractContainerScreen<ConsoleInventoryMenu>(menu, inventory, title) {
    private var overview: ConsoleScreen? = null
    private var computerAvailable = false

    init { imageWidth = 236; imageHeight = 249 }

    override fun init() {
        super.init()
        val host = minecraft?.level?.getBlockEntity(menu.anchor) as? ConsoleBlockEntity ?: return
        overview = object : ConsoleScreen(host) {
            override fun onClose() { this@ConsoleInventoryScreen.onClose() }
        }
        rebuildOverview()
    }

    private fun rebuildOverview() {
        // Preserve the native 198x153 layout. Its left/top match this container's left/top.
        overview?.init(minecraft!!, width - 38, height - 96)
        computerAvailable = minecraft?.level?.let { ConsoleMultiblockManager.resolve(it, menu.anchor).owner != null } == true
    }

    override fun containerTick() {
        super.containerTick()
        val level = minecraft?.level ?: return
        val host = level.getBlockEntity(menu.anchor) as? ConsoleBlockEntity
        if (host == null || host.isRemoved) { onClose(); return }
        ControlDeskUiSwitchState.rememberClientOverview(host)
        val available = ConsoleMultiblockManager.resolve(level, menu.anchor).owner != null
        if (available != computerAvailable) rebuildOverview()
    }

    override fun renderBg(graphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        AeroworksGuiTextures.CONSOLE_BACKGROUND.render(graphics, leftPos, topPos)
        panel(graphics, leftPos + 198, topPos, 38, 56)
        panel(graphics, leftPos, topPos + 153, 198, 96)
        menu.slots.forEach { slot ->
            val x = leftPos + slot.x; val y = topPos + slot.y
            graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF373737.toInt())
            graphics.fill(x, y, x + 17, y + 17, 0xFFFFFFFF.toInt())
            graphics.fill(x, y, x + 16, y + 16, 0xFF8B8B8B.toInt())
        }
    }

    override fun renderLabels(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        graphics.drawString(font, "CC", 206, 6, 0x404040, false)
        graphics.drawString(font, playerInventoryTitle, 17, 155, 0x404040, false)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        overview?.render(graphics, mouseX, mouseY, partialTick)
        renderTooltip(graphics, mouseX, mouseY)
        if (hoveredSlot === menu.slots[0] && !menu.slots[0].hasItem())
            graphics.renderTooltip(font, Component.translatable("gui.cc_aeroworks.computer_slot"), mouseX, mouseY)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (overview?.mouseClicked(mouseX, mouseY, button) == true) return true
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean =
        overview?.mouseScrolled(mouseX, mouseY, horizontal, vertical) == true || super.mouseScrolled(mouseX, mouseY, horizontal, vertical)

    private fun panel(graphics: GuiGraphics, x: Int, y: Int, w: Int, h: Int) {
        graphics.fill(x, y, x + w, y + h, 0xFF555555.toInt())
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFFFFFFF.toInt())
        graphics.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xFFC6C6C6.toInt())
    }
}
