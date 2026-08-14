package de.teutonstudio.ccaeroworks.network

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.computer.DisplayBindingEvents
import de.teutonstudio.ccaeroworks.display.DeskDisplayType
import de.teutonstudio.ccaeroworks.display.DisplayBindings
import de.teutonstudio.ccaeroworks.display.DisplayContentSource
import de.teutonstudio.ccaeroworks.registry.CCModuleTypes
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

/** Configures the visible content owner of a normal large Desk Display. Blank path restores manual/API mode. */
data class SetDisplayScriptSourcePayload(
    val pos: BlockPos,
    val socket: Int,
    val path: String
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<SetDisplayScriptSourcePayload> =
            CustomPacketPayload.Type(CCAeroworks.id("set_display_script_source"))

        @JvmField
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, SetDisplayScriptSourcePayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, SetDisplayScriptSourcePayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf): SetDisplayScriptSourcePayload =
                    SetDisplayScriptSourcePayload(
                        buffer.readBlockPos(),
                        buffer.readVarInt(),
                        buffer.readUtf(DisplayBindings.MAX_SCRIPT_PATH_LENGTH)
                    )

                override fun encode(buffer: RegistryFriendlyByteBuf, payload: SetDisplayScriptSourcePayload) {
                    buffer.writeBlockPos(payload.pos)
                    buffer.writeVarInt(payload.socket)
                    buffer.writeUtf(payload.path, DisplayBindings.MAX_SCRIPT_PATH_LENGTH)
                }
            }

        @JvmStatic
        fun handle(payload: SetDisplayScriptSourcePayload, context: IPayloadContext) {
            val player = context.player() as? ServerPlayer ?: return
            val level = player.serverLevel()
            if (!level.hasChunkAt(payload.pos) || !level.mayInteract(player, payload.pos)) return

            val desk = level.getBlockEntity(payload.pos) as? ConsoleBlockEntity ?: return
            if (desk.hasController() && !desk.checkUser(player.uuid)) return
            val maximumDistance = player.blockInteractionRange() + 1.0
            if (player.distanceToSqr(payload.pos.center) > maximumDistance * maximumDistance) return
            if (payload.socket !in 0 until desk.socketCount()) return

            val module = desk.module(payload.socket) ?: return
            if (CCModuleTypes.displayType(module.type()) != DeskDisplayType.THREE_DIGIT) return

            val normalized = payload.path.trim()
            val content = if (normalized.isEmpty()) {
                DisplayContentSource.Default
            } else {
                if (normalized.length > DisplayBindings.MAX_SCRIPT_PATH_LENGTH) return
                DisplayContentSource.ScriptSource(normalized)
            }
            if (DisplayBindings.setContent(desk, payload.socket, content)) {
                DisplayBindingEvents.notifyChanged(desk, payload.socket)
            }
        }
    }
}
