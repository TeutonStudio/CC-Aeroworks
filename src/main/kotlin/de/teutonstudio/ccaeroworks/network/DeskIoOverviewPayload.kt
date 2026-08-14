package de.teutonstudio.ccaeroworks.network

import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.client.DeskIoOverviewClient
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.neoforged.neoforge.network.handling.IPayloadContext

/** Compact server snapshot used only by the client-side I/O overview. */
data class DeskIoOverviewPayload(
    val origin: BlockPos,
    val json: String
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        const val MAX_JSON_LENGTH: Int = 262_144

        @JvmField
        val TYPE: CustomPacketPayload.Type<DeskIoOverviewPayload> =
            CustomPacketPayload.Type(CCAeroworks.id("desk_io_overview"))

        @JvmField
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, DeskIoOverviewPayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, DeskIoOverviewPayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf): DeskIoOverviewPayload =
                    DeskIoOverviewPayload(
                        buffer.readBlockPos(),
                        buffer.readUtf(MAX_JSON_LENGTH)
                    )

                override fun encode(buffer: RegistryFriendlyByteBuf, payload: DeskIoOverviewPayload) {
                    buffer.writeBlockPos(payload.origin)
                    buffer.writeUtf(payload.json, MAX_JSON_LENGTH)
                }
            }

        @JvmStatic
        fun handle(payload: DeskIoOverviewPayload, context: IPayloadContext) {
            context.enqueueWork {
                DeskIoOverviewClient.open(payload.origin, payload.json)
            }
        }
    }
}
