package de.teutonstudio.ccaeroworks.mixin.client

import com.mred231.aeroworks.content.controls.ConsoleSocket
import com.mred231.aeroworks.content.controls.ModuleMenu
import com.mred231.aeroworks.content.controls.ModuleScreen
import de.teutonstudio.ccaeroworks.client.ModuleScreenRowGeometry
import de.teutonstudio.ccaeroworks.client.SourceSelectorOverlayOwner
import de.teutonstudio.ccaeroworks.client.SourceSelectorWidget
import de.teutonstudio.ccaeroworks.client.scriptSourceOptions
import de.teutonstudio.ccaeroworks.display.DeskDisplayType
import de.teutonstudio.ccaeroworks.display.DisplayBinding
import de.teutonstudio.ccaeroworks.display.DisplayBindings
import de.teutonstudio.ccaeroworks.display.DisplayScriptCatalogState
import de.teutonstudio.ccaeroworks.network.RequestDisplayScriptCatalogPayload
import de.teutonstudio.ccaeroworks.network.SetDisplayTouchScriptPayload
import de.teutonstudio.ccaeroworks.registry.CCModuleTypes
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.neoforged.neoforge.network.PacketDistributor
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

private const val BINDING_ROW_WIDTH: Int = 235

@Mixin(value = [ModuleScreen::class], remap = false)
abstract class ModuleScreenDisplayBindingMixin(
    menu: ModuleMenu,
    inventory: Inventory,
    title: Component
) : AbstractContainerScreen<ModuleMenu>(menu, inventory, title), SourceSelectorOverlayOwner {
    @Unique private var ccaeroworks_nativeContentHeight: Int = -1
    @Unique private var ccaeroworks_extensionRows: Int = 0
    @Unique private var ccaeroworks_scriptDropdown: SourceSelectorWidget<String>? = null
    @Unique private var ccaeroworks_scriptCatalogRequested: Boolean = false

    @Inject(method = ["init()V"], at = [At("HEAD")])
    private fun ccaeroworks_prepareDisplayBindingRows(callback: CallbackInfo) {
        if (ccaeroworks_nativeContentHeight >= 0) {
  (this as ModuleScreenAccessor).ccaeroworks_setContentHeight(ccaeroworks_nativeContentHeight)
        }
        ccaeroworks_extensionRows = 0
        ccaeroworks_scriptDropdown = null
    }

    @Inject(method = ["init()V"], at = [At("TAIL")])
    private fun ccaeroworks_addDisplayBindingRows(callback: CallbackInfo) {
        val module = (this as ModuleScreenInvoker).ccaeroworks_module() ?: return
        if (CCModuleTypes.displayType(module.type()) != DeskDisplayType.THREE_DIGIT) return
        val holder = menu.contentHolder as? ConsoleSocket ?: return
        if (!holder.valid()) return
        val desk = holder.be()
        val socket = (0 until desk.socketCount()).firstOrNull { desk.module(it) === module } ?: return
        val screen = this as ModuleScreenAccessor
        ccaeroworks_nativeContentHeight = screen.ccaeroworks_getContentHeight()
        ccaeroworks_addScriptRow(desk.blockPos, socket)
        ccaeroworks_extensionRows = 1
        screen.ccaeroworks_setContentHeight(
  ModuleScreenRowGeometry.contentHeightWithExtensions(ccaeroworks_nativeContentHeight, ccaeroworks_extensionRows)
        )
    }

    @Inject(method = ["renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V"], at = [At("TAIL")])
    private fun ccaeroworks_positionBindingRows(
        graphics: GuiGraphics,
        partialTick: Float,
        mouseX: Int,
        mouseY: Int,
        callback: CallbackInfo
    ) {
        if (ccaeroworks_extensionRows <= 0) return
        val invoker = this as ModuleScreenInvoker
        val listTop = invoker.ccaeroworks_listTop()
        val listBottom = listTop + ModuleScreenRowGeometry.LIST_HEIGHT
        val rowLeft = invoker.ccaeroworks_rowLeft()
        val rowTop = ModuleScreenRowGeometry.extensionScreenTop(
  ccaeroworks_nativeContentHeight,
  0,
  listTop,
  (this as ModuleScreenAccessor).ccaeroworks_getRenderedScroll()
        )
        val visible = ModuleScreenRowGeometry.intersectsViewport(
  rowTop,
  ModuleScreenRowGeometry.EXTENSION_ROW_HEIGHT,
  listTop
        )
        ccaeroworks_scriptDropdown?.setRowPosition(rowLeft, rowTop, visible, listTop, listBottom)
    }

    @Inject(method = ["render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"], at = [At("TAIL")])
    private fun ccaeroworks_renderBindingPopup(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
        callback: CallbackInfo
    ) {
        ccaeroworks_scriptDropdown?.renderOverlay(graphics, mouseX, mouseY)
    }

    override fun ccaeroworks_isSourceSelectorPopupHovered(mouseX: Double, mouseY: Double): Boolean =
        ccaeroworks_scriptDropdown?.isPopupMouseOver(mouseX, mouseY) == true

    @Unique
    private fun ccaeroworks_addScriptRow(deskPos: net.minecraft.core.BlockPos, socket: Int) {
        if (!ccaeroworks_scriptCatalogRequested) {
  DisplayScriptCatalogState.clear(deskPos, socket)
  PacketDistributor.sendToServer(RequestDisplayScriptCatalogPayload(deskPos, socket))
  ccaeroworks_scriptCatalogRequested = true
        }
        val holder = menu.contentHolder as? ConsoleSocket ?: return
        val desk = holder.be()
        val currentPath = (DisplayBindings.get(desk, socket) as? DisplayBinding.LuaHandler)?.path.orEmpty()
        val invoker = this as ModuleScreenInvoker
        ccaeroworks_scriptDropdown = addRenderableWidget(
  SourceSelectorWidget(
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
      currentPath,
      { selectedPath -> scriptSourceOptions(deskPos, socket, selectedPath) },
      { path -> PacketDistributor.sendToServer(SetDisplayTouchScriptPayload(deskPos, socket, path)) },
      Component.literal("Skriptquelle")
  )
        )
    }
}
