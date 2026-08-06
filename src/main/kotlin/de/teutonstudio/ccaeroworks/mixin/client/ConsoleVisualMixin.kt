package de.teutonstudio.ccaeroworks.mixin.client

import com.mred231.aeroworks.content.controls.ConsoleBlock
import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import com.mred231.aeroworks.content.controls.ConsoleVisual
import de.teutonstudio.ccaeroworks.client.display.DeskDisplayModels
import de.teutonstudio.ccaeroworks.client.display.DeskDisplayRenderer
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskAccess
import dev.engine_room.flywheel.api.instance.Instance
import dev.engine_room.flywheel.api.visual.DynamicVisual
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import dev.engine_room.flywheel.lib.instance.InstanceTypes
import dev.engine_room.flywheel.lib.instance.TransformedInstance
import dev.engine_room.flywheel.lib.model.Models
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import java.util.function.Consumer

@Mixin(value = [ConsoleVisual::class], remap = false)
abstract class ConsoleVisualMixin(
    context: VisualizationContext,
    blockEntity: ConsoleBlockEntity,
    partialTick: Float
) : AbstractBlockEntityVisual<ConsoleBlockEntity>(context, blockEntity, partialTick) {
    @field:Unique
    private val displayDigits: MutableList<CCAeroworksDigit> = mutableListOf()

    @field:Unique
    private var displayKey: String = ""

    @Inject(
        method = ["<init>(Ldev/engine_room/flywheel/api/visualization/VisualizationContext;Lcom/mred231/aeroworks/content/controls/ConsoleBlockEntity;F)V"],
        at = [At("TAIL")]
    )
    private fun construct(
        context: VisualizationContext,
        blockEntity: ConsoleBlockEntity,
        partialTick: Float,
        callback: CallbackInfo
    ) {
        rebuildDigits()
        applyTransforms()
    }

    @Inject(
        method = ["beginFrame(Ldev/engine_room/flywheel/api/visual/DynamicVisual\$Context;)V"],
        at = [At("TAIL")]
    )
    private fun beginFrame(context: DynamicVisual.Context, callback: CallbackInfo) {
        rebuildDigits()
        applyTransforms()
    }

    @Inject(method = ["updateLight(F)V"], at = [At("TAIL")])
    private fun updateDisplayLight(partialTick: Float, callback: CallbackInfo) {
        displayDigits.forEach { relight(it.instance) }
    }

    @Inject(method = ["collectCrumblingInstances(Ljava/util/function/Consumer;)V"], at = [At("TAIL")])
    private fun collectDisplayInstances(consumer: Consumer<Instance>, callback: CallbackInfo) {
        displayDigits.forEach { consumer.accept(it.instance) }
    }

    @Inject(method = ["_delete()V"], at = [At("TAIL")])
    private fun deleteDisplayInstances(callback: CallbackInfo) {
        displayDigits.forEach { it.instance.delete() }
        displayDigits.clear()
    }

    @Unique
    private fun rebuildDigits() {
        val displays = AeroworksDeskAccess.renderedDisplays(blockEntity)
        val nextKey = displays.joinToString(separator = ";", postfix = ";") {
            "${it.socket}:${it.text}:${it.pixels?.encode().orEmpty()}"
        }
        if (nextKey == displayKey) return
        displayKey = nextKey
        displayDigits.forEach { it.instance.delete() }
        displayDigits.clear()

        displays.forEach { display ->
            if (display.pixels != null) {
                for (y in 0 until display.pixels.height) for (x in 0 until display.pixels.width) {
                    if (!display.pixels.get(x, y)) continue
                    val instance = instancerProvider()
                        .instancer(InstanceTypes.TRANSFORMED, Models.partial(DeskDisplayModels.PIXEL))
                        .createInstance()
                    displayDigits += CCAeroworksDigit(
                        display.socket,
                        DeskDisplayRenderer.pixelOffsetX(display.type, display.pixels.width, x),
                        DeskDisplayRenderer.pixelOffsetZ(display.pixels.height, y),
                        instance
                    )
                    relight(instance)
                }
            } else {
                val text = display.text.padEnd(display.type.width, ' ')
                repeat(display.type.width) { index ->
                    DeskDisplayModels.segments(text[index]).forEach { segment ->
                        val instance = instancerProvider()
                            .instancer(InstanceTypes.TRANSFORMED, Models.partial(segment.model))
                            .createInstance()
                        displayDigits += CCAeroworksDigit(
                            display.socket,
                            DeskDisplayRenderer.digitOffset(display.type.width, index) + segment.x,
                            segment.z,
                            instance
                        )
                        relight(instance)
                    }
                }
            }
        }
    }

    @Unique
    private fun applyTransforms() {
        val sockets = blockEntity.sockets()
        val rotation = ConsoleBlock.rotationFor(blockEntity.blockState)
        displayDigits.forEach { digit ->
            val socket = sockets.getOrNull(digit.socket) ?: return@forEach
            digit.instance.setIdentityTransform()
                .translate(visualPosition)
                .translate(0.5f, 0.5f, 0.5f)
                .rotate(rotation)
                .translate(socket.offset().x - 0.5, socket.offset().y - 0.5, socket.offset().z - 0.5)
                .rotate(socket.orientation())
                .translate(-0.5f, 0.0f, -0.5f)
                .translate(digit.x, 0.0, digit.z)
                .setChanged()
        }
    }

    @Unique
    private data class CCAeroworksDigit(
        val socket: Int,
        val x: Double,
        val z: Double,
        val instance: TransformedInstance
    )
}
