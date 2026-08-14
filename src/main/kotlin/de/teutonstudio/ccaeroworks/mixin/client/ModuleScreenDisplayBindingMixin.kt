package de.teutonstudio.ccaeroworks.mixin.client

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import com.mred231.aeroworks.content.controls.ConsoleSocket
import com.mred231.aeroworks.content.controls.ModuleMenu
import com.mred231.aeroworks.content.controls.ModuleScreen
import de.teutonstudio.ccaeroworks.display.DeskDisplayType
import de.teutonstudio.ccaeroworks.display.DisplayBinding
import de.teutonstudio.ccaeroworks.display.DisplayBindings
import de.teutonstudio.ccaeroworks.display.RadarSourceDescriptor
import de.teutonstudio.ccaeroworks.display.RadarSourceRegistry
import de.teutonstudio.ccaeroworks.network.SetDisplayTouchScriptPayload
import de.teutonstudio.ccaeroworks.network.SetRadarDisplaySourcePayload
import de.teutonstudio.ccaeroworks.registry.CCModuleTypes
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
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
    private var ccaeroworks_selectedRadarIngress: BlockPos? = null

    @Unique
    private var ccaeroworks_radarSourceButton: Button? = null

    @Unique
    private var ccaeroworks_touchScriptField: EditBox? = null

    @Unique
    private var ccaeroworks_touchScriptSetButton: Button? = null

    @Unique
    private var ccaeroworks_nativeContentHeight: Int = 0

    @Inject(method = ["init()V"], at = [At("TAIL")])
    private fun ccaeroworks_addDisplayBinding(callback: CallbackInfo) {
        val module = (this as ModuleScreenInvoker).ccaeroworks_module() ?: return
        val radarDisplay = CCModuleTypes.radarDisplayType(module.type()) != null
        val touchDisplay = CCModuleTypes.displayType(module.type()) == DeskDisplayType.THREE_DIGIT
        if (!radarDisplay && !touchDisplay) return

        val holder = menu.contentHolder as? ConsoleSocket ?: return
        if (!holder.valid()) return
        val desk = holder.be()
        val socket = (0 until desk.socketCount()).firstOrNull { desk.module(it) === module } ?: return

        ccaeroworks_bindingDesk = desk
        ccaeroworks_bindingSocket = socket

        val screen = this as ModuleScreenAccessor
        ccaeroworks_nativeContentHeight = screen.ccaeroworks_getContentHeight()
        screen.ccaeroworks_setContentHeight(
            ModuleScreenRowGeometry.contentHeightWithExtensions(ccaeroworks_nativeContentHeight, 1)
        )

        if (radarDisplay) {
            ccaeroworks_addRadarSourceButton(desk, socket)
        } else {
            ccaeroworks_addTouchScriptField(desk, socket)
        }
    }

    @Inject(
        method = ["renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V"],
        at = [At("TAIL")]
    )
    private fun ccaeroworks_renderBindingRow(
        graphics: GuiGraphics,
        partialTick: Float,
        mouseX: Int,
        mouseY: Int,
        callback: CallbackInfo
    ) {
        if (ccaeroworks_bindingSocket < 0) return
        val invoker = this as ModuleScreenInvoker
        val listLeft = invoker.ccaeroworks_listLeft()
        val listTop = invoker.ccaeroworks_listTop()
        val rowLeft = invoker.ccaeroworks_rowLeft()
        val rowTop = ccaeroworks_bindingRowTop()

        ccaeroworks_syncBindingWidgets(rowLeft, rowTop, listTop)

        graphics.enableScissor(
            listLeft,
            listTop,
            listLeft + ModuleScreenRowGeometry.LIST_WIDTH,
            listTop + ModuleScreenRowGeometry.LIST_HEIGHT
        )
        try {
            graphics.fill(
                rowLeft,
                rowTop,
                rowLeft + ROW_WIDTH,
                rowTop + ModuleScreenRowGeometry.EXTENSION_ROW_HEIGHT,
                ROW_BACKGROUND
            )
            graphics.fill(
                rowLeft,
                rowTop,
                rowLeft + ROW_WIDTH,
                rowTop + 1,
                ROW_SEPARATOR
            )
        } finally {
            graphics.disableScissor()
        }
    }

    @Unique
    private fun ccaeroworks_addRadarSourceButton(desk: ConsoleBlockEntity, socket: Int) {
        ccaeroworks_selectedRadarIngress =
            (DisplayBindings.get(desk, socket) as? DisplayBinding.RadarSource)?.source?.ingressPos

        val rowLeft = (this as ModuleScreenInvoker).ccaeroworks_rowLeft()
        val rowTop = ccaeroworks_bindingRowTop()
        ccaeroworks_radarSourceButton = addRenderableWidget(
            Button.builder(ccaeroworks_radarSourceMessage()) {
                ccaeroworks_cycleRadarSource()
            }.bounds(
                rowLeft + CONTROL_INSET,
                rowTop + WIDGET_INSET_Y,
                CONTROL_WIDTH,
                WIDGET_HEIGHT
            ).build()
        )
    }

    @Unique
    private fun ccaeroworks_addTouchScriptField(desk: ConsoleBlockEntity, socket: Int) {
        val buttonWidth = 42
        val fieldWidth = CONTROL_WIDTH - buttonWidth - CONTROL_GAP
        val rowLeft = (this as ModuleScreenInvoker).ccaeroworks_rowLeft()
        val rowTop = ccaeroworks_bindingRowTop()
        val controlX = rowLeft + CONTROL_INSET
        val controlY = rowTop + WIDGET_INSET_Y
        val currentPath = (DisplayBindings.get(desk, socket) as? DisplayBinding.LuaHandler)?.path.orEmpty()

        ccaeroworks_touchScriptField = EditBox(
            font,
            controlX,
            controlY,
            fieldWidth,
            WIDGET_HEIGHT,
            Component.literal("Touch script")
        ).also { field ->
            field.setMaxLength(DisplayBindings.MAX_HANDLER_PATH_LENGTH)
            field.setValue(currentPath)
            addRenderableWidget(field)
        }

        ccaeroworks_touchScriptSetButton = addRenderableWidget(
            Button.builder(Component.literal("Set")) {
                val path = ccaeroworks_touchScriptField?.value.orEmpty()
                PacketDistributor.sendToServer(
                    SetDisplayTouchScriptPayload(desk.blockPos, socket, path)
                )
            }.bounds(
                controlX + fieldWidth + CONTROL_GAP,
                controlY,
                buttonWidth,
                WIDGET_HEIGHT
            ).build()
        )
    }

    @Unique
    private fun ccaeroworks_syncBindingWidgets(rowLeft: Int, rowTop: Int, listTop: Int) {
        val controlX = rowLeft + CONTROL_INSET
        val controlY = rowTop + WIDGET_INSET_Y
        val visible = ModuleScreenRowGeometry.fullyVisible(
            controlY,
            WIDGET_HEIGHT,
            listTop
        )

        ccaeroworks_radarSourceButton?.let { button ->
            button.x = controlX
            button.y = controlY
            button.visible = visible
            button.active = visible
        }

        val field = ccaeroworks_touchScriptField
        field?.let {
            it.x = controlX
            it.y = controlY
            it.visible = visible
            it.active = visible
            if (!visible && it.isFocused) it.isFocused = false
        }

        ccaeroworks_touchScriptSetButton?.let { button ->
            val fieldWidth = CONTROL_WIDTH - button.width - CONTROL_GAP
            button.x = controlX + fieldWidth + CONTROL_GAP
            button.y = controlY
            button.visible = visible
            button.active = visible
        }
    }

    @Unique
    private fun ccaeroworks_bindingRowTop(): Int {
        val invoker = this as ModuleScreenInvoker
        val screen = this as ModuleScreenAccessor
        return ModuleScreenRowGeometry.extensionScreenTop(
            ccaeroworks_nativeContentHeight,
            0,
            invoker.ccaeroworks_listTop(),
            screen.ccaeroworks_getRenderedScroll()
        )
    }

    @Unique
    private fun ccaeroworks_cycleRadarSource() {
        val desk = ccaeroworks_bindingDesk ?: return
        val socket = ccaeroworks_bindingSocket
        if (socket < 0) return

        val remote = RadarSourceRegistry.sources(desk)
            .filter { it.ingressPos != desk.blockPos }
        val positions = remote.map(RadarSourceDescriptor::ingressPos)
        val current = ccaeroworks_selectedRadarIngress
        val next = when {
            positions.isEmpty() -> null
            current == null -> positions.first()
            else -> positions.getOrNull(positions.indexOf(current) + 1)
        }

        ccaeroworks_selectedRadarIngress = next
        ccaeroworks_radarSourceButton?.setMessage(ccaeroworks_radarSourceMessage())
        PacketDistributor.sendToServer(
            SetRadarDisplaySourcePayload(desk.blockPos, socket, next)
        )
    }

    @Unique
    private fun ccaeroworks_radarSourceMessage(): Component {
        val desk = ccaeroworks_bindingDesk
            ?: return Component.literal("Radar source: local")
        val selected = ccaeroworks_selectedRadarIngress
            ?: return Component.literal("Radar source: local")
        val source = RadarSourceRegistry.sources(desk).firstOrNull { it.ingressPos == selected }
            ?: return Component.literal("Radar source: unavailable")
        val radar = source.radarPos
        val suffix = if (radar != null) {
            "#${source.memberIndex} ${radar.x},${radar.y},${radar.z}"
        } else {
            "#${source.memberIndex} ${source.status.name.lowercase()}"
        }
        return Component.literal("Radar source: $suffix")
    }

    companion object {
        private const val ROW_WIDTH: Int = 235
        private const val CONTROL_INSET: Int = 8
        private const val CONTROL_WIDTH: Int = ROW_WIDTH - CONTROL_INSET * 2
        private const val CONTROL_GAP: Int = 4
        private const val WIDGET_INSET_Y: Int = 5
        private const val WIDGET_HEIGHT: Int = 20
        private const val ROW_BACKGROUND: Int = 0x55000000
        private const val ROW_SEPARATOR: Int = 0x66808080
    }
}
