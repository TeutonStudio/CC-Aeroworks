package de.teutonstudio.ccaeroworks.mixin

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.compat.aeroworks.DeskIdentityAccess
import de.teutonstudio.ccaeroworks.registry.CCDataComponents
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentMap
import net.minecraft.nbt.CompoundTag
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import java.util.UUID

private const val NBT_KEY = "CCAeroworksDeskId"

@Mixin(value = [ConsoleBlockEntity::class], remap = false)
abstract class ConsoleBlockEntityDeskIdMixin : DeskIdentityAccess {
    @Unique
    private var ccaeroworks_deskId: UUID = UUID.randomUUID()

    override fun ccaeroworks_getDeskId(): UUID = ccaeroworks_deskId

    override fun ccaeroworks_setDeskId(id: UUID) {
        ccaeroworks_deskId = id
    }

    @Inject(method = ["write"], at = [At("TAIL")])
    private fun ccaeroworks_writeDeskId(
        tag: CompoundTag,
        registries: HolderLookup.Provider,
        clientPacket: Boolean,
        callback: CallbackInfo
    ) {
        tag.putUUID(NBT_KEY, ccaeroworks_deskId)
    }

    @Inject(method = ["read"], at = [At("TAIL")])
    private fun ccaeroworks_readDeskId(
        tag: CompoundTag,
        registries: HolderLookup.Provider,
        clientPacket: Boolean,
        callback: CallbackInfo
    ) {
        if (tag.hasUUID(NBT_KEY)) ccaeroworks_deskId = tag.getUUID(NBT_KEY)
    }

    @Inject(method = ["collectImplicitComponents"], at = [At("TAIL")])
    private fun ccaeroworks_collectDeskId(
        builder: DataComponentMap.Builder,
        callback: CallbackInfo
    ) {
        builder.set(CCDataComponents.DESK_ID.get(), ccaeroworks_deskId.toString())
    }
}
