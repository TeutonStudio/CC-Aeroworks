package de.teutonstudio.ccaeroworks.telemetry

import com.simibubi.create.api.behaviour.display.DisplayTarget
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats
import net.minecraft.network.chat.MutableComponent

class TelemetryDisplayTarget : DisplayTarget() {
    override fun acceptText(line: Int, text: List<MutableComponent>, context: DisplayLinkContext) {
        val target = context.getTargetBlockEntity() ?: return
        if (target.level?.isClientSide != false) return
        TelemetryRuntime.accept(target, context, text)
    }

    override fun provideStats(context: DisplayLinkContext): DisplayTargetStats =
        DisplayTargetStats(1, 64, this)
}
