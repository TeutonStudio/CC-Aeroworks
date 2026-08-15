package de.teutonstudio.ccaeroworks.network

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.display.DisplayBinding
import de.teutonstudio.ccaeroworks.display.DisplayBindings
import de.teutonstudio.ccaeroworks.display.DeskDisplayType
import de.teutonstudio.ccaeroworks.registry.CCModuleTypes
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

data class SetDisplayTouchScriptPayload(
    val pos: BlockPos,
    val socket: Int,
    val path: String
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<SetDisplayTouchScriptPayload> =
            CustomPacketPayload.Type(CCAeroworks.id("set_display_touch_script"))

        @JvmField
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, SetDisplayTouchScriptPayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, SetDisplayTouchScriptPayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf): SetDisplayTouchScriptPayload =
                    SetDisplayTouchScriptPayload(
                        buffer.readBlockPos(),
                        buffer.readVarInt(),
                        buffer.readUtf(DisplayBindings.MAX_HANDLER_PATH_LENGTH)
                    )

                override fun encode(buffer: RegistryFriendlyByteBuf, payload: SetDisplayTouchScriptPayload) {
                    buffer.writeBlockPos(payload.pos)
                    buffer.writeVarInt(payload.socket)
                    buffer.writeUtf(payload.path, DisplayBindings.MAX_HANDLER_PATH_LENGTH)
                }
            }

        @JvmStatic
        fun handle(payload: SetDisplayTouchScriptPayload, context: IPayloadContext) {
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
            if (!DisplayBindings.validOptionalPath(normalized)) return
            val existing = DisplayBindings.get(desk, payload.socket)
            val boot = DisplayBindings.bootProgramPath(existing)
            val binding = when {
                normalized.isEmpty() && boot.isEmpty() -> DisplayBinding.Default
                else -> DisplayBinding.LuaApplication(normalized, boot)
            }
            DisplayBindings.set(desk, payload.socket, binding)
        }
    }
}
