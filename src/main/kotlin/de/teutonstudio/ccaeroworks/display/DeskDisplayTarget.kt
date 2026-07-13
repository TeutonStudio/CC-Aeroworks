package de.teutonstudio.ccaeroworks.display

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import com.simibubi.create.api.behaviour.display.DisplayTarget
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskAccess
import net.minecraft.network.chat.MutableComponent

class DeskDisplayTarget : DisplayTarget() {
    override fun acceptText(line: Int, text: List<MutableComponent>, context: DisplayLinkContext) {
        val desk = context.getTargetBlockEntity() as? ConsoleBlockEntity ?: return
        if (desk.level?.isClientSide != false) return
        val displays = AeroworksDeskAccess.displays(desk)
        text.forEachIndexed { offset, component ->
            displays.getOrNull(line + offset)?.let { display ->
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
}
