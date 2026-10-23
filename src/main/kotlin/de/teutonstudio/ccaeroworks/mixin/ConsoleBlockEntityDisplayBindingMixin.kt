package de.teutonstudio.ccaeroworks.mixin

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.display.DisplayBinding
import de.teutonstudio.ccaeroworks.display.DisplayBindingStateAccess
import de.teutonstudio.ccaeroworks.display.DisplayColorStateAccess
import de.teutonstudio.ccaeroworks.display.DisplayResolutionStateAccess
import de.teutonstudio.ccaeroworks.network.DisplayDrawPayload
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
abstract class ConsoleBlockEntityDisplayBindingMixin : DisplayBindingStateAccess, DisplayResolutionStateAccess, DisplayColorStateAccess {
    @Unique
    private val ccaeroworks_displayPpb = linkedMapOf<Int, Int>()

    override fun ccaeroworks_getDisplayPpb(socket: Int): Int? = ccaeroworks_displayPpb[socket]

    override fun ccaeroworks_setDisplayPpb(socket: Int, ppb: Int) {
        require(ppb in 16..1024)
        val desk = this as ConsoleBlockEntity
        if (socket !in 0 until desk.socketCount()) return
        if (ccaeroworks_displayPpb.put(socket, ppb) == ppb) return
        desk.setChanged()
        desk.notifyUpdate()
    }

    @Unique
    private val ccaeroworks_displayColors = linkedMapOf<Int, Int>()

    override fun ccaeroworks_getDisplayColor(socket: Int): Int? = ccaeroworks_displayColors[socket]

    override fun ccaeroworks_setDisplayColor(socket: Int, color: Int) {
        require(color in 0..0xFFFFFF)
        val desk = this as ConsoleBlockEntity
        if (socket !in 0 until desk.socketCount()) return
        if (ccaeroworks_displayColors.put(socket, color) == color) return
        desk.setChanged()
        desk.notifyUpdate()
    }

    @Unique
    private val ccaeroworks_displayBindings = linkedMapOf<Int, DisplayBinding>()

    override fun ccaeroworks_getDisplayBindings(): Map<Int, DisplayBinding> =
        ccaeroworks_displayBindings.toMap()

    override fun ccaeroworks_setDisplayBinding(socket: Int, binding: DisplayBinding) {
        val desk = this as ConsoleBlockEntity
        if (socket !in 0 until desk.socketCount()) return

        val changed = if (binding == DisplayBinding.Default) {
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
        val resolutions = CompoundTag()
        ccaeroworks_displayPpb.forEach { (socket, ppb) -> resolutions.putInt(socket.toString(), ppb) }
        tag.put("CCAeroworksDisplayPpb", resolutions)
        val colors = CompoundTag()
        ccaeroworks_displayColors.forEach { (socket, color) -> colors.putInt(socket.toString(), color) }
        tag.put("CCAeroworksDisplayColors", colors)
        if (ccaeroworks_displayBindings.isEmpty()) {
            tag.remove(DISPLAY_BINDINGS_NBT_KEY)
            return
        }

        val entries = ListTag()
        ccaeroworks_displayBindings.forEach { (socket, binding) ->
            if (binding == DisplayBinding.Default) return@forEach
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
        ccaeroworks_displayPpb.clear()
        val resolutions = tag.getCompound("CCAeroworksDisplayPpb")
        val host = this as ConsoleBlockEntity
        resolutions.allKeys.forEach { key ->
            val socket = key.toIntOrNull() ?: return@forEach
            val ppb = resolutions.getInt(key)
            if (socket in 0 until host.socketCount() && ppb in 16..1024) ccaeroworks_displayPpb[socket] = ppb
        }
        ccaeroworks_displayColors.clear()
        val colors = tag.getCompound("CCAeroworksDisplayColors")
        colors.allKeys.forEach { key ->
            val socket = key.toIntOrNull() ?: return@forEach
            val color = colors.getInt(key)
            if (socket in 0 until host.socketCount() && color in 0..0xFFFFFF) ccaeroworks_displayColors[socket] = color
        }
        ccaeroworks_displayBindings.clear()
        if (!tag.contains(DISPLAY_BINDINGS_NBT_KEY, Tag.TAG_LIST.toInt())) return

        val desk = this as ConsoleBlockEntity
        val entries = tag.getList(DISPLAY_BINDINGS_NBT_KEY, Tag.TAG_COMPOUND.toInt())
        for (index in 0 until entries.size) {
            val entry = entries.getCompound(index)
            val socket = entry.getInt("socket")
            if (socket !in 0 until desk.socketCount()) continue
            val binding = DisplayBinding.fromTag(entry.getCompound("binding")) ?: continue
            if (binding != DisplayBinding.Default) ccaeroworks_displayBindings[socket] = binding
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
        DisplayDrawPayload.invalidateDisplay(this as ConsoleBlockEntity, socket)
        ccaeroworks_displayPpb.remove(socket)
        ccaeroworks_displayColors.remove(socket)
        ccaeroworks_displayBindings.remove(socket)
    }
}
