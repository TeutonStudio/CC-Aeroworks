package de.teutonstudio.ccaeroworks.input

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level

data class CombinedLeverTarget(
    val dimension: ResourceKey<Level>,
    val pos: BlockPos,
    val socket: Int,
    val activationBinding: String,
    val axes: List<CombinedAxisTarget>,
    val baselineDX: Double,
    val baselineDY: Double
) {
    var baselinePending: Boolean = true
    var lastPacketNanos: Long = 0L
    var sequence: Int = 0
    var watchdogTicks: Int = 0
}

data class CombinedAxisTarget(
    val channel: String,
    val accumulator: LeverAccumulator,
    var sentValue: Int,
    var pendingValue: Int? = null
)
