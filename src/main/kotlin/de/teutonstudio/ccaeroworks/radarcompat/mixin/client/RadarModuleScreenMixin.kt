package de.teutonstudio.ccaeroworks.radarcompat.mixin.client

import com.mred231.aeroworks.content.controls.ConsoleSocket
import com.mred231.aeroworks.content.controls.ModuleMenu
import com.mred231.aeroworks.content.controls.ModuleScreen
import de.teutonstudio.ccaeroworks.client.ModuleScreenRowGeometry
import de.teutonstudio.ccaeroworks.client.SourceSelectorWidget
import de.teutonstudio.ccaeroworks.display.DisplayBindings
import de.teutonstudio.ccaeroworks.mixin.client.ModuleScreenAccessor
import de.teutonstudio.ccaeroworks.mixin.client.ModuleScreenInvoker
import de.teutonstudio.ccaeroworks.radarcompat.client.RadarSourceChoice
import de.teutonstudio.ccaeroworks.radarcompat.client.RadarSourceSelectorOverlayOwner
import de.teutonstudio.ccaeroworks.radarcompat.client.radarSourceKey
import de.teutonstudio.ccaeroworks.radarcompat.client.radarSourceOption
import de.teutonstudio.ccaeroworks.radarcompat.display.RadarDisplayBindings
import de.teutonstudio.ccaeroworks.radarcompat.display.RadarSourceRegistry
import de.teutonstudio.ccaeroworks.radarcompat.network.SetRadarDisplaySourcePayload
import de.teutonstudio.ccaeroworks.radarcompat.registry.RadarModuleTypes
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

private const val BINDING_ROW_WIDTH = 235

@Mixin(value = [ModuleScreen::class], remap = false)
abstract class RadarModuleScreenMixin(
    menu: ModuleMenu,
    inventory: Inventory,
    title: Component
) : AbstractContainerScreen<ModuleMenu>(menu, inventory, title), RadarSourceSelectorOverlayOwner {
    @Unique private var ccaeroworks_radarNativeContentHeight = -1
    @Unique private var ccaeroworks_radarDropdown: SourceSelectorWidget<RadarSourceChoice>? = null

    @Inject(method = ["init()V"], at = [At("HEAD")])
    private fun ccaeroworks_prepareRadarBindingRow(callback: CallbackInfo) {
        if (ccaeroworks_radarNativeContentHeight >= 0) {
  (this as ModuleScreenAccessor).ccaeroworks_setContentHeight(ccaeroworks_radarNativeContentHeight)
        }
        ccaeroworks_radarDropdown = null
    }

    @Inject(method = ["init()V"], at = [At("TAIL")])
    private fun ccaeroworks_addRadarBindingRow(callback: CallbackInfo) {
        val module = (this as ModuleScreenInvoker).ccaeroworks_module() ?: return
        if (RadarModuleTypes.radarDisplayType(module.type()) == null) return
        val holder = menu.contentHolder as? ConsoleSocket ?: return
        if (!holder.valid()) return
        val desk = holder.be()
        val socket = (0 until desk.socketCount()).firstOrNull { desk.module(it) === module } ?: return
        val screen = this as ModuleScreenAccessor
        ccaeroworks_radarNativeContentHeight = screen.ccaeroworks_getContentHeight()
        val selectedIngress = RadarDisplayBindings.source(DisplayBindings.get(desk, socket))?.ingressPos
        val descriptors = RadarSourceRegistry.sources(desk)
        val local = descriptors.firstOrNull { it.ingressPos == desk.blockPos }
        val choices = mutableListOf(RadarSourceChoice(null, local))
        descriptors.asSequence()
  .filter { it.ingressPos != desk.blockPos }
  .distinctBy { it.ingressPos }
  .forEach { choices += RadarSourceChoice(it.ingressPos, it) }
        if (selectedIngress != null && choices.none { it.ingressPos == selectedIngress }) {
  choices += RadarSourceChoice(selectedIngress, null)
        }
        val invoker = this as ModuleScreenInvoker
        ccaeroworks_radarDropdown = addRenderableWidget(
  SourceSelectorWidget(
      invoker.ccaeroworks_rowLeft(),
      ModuleScreenRowGeometry.extensionScreenTop(
          ccaeroworks_radarNativeContentHeight,
          0,
          invoker.ccaeroworks_listTop(),
          screen.ccaeroworks_getRenderedScroll()
      ),
      BINDING_ROW_WIDTH,
      ModuleScreenRowGeometry.EXTENSION_ROW_HEIGHT,
      font,
      radarSourceKey(selectedIngress),
      { _ -> choices.map(::radarSourceOption) },
      { choice ->
          PacketDistributor.sendToServer(
              SetRadarDisplaySourcePayload(desk.blockPos, socket, choice.ingressPos)
          )
      },
      Component.literal("Radarquelle")
  )
        )
        screen.ccaeroworks_setContentHeight(
  ModuleScreenRowGeometry.contentHeightWithExtensions(ccaeroworks_radarNativeContentHeight, 1)
        )
    }

    @Inject(method = ["renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V"], at = [At("TAIL")])
    private fun ccaeroworks_positionRadarBindingRow(
        graphics: GuiGraphics,
        partialTick: Float,
        mouseX: Int,
        mouseY: Int,
        callback: CallbackInfo
    ) {
        val dropdown = ccaeroworks_radarDropdown ?: return
        val invoker = this as ModuleScreenInvoker
        val listTop = invoker.ccaeroworks_listTop()
        val listBottom = listTop + ModuleScreenRowGeometry.LIST_HEIGHT
        val rowTop = ModuleScreenRowGeometry.extensionScreenTop(
  ccaeroworks_radarNativeContentHeight,
  0,
  listTop,
  (this as ModuleScreenAccessor).ccaeroworks_getRenderedScroll()
        )
        val visible = ModuleScreenRowGeometry.intersectsViewport(
  rowTop,
  ModuleScreenRowGeometry.EXTENSION_ROW_HEIGHT,
  listTop
        )
        dropdown.setRowPosition(invoker.ccaeroworks_rowLeft(), rowTop, visible, listTop, listBottom)
    }

    @Inject(method = ["render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"], at = [At("TAIL")])
    private fun ccaeroworks_renderRadarBindingPopup(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
        callback: CallbackInfo
    ) {
        ccaeroworks_radarDropdown?.renderOverlay(graphics, mouseX, mouseY)
    }

    override fun ccaeroworks_isRadarSourceSelectorPopupHovered(mouseX: Double, mouseY: Double): Boolean =
        ccaeroworks_radarDropdown?.isPopupMouseOver(mouseX, mouseY) == true
}
