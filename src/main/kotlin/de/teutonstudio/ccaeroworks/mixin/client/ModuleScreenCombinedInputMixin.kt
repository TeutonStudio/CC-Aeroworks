package de.teutonstudio.ccaeroworks.mixin.client

import com.mred231.aeroworks.content.controls.ModuleColumn
import com.mred231.aeroworks.content.controls.ModuleMenu
import com.mred231.aeroworks.content.controls.ModuleScreen
import com.mred231.aeroworks.content.controls.ModuleSetting
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.input.CombinedInputSource
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(value = [ModuleScreen::class], remap = false)
abstract class ModuleScreenCombinedInputMixin {
    @Inject(method = ["init()V"], at = [At("TAIL")])
    private fun initializeCombinedChannels(callback: CallbackInfo) {
        val invoker = this as ModuleScreenInvoker
        val module = invoker.ccaeroworks_module() ?: return
        if (!CombinedInputSource.supports(module)) return
        val menu = (this as AbstractContainerScreenAccessor).ccaeroworks_getMenu() as? ModuleMenu ?: return

        if (CombinedInputSource.isCombinedOnly(module)) {
            menu.columns().forEachIndexed { index, column ->
                if (column.channel().id() !in CombinedInputSource.channels(module)) return@forEachIndexed
                forceCombined(invoker, module, column, index)
            }
        }
    }

    @Inject(method = ["mouseClicked(DDI)Z"], at = [At("HEAD")], cancellable = true)
    private fun cycleCombinedMode(mouseX: Double, mouseY: Double, button: Int, callback: CallbackInfoReturnable<Boolean>) {
        if (button != 0) return
        val invoker = this as ModuleScreenInvoker
        val module = invoker.ccaeroworks_module() ?: return
        val index = invoker.ccaeroworks_modeToggleAt(mouseX.toInt(), mouseY.toInt())
        if (index < 0 || !CombinedInputSource.supports(module)) return
        val menu = (this as AbstractContainerScreenAccessor).ccaeroworks_getMenu() as? ModuleMenu ?: return
        val column = menu.columns().getOrNull(index) ?: return
        if (column.channel().id() !in CombinedInputSource.channels(module)) return

        if (CombinedInputSource.isCombinedOnly(module)) {
            forceCombined(invoker, module, column, index)
            callback.returnValue = true
            return
        }

        val analog = invoker.ccaeroworks_analogDriven(column)
        val combined = analog && module.analogSourceFor(column.channel().id()) == CombinedInputSource.ID

        when {
            !analog -> return
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
        val module = invoker.ccaeroworks_module() ?: return
        val index = invoker.ccaeroworks_bindAreaAt(mouseX.toInt(), mouseY.toInt())
        if (index < 0 || !CombinedInputSource.supports(module)) return
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
    private fun showCombinedKey(column: ModuleColumn, capturing: Boolean, maxWidth: Int, callback: CallbackInfoReturnable<String>) {
        val invoker = this as ModuleScreenInvoker
        val module = invoker.ccaeroworks_module() ?: return
        if (!CombinedInputSource.supports(module) || !isCombined(invoker, column)) return
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
        val module = invoker.ccaeroworks_module() ?: return
        if (!CombinedInputSource.supports(module)) return
        val menu = (this as AbstractContainerScreenAccessor).ccaeroworks_getMenu() as? ModuleMenu ?: return
        val column = menu.columns().getOrNull(columnIndex) ?: return
        if (!invoker.ccaeroworks_analogDriven(column) ||
            module.analogSourceFor(column.channel().id()) != CombinedInputSource.ID
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

    @Inject(
        method = ["render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"],
        at = [At("TAIL")]
    )
    private fun renderCombinedModeIcons(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
        callback: CallbackInfo
    ) {
        val invoker = this as ModuleScreenInvoker
        val module = invoker.ccaeroworks_module() ?: return
        if (!CombinedInputSource.supports(module)) return
        val menu = (this as AbstractContainerScreenAccessor).ccaeroworks_getMenu() as? ModuleMenu ?: return
        val screen = this as ModuleScreenAccessor
        val groups = ModuleScreenRowGeometry.nativeGroups(menu.columns())
        val listLeft = invoker.ccaeroworks_listLeft()
        val listTop = invoker.ccaeroworks_listTop()
        val rowLeft = invoker.ccaeroworks_rowLeft()
        val renderedScroll = screen.ccaeroworks_getRenderedScroll()
        val combinedIcon = CCAeroworks.id("textures/gui/combined_input_placeholder.png")

        graphics.enableScissor(
            listLeft,
            listTop,
            listLeft + ModuleScreenRowGeometry.LIST_WIDTH,
            listTop + ModuleScreenRowGeometry.LIST_HEIGHT
        )
        try {
            menu.columns().forEachIndexed { index, column ->
                if (!CombinedInputSource.isCombined(module, column.channel().id())) return@forEachIndexed
                val bounds = ModuleScreenRowGeometry.modeToggleRect(
                    groups,
                    index,
                    rowLeft,
                    listTop,
                    renderedScroll
                ) ?: return@forEachIndexed
                val iconSize = minOf(16, bounds.width, bounds.height).coerceAtLeast(1)
                val iconX = bounds.x + (bounds.width - iconSize) / 2
                val iconY = bounds.y + (bounds.height - iconSize) / 2

                graphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, -0xddddde)
                graphics.blit(combinedIcon, iconX, iconY, 0.0f, 0.0f, iconSize, iconSize, 16, 16)
            }
        } finally {
            graphics.disableScissor()
        }
    }

    @Unique
    private fun forceCombined(
        invoker: ModuleScreenInvoker,
        module: com.mred231.aeroworks.content.controls.MountedModule,
        column: ModuleColumn,
        index: Int
    ) {
        val channel = column.channel().id()
        if (!module.analogActiveFor(channel)) {
            invoker.ccaeroworks_sendChannelFlag(column, ModuleSetting.ANALOG_ACTIVE, true)
        }
        if (module.analogSourceFor(channel) != CombinedInputSource.ID) {
            invoker.ccaeroworks_sendAnalogSource(index, CombinedInputSource.ID)
        }
        if (invoker.ccaeroworks_bindFor(column).isBlank()) {
            invoker.ccaeroworks_sendBind(index, "key.keyboard.k")
        }
    }

    @Unique
    private fun isCombined(invoker: ModuleScreenInvoker, column: ModuleColumn): Boolean =
        invoker.ccaeroworks_module()?.let { CombinedInputSource.isCombined(it, column.channel().id()) } == true
}
