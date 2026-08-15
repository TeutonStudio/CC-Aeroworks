package de.teutonstudio.ccaeroworks.network

import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.computer.ControlDeskUiSwitchState
import de.teutonstudio.ccaeroworks.computer.source.InformationSourceKind
import de.teutonstudio.ccaeroworks.computer.source.InformationSourceSnapshot
import de.teutonstudio.ccaeroworks.computer.source.InformationSourceSnapshotBuilder
import de.teutonstudio.ccaeroworks.computer.source.InformationSourceSnapshotState
import de.teutonstudio.ccaeroworks.computer.source.InformationSourceView
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext

class RequestInformationSourceSnapshotPayload : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE = CustomPacketPayload.Type<RequestInformationSourceSnapshotPayload>(
            CCAeroworks.id("request_information_source_snapshot")
        )

        @JvmField
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, RequestInformationSourceSnapshotPayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, RequestInformationSourceSnapshotPayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf) = RequestInformationSourceSnapshotPayload()
                override fun encode(buffer: RegistryFriendlyByteBuf, payload: RequestInformationSourceSnapshotPayload) = Unit
            }

        @JvmStatic
        fun handle(payload: RequestInformationSourceSnapshotPayload, context: IPayloadContext) {
            val player = context.player() as? ServerPlayer ?: return
            val owner = ControlDeskUiSwitchState.activeComputerDesk(player) ?: return
            PacketDistributor.sendToPlayer(
                player,
                InformationSourceSnapshotPayload(InformationSourceSnapshotBuilder.build(owner))
            )
        }
    }
}

data class InformationSourceSnapshotPayload(
    val snapshot: InformationSourceSnapshot
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE = CustomPacketPayload.Type<InformationSourceSnapshotPayload>(
            CCAeroworks.id("information_source_snapshot")
        )

        @JvmField
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, InformationSourceSnapshotPayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, InformationSourceSnapshotPayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf): InformationSourceSnapshotPayload {
                    val count = buffer.readVarInt()
                    require(count in 0..256) { "Invalid information source count: $count" }
                    val sources = ArrayList<InformationSourceView>(count)
                    repeat(count) {
                        val kindOrdinal = buffer.readVarInt()
                        val kind = InformationSourceKind.entries.getOrNull(kindOrdinal)
                            ?: throw IllegalArgumentException("Invalid information source kind: $kindOrdinal")
                        sources += InformationSourceView(
                            id = buffer.readUtf(256),
                            kind = kind,
                            label = buffer.readUtf(128),
                            status = buffer.readUtf(32),
                            x = buffer.readInt(),
                            y = buffer.readInt(),
                            z = buffer.readInt(),
                            side = buffer.readUtf(16),
                            details = buffer.readUtf(160)
                        )
                    }
                    return InformationSourceSnapshotPayload(InformationSourceSnapshot(sources))
                }

                override fun encode(buffer: RegistryFriendlyByteBuf, payload: InformationSourceSnapshotPayload) {
                    val sources = payload.snapshot.sources.take(256)
                    buffer.writeVarInt(sources.size)
                    sources.forEach { source ->
                        buffer.writeVarInt(source.kind.ordinal)
                        buffer.writeUtf(source.id, 256)
                        buffer.writeUtf(source.label, 128)
                        buffer.writeUtf(source.status, 32)
                        buffer.writeInt(source.x)
                        buffer.writeInt(source.y)
                        buffer.writeInt(source.z)
                        buffer.writeUtf(source.side, 16)
                        buffer.writeUtf(source.details, 160)
                    }
                }
            }

        @JvmStatic
        fun handle(payload: InformationSourceSnapshotPayload, context: IPayloadContext) {
            InformationSourceSnapshotState.accept(payload.snapshot)
        }
    }
}
