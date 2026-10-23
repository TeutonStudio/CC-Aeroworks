package de.teutonstudio.ccaeroworks.network

import com.simibubi.create.content.contraptions.AbstractContraptionEntity
import de.teutonstudio.ccaeroworks.CCAeroworks
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.WeakHashMap

data class MobileContraptionSnapshotPayload(
    val entityId: Int,
    val revision: Long,
    val entries: Map<BlockPos, CompoundTag>
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        const val MAX_ENTRIES = 128
        @JvmField val TYPE = CustomPacketPayload.Type<MobileContraptionSnapshotPayload>(
            CCAeroworks.id("mobile_contraption_snapshot")
        )
        @JvmField val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, MobileContraptionSnapshotPayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, MobileContraptionSnapshotPayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf): MobileContraptionSnapshotPayload {
                    val entityId = buffer.readVarInt()
                    val revision = buffer.readVarLong()
                    val count = buffer.readVarInt()
                    require(count in 0..MAX_ENTRIES) { "Invalid mobile snapshot entry count $count" }
                    val entries = linkedMapOf<BlockPos, CompoundTag>()
                    repeat(count) {
                        entries[buffer.readBlockPos()] = buffer.readNbt() ?: CompoundTag()
                    }
                    return MobileContraptionSnapshotPayload(entityId, revision, entries)
                }

                override fun encode(buffer: RegistryFriendlyByteBuf, payload: MobileContraptionSnapshotPayload) {
                    val entries = payload.entries.entries.take(MAX_ENTRIES)
                    buffer.writeVarInt(payload.entityId)
                    buffer.writeVarLong(payload.revision)
                    buffer.writeVarInt(entries.size)
                    entries.forEach { (pos, tag) ->
                        buffer.writeBlockPos(pos)
                        buffer.writeNbt(tag)
                    }
                }
            }

        @JvmStatic
        fun handle(payload: MobileContraptionSnapshotPayload, context: IPayloadContext) {
            val level = context.player().level()
            val entity = level.getEntity(payload.entityId) as? AbstractContraptionEntity ?: return
            synchronized(CLIENT_REVISIONS) {
                val revisions = CLIENT_REVISIONS.getOrPut(level) { hashMapOf() }
                if (payload.revision <= (revisions[payload.entityId] ?: Long.MIN_VALUE)) return
                revisions[payload.entityId] = payload.revision
            }
            val contraption = entity.contraption ?: return
            var changed = false
            payload.entries.forEach { (pos, tag) ->
                val previous = contraption.blocks[pos] ?: return@forEach
                contraption.blocks[pos] = StructureTemplate.StructureBlockInfo(pos, previous.state(), tag.copy())
                changed = true
            }
            if (changed) contraption.invalidateClientContraptionChildren()
        }

        private val CLIENT_REVISIONS = WeakHashMap<net.minecraft.world.level.Level, MutableMap<Int, Long>>()
    }
}
