package de.teutonstudio.ccaeroworks.mixin.client

import com.mojang.blaze3d.platform.InputConstants
import com.mred231.aeroworks.content.controls.ModuleMenu
import com.mred231.aeroworks.content.controls.ModuleScreen
import de.teutonstudio.ccaeroworks.display.DeskDisplayType
import de.teutonstudio.ccaeroworks.display.RadarDisplayType
import de.teutonstudio.ccaeroworks.input.DisplayInteractionKey
import de.teutonstudio.ccaeroworks.registry.CCModuleTypes
import net.minecraft.client.KeyMapping
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
    private var ccaeroworks_displayInteractionButton: Button? = null

    @Inject(method = ["init()V"], at = [At("TAIL")])
    private fun ccaeroworks_addDisplayInteractionKey(callback: CallbackInfo) {
        if (!ccaeroworks_isInteractiveLargeDisplay()) return

        val buttonWidth = (imageWidth - 16).coerceAtMost(156)
        val buttonHeight = 20
        val buttonX = leftPos + (imageWidth - buttonWidth) / 2
        val buttonY = topPos + imageHeight - buttonHeight - 6

        ccaeroworks_displayInteractionButton = addRenderableWidget(
            Button.builder(ccaeroworks_displayInteractionMessage()) { button ->
                ccaeroworks_capturingDisplayInteraction = true
                button.setMessage(ccaeroworks_displayInteractionMessage())
            }.bounds(buttonX, buttonY, buttonWidth, buttonHeight).build()
        )
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
        if (!ccaeroworks_capturingDisplayInteraction) return
        if (button < 0) return

        ccaeroworks_applyDisplayInteractionKey(InputConstants.Type.MOUSE.getOrCreate(button))
        callback.returnValue = true
    }

    @Unique
    private fun ccaeroworks_applyDisplayInteractionKey(key: InputConstants.Key) {
        val client = minecraft ?: return
        client.options.setKey(DisplayInteractionKey.KEY_MAPPING, key)
        KeyMapping.resetMapping()
        client.options.save()
        ccaeroworks_capturingDisplayInteraction = false
        ccaeroworks_refreshDisplayInteractionButton()
    }

    @Unique
    private fun ccaeroworks_refreshDisplayInteractionButton() {
        ccaeroworks_displayInteractionButton?.setMessage(ccaeroworks_displayInteractionMessage())
    }

    @Unique
    private fun ccaeroworks_displayInteractionMessage(): Component {
        val label = Component.translatable(DisplayInteractionKey.TRANSLATION_KEY).append(": ")
        return if (ccaeroworks_capturingDisplayInteraction) {
            label.append(Component.translatable("gui.aeroworks.joystick.bind_capture_prompt"))
        } else {
            label.append(DisplayInteractionKey.KEY_MAPPING.translatedKeyMessage)
        }
    }

    @Unique
    private fun ccaeroworks_isInteractiveLargeDisplay(): Boolean {
        val module = (this as ModuleScreenInvoker).ccaeroworks_module() ?: return false
        return CCModuleTypes.displayType(module.type()) == DeskDisplayType.THREE_DIGIT ||
            CCModuleTypes.radarDisplayType(module.type()) == RadarDisplayType.LARGE
    }
}
