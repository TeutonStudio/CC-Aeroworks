package de.teutonstudio.ccaeroworks.mixin

import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity
import de.teutonstudio.ccaeroworks.computer.channel.channelGroups
import de.teutonstudio.ccaeroworks.registry.CCDataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/** Restore persistent ComputerControlDesk channel groups from the placed item without naming BlockEntity.DataComponentInput. */
@Mixin(BlockEntity::class)
abstract class BlockEntityChannelGroupsMixin {
    @Inject(method = ["applyComponentsFromItemStack"], at = [At("TAIL")])
    private fun ccaeroworks_applyChannelGroupsFromItem(stack: ItemStack, callback: CallbackInfo) {
        val desk = this as? ComputerControlDeskBlockEntity ?: return
        desk.channelGroups().loadEncodedDefinitions(stack.get(CCDataComponents.CHANNEL_GROUPS.get()))
    }
}
