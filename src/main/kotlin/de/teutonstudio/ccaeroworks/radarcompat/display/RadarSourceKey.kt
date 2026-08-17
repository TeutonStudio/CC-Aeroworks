package de.teutonstudio.ccaeroworks.radarcompat.display

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.resources.ResourceLocation

data class RadarSourceKey(val dimension: ResourceLocation, val ingressPos: BlockPos) {
    val id: String get() = "$dimension@${ingressPos.asLong()}"

    fun toTag(): CompoundTag = CompoundTag().apply {
        putString("dimension", dimension.toString())
        put("ingressPos", NbtUtils.writeBlockPos(ingressPos))
    }

    companion object {
        fun fromTag(tag: CompoundTag): RadarSourceKey? {
            val dimension = ResourceLocation.tryParse(tag.getString("dimension")) ?: return null
            val ingressPos = NbtUtils.readBlockPos(tag, "ingressPos").orElse(null) ?: return null
            return RadarSourceKey(dimension, ingressPos.immutable())
        }
    }
}
