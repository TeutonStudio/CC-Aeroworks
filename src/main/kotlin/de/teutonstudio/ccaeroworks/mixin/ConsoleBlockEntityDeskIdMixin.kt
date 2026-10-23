package de.teutonstudio.ccaeroworks.mixin

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import com.mred231.aeroworks.content.controls.console.DeskSkin
import de.teutonstudio.ccaeroworks.compat.aeroworks.DeskIdentityAccess
import de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyBlockEntity
import de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyStateAccess
import de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyStyleAccess
import de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyStyles
import de.teutonstudio.ccaeroworks.registry.CCDataComponents
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentMap
import net.minecraft.nbt.CompoundTag
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.entity.BlockEntity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import java.util.UUID

private const val NBT_KEY = "CCAeroworksDeskId"

@Mixin(value = [ConsoleBlockEntity::class], remap = false)
abstract class ConsoleBlockEntityDeskIdMixin : DeskIdentityAccess, ConsoleAssemblyStateAccess, ConsoleAssemblyStyleAccess {
    @Unique
    private var ccaeroworks_deskId: UUID = UUID.randomUUID()

    @Unique
    private var ccaeroworks_assemblyId: UUID? = null

    @Unique
    private var ccaeroworks_legacyAssembly: Boolean = false

    @Unique
    private var ccaeroworks_styleRevision: Long = 0

    override fun ccaeroworks_getDeskId(): UUID = ccaeroworks_deskId

    override fun ccaeroworks_setDeskId(id: UUID) {
        ccaeroworks_deskId = id
    }

    override fun ccaeroworks_getAssemblyId(): UUID? = ccaeroworks_assemblyId

    override fun ccaeroworks_setAssemblyId(id: UUID?) {
        ccaeroworks_assemblyId = id
    }

    override fun ccaeroworks_isLegacyAssembly(): Boolean = ccaeroworks_legacyAssembly

    override fun ccaeroworks_setLegacyAssembly(legacy: Boolean) {
        ccaeroworks_legacyAssembly = legacy
    }

    override fun ccaeroworks_getAssemblySkin(): DeskSkin =
        (this as ConsoleBlockEntity).deskSkin()

    override fun ccaeroworks_getStyleRevision(): Long = ccaeroworks_styleRevision

    override fun ccaeroworks_setStyleRevision(revision: Long) {
        ccaeroworks_styleRevision = revision
    }

    @Inject(method = ["write"], at = [At("TAIL")])
    private fun ccaeroworks_writeDeskId(
        tag: CompoundTag,
        registries: HolderLookup.Provider,
        clientPacket: Boolean,
        callback: CallbackInfo
    ) {
        tag.putUUID(NBT_KEY, ccaeroworks_deskId)
        ccaeroworks_assemblyId?.let { tag.putUUID(ConsoleAssemblyBlockEntity.NBT_ASSEMBLY_ID, it) }
        tag.putBoolean(ConsoleAssemblyBlockEntity.NBT_LEGACY, ccaeroworks_legacyAssembly)
        tag.putLong(ConsoleAssemblyBlockEntity.NBT_STYLE_REVISION, ccaeroworks_styleRevision)
        tag.putString(
            ConsoleAssemblyBlockEntity.NBT_PROFILE,
            BuiltInRegistries.BLOCK.getKey((this as BlockEntity).blockState.block).toString()
        )
    }

    @Inject(method = ["read"], at = [At("TAIL")])
    private fun ccaeroworks_readDeskId(
        tag: CompoundTag,
        registries: HolderLookup.Provider,
        clientPacket: Boolean,
        callback: CallbackInfo
    ) {
        if (tag.hasUUID(NBT_KEY)) ccaeroworks_deskId = tag.getUUID(NBT_KEY)
        ccaeroworks_assemblyId = if (tag.hasUUID(ConsoleAssemblyBlockEntity.NBT_ASSEMBLY_ID)) {
            tag.getUUID(ConsoleAssemblyBlockEntity.NBT_ASSEMBLY_ID)
        } else null
        ccaeroworks_legacyAssembly = tag.getBoolean(ConsoleAssemblyBlockEntity.NBT_LEGACY)
        ccaeroworks_styleRevision = tag.getLong(ConsoleAssemblyBlockEntity.NBT_STYLE_REVISION)
    }

    @Inject(method = ["onSkinChanged"], at = [At("TAIL")])
    private fun ccaeroworks_propagateAssemblySkin(callback: CallbackInfo) {
        ConsoleAssemblyStyles.onNativeSkinChanged(this as ConsoleBlockEntity)
    }

    @Inject(method = ["collectImplicitComponents"], at = [At("TAIL")])
    private fun ccaeroworks_collectDeskId(
        builder: DataComponentMap.Builder,
        callback: CallbackInfo
    ) {
        builder.set(CCDataComponents.DESK_ID.get(), ccaeroworks_deskId.toString())
    }
}
