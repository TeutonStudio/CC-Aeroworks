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
    private val radarElements: MutableMap<String, CCAeroworksDisplayElement> = linkedMapOf()

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
        rebuildDisplayElements()
        reconcileRadarElements()
        applyTransforms()
    }

    @Inject(
        method = ["beginFrame(Ldev/engine_room/flywheel/api/visual/DynamicVisual\$Context;)V"],
        at = [At("TAIL")]
    )
    private fun beginFrame(context: DynamicVisual.Context, callback: CallbackInfo) {
        rebuildDisplayElements()
        reconcileRadarElements()
        applyTransforms()
    }

    @Inject(method = ["updateLight(F)V"], at = [At("TAIL")])
    private fun updateDisplayLight(partialTick: Float, callback: CallbackInfo) {
        displayElements.forEach { relight(it.instance) }
        radarElements.values.forEach { relight(it.instance) }
    }

    @Inject(method = ["collectCrumblingInstances(Ljava/util/function/Consumer;)V"], at = [At("TAIL")])
    private fun collectDisplayInstances(consumer: Consumer<Instance>, callback: CallbackInfo) {
        displayElements.forEach { consumer.accept(it.instance) }
        radarElements.values.forEach { consumer.accept(it.instance) }
    }

    @Inject(method = ["_delete()V"], at = [At("TAIL")])
    private fun deleteDisplayInstances(callback: CallbackInfo) {
        displayElements.forEach { it.instance.delete() }
        displayElements.clear()
        radarElements.values.forEach { it.instance.delete() }
        radarElements.clear()
    }

    @Unique
    private fun rebuildDisplayElements() {
        val displays = AeroworksDeskAccess.renderedDisplays(blockEntity)
        val nextKey = buildString {
            displays.forEach {
                append(it.socket).append(':').append(it.text).append(':')
                    .append(it.pixels?.encode().orEmpty()).append(';')
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
                    displayElements += createElement(
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
                        displayElements += createElement(
                            socket = display.socket,
                            model = segment.model,
                            x = DeskDisplayRenderer.digitOffset(display.type.width, index) + segment.x,
                            z = segment.z
                        )
                    }
                }
            }
        }
    }

    @Unique
    private fun reconcileRadarElements() {
        val gameTime = blockEntity.level?.gameTime ?: 0L
        val desiredKeys = mutableSetOf<String>()

        AeroworksDeskAccess.radarSurfaces(blockEntity).forEach { surface ->
            RadarSurfaceRenderer.elements(surface, gameTime).forEach { desired ->
                val key = "${surface.socket}:${surface.type}:${desired.key}"
                desiredKeys += key
                val existing = radarElements[key]
                if (existing == null || existing.model !== desired.model) {
                    existing?.instance?.delete()
                    radarElements[key] = createElement(
                        socket = surface.socket,
                        model = desired.model,
                        x = desired.x,
                        z = desired.z,
                        spinning = desired.spinning
                    )
                } else {
                    existing.x = desired.x
                    existing.z = desired.z
                    existing.spinning = desired.spinning
                }
            }
        }

        val iterator = radarElements.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key in desiredKeys) continue
            entry.value.instance.delete()
            iterator.remove()
        }
    }

    @Unique
    private fun createElement(
        socket: Int,
        model: PartialModel,
        x: Double,
        z: Double,
        spinning: Boolean = false
    ): CCAeroworksDisplayElement {
        val instance = instancerProvider()
            .instancer(InstanceTypes.TRANSFORMED, Models.partial(model))
            .createInstance()
        relight(instance)
        return CCAeroworksDisplayElement(socket, model, x, z, spinning, instance)
    }

    @Unique
    private fun applyTransforms() {
        val gameTime = blockEntity.level?.gameTime ?: 0L
        displayElements.forEach { applyTransform(it, gameTime) }
        radarElements.values.forEach { applyTransform(it, gameTime) }
    }

    @Unique
    private fun applyTransform(element: CCAeroworksDisplayElement, gameTime: Long) {
        val socket = blockEntity.sockets().getOrNull(element.socket) ?: return
        val rotation = ConsoleBlock.rotationFor(blockEntity.blockState)
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

    @Unique
    private data class CCAeroworksDisplayElement(
        val socket: Int,
        val model: PartialModel,
        var x: Double,
        var z: Double,
        var spinning: Boolean,
        val instance: TransformedInstance
    )
}
