package de.teutonstudio.ccaeroworks.client.display

import com.mred231.aeroworks.content.controls.ConsoleBlock
import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.compat.sable.SableClientRenderPose
import de.teutonstudio.ccaeroworks.display.DeskDisplayGeometry
import de.teutonstudio.ccaeroworks.input.DisplayCombinedInputController
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.RenderType
import net.neoforged.neoforge.client.event.RenderLevelStageEvent

object DisplayPointerRenderer {
    private const val HALF_WIDTH = 0.022
    private const val POINTER_HEIGHT = 0.18

    @JvmStatic
    fun renderLevel(event: RenderLevelStageEvent) {
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return
        val active = DisplayCombinedInputController.activeTarget() ?: return

        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        if (level.dimension() != active.dimension) return
        val desk = level.getBlockEntity(active.pos) as? ConsoleBlockEntity ?: return
        if (!DeskDisplayGeometry.isInteractiveDisplay(desk, active.socket)) return
        val socket = desk.sockets().getOrNull(active.socket) ?: return

        val poseStack = event.poseStack
        val buffers = minecraft.renderBuffers().bufferSource()
        val renderType = RenderType.debugFilledBox()
        val consumer = buffers.getBuffer(renderType)
        val partialTicks = event.partialTick.getGameTimeDeltaPartialTick(true)

        poseStack.pushPose()
        try {
            SableClientRenderPose.apply(
                poseStack,
                desk,
                desk.blockPos.x.toDouble(),
                desk.blockPos.y.toDouble(),
                desk.blockPos.z.toDouble(),
                event.camera.position,
                partialTicks
            )
            poseStack.translate(0.5, 0.5, 0.5)
            poseStack.mulPose(ConsoleBlock.rotationFor(desk.blockState))
            poseStack.translate(
                socket.offset().x - 0.5,
                socket.offset().y - 0.5,
                socket.offset().z - 0.5
            )
            poseStack.mulPose(socket.orientation())
            poseStack.translate(-0.5, 0.0, -0.5)

            val x = DeskDisplayGeometry.localX(active.u)
            val z = DeskDisplayGeometry.localZ(active.v)
            val baseY = DeskDisplayGeometry.SURFACE_Y + 0.006
            LevelRenderer.addChainedFilledBoxVertices(
                poseStack,
                consumer,
                x - HALF_WIDTH,
                baseY,
                z - HALF_WIDTH,
                x + HALF_WIDTH,
                baseY + POINTER_HEIGHT,
                z + HALF_WIDTH,
                0.82f,
                0.9f,
                1.0f,
                0.38f
            )
        } finally {
            poseStack.popPose()
        }
        buffers.endBatch(renderType)
    }
}
