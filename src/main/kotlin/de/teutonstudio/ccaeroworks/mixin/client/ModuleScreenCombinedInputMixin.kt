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
    @Unique
    private val ccaeroworks_modeToggleBounds = linkedMapOf<Int, IntArray>()

    @Unique
    private val ccaeroworks_bindAreaBounds = linkedMapOf<Int, IntArray>()

    @Inject(method = ["init()V"], at = [At("TAIL")])
    private fun initializeCombinedChannels(callback: CallbackInfo) {
        val invoker = this as ModuleScreenInvoker
        val module = invoker.ccaeroworks_module() ?: return
        if (!CombinedInputSource.supports(module)) return
        val accessor = this as AbstractContainerScreenAccessor
        val menu = accessor.ccaeroworks_getMenu() as? ModuleMenu ?: return

        if (CombinedInputSource.isCombinedOnly(module)) {
            menu.columns().forEachIndexed { index, column ->
                if (column.channel().id() !in CombinedInputSource.channels(module)) return@forEachIndexed
                forceCombined(invoker, module, column, index)
            }
        }

        discoverCombinedBounds(invoker, module, menu, accessor)
    }

    @Inject(method = ["mouseClicked(DDI)Z"], at = [At("HEAD")], cancellable = true)
    private fun cycleCombinedMode(
        mouseX: Double,
        mouseY: Double,
        button: Int,
        callback: CallbackInfoReturnable<Boolean>
    ) {
        if (button != 0) return
        val invoker = this as ModuleScreenInvoker
        val module = invoker.ccaeroworks_module() ?: return
        val index = invoker.ccaeroworks_modeToggleAt(mouseX.toInt(), mouseY.toInt())
        if (index < 0 || !CombinedInputSource.supports(module)) return
        val menu = (this as AbstractContainerScreenAccessor).ccaeroworks_getMenu() as? ModuleMenu ?: return
        val column = menu.columns().getOrNull(index) ?: return
        val channel = column.channel().id()
        if (channel !in CombinedInputSource.channels(module)) return

        if (CombinedInputSource.isCombinedOnly(module)) {
            forceCombined(invoker, module, column, index)
            callback.returnValue = true
            return
        }

        val analog = invoker.ccaeroworks_analogDriven(column)
        val combined = analog && CombinedInputSource.isCombined(module, channel)

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
    private fun toggleCombinedAxis(
        mouseX: Double,
        mouseY: Double,
        button: Int,
        callback: CallbackInfoReturnable<Boolean>
    ) {
        if (button != 0) return
        val invoker = this as ModuleScreenInvoker
        val module = invoker.ccaeroworks_module() ?: return
        if (!CombinedInputSource.supports(module)) return
        val menu = (this as AbstractContainerScreenAccessor).ccaeroworks_getMenu() as? ModuleMenu ?: return

        menu.columns().forEachIndexed { index, column ->
            val channel = column.channel().id()
            if (!CombinedInputSource.supportsAxisSelection(module, channel)) return@forEachIndexed
            if (!CombinedInputSource.isCombined(module, channel)) return@forEachIndexed
            val bounds = axisToggleBounds(index) ?: return@forEachIndexed
            if (!inside(bounds, mouseX, mouseY)) return@forEachIndexed

            val next = when (CombinedInputSource.mouseAxis(module, channel)) {
                CombinedInputSource.MouseAxis.X -> CombinedInputSource.MouseAxis.Y
                CombinedInputSource.MouseAxis.Y -> CombinedInputSource.MouseAxis.X
            }
            invoker.ccaeroworks_sendAnalogSource(index, CombinedInputSource.sourceForAxis(next))
            callback.returnValue = true
            return
        }
    }

    @Inject(method = ["mouseClicked(DDI)Z"], at = [At("HEAD")], cancellable = true)
    private fun captureCombinedKey(
        mouseX: Double,
        mouseY: Double,
        button: Int,
        callback: CallbackInfoReturnable<Boolean>
    ) {
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
    private fun showCombinedKey(
        column: ModuleColumn,
        capturing: Boolean,
        maxWidth: Int,
        callback: CallbackInfoReturnable<String>
    ) {
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
    private fun renderCombinedTooltip(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        columnIndex: Int,
        callback: CallbackInfo
    ) {
        val invoker = this as ModuleScreenInvoker
        val module = invoker.ccaeroworks_module() ?: return
        if (!CombinedInputSource.supports(module)) return
        val menu = (this as AbstractContainerScreenAccessor).ccaeroworks_getMenu() as? ModuleMenu ?: return
        val column = menu.columns().getOrNull(columnIndex) ?: return
        val channel = column.channel().id()
        if (!invoker.ccaeroworks_analogDriven(column) || !CombinedInputSource.isCombined(module, channel)) return

        val axisSuffix = when (CombinedInputSource.mouseAxis(module, channel)) {
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
    private fun renderCombinedControls(
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
        val combinedIcon = CCAeroworks.id("textures/gui/combined_input_placeholder.png")
        val font = Minecraft.getInstance().font

        menu.columns().forEachIndexed { index, column ->
            val channel = column.channel().id()
            if (!CombinedInputSource.isCombined(module, channel)) return@forEachIndexed

            ccaeroworks_modeToggleBounds[index]?.let { bounds ->
                val width = bounds[2] - bounds[0] + 1
                val height = bounds[3] - bounds[1] + 1
                val iconSize = minOf(16, width, height).coerceAtLeast(1)
                val iconX = bounds[0] + (width - iconSize) / 2
                val iconY = bounds[1] + (height - iconSize) / 2

                graphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, -0xddddde)
                graphics.blit(combinedIcon, iconX, iconY, 0.0f, 0.0f, iconSize, iconSize, 16, 16)
            }

            if (!CombinedInputSource.supportsAxisSelection(module, channel)) return@forEachIndexed
            val axisBounds = axisToggleBounds(index) ?: return@forEachIndexed
            val axis = CombinedInputSource.mouseAxis(module, channel)
            val label = if (axis == CombinedInputSource.MouseAxis.X) "X" else "Y"

            graphics.fill(axisBounds[0], axisBounds[1], axisBounds[2] + 1, axisBounds[3] + 1, 0xCC202020.toInt())
            val textX = axisBounds[0] + ((axisBounds[2] - axisBounds[0] + 1) - font.width(label)) / 2
            val textY = axisBounds[1] + ((axisBounds[3] - axisBounds[1] + 1) - font.lineHeight) / 2 + 1
            graphics.drawString(font, label, textX, textY, 0xFFF2F2F2.toInt(), false)

            if (inside(axisBounds, mouseX.toDouble(), mouseY.toDouble())) {
                val suffix = if (axis == CombinedInputSource.MouseAxis.X) "x" else "y"
                graphics.renderComponentTooltip(
                    font,
                    listOf(Component.translatable("gui.cc_aeroworks.module.mode_combined_$suffix").withStyle(ChatFormatting.GOLD)),
                    mouseX,
                    mouseY
                )
            }
        }
    }

    @Unique
    private fun discoverCombinedBounds(
        invoker: ModuleScreenInvoker,
        module: com.mred231.aeroworks.content.controls.MountedModule,
        menu: ModuleMenu,
        accessor: AbstractContainerScreenAccessor
    ) {
        ccaeroworks_modeToggleBounds.clear()
        ccaeroworks_bindAreaBounds.clear()
        val supported = CombinedInputSource.channels(module).toSet()
        if (supported.isEmpty()) return

        val minX = accessor.ccaeroworks_getLeftPos()
        val minY = accessor.ccaeroworks_getTopPos()
        val maxX = minX + accessor.ccaeroworks_getImageWidth()
        val maxY = minY + accessor.ccaeroworks_getImageHeight()

        for (y in minY until maxY) {
            for (x in minX until maxX) {
                val modeIndex = invoker.ccaeroworks_modeToggleAt(x, y)
                if (modeIndex in menu.columns().indices) {
                    val channel = menu.columns()[modeIndex].channel().id()
                    if (channel in supported) expandBounds(ccaeroworks_modeToggleBounds, modeIndex, x, y)
                }

                val bindIndex = invoker.ccaeroworks_bindAreaAt(x, y)
                if (bindIndex in menu.columns().indices) {
                    val channel = menu.columns()[bindIndex].channel().id()
                    if (channel in supported) expandBounds(ccaeroworks_bindAreaBounds, bindIndex, x, y)
                }
            }
        }
    }

    @Unique
    private fun expandBounds(map: MutableMap<Int, IntArray>, index: Int, x: Int, y: Int) {
        val bounds = map.getOrPut(index) { intArrayOf(x, y, x, y) }
        if (x < bounds[0]) bounds[0] = x
        if (y < bounds[1]) bounds[1] = y
        if (x > bounds[2]) bounds[2] = x
        if (y > bounds[3]) bounds[3] = y
    }

    @Unique
    private fun axisToggleBounds(index: Int): IntArray? {
        val bind = ccaeroworks_bindAreaBounds[index] ?: return null
        val bindHeight = bind[3] - bind[1] + 1
        val size = minOf(12, bindHeight).coerceAtLeast(8)
        val right = bind[0] - 2
        val left = right - size + 1
        val top = bind[1] + (bindHeight - size) / 2
        return intArrayOf(left, top, right, top + size - 1)
    }

    @Unique
    private fun inside(bounds: IntArray, mouseX: Double, mouseY: Double): Boolean =
        mouseX >= bounds[0] && mouseX <= bounds[2] && mouseY >= bounds[1] && mouseY <= bounds[3]

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
        if (!CombinedInputSource.isCombinedSource(module.analogSourceFor(channel))) {
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
