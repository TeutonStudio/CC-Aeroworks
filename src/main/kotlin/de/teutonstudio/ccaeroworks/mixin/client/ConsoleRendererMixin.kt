package de.teutonstudio.ccaeroworks.mixin.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import com.mred231.aeroworks.content.controls.ConsoleRenderer
import de.teutonstudio.ccaeroworks.client.display.DeskDisplayRenderer
import net.minecraft.client.renderer.MultiBufferSource
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(value = [ConsoleRenderer::class], remap = false)
abstract class ConsoleRendererMixin {
    @Inject(
        method = ["renderSafe(Lcom/mred231/aeroworks/content/controls/ConsoleBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"],
        at = [At("TAIL")]
    )
    private fun renderDigits(
        desk: ConsoleBlockEntity,
        partialTicks: Float,
        poseStack: PoseStack,
        buffers: MultiBufferSource,
        light: Int,
        overlay: Int,
        callback: CallbackInfo
    ) {
        DeskDisplayRenderer.render(desk, poseStack, buffers, light)
    }
}
