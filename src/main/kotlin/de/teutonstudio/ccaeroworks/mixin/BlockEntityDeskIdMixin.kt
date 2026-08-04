package de.teutonstudio.ccaeroworks.mixin

import de.teutonstudio.ccaeroworks.compat.aeroworks.DeskIdentityAccess
import de.teutonstudio.ccaeroworks.registry.CCDataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import java.util.UUID

@Mixin(BlockEntity::class)
abstract class BlockEntityDeskIdMixin {
    @Inject(method = ["applyComponentsFromItemStack"], at = [At("TAIL")])
    private fun ccaeroworks_applyDeskId(stack: ItemStack, callback: CallbackInfo) {
        val desk = (this as Any) as? DeskIdentityAccess ?: return
        val raw = stack.get(CCDataComponents.DESK_ID.get()) ?: return
        val id = runCatching { UUID.fromString(raw) }.getOrNull() ?: return
        desk.ccaeroworks_setDeskId(id)
    }
}
