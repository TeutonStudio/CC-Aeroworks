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
    var mouseBaselineX: Double,
    var mouseBaselineY: Double,
    var subtractMouseBaseline: Boolean = true
)

data class CombinedAxisTarget(
    val channel: String,
    val mouseAxis: CombinedInputSource.MouseAxis,
    val accumulator: LeverAccumulator,
    var sentValue: Int,
    var pendingValue: Int? = null,
    var lastPacketNanos: Long = 0L
)
