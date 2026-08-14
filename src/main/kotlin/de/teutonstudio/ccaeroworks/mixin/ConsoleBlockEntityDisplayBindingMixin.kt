package de.teutonstudio.ccaeroworks.mixin

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.display.DisplayBinding
import de.teutonstudio.ccaeroworks.display.DisplayBindingStateAccess
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.world.item.ItemStack
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

private const val DISPLAY_BINDINGS_NBT_KEY = "CCAeroworksDisplayBindings"

@Mixin(value = [ConsoleBlockEntity::class], remap = false)
abstract class ConsoleBlockEntityDisplayBindingMixin : DisplayBindingStateAccess {
    @Unique
    private val ccaeroworks_displayBindings = linkedMapOf<Int, DisplayBinding>()

    override fun ccaeroworks_getDisplayBindings(): Map<Int, DisplayBinding> =
        ccaeroworks_displayBindings.toMap()

    override fun ccaeroworks_setDisplayBinding(socket: Int, binding: DisplayBinding) {
        val desk = this as ConsoleBlockEntity
        if (socket !in 0 until desk.socketCount()) return

        val changed = if (binding.isDefault) {
            ccaeroworks_displayBindings.remove(socket) != null
        } else {
            ccaeroworks_displayBindings.put(socket, binding) != binding
        }
        if (!changed) return

        desk.setChanged()
        desk.notifyUpdate()
    }

    @Inject(method = ["write"], at = [At("TAIL")])
    private fun ccaeroworks_writeDisplayBindings(
        tag: CompoundTag,
        registries: HolderLookup.Provider,
        clientPacket: Boolean,
        callback: CallbackInfo
    ) {
        if (ccaeroworks_displayBindings.isEmpty()) {
            tag.remove(DISPLAY_BINDINGS_NBT_KEY)
            return
        }

        val entries = ListTag()
        ccaeroworks_displayBindings.forEach { (socket, binding) ->
            if (binding.isDefault) return@forEach
            entries.add(CompoundTag().apply {
                putInt("socket", socket)
                put("binding", binding.toTag())
            })
        }
        if (entries.isEmpty()) tag.remove(DISPLAY_BINDINGS_NBT_KEY)
        else tag.put(DISPLAY_BINDINGS_NBT_KEY, entries)
    }

    @Inject(method = ["read"], at = [At("TAIL")])
    private fun ccaeroworks_readDisplayBindings(
        tag: CompoundTag,
        registries: HolderLookup.Provider,
        clientPacket: Boolean,
        callback: CallbackInfo
    ) {
        ccaeroworks_displayBindings.clear()
        if (!tag.contains(DISPLAY_BINDINGS_NBT_KEY, Tag.TAG_LIST.toInt())) return

        val desk = this as ConsoleBlockEntity
        val entries = tag.getList(DISPLAY_BINDINGS_NBT_KEY, Tag.TAG_COMPOUND.toInt())
        for (index in 0 until entries.size) {
            val entry = entries.getCompound(index)
            val socket = entry.getInt("socket")
            if (socket !in 0 until desk.socketCount()) continue
            val binding = DisplayBinding.fromTag(entry.getCompound("binding")) ?: continue
            if (!binding.isDefault) ccaeroworks_displayBindings[socket] = binding
        }
    }

    @Inject(
        method = ["dismount(I)Lnet/minecraft/world/item/ItemStack;"],
        at = [At("HEAD")]
    )
    private fun ccaeroworks_clearBindingOnDismount(
        socket: Int,
        callback: CallbackInfoReturnable<ItemStack>
    ) {
        ccaeroworks_displayBindings.remove(socket)
    }
}
