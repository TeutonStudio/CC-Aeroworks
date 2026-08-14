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
    var u: Double,
    var v: Double,
    var mouseBaselineX: Double,
    var mouseBaselineY: Double,
    var subtractMouseBaseline: Boolean = true
)
