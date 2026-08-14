package de.teutonstudio.ccaeroworks.display

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import com.simibubi.create.api.behaviour.display.DisplayTarget
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskAccess
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockDisplayBounds
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.phys.AABB

class DeskDisplayTarget : DisplayTarget() {
    override fun acceptText(line: Int, text: List<MutableComponent>, context: DisplayLinkContext) {
        val desk = context.getTargetBlockEntity() as? ConsoleBlockEntity ?: return
        if (desk.level?.isClientSide != false) return
        val displays = AeroworksDeskAccess.displays(desk)
        text.forEachIndexed { offset, component ->
            displays.getOrNull(line + offset)?.let { display ->
                // A script_source owns automatic content production. Manual Desk API writes remain
                // available because the configured Lua controller renders through that same API.
                if (DisplayBindings.get(desk, display.socket).content is DisplayContentSource.ScriptSource) {
                    return@let
                }
                AeroworksDeskAccess.setDisplayText(desk, display.socket, component.string)
            }
        }
        reserve(line, desk, context)
    }

    override fun provideStats(context: DisplayLinkContext): DisplayTargetStats {
        val desk = context.getTargetBlockEntity() as? ConsoleBlockEntity
        val rows = desk?.let(AeroworksDeskAccess::displays)?.size?.coerceAtLeast(1) ?: 1
        return DisplayTargetStats(rows, 3, this)
    }

    override fun getMultiblockBounds(level: LevelAccessor, pos: BlockPos): AABB =
        ConsoleMultiblockDisplayBounds.resolve(level, pos) ?: super.getMultiblockBounds(level, pos)
}
