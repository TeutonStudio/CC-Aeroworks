package de.teutonstudio.ccaeroworks.mixin.client

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import com.mred231.aeroworks.content.controls.ConsoleSocket
import com.mred231.aeroworks.content.controls.ModuleMenu
import com.mred231.aeroworks.content.controls.ModuleScreen
import de.teutonstudio.ccaeroworks.display.DeskDisplayType
import de.teutonstudio.ccaeroworks.display.DisplayBindings
import de.teutonstudio.ccaeroworks.display.DisplayContentSource
import de.teutonstudio.ccaeroworks.display.DisplayInputBinding
import de.teutonstudio.ccaeroworks.display.RadarSourceDescriptor
import de.teutonstudio.ccaeroworks.display.RadarSourceRegistry
import de.teutonstudio.ccaeroworks.network.SetDisplayTouchScriptPayload
import de.teutonstudio.ccaeroworks.network.SetRadarDisplaySourcePayload
import de.teutonstudio.ccaeroworks.registry.CCModuleTypes
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

        if (radarDisplay) {
            ccaeroworks_addRadarSourceButton(desk, socket)
        } else {
            ccaeroworks_addTouchScriptField(desk, socket)
        }
    }

    @Unique
    private fun ccaeroworks_addRadarSourceButton(desk: ConsoleBlockEntity, socket: Int) {
        ccaeroworks_selectedRadarIngress =
            (DisplayBindings.get(desk, socket).content as? DisplayContentSource.RadarSource)?.source?.ingressPos

        val buttonWidth = (imageWidth - 16).coerceAtMost(156)
        val buttonHeight = 20
        val buttonX = leftPos + (imageWidth - buttonWidth) / 2
        val buttonY = topPos + imageHeight - buttonHeight * 2 - 10

        ccaeroworks_radarSourceButton = addRenderableWidget(
            Button.builder(ccaeroworks_radarSourceMessage()) {
                ccaeroworks_cycleRadarSource()
            }.bounds(buttonX, buttonY, buttonWidth, buttonHeight).build()
        )
    }

    @Unique
    private fun ccaeroworks_addTouchScriptField(desk: ConsoleBlockEntity, socket: Int) {
        val rowWidth = (imageWidth - 16).coerceAtMost(156)
        val buttonWidth = 42
        val fieldWidth = rowWidth - buttonWidth - 4
        val rowX = leftPos + (imageWidth - rowWidth) / 2
        val rowY = topPos + imageHeight - 50
        val currentPath = (DisplayBindings.get(desk, socket).input as? DisplayInputBinding.LuaHandler)?.path.orEmpty()

        ccaeroworks_touchScriptField = EditBox(
            font,
            rowX,
            rowY,
            fieldWidth,
            20,
            Component.literal("Touch script")
        ).also { field ->
            field.setMaxLength(DisplayBindings.MAX_HANDLER_PATH_LENGTH)
            field.setValue(currentPath)
            addRenderableWidget(field)
        }

        addRenderableWidget(
            Button.builder(Component.literal("Set")) {
                val path = ccaeroworks_touchScriptField?.value.orEmpty()
                PacketDistributor.sendToServer(
                    SetDisplayTouchScriptPayload(desk.blockPos, socket, path)
                )
            }.bounds(rowX + fieldWidth + 4, rowY, buttonWidth, 20).build()
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
}
