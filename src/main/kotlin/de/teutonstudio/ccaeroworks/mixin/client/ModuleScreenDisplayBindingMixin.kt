package de.teutonstudio.ccaeroworks.mixin.client

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import com.mred231.aeroworks.content.controls.ConsoleSocket
import com.mred231.aeroworks.content.controls.ModuleMenu
import com.mred231.aeroworks.content.controls.ModuleScreen
import com.mred231.aeroworks.foundation.gui.AeroworksGuiTextures
import de.teutonstudio.ccaeroworks.client.ModuleScreenRowGeometry
import de.teutonstudio.ccaeroworks.client.RadarSourceChoice
import de.teutonstudio.ccaeroworks.client.RadarSourceRowButton
import de.teutonstudio.ccaeroworks.client.ScriptSourceDropdownWidget
import de.teutonstudio.ccaeroworks.display.DeskDisplayType
import de.teutonstudio.ccaeroworks.display.DisplayBinding
import de.teutonstudio.ccaeroworks.display.DisplayBindings
import de.teutonstudio.ccaeroworks.display.DisplayScriptCatalogState
import de.teutonstudio.ccaeroworks.display.RadarSourceRegistry
import de.teutonstudio.ccaeroworks.network.RequestDisplayScriptCatalogPayload
import de.teutonstudio.ccaeroworks.network.SetDisplayTouchScriptPayload
import de.teutonstudio.ccaeroworks.network.SetRadarDisplaySourcePayload
import de.teutonstudio.ccaeroworks.registry.CCModuleTypes
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.neoforged.neoforge.network.PacketDistributor
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

private const val BINDING_ROW_WIDTH: Int = 235

/**
 * Owns every CC-Aeroworks extension row appended to Aeroworks' ModuleScreen.
 *
 * Native contentHeight is captured once and extended exactly once, avoiding competing mixins which
 * independently believe they own the same scrollbar. Radar choices and script selection are normal
 * rows in the native 251x108 viewport and derive Y from renderedScroll on every frame.
 */
