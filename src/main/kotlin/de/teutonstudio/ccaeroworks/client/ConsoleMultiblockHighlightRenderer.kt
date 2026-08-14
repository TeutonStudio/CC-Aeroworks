package de.teutonstudio.ccaeroworks.client

import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes
import de.teutonstudio.ccaeroworks.compat.sable.SableClientRenderPose
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockSnapshot
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.RenderType
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.shapes.VoxelShape
import net.neoforged.neoforge.client.event.RenderHighlightEvent

/**
 * Replaces the normal single-block ControlDesk selection outline with the outer
 * outline of the complete, currently loaded ControlDesk multiblock.
 */
object ConsoleMultiblockHighlightRenderer {
    private data class CachedGeometry(
        val level: Level,
        val revision: Long,
        val anchor: BlockPos,
        val shape: VoxelShape
    )

    private var cachedGeometry: CachedGeometry? = null

    @JvmStatic
    fun render(event: RenderHighlightEvent.Block) {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        val targetPos = event.target.blockPos
        val targetState = level.getBlockState(targetPos)
        if (!AeroworksTypes.isControlDesk(targetState.block)) return

        val snapshot = ConsoleMultiblockManager.resolve(level, targetPos)
        if (snapshot.members.size <= 1) return
        if (snapshot.state == ConsoleNetworkState.PARTIALLY_LOADED ||
            snapshot.state == ConsoleNetworkState.TOO_LARGE
        ) {
            // The client cannot prove the complete outer boundary in these states.
            // Keeping Vanilla's single-block outline is less misleading than drawing
            // a truncated multiblock outline.
            return
        }

        val anchorMember = snapshot.memberAt(snapshot.anchor) ?: return
        val shape = geometry(level, snapshot)
        if (shape.isEmpty) return

        val poseStack = event.poseStack
        poseStack.pushPose()
        try {
            SableClientRenderPose.apply(
                poseStack,
                anchorMember.desk,
                snapshot.anchor.x.toDouble(),
                snapshot.anchor.y.toDouble(),
                snapshot.anchor.z.toDouble(),
                event.camera.position,
                event.deltaTracker.getGameTimeDeltaPartialTick(true)
            )

            val buffer = event.multiBufferSource.getBuffer(RenderType.lines())
            LevelRenderer.renderVoxelShape(
                poseStack,
                buffer,
                shape,
                0.0,
                0.0,
                0.0,
                0.0f,
                0.0f,
                0.0f,
                0.4f,
                false
            )
            event.isCanceled = true
        } finally {
            poseStack.popPose()
        }
    }

    private fun geometry(level: Level, snapshot: ConsoleMultiblockSnapshot): VoxelShape {
        cachedGeometry?.takeIf {
            it.level === level &&
                it.revision == snapshot.revision &&
                it.anchor == snapshot.anchor
        }?.let { return it.shape }

        val shape = ConsoleMultiblockHighlightGeometry.build(level, snapshot)
        cachedGeometry = CachedGeometry(level, snapshot.revision, snapshot.anchor, shape)
        return shape
    }
}
