package de.teutonstudio.ccaeroworks.network

import io.netty.buffer.Unpooled
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.RegistryAccess
import net.minecraft.network.RegistryFriendlyByteBuf
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WireConnectionPayloadTest {
    @Test fun `all six target faces and negative coordinates survive the wire protocol`() {
        for (side in Direction.entries) {
            val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
            try {
                val expected = SetWireConnectionPayload("control:desk-b:2:aeroworks:joystick:y:forward", BlockPos(-117, -32, 703), side, true)
                SetWireConnectionPayload.STREAM_CODEC.encode(buffer, expected)
                assertEquals(expected, SetWireConnectionPayload.STREAM_CODEC.decode(buffer))
                assertFalse(buffer.isReadable)
            } finally { buffer.release() }
        }
    }

    @Test fun `invalid target face is rejected rather than wrapped onto another side`() {
        for (invalid in listOf(-1, 6, Int.MAX_VALUE)) {
            val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
            try {
                buffer.writeUtf("wire:123")
                buffer.writeBlockPos(BlockPos.ZERO)
                buffer.writeVarInt(invalid)
                buffer.writeBoolean(false)
                assertThrows(IllegalStateException::class.java) { SetWireConnectionPayload.STREAM_CODEC.decode(buffer) }
            } finally { buffer.release() }
        }
    }
}
