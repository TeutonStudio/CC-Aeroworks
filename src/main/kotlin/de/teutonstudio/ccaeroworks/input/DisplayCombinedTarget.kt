package de.teutonstudio.ccaeroworks.input

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level

data class DisplayCombinedTarget(
    val dimension: ResourceKey<Level>,
    val pos: BlockPos,
    val socket: Int,
    val xBinding: String?,
    val yBinding: String?,
    val heldBindings: MutableSet<String>,
    var u: Double,
    var v: Double,
    val baselineDX: Double,
    val baselineDY: Double
) {
    var baselinePending: Boolean = true
    var watchdogTicks: Int = 0

    var drawActive: Boolean = false
    var drawGestureId: Long = 0L
    var drawSequence: Int = 0
    var drawLastSentU: Double = 0.0
    var drawLastSentV: Double = 0.0
    var drawDirty: Boolean = false

    fun xActive(): Boolean = xBinding != null && xBinding in heldBindings
    fun yActive(): Boolean = yBinding != null && yBinding in heldBindings
}
