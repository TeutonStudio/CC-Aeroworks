package de.teutonstudio.ccaeroworks.mixin

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.compat.createradar.RadarDeskStateAccess
import de.teutonstudio.ccaeroworks.display.RadarDisplaySnapshot
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

private const val RADAR_NBT_KEY = "CCAeroworksRadarDisplay"

@Mixin(value = [ConsoleBlockEntity::class], remap = false)
abstract class ConsoleBlockEntityRadarMixin : RadarDeskStateAccess {
    @Unique
    private var ccaeroworks_radarSnapshot: RadarDisplaySnapshot? = null

    override fun ccaeroworks_getRadarSnapshot(): RadarDisplaySnapshot? = ccaeroworks_radarSnapshot

    override fun ccaeroworks_setRadarSnapshot(snapshot: RadarDisplaySnapshot?) {
        ccaeroworks_radarSnapshot = snapshot
    }

    @Inject(method = ["write"], at = [At("TAIL")])
    private fun ccaeroworks_writeRadarSnapshot(
        tag: CompoundTag,
        registries: HolderLookup.Provider,
        clientPacket: Boolean,
        callback: CallbackInfo
    ) {
        if (clientPacket) ccaeroworks_radarSnapshot?.let { tag.put(RADAR_NBT_KEY, it.toTag()) }
    }

    @Inject(method = ["read"], at = [At("TAIL")])
    private fun ccaeroworks_readRadarSnapshot(
        tag: CompoundTag,
        registries: HolderLookup.Provider,
        clientPacket: Boolean,
        callback: CallbackInfo
    ) {
        if (!clientPacket) return
        ccaeroworks_radarSnapshot = if (tag.contains(RADAR_NBT_KEY, Tag.TAG_COMPOUND)) {
            RadarDisplaySnapshot.fromTag(tag.getCompound(RADAR_NBT_KEY))
        } else {
            null
        }
    }
}
