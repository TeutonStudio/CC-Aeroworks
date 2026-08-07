package de.teutonstudio.ccaeroworks.mixin.client

import com.mojang.math.Axis
import com.mred231.aeroworks.content.controls.ConsoleBlock
import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import com.mred231.aeroworks.content.controls.ConsoleVisual
import de.teutonstudio.ccaeroworks.client.display.DeskDisplayModels
import de.teutonstudio.ccaeroworks.client.display.DeskDisplayRenderer
import de.teutonstudio.ccaeroworks.client.display.RadarSurfaceRenderer
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskAccess
import dev.engine_room.flywheel.api.instance.Instance
import dev.engine_room.flywheel.api.visual.DynamicVisual
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import dev.engine_room.flywheel.lib.instance.InstanceTypes
import dev.engine_room.flywheel.lib.instance.TransformedInstance
import dev.engine_room.flywheel.lib.model.Models
import dev.engine_room.flywheel.lib.model.baked.PartialModel
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
    private val displayElements: MutableList<CCAeroworksDisplayElement> = mutableListOf()

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
        rebuildElements()
        applyTransforms()
    }

    @Inject(
        method = ["beginFrame(Ldev/engine_room/flywheel/api/visual/DynamicVisual\$Context;)V"],
        at = [At("TAIL")]
    )
    private fun beginFrame(context: DynamicVisual.Context, callback: CallbackInfo) {
        rebuildElements()
        applyTransforms()
    }

    @Inject(method = ["updateLight(F)V"], at = [At("TAIL")])
    private fun updateDisplayLight(partialTick: Float, callback: CallbackInfo) {
        displayElements.forEach { relight(it.instance) }
    }

    @Inject(method = ["collectCrumblingInstances(Ljava/util/function/Consumer;)V"], at = [At("TAIL")])
    private fun collectDisplayInstances(consumer: Consumer<Instance>, callback: CallbackInfo) {
        displayElements.forEach { consumer.accept(it.instance) }
    }

    @Inject(method = ["_delete()V"], at = [At("TAIL")])
    private fun deleteDisplayInstances(callback: CallbackInfo) {
        displayElements.forEach { it.instance.delete() }
        displayElements.clear()
    }

    @Unique
    private fun rebuildElements() {
        val displays = AeroworksDeskAccess.renderedDisplays(blockEntity)
        val radarSurfaces = AeroworksDeskAccess.radarSurfaces(blockEntity)
        val gameTime = blockEntity.level?.gameTime ?: 0L
        val nextKey = buildString {
            displays.forEach {
                append(it.socket).append(':').append(it.text).append(':')
                    .append(it.pixels?.encode().orEmpty()).append(';')
            }
            append('|')
            radarSurfaces.forEach {
                append(RadarSurfaceRenderer.key(it, gameTime)).append(';')
            }
        }
        if (nextKey == displayKey) return
        displayKey = nextKey
        displayElements.forEach { it.instance.delete() }
        displayElements.clear()

        displays.forEach { display ->
            if (display.pixels != null) {
                for (y in 0 until display.pixels.height) for (x in 0 until display.pixels.width) {
                    if (!display.pixels.get(x, y)) continue
                    addElement(
                        socket = display.socket,
                        model = DeskDisplayModels.PIXEL,
                        x = DeskDisplayRenderer.pixelOffsetX(display.type, display.pixels.width, x),
                        z = DeskDisplayRenderer.pixelOffsetZ(display.pixels.height, y)
                    )
                }
            } else {
                val text = display.text.padEnd(display.type.width, ' ')
                repeat(display.type.width) { index ->
                    DeskDisplayModels.segments(text[index]).forEach { segment ->
                        addElement(
                            socket = display.socket,
                            model = segment.model,
                            x = DeskDisplayRenderer.digitOffset(display.type.width, index) + segment.x,
                            z = segment.z
                        )
                    }
                }
            }
        }

        radarSurfaces.forEach { surface ->
            RadarSurfaceRenderer.elements(surface, gameTime).forEach { element ->
                addElement(
                    socket = surface.socket,
                    model = element.model,
                    x = element.x,
                    z = element.z,
                    spinning = element.spinning
                )
            }
        }
    }

    @Unique
    private fun addElement(
        socket: Int,
        model: PartialModel,
        x: Double,
        z: Double,
        spinning: Boolean = false
    ) {
        val instance = instancerProvider()
            .instancer(InstanceTypes.TRANSFORMED, Models.partial(model))
            .createInstance()
        displayElements += CCAeroworksDisplayElement(socket, x, z, spinning, instance)
        relight(instance)
    }

    @Unique
    private fun applyTransforms() {
        val sockets = blockEntity.sockets()
        val rotation = ConsoleBlock.rotationFor(blockEntity.blockState)
        val gameTime = blockEntity.level?.gameTime ?: 0L
        displayElements.forEach { element ->
            val socket = sockets.getOrNull(element.socket) ?: return@forEach
            val instance = element.instance
            instance.setIdentityTransform()
                .translate(visualPosition)
                .translate(0.5f, 0.5f, 0.5f)
                .rotate(rotation)
                .translate(socket.offset().x - 0.5, socket.offset().y - 0.5, socket.offset().z - 0.5)
                .rotate(socket.orientation())
                .translate(-0.5f, 0.0f, -0.5f)

            if (element.spinning) {
                instance
                    .translate(0.5f, 0.0f, 0.5f)
                    .rotate(Axis.YP.rotationDegrees(RadarSurfaceRenderer.sweepAngle(gameTime)))
                    .translate(-0.5f, 0.0f, -0.5f)
            }
            instance
                .translate(element.x, 0.0, element.z)
                .setChanged()
        }
    }

    @Unique
    private data class CCAeroworksDisplayElement(
        val socket: Int,
        val x: Double,
        val z: Double,
        val spinning: Boolean,
        val instance: TransformedInstance
    )
}
