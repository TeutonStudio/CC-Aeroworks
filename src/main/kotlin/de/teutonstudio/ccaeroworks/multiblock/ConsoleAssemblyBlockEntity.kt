package de.teutonstudio.ccaeroworks.multiblock

import com.mred231.aeroworks.content.controls.console.DeskSkin
import de.teutonstudio.ccaeroworks.registry.CCBlockEntities
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.Connection
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.client.model.data.ModelData
import java.util.UUID

/** Non-ticking persistence carrier added to Create's otherwise stateless train controls. */
class ConsoleAssemblyBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(CCBlockEntities.CONSOLE_ASSEMBLY_SEGMENT.get(), pos, state),
    ConsoleAssemblyStateAccess,
    ConsoleAssemblyStyleAccess {
    private var assemblyId: UUID? = null
    private var legacy = false
    private val assemblySkin = DeskSkin()
    private var styleRevision = 0L

    override fun ccaeroworks_getAssemblyId(): UUID? = assemblyId

    override fun ccaeroworks_setAssemblyId(id: UUID?) {
        if (assemblyId == id) return
        assemblyId = id
        setChanged()
        level?.sendBlockUpdated(blockPos, blockState, blockState, 3)
    }

    override fun ccaeroworks_isLegacyAssembly(): Boolean = legacy

    override fun ccaeroworks_setLegacyAssembly(legacy: Boolean) {
        this.legacy = legacy
        setChanged()
    }

    override fun ccaeroworks_getAssemblySkin(): DeskSkin = assemblySkin

    override fun ccaeroworks_getStyleRevision(): Long = styleRevision

    override fun ccaeroworks_setStyleRevision(revision: Long) {
        if (styleRevision == revision) return
        styleRevision = revision
        setChanged()
    }

    override fun getModelData(): ModelData {
        if (assemblySkin.isEmpty) return ModelData.EMPTY
        val builder = ModelData.builder()
        DeskSkin.Zone.entries.forEach { zone ->
            assemblySkin.material(zone)?.let { builder.with(zone.property(), it) }
        }
        return builder.build()
    }

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        assemblyId?.let { tag.putUUID(NBT_ASSEMBLY_ID, it) }
        tag.putBoolean(NBT_LEGACY, legacy)
        tag.putString(NBT_PROFILE, blockState.blockHolder.unwrapKey().map { it.location().toString() }.orElse("unknown"))
        tag.putLong(NBT_STYLE_REVISION, styleRevision)
        assemblySkin.write(tag, registries)
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        assemblyId = if (tag.hasUUID(NBT_ASSEMBLY_ID)) tag.getUUID(NBT_ASSEMBLY_ID) else null
        legacy = tag.getBoolean(NBT_LEGACY)
        styleRevision = tag.getLong(NBT_STYLE_REVISION)
        assemblySkin.read(tag, registries.lookupOrThrow(Registries.BLOCK), registries)
    }

    override fun getUpdatePacket(): ClientboundBlockEntityDataPacket =
        ClientboundBlockEntityDataPacket.create(this)

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag =
        saveWithoutMetadata(registries)

    override fun onDataPacket(
        net: Connection,
        pkt: ClientboundBlockEntityDataPacket,
        lookupProvider: HolderLookup.Provider
    ) {
        pkt.tag?.let { loadWithComponents(it, lookupProvider) }
    }

    companion object {
        const val NBT_ASSEMBLY_ID = "CCAeroworksAssemblyId"
        const val NBT_LEGACY = "CCAeroworksAssemblyLegacy"
        const val NBT_PROFILE = "CCAeroworksAssemblyProfile"
        const val NBT_STYLE_REVISION = "CCAeroworksStyleRevision"
    }
}
