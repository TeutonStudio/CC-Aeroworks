package de.teutonstudio.ccaeroworks.input

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level

data class CombinedLeverTarget(
    val dimension: ResourceKey<Level>,
    val pos: BlockPos,
    val socket: Int,
    val channel: String,
    val activationBinding: String,
    val accumulator: LeverAccumulator,
    var sentValue: Int,
    var pendingValue: Int? = null,
    var lastPacketNanos: Long = 0L
)
