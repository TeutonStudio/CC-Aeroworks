package de.teutonstudio.ccaeroworks.mixin.client

import com.mojang.blaze3d.platform.InputConstants
import com.mred231.aeroworks.content.controls.ModuleMenu
import com.mred231.aeroworks.content.controls.ModuleScreen
import com.mred231.aeroworks.content.controls.ModuleSetting
import de.teutonstudio.ccaeroworks.display.DeskDisplayType
import de.teutonstudio.ccaeroworks.display.RadarDisplayType
import de.teutonstudio.ccaeroworks.input.CombinedInputSource
import de.teutonstudio.ccaeroworks.input.DisplayInteractionKey
import de.teutonstudio.ccaeroworks.registry.CCModuleTypes
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import org.lwjgl.glfw.GLFW
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(value = [ModuleScreen::class], remap = false)
abstract class ModuleScreenDisplayInteractionMixin(
    menu: ModuleMenu,
    inventory: Inventory,
    title: Component
) : AbstractContainerScreen<ModuleMenu>(menu, inventory, title) {
    @Unique
    private var ccaeroworks_capturingDisplayInteraction = false

    @Unique
    private var ccaeroworks_displayModeButton: Button? = null

    @Unique
    private var ccaeroworks_displayInteractionButton: Button? = null

    @Unique
    private val ccaeroworks_combinedButtons = linkedMapOf<Int, Button>()

    @Inject(method = ["init()V"], at = [At("TAIL")])
    private fun ccaeroworks_addCustomInputControls(callback: CallbackInfo) {
        ccaeroworks_combinedButtons.clear()
        if (ccaeroworks_isInteractiveLargeDisplay()) {
            ccaeroworks_addDisplayInteractionKey()
        } else {
            ccaeroworks_addCombinedModeButtons()
        }
    }

    @Unique
    private fun ccaeroworks_addDisplayInteractionKey() {
        // Mirror the normal combined-input presentation: mode on the left, activation binding on
        // the right. The display only supports Combined, so the mode control is intentionally fixed.
        val totalWidth = (imageWidth - 16).coerceAtMost(156)
        val gap = 4
        val modeWidth = (totalWidth * 2 / 5).coerceAtLeast(54)
        val bindWidth = totalWidth - modeWidth - gap
        val buttonHeight = 20
        val rowX = leftPos + (imageWidth - totalWidth) / 2
        val rowY = topPos + imageHeight - buttonHeight - 6

        ccaeroworks_displayModeButton = addRenderableWidget(
            Button.builder(Component.translatable("input.cc_aeroworks.combined")) { }.bounds(
                rowX,
                rowY,
                modeWidth,
                buttonHeight
            ).build().also { it.active = false }
        )

        ccaeroworks_displayInteractionButton = addRenderableWidget(
            Button.builder(ccaeroworks_displayInteractionMessage()) { button ->
                ccaeroworks_capturingDisplayInteraction = true
                button.setMessage(ccaeroworks_displayInteractionMessage())
            }.bounds(
                rowX + modeWidth + gap,
                rowY,
                bindWidth,
                buttonHeight
            ).build()
        )
    }

    @Unique
    private fun ccaeroworks_addCombinedModeButtons() {
        val invoker = this as ModuleScreenInvoker
        val module = invoker.ccaeroworks_module() ?: return
        if (!CombinedInputSource.supports(module)) return

        val supported = menu.columns().mapIndexedNotNull { index, column ->
            if (column.channel().id() in CombinedInputSource.channels(module)) index else null
        }
        if (supported.isEmpty()) return

        val totalWidth = (imageWidth - 16).coerceAtMost(156)
        val gap = 2
        val buttonHeight = 20
        val rowX = leftPos + (imageWidth - totalWidth) / 2
        val rowY = topPos + imageHeight - buttonHeight - 6
        val buttonWidth = ((totalWidth - gap * (supported.size - 1)) / supported.size).coerceAtLeast(24)

        supported.forEachIndexed { visibleIndex, columnIndex ->
            val column = menu.columns()[columnIndex]
            val button = Button.builder(ccaeroworks_combinedButtonMessage(columnIndex)) { pressed ->
                val currentModule = invoker.ccaeroworks_module() ?: return@builder
                val currentColumn = menu.columns().getOrNull(columnIndex) ?: return@builder
                val channel = currentColumn.channel().id()
                val enableCombined = !CombinedInputSource.isCombined(currentModule, channel)

                if (enableCombined) {
                    invoker.ccaeroworks_sendChannelFlag(currentColumn, ModuleSetting.ANALOG_ACTIVE, true)
                    invoker.ccaeroworks_sendAnalogSource(columnIndex, CombinedInputSource.ID)
                    if (invoker.ccaeroworks_bindFor(currentColumn).isBlank()) {
                        invoker.ccaeroworks_sendBind(columnIndex, "key.keyboard.k")
                    }
                } else {
                    invoker.ccaeroworks_sendAnalogSource(columnIndex, "")
                    invoker.ccaeroworks_sendChannelFlag(currentColumn, ModuleSetting.ANALOG_ACTIVE, false)
                }

                pressed.setMessage(ccaeroworks_combinedButtonMessage(columnIndex, enableCombined))
            }.bounds(
                rowX + visibleIndex * (buttonWidth + gap),
                rowY,
                if (visibleIndex == supported.lastIndex) totalWidth - visibleIndex * (buttonWidth + gap) else buttonWidth,
                buttonHeight
            ).build()
            ccaeroworks_combinedButtons[columnIndex] = addRenderableWidget(button)
        }
    }

    @Inject(method = ["render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"], at = [At("TAIL")])
    private fun ccaeroworks_refreshCombinedButtons(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
        callback: CallbackInfo
    ) {
        ccaeroworks_combinedButtons.forEach { (columnIndex, button) ->
            button.setMessage(ccaeroworks_combinedButtonMessage(columnIndex))
        }
    }

    @Inject(method = ["keyPressed(III)Z"], at = [At("HEAD")], cancellable = true)
    private fun ccaeroworks_captureDisplayInteractionKey(
        keyCode: Int,
        scanCode: Int,
        modifiers: Int,
        callback: CallbackInfoReturnable<Boolean>
    ) {
        if (!ccaeroworks_capturingDisplayInteraction) return

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            ccaeroworks_capturingDisplayInteraction = false
            ccaeroworks_refreshDisplayInteractionButton()
            callback.returnValue = true
            return
        }

        ccaeroworks_applyDisplayInteractionKey(InputConstants.getKey(keyCode, scanCode))
        callback.returnValue = true
    }

    @Inject(method = ["mouseClicked(DDI)Z"], at = [At("HEAD")], cancellable = true)
    private fun ccaeroworks_captureDisplayInteractionMouseButton(
        mouseX: Double,
        mouseY: Double,
        button: Int,
        callback: CallbackInfoReturnable<Boolean>
    ) {
        if (ccaeroworks_capturingDisplayInteraction) {
            if (button < 0) return
            ccaeroworks_applyDisplayInteractionKey(InputConstants.Type.MOUSE.getOrCreate(button))
            callback.returnValue = true
            return
        }

        // Match the regular combined binding field: right-click clears the binding immediately.
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT &&
            ccaeroworks_displayInteractionButton?.isMouseOver(mouseX, mouseY) == true
        ) {
            DisplayInteractionKey.clearBinding()
            ccaeroworks_refreshDisplayInteractionButton()
            callback.returnValue = true
        }
    }

    @Unique
    private fun ccaeroworks_applyDisplayInteractionKey(key: InputConstants.Key) {
        DisplayInteractionKey.setBinding(key)
        ccaeroworks_capturingDisplayInteraction = false
        ccaeroworks_refreshDisplayInteractionButton()
    }

    @Unique
    private fun ccaeroworks_refreshDisplayInteractionButton() {
        ccaeroworks_displayInteractionButton?.setMessage(ccaeroworks_displayInteractionMessage())
    }

    @Unique
    private fun ccaeroworks_displayInteractionMessage(): Component =
        if (ccaeroworks_capturingDisplayInteraction) {
            Component.translatable("gui.aeroworks.joystick.bind_capture_prompt")
        } else {
            DisplayInteractionKey.displayMessage()
        }

    @Unique
    private fun ccaeroworks_combinedButtonMessage(columnIndex: Int, forcedState: Boolean? = null): Component {
        val invoker = this as ModuleScreenInvoker
        val module = invoker.ccaeroworks_module()
        val column = menu.columns().getOrNull(columnIndex)
        if (module == null || column == null) return Component.translatable("input.cc_aeroworks.combined")

        val channel = column.channel().id()
        val active = forcedState ?: CombinedInputSource.isCombined(module, channel)
        val prefix = when (channel) {
            "x", "y" -> channel.uppercase()
            "red" -> "R"
            "amber" -> "A"
            "green" -> "G"
            "blue" -> "B"
            "lever" -> ""
            else -> channel
        }
        val mode = Component.translatable("input.cc_aeroworks.combined")
        val text = if (prefix.isBlank()) mode else Component.literal("$prefix: ").append(mode)
        return if (active) text.copy().withStyle(ChatFormatting.GOLD) else text
    }

    @Unique
    private fun ccaeroworks_isInteractiveLargeDisplay(): Boolean {
        val module = (this as ModuleScreenInvoker).ccaeroworks_module() ?: return false
        return CCModuleTypes.displayType(module.type()) == DeskDisplayType.THREE_DIGIT ||
            CCModuleTypes.radarDisplayType(module.type()) == RadarDisplayType.LARGE
    }
}
