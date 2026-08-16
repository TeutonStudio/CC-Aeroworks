package de.teutonstudio.ccaeroworks.mixin.client

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import com.mred231.aeroworks.content.controls.ConsoleSocket
import com.mred231.aeroworks.content.controls.ModuleMenu
import com.mred231.aeroworks.content.controls.ModuleScreen
import de.teutonstudio.ccaeroworks.client.ModuleScreenRowGeometry
import de.teutonstudio.ccaeroworks.client.RadarSourceChoice
import de.teutonstudio.ccaeroworks.client.SourceSelectorWidget
import de.teutonstudio.ccaeroworks.client.radarSourceKey
import de.teutonstudio.ccaeroworks.client.radarSourceOption
import de.teutonstudio.ccaeroworks.client.scriptSourceOptions
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
 * Owns the CC-Aeroworks source-selector row appended to Aeroworks' ModuleScreen.
 *
 * Radar and script bindings both occupy exactly one extension row. The selector owns its visual
 * background instead of reusing Aeroworks' native control row, so source bindings do not inherit
 * unrelated Redstone/radio slot decoration.
 */
@Mixin(value = [ModuleScreen::class], remap = false)
abstract class ModuleScreenDisplayBindingMixin(
    menu: ModuleMenu,
    inventory: Inventory,
    title: Component
) : AbstractContainerScreen<ModuleMenu>(menu, inventory, title) {
    @Unique
    private var ccaeroworks_nativeContentHeight: Int = 0

    @Unique
    private var ccaeroworks_extensionRows: Int = 0

    @Unique
    private var ccaeroworks_radarDropdown: SourceSelectorWidget<RadarSourceChoice>? = null

    @Unique
    private var ccaeroworks_scriptDropdown: SourceSelectorWidget<String>? = null

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

        val screen = this as ModuleScreenAccessor
        ccaeroworks_nativeContentHeight = screen.ccaeroworks_getContentHeight()

        if (radarDisplay) {
            ccaeroworks_addRadarRow(desk, socket)
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
        val rowLeft = invoker.ccaeroworks_rowLeft()
        val renderedScroll = (this as ModuleScreenAccessor).ccaeroworks_getRenderedScroll()
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
        ccaeroworks_radarDropdown?.setRowPosition(rowLeft, rowTop, visible)
        ccaeroworks_scriptDropdown?.setRowPosition(rowLeft, rowTop, visible)
    }

    @Unique
    private fun ccaeroworks_addRadarRow(desk: ConsoleBlockEntity, socket: Int) {
        val selectedIngress =
            (DisplayBindings.get(desk, socket) as? DisplayBinding.RadarSource)?.source?.ingressPos

        val descriptors = RadarSourceRegistry.sources(desk)
        val local = descriptors.firstOrNull { it.ingressPos == desk.blockPos }
        val choices = mutableListOf(RadarSourceChoice(null, local))
        descriptors
            .asSequence()
            .filter { it.ingressPos != desk.blockPos }
            .distinctBy { it.ingressPos }
            .forEach { choices += RadarSourceChoice(it.ingressPos, it) }
        if (selectedIngress != null && choices.none { it.ingressPos == selectedIngress }) {
            choices += RadarSourceChoice(selectedIngress, null)
        }

        val invoker = this as ModuleScreenInvoker
        val dropdown = SourceSelectorWidget(
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
            radarSourceKey(selectedIngress),
            { choices.map(::radarSourceOption) },
            { selectedChoice ->
                PacketDistributor.sendToServer(
                    SetRadarDisplaySourcePayload(desk.blockPos, socket, selectedChoice.ingressPos)
                )
            },
            Component.literal("Radar source")
        )
        ccaeroworks_radarDropdown = addRenderableWidget(dropdown)
        ccaeroworks_extensionRows = 1
    }

    @Unique
    private fun ccaeroworks_addScriptRow(desk: ConsoleBlockEntity, socket: Int) {
        DisplayScriptCatalogState.clear(desk.blockPos, socket)
        PacketDistributor.sendToServer(RequestDisplayScriptCatalogPayload(desk.blockPos, socket))
        val currentPath = (DisplayBindings.get(desk, socket) as? DisplayBinding.LuaHandler)?.path.orEmpty()
        val invoker = this as ModuleScreenInvoker
        val dropdown = SourceSelectorWidget(
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
            { selectedPath -> scriptSourceOptions(desk.blockPos, socket, selectedPath) },
            { path ->
                PacketDistributor.sendToServer(SetDisplayTouchScriptPayload(desk.blockPos, socket, path))
            },
            Component.literal("Script source")
        )
        ccaeroworks_scriptDropdown = addRenderableWidget(dropdown)
        ccaeroworks_extensionRows = 1
    }
}
