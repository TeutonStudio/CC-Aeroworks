package de.teutonstudio.ccaeroworks.mixin

import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity
import de.teutonstudio.ccaeroworks.computer.channel.ChannelGroupBank
import de.teutonstudio.ccaeroworks.computer.channel.ChannelGroupOwnerAccess
import de.teutonstudio.ccaeroworks.registry.CCDataComponents
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentMap
import net.minecraft.nbt.CompoundTag
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(value = [ComputerControlDeskBlockEntity::class], remap = false)
abstract class ComputerControlDeskChannelGroupsMixin : ChannelGroupOwnerAccess {
    @Unique
    private var ccaeroworks_channelGroupBank: ChannelGroupBank? = null

    override fun ccaeroworks_channelGroups(): ChannelGroupBank =
        ccaeroworks_channelGroupBank ?: ChannelGroupBank(this as ComputerControlDeskBlockEntity).also {
            ccaeroworks_channelGroupBank = it
        }

    @Inject(method = ["write"], at = [At("TAIL")])
    private fun ccaeroworks_writeChannelGroups(
        tag: CompoundTag,
        registries: HolderLookup.Provider,
        clientPacket: Boolean,
        callback: CallbackInfo
    ) {
        val encoded = ccaeroworks_channelGroups().encodedDefinitions()
        if (encoded.isEmpty()) tag.remove("CCAeroworksChannelGroups")
        else tag.putString("CCAeroworksChannelGroups", encoded)
    }

    @Inject(method = ["read"], at = [At("TAIL")])
    private fun ccaeroworks_readChannelGroups(
        tag: CompoundTag,
        registries: HolderLookup.Provider,
        clientPacket: Boolean,
        callback: CallbackInfo
    ) {
        ccaeroworks_channelGroups().loadEncodedDefinitions(tag.getString("CCAeroworksChannelGroups").takeIf(String::isNotEmpty))
    }

    @Inject(method = ["collectImplicitComponents"], at = [At("TAIL")])
    private fun ccaeroworks_collectChannelGroups(builder: DataComponentMap.Builder, callback: CallbackInfo) {
        builder.set(
            CCDataComponents.CHANNEL_GROUPS.get(),
            ccaeroworks_channelGroups().encodedDefinitions().takeIf(String::isNotEmpty)
        )
    }
}
