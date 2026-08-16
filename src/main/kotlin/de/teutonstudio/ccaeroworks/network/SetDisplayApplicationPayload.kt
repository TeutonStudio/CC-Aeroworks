package de.teutonstudio.ccaeroworks.network

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.sable.SableInteractionGeometry
import de.teutonstudio.ccaeroworks.display.DeskDisplayType
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

data class SetDisplayApplicationPayload(
    val pos: BlockPos,
    val socket: Int,
    val controllerPath: String,
    val bootProgramPath: String
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<SetDisplayApplicationPayload> =
            CustomPacketPayload.Type(CCAeroworks.id("set_display_application"))

        @JvmField
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, SetDisplayApplicationPayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, SetDisplayApplicationPayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf): SetDisplayApplicationPayload =
                    SetDisplayApplicationPayload(
                        buffer.readBlockPos(),
                        buffer.readVarInt(),
                        buffer.readUtf(DisplayBindings.MAX_HANDLER_PATH_LENGTH),
                        buffer.readUtf(DisplayBindings.MAX_HANDLER_PATH_LENGTH)
                    )

                override fun encode(buffer: RegistryFriendlyByteBuf, payload: SetDisplayApplicationPayload) {
                    buffer.writeBlockPos(payload.pos)
                    buffer.writeVarInt(payload.socket)
                    buffer.writeUtf(payload.controllerPath, DisplayBindings.MAX_HANDLER_PATH_LENGTH)
                    buffer.writeUtf(payload.bootProgramPath, DisplayBindings.MAX_HANDLER_PATH_LENGTH)
                }
            }

        @JvmStatic
        fun handle(payload: SetDisplayApplicationPayload, context: IPayloadContext) {
            val player = context.player() as? ServerPlayer ?: return
            val level = player.serverLevel()
            if (!level.hasChunkAt(payload.pos) || !SableInteractionGeometry.mayInteract(player, level, payload.pos)) return
            val desk = level.getBlockEntity(payload.pos) as? ConsoleBlockEntity ?: return
            if (desk.hasController() && !desk.checkUser(player.uuid)) return
            if (!SableInteractionGeometry.withinReach(player, level, payload.pos)) return
            if (payload.socket !in 0 until desk.socketCount()) return
            val module = desk.module(payload.socket) ?: return
            val displayType = CCModuleTypes.displayType(module.type()) ?: return
            if (displayType != DeskDisplayType.THREE_DIGIT) return

            val controller = payload.controllerPath.trim()
            val boot = payload.bootProgramPath.trim()
            if (!DisplayBindings.validOptionalPath(controller) || !DisplayBindings.validOptionalPath(boot)) return

            val owner = DisplayScriptCatalog.ownerFor(desk) ?: return
            if (controller.isNotEmpty() && DisplayScriptCatalog.find(owner, controller, displayType) == null) return
            if (boot.isNotEmpty() && DisplayScriptCatalog.find(owner, boot, displayType) == null) return

            val binding = if (controller.isEmpty() && boot.isEmpty()) {
                DisplayBinding.Default
            } else {
                DisplayBinding.LuaApplication(controller, boot)
            }
            DisplayBindings.set(desk, payload.socket, binding)
        }
    }
}
