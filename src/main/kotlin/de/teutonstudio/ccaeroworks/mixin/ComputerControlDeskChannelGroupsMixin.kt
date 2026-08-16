package de.teutonstudio.ccaeroworks.mixin

import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity
import de.teutonstudio.ccaeroworks.computer.channel.ChannelGroupBank
import de.teutonstudio.ccaeroworks.computer.channel.ChannelGroupOwnerAccess
import de.teutonstudio.ccaeroworks.computer.channel.ChannelPathBank
import de.teutonstudio.ccaeroworks.computer.channel.ChannelPathOwnerAccess
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
abstract class ComputerControlDeskChannelGroupsMixin : ChannelGroupOwnerAccess, ChannelPathOwnerAccess {
    @Unique private var ccaeroworks_channelGroupBank: ChannelGroupBank? = null
    @Unique private var ccaeroworks_channelPathBank: ChannelPathBank? = null

    override fun ccaeroworks_channelGroups(): ChannelGroupBank =
        ccaeroworks_channelGroupBank ?: ChannelGroupBank(this as ComputerControlDeskBlockEntity).also { ccaeroworks_channelGroupBank = it }

    override fun ccaeroworks_channelPaths(): ChannelPathBank =
        ccaeroworks_channelPathBank ?: ChannelPathBank(this as ComputerControlDeskBlockEntity).also { ccaeroworks_channelPathBank = it }

    @Inject(method = ["write"], at = [At("TAIL")])
    private fun ccaeroworks_writeChannelMetadata(
        tag: CompoundTag,
        registries: HolderLookup.Provider,
        clientPacket: Boolean,
        callback: CallbackInfo
    ) {
        val groups = ccaeroworks_channelGroups().encodedDefinitions()
        if (groups.isEmpty()) tag.remove("CCAeroworksChannelGroups") else tag.putString("CCAeroworksChannelGroups", groups)
        val paths = ccaeroworks_channelPaths().encodedDefinitions()
        if (paths.isEmpty()) tag.remove("CCAeroworksChannelPaths") else tag.putString("CCAeroworksChannelPaths", paths)
    }

    @Inject(method = ["read"], at = [At("TAIL")])
    private fun ccaeroworks_readChannelMetadata(
        tag: CompoundTag,
        registries: HolderLookup.Provider,
        clientPacket: Boolean,
        callback: CallbackInfo
    ) {
        ccaeroworks_channelGroups().loadEncodedDefinitions(tag.getString("CCAeroworksChannelGroups").takeIf(String::isNotEmpty))
        ccaeroworks_channelPaths().loadEncodedDefinitions(tag.getString("CCAeroworksChannelPaths").takeIf(String::isNotEmpty))
    }

    @Inject(method = ["collectImplicitComponents"], at = [At("TAIL")])
    private fun ccaeroworks_collectChannelMetadata(builder: DataComponentMap.Builder, callback: CallbackInfo) {
        builder.set(CCDataComponents.CHANNEL_GROUPS.get(), ccaeroworks_channelGroups().encodedDefinitions().takeIf(String::isNotEmpty))
        builder.set(CCDataComponents.CHANNEL_PATHS.get(), ccaeroworks_channelPaths().encodedDefinitions().takeIf(String::isNotEmpty))
    }
}
