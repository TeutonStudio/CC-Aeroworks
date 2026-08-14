package de.teutonstudio.ccaeroworks.telemetry

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import com.simibubi.create.api.behaviour.display.DisplayTarget
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockDisplayBounds
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.phys.AABB

class TelemetryDisplayTarget : DisplayTarget() {
    override fun acceptText(line: Int, text: List<MutableComponent>, context: DisplayLinkContext) {
        val target = context.getTargetBlockEntity() ?: return
        if (target.level?.isClientSide != false) return
        TelemetryRuntime.accept(target, context, text)
    }

    override fun provideStats(context: DisplayLinkContext): DisplayTargetStats =
        DisplayTargetStats(1, 64, this)

    override fun getMultiblockBounds(level: LevelAccessor, pos: BlockPos): AABB {
        if (level.getBlockEntity(pos) !is ConsoleBlockEntity) {
            return super.getMultiblockBounds(level, pos)
        }
        return ConsoleMultiblockDisplayBounds.resolve(level, pos) ?: super.getMultiblockBounds(level, pos)
    }
}
