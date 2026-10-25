package de.teutonstudio.ccaeroworks.ctfcompat.control

import de.teutonstudio.ccaeroworks.display.DisplayBinding
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag

class TrainControlBindingTest {
    @Test
    fun `binding round trips controller and stable channel ids`() {
        val id = UUID.randomUUID()
        val expected = TrainControlBinding(
            id,
            linkedMapOf("x" to TrainFunction.STEER, "nested/press" to TrainFunction.STATION)
        )
        val extension = TrainControlBindings.extension(expected)
        assertEquals(expected, TrainControlBindings.from(extension))
    }

    @Test
    fun `new train source may have no assignments`() {
        val expected = TrainControlBinding(UUID.randomUUID(), emptyMap())
        assertEquals(expected, TrainControlBindings.decode(TrainControlBindings.extension(expected)))
    }

    @Test
    fun `unknown function is rejected without discarding raw extension`() {
        val payload = CompoundTag().apply {
            putUUID("controller", UUID.randomUUID())
            put("assignments", ListTag().apply {
                add(CompoundTag().apply {
                    putString("channel", "x")
                    putString("function", "future_function")
                })
            })
        }
        val extension = DisplayBinding.Extension(TrainControlBindings.TYPE, payload)
        assertNull(TrainControlBindings.decode(extension))
        assertTrue(extension.payload.hasUUID("controller"))
    }

    @Test
    fun `duplicate channels are rejected`() {
        val payload = CompoundTag().apply {
            putUUID("controller", UUID.randomUUID())
            put("assignments", ListTag().apply {
                repeat(2) {
                    add(CompoundTag().apply {
                        putString("channel", "press")
                        putString("function", "station")
                    })
                }
            })
        }
        assertNull(TrainControlBindings.decode(DisplayBinding.Extension(TrainControlBindings.TYPE, payload)))
    }
}
