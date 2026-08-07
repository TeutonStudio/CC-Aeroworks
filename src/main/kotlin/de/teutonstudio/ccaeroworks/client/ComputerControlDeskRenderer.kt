package de.teutonstudio.ccaeroworks.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import com.mred231.aeroworks.content.controls.ConsoleRenderer
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.client.display.DeskDisplayRenderer
import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider

class ComputerControlDeskRenderer(
    context: BlockEntityRendererProvider.Context
) : BlockEntityRenderer<ComputerControlDeskBlockEntity> {
    private val delegate: BlockEntityRenderer<ConsoleBlockEntity>? = createDelegate(context)

    override fun render(
        blockEntity: ComputerControlDeskBlockEntity,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int
    ) {
        val aeroworksRenderer = delegate
        if (aeroworksRenderer != null) {
            // ConsoleRendererMixin appends the display layers to the native Aeroworks renderer.
            aeroworksRenderer.render(
                blockEntity,
                partialTick,
                poseStack,
                bufferSource,
                packedLight,
                packedOverlay
            )
            return
        }

        // Keep displays visible if an Aeroworks update changes the renderer constructor. The
        // static desk model still renders normally; only native animated controls need delegate.
        DeskDisplayRenderer.render(blockEntity, poseStack, bufferSource, packedLight)
    }

    @Suppress("UNCHECKED_CAST")
    private fun createDelegate(
        context: BlockEntityRendererProvider.Context
    ): BlockEntityRenderer<ConsoleBlockEntity>? {
        return runCatching {
            val constructor = ConsoleRenderer::class.java.declaredConstructors.firstOrNull {
                val parameters = it.parameterTypes
                parameters.isEmpty() ||
                    (parameters.size == 1 && parameters[0].isInstance(context))
            } ?: error(
                "No compatible ConsoleRenderer constructor; available=" +
                    ConsoleRenderer::class.java.declaredConstructors.joinToString { it.toGenericString() }
            )
            if (!constructor.trySetAccessible()) {
                error("Cannot access ConsoleRenderer constructor ${constructor.toGenericString()}")
            }
            val renderer = if (constructor.parameterCount == 0) {
                constructor.newInstance()
            } else {
                constructor.newInstance(context)
            }
            renderer as BlockEntityRenderer<ConsoleBlockEntity>
        }.onFailure {
            CCAeroworks.LOGGER.error(
                "[CC-Aeroworks] Could not create the Aeroworks console renderer for computer desks; using display-only fallback",
                it
            )
        }.getOrNull()
    }
}
