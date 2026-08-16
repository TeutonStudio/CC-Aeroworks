package de.teutonstudio.ccaeroworks.network

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.sable.SableInteractionGeometry
import de.teutonstudio.ccaeroworks.display.DisplayBinding
import de.teutonstudio.ccaeroworks.display.DisplayBindings
import de.teutonstudio.ccaeroworks.display.DisplayScriptCatalog
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
            if (!level.hasChunkAt(payload.pos) || !SableInteractionGeometry.mayInteract(player, level, payload.pos)) return

            val desk = level.getBlockEntity(payload.pos) as? ConsoleBlockEntity ?: return
            if (desk.hasController() && !desk.checkUser(player.uuid)) return
            if (!SableInteractionGeometry.withinReach(player, level, payload.pos)) return
            if (payload.socket !in 0 until desk.socketCount()) return

            val module = desk.module(payload.socket) ?: return
            val displayType = CCModuleTypes.displayType(module.type()) ?: return
            val normalized = payload.path.trim()
            if (normalized.length > DisplayBindings.MAX_HANDLER_PATH_LENGTH) return

            val previous = DisplayBindings.get(desk, payload.socket)
            val boot = DisplayBindings.bootProgramPath(previous)
            val binding = if (normalized.isEmpty()) {
                if (boot.isEmpty()) DisplayBinding.Default else DisplayBinding.LuaApplication("", boot)
            } else {
                val owner = DisplayScriptCatalog.ownerFor(desk) ?: return
                val descriptor = DisplayScriptCatalog.find(owner, normalized, displayType) ?: return
                if (boot.isEmpty()) {
                    DisplayBinding.LuaHandler(descriptor.path)
                } else {
                    DisplayBinding.LuaApplication(descriptor.path, boot)
                }
            }
            DisplayBindings.set(desk, payload.socket, binding)
        }
    }
}
