package de.teutonstudio.ccaeroworks.client.display

import com.mred231.aeroworks.content.controls.ConsoleBlock
import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.compat.sable.SableClientRenderPose
import de.teutonstudio.ccaeroworks.display.DeskDisplayGeometry
import de.teutonstudio.ccaeroworks.input.CombinedInputCoordinator
import de.teutonstudio.ccaeroworks.input.DisplayCombinedInputController
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.RenderType
import net.neoforged.neoforge.client.event.RenderLevelStageEvent

/**
 * Lightweight pseudo-finger for Combined display interaction.
 *
 * Position is deliberately unsmoothed: visual coordinates always match the input state from the
 * latest mouse sample. Only the shape is stylised so the contact point is easier to read.
 */
object DisplayPointerRenderer {
    private const val CONTACT_HALF_WIDTH = 0.034
    private const val FINGER_HALF_WIDTH = 0.018
    private const val CONTACT_HEIGHT = 0.006
    private const val FINGER_HEIGHT = 0.105

    @JvmStatic
    fun renderLevel(event: RenderLevelStageEvent) {
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return
        val active = DisplayCombinedInputController.activeTarget() ?: return

        val minecraft = Minecraft.getInstance()
        if (CombinedInputCoordinator.isShiftCameraOnly(minecraft)) return
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
            val baseY = DeskDisplayGeometry.SURFACE_Y + 0.004

            LevelRenderer.addChainedFilledBoxVertices(
                poseStack,
                consumer,
                x - CONTACT_HALF_WIDTH,
                baseY,
                z - CONTACT_HALF_WIDTH,
                x + CONTACT_HALF_WIDTH,
                baseY + CONTACT_HEIGHT,
                z + CONTACT_HALF_WIDTH,
                0.72f,
                0.86f,
                1.0f,
                0.28f
            )

            LevelRenderer.addChainedFilledBoxVertices(
                poseStack,
                consumer,
                x - FINGER_HALF_WIDTH,
                baseY + CONTACT_HEIGHT,
                z - FINGER_HALF_WIDTH,
                x + FINGER_HALF_WIDTH,
                baseY + FINGER_HEIGHT,
                z + FINGER_HALF_WIDTH,
                0.82f,
                0.9f,
                1.0f,
                0.42f
            )
        } finally {
            poseStack.popPose()
        }
        buffers.endBatch(renderType)
    }
}
