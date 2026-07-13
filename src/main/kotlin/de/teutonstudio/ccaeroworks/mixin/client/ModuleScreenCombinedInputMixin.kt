package de.teutonstudio.ccaeroworks.mixin.client

import com.mred231.aeroworks.content.controls.ModuleMenu
import com.mred231.aeroworks.content.controls.ModuleScreen
import com.mred231.aeroworks.content.controls.ModuleSetting
import com.mred231.aeroworks.content.controls.ModuleTypes
import de.teutonstudio.ccaeroworks.input.CombinedInputSource
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(value = [ModuleScreen::class], remap = false)
abstract class ModuleScreenCombinedInputMixin {
    @Inject(method = ["mouseClicked(DDI)Z"], at = [At("HEAD")], cancellable = true)
    private fun cycleCombinedMode(mouseX: Double, mouseY: Double, button: Int, callback: CallbackInfoReturnable<Boolean>) {
        if (button != 0) return
        val invoker = this as ModuleScreenInvoker
        val index = invoker.ccaeroworks_modeToggleAt(mouseX.toInt(), mouseY.toInt())
        if (index < 0 || !isSupported(invoker)) return
        val menu = (this as AbstractContainerScreenAccessor).ccaeroworks_getMenu() as? ModuleMenu ?: return
        val column = menu.columns().getOrNull(index) ?: return
        val analog = invoker.ccaeroworks_analogDriven(column)
        val combined = analog && invoker.ccaeroworks_module()
            ?.analogSourceFor(column.channel().id()) == CombinedInputSource.ID

        when {
            !analog -> return // Vanilla performs Buttons -> Analog.
            !combined -> {
                invoker.ccaeroworks_sendAnalogSource(index, CombinedInputSource.ID)
                if (invoker.ccaeroworks_bindFor(column).isBlank()) {
                    invoker.ccaeroworks_sendBind(index, "key.keyboard.k")
                }
            }
            else -> {
                invoker.ccaeroworks_sendAnalogSource(index, "")
                invoker.ccaeroworks_sendChannelFlag(column, ModuleSetting.ANALOG_ACTIVE, false)
            }
        }
        callback.returnValue = true
    }

    @Inject(method = ["mouseClicked(DDI)Z"], at = [At("HEAD")], cancellable = true)
    private fun captureCombinedKey(mouseX: Double, mouseY: Double, button: Int, callback: CallbackInfoReturnable<Boolean>) {
        val invoker = this as ModuleScreenInvoker
        val index = invoker.ccaeroworks_bindAreaAt(mouseX.toInt(), mouseY.toInt())
        if (index < 0 || !isSupported(invoker)) return
        val menu = (this as AbstractContainerScreenAccessor).ccaeroworks_getMenu() as? ModuleMenu ?: return
        val column = menu.columns().getOrNull(index) ?: return
        if (!isCombined(invoker, column)) return
        when (button) {
            0 -> (this as ModuleScreenAccessor).ccaeroworks_setCapturingColumn(index)
            1 -> invoker.ccaeroworks_sendBind(index, "")
            else -> return
        }
        callback.returnValue = true
    }

    @Inject(
        method = ["analogText(Lcom/mred231/aeroworks/content/controls/ModuleColumn;ZI)Ljava/lang/String;"],
        at = [At("HEAD")],
        cancellable = true
    )
    private fun showCombinedKey(column: com.mred231.aeroworks.content.controls.ModuleColumn, capturing: Boolean, maxWidth: Int, callback: CallbackInfoReturnable<String>) {
        val invoker = this as ModuleScreenInvoker
        if (!isSupported(invoker) || !isCombined(invoker, column)) return
        val menu = (this as AbstractContainerScreenAccessor).ccaeroworks_getMenu() as? ModuleMenu ?: return
        val columnIndex = menu.columns().indexOf(column)
        val actuallyCapturing = (this as ModuleScreenAccessor).ccaeroworks_getCapturingColumn() == columnIndex
        val text = if (capturing || actuallyCapturing) {
            Component.translatable("gui.aeroworks.joystick.bind_capture_prompt").string
        } else {
            val binding = invoker.ccaeroworks_bindFor(column)
            if (binding.isBlank()) Component.translatable("gui.aeroworks.joystick.bind_unbound").string
            else com.mred231.aeroworks.foundation.input.InputSource.displayName(binding).string
        }
        callback.returnValue = Minecraft.getInstance().font.plainSubstrByWidth(text, maxWidth)
    }

    @Inject(
        method = ["renderModeTooltip(Lnet/minecraft/client/gui/GuiGraphics;III)V"],
        at = [At("HEAD")],
        cancellable = true
    )
    private fun renderCombinedTooltip(graphics: GuiGraphics, mouseX: Int, mouseY: Int, columnIndex: Int, callback: CallbackInfo) {
        val invoker = this as ModuleScreenInvoker
        if (!isSupported(invoker)) return
        val menu = (this as AbstractContainerScreenAccessor).ccaeroworks_getMenu() as? ModuleMenu ?: return
        val column = menu.columns().getOrNull(columnIndex) ?: return
        if (!invoker.ccaeroworks_analogDriven(column) ||
            invoker.ccaeroworks_module()?.analogSourceFor(column.channel().id()) != CombinedInputSource.ID
        ) return
        val axisSuffix = when (CombinedInputSource.mouseAxis(column.channel().id())) {
            CombinedInputSource.MouseAxis.X -> "x"
            CombinedInputSource.MouseAxis.Y -> "y"
        }
        graphics.renderComponentTooltip(
            Minecraft.getInstance().font,
            listOf(Component.translatable("gui.cc_aeroworks.module.mode_combined_$axisSuffix").withStyle(ChatFormatting.GOLD)),
            mouseX,
            mouseY
        )
        callback.cancel()
    }

    private fun isSupported(invoker: ModuleScreenInvoker): Boolean =
        invoker.ccaeroworks_module()?.let(CombinedInputSource::supports) == true

    private fun isCombined(invoker: ModuleScreenInvoker, column: com.mred231.aeroworks.content.controls.ModuleColumn): Boolean =
        invoker.ccaeroworks_module()?.let { CombinedInputSource.isCombined(it, column.channel().id()) } == true
}
