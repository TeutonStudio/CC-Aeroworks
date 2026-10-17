package de.teutonstudio.ccaeroworks.network

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.computer.ConsoleInventoryMenu
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.SimpleMenuProvider
import net.neoforged.neoforge.network.handling.IPayloadContext

data class OpenConsoleInventoryPayload(val anchor: BlockPos) : CustomPacketPayload {
    override fun type() = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<OpenConsoleInventoryPayload>(CCAeroworks.id("open_console_inventory"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, OpenConsoleInventoryPayload> = object : StreamCodec<RegistryFriendlyByteBuf, OpenConsoleInventoryPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf) = OpenConsoleInventoryPayload(buffer.readBlockPos())
            override fun encode(buffer: RegistryFriendlyByteBuf, payload: OpenConsoleInventoryPayload) { buffer.writeBlockPos(payload.anchor) }
        }
        fun handle(payload: OpenConsoleInventoryPayload, context: IPayloadContext) {
            val player = context.player() as? ServerPlayer ?: return
            val level = player.serverLevel()
            if (!level.hasChunkAt(payload.anchor) || !level.mayInteract(player, payload.anchor)) return
            if (player.distanceToSqr(payload.anchor.center) > (player.blockInteractionRange() + 1.0) * (player.blockInteractionRange() + 1.0)) return
            val host = level.getBlockEntity(payload.anchor) as? ConsoleBlockEntity ?: return
            if (!AeroworksTypes.isControlDesk(host.blockState.block)) return
            player.openMenu(SimpleMenuProvider({ id, inventory, _ -> ConsoleInventoryMenu(id, inventory, payload.anchor, host) },
                Component.translatable("gui.cc_aeroworks.console_inventory"))) { buffer -> buffer.writeBlockPos(payload.anchor) }
        }
    }
}