@Mixin(value = [ModuleScreen::class], remap = false)
abstract class ModuleScreenDisplayBindingMixin(
    menu: ModuleMenu,
    inventory: Inventory,
    title: Component
) : AbstractContainerScreen<ModuleMenu>(menu, inventory, title) {
    @Unique
    private var ccaeroworks_bindingDesk: ConsoleBlockEntity? = null

    @Unique
    private var ccaeroworks_bindingSocket: Int = -1

    @Unique
    private var ccaeroworks_nativeContentHeight: Int = 0

    @Unique
    private var ccaeroworks_extensionRows: Int = 0

    @Unique
    private var ccaeroworks_selectedRadarIngress: BlockPos? = null

    @Unique
    private val ccaeroworks_radarRows = mutableListOf<RadarSourceRowButton>()

    @Unique
    private var ccaeroworks_scriptDropdown: ScriptSourceDropdownWidget? = null

    @Inject(method = ["init()V"], at = [At("TAIL")])
    private fun ccaeroworks_addDisplayBindingRows(callback: CallbackInfo) {
        val module = (this as ModuleScreenInvoker).ccaeroworks_module() ?: return
        val radarDisplay = CCModuleTypes.radarDisplayType(module.type()) != null
        val displayType = CCModuleTypes.displayType(module.type())
        val scriptDisplay = displayType == DeskDisplayType.THREE_DIGIT
        if (!radarDisplay && !scriptDisplay) return

        val holder = menu.contentHolder as? ConsoleSocket ?: return
        if (!holder.valid()) return
        val desk = holder.be()
        val socket = (0 until desk.socketCount()).firstOrNull { desk.module(it) === module } ?: return
        ccaeroworks_bindingDesk = desk
        ccaeroworks_bindingSocket = socket

        val screen = this as ModuleScreenAccessor
        ccaeroworks_nativeContentHeight = screen.ccaeroworks_getContentHeight()

        if (radarDisplay) {
            ccaeroworks_addRadarRows(desk, socket)
        } else if (scriptDisplay) {
            ccaeroworks_addScriptRow(desk, socket)
        }

        if (ccaeroworks_extensionRows > 0) {
            screen.ccaeroworks_setContentHeight(
                ModuleScreenRowGeometry.contentHeightWithExtensions(
                    ccaeroworks_nativeContentHeight,
                    ccaeroworks_extensionRows
                )
            )
        }
    }

    @Inject(
        method = ["renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V"],
        at = [At("TAIL")]
    )
    private fun ccaeroworks_renderBindingRows(
        graphics: GuiGraphics,
        partialTick: Float,
        mouseX: Int,
        mouseY: Int,
        callback: CallbackInfo
    ) {
        if (ccaeroworks_extensionRows <= 0) return
        val invoker = this as ModuleScreenInvoker
        val listLeft = invoker.ccaeroworks_listLeft()
        val listTop = invoker.ccaeroworks_listTop()
        val rowLeft = invoker.ccaeroworks_rowLeft()
        val renderedScroll = (this as ModuleScreenAccessor).ccaeroworks_getRenderedScroll()

        graphics.enableScissor(
            listLeft,
            listTop,
            listLeft + ModuleScreenRowGeometry.LIST_WIDTH,
            listTop + ModuleScreenRowGeometry.LIST_HEIGHT
        )
        try {
            repeat(ccaeroworks_extensionRows) { index ->
                val rowTop = ModuleScreenRowGeometry.extensionScreenTop(
                    ccaeroworks_nativeContentHeight,
                    index,
                    listTop,
                    renderedScroll
                )
                AeroworksGuiTextures.MODULE_ROW.render(graphics, rowLeft, rowTop)
            }
        } finally {
            graphics.disableScissor()
        }

        ccaeroworks_radarRows.forEachIndexed { index, row ->
            val rowTop = ModuleScreenRowGeometry.extensionScreenTop(
                ccaeroworks_nativeContentHeight,
                index,
                listTop,
                renderedScroll
            )
            val visible = ModuleScreenRowGeometry.fullyVisible(
                rowTop,
                ModuleScreenRowGeometry.EXTENSION_ROW_HEIGHT,
                listTop
            )
            row.setX(rowLeft)
            row.setY(rowTop)
            row.visible = visible
            row.active = visible
            row.selected = row.choice.ingressPos == ccaeroworks_selectedRadarIngress
        }

        ccaeroworks_scriptDropdown?.let { dropdown ->
            val rowTop = ModuleScreenRowGeometry.extensionScreenTop(
                ccaeroworks_nativeContentHeight,
                0,
                listTop,
                renderedScroll
            )
            val visible = ModuleScreenRowGeometry.fullyVisible(
                rowTop,
                ModuleScreenRowGeometry.EXTENSION_ROW_HEIGHT,
                listTop
            )
            dropdown.setRowPosition(rowLeft, rowTop, visible)
        }
    }

    @Unique
    private fun ccaeroworks_addRadarRows(desk: ConsoleBlockEntity, socket: Int) {
        ccaeroworks_selectedRadarIngress =
            (DisplayBindings.get(desk, socket) as? DisplayBinding.RadarSource)?.source?.ingressPos

        val descriptors = RadarSourceRegistry.sources(desk)
        val local = descriptors.firstOrNull { it.ingressPos == desk.blockPos }
        val choices = mutableListOf(RadarSourceChoice(null, local))
        descriptors
            .asSequence()
            .filter { it.ingressPos != desk.blockPos }
            .distinctBy { it.ingressPos }
            .forEach { choices += RadarSourceChoice(it.ingressPos, it) }

        val selected = ccaeroworks_selectedRadarIngress
        if (selected != null && choices.none { it.ingressPos == selected }) {
            choices += RadarSourceChoice(selected, null)
        }

        val invoker = this as ModuleScreenInvoker
        val rowLeft = invoker.ccaeroworks_rowLeft()
        val listTop = invoker.ccaeroworks_listTop()
        choices.forEachIndexed { index, choice ->
            val rowTop = ModuleScreenRowGeometry.extensionScreenTop(
                ccaeroworks_nativeContentHeight,
                index,
                listTop,
                (this as ModuleScreenAccessor).ccaeroworks_getRenderedScroll()
            )
            val row = RadarSourceRowButton(
                rowLeft,
                rowTop,
                BINDING_ROW_WIDTH,
                ModuleScreenRowGeometry.EXTENSION_ROW_HEIGHT,
                font,
                choice
            ) { selectedChoice ->
                ccaeroworks_selectedRadarIngress = selectedChoice.ingressPos
                PacketDistributor.sendToServer(
                    SetRadarDisplaySourcePayload(desk.blockPos, socket, selectedChoice.ingressPos)
                )
            }
            ccaeroworks_radarRows += addRenderableWidget(row)
        }
        ccaeroworks_extensionRows = choices.size
    }

    @Unique
    private fun ccaeroworks_addScriptRow(desk: ConsoleBlockEntity, socket: Int) {
        DisplayScriptCatalogState.clear(desk.blockPos, socket)
        PacketDistributor.sendToServer(RequestDisplayScriptCatalogPayload(desk.blockPos, socket))
        val currentPath = (DisplayBindings.get(desk, socket) as? DisplayBinding.LuaHandler)?.path.orEmpty()
        val invoker = this as ModuleScreenInvoker
        val dropdown = ScriptSourceDropdownWidget(
            invoker.ccaeroworks_rowLeft(),
            ModuleScreenRowGeometry.extensionScreenTop(
                ccaeroworks_nativeContentHeight,
                0,
                invoker.ccaeroworks_listTop(),
                (this as ModuleScreenAccessor).ccaeroworks_getRenderedScroll()
            ),
            BINDING_ROW_WIDTH,
            ModuleScreenRowGeometry.EXTENSION_ROW_HEIGHT,
            font,
            desk.blockPos,
            socket,
            currentPath
        ) { path ->
            PacketDistributor.sendToServer(SetDisplayTouchScriptPayload(desk.blockPos, socket, path))
        }
        ccaeroworks_scriptDropdown = addRenderableWidget(dropdown)
        ccaeroworks_extensionRows = 1
    }
}
