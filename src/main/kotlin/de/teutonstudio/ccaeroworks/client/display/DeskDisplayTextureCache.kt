package de.teutonstudio.ccaeroworks.client.display

import com.mojang.blaze3d.platform.NativeImage
import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.display.DeskDisplayPixels
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.client.event.ClientTickEvent
import java.util.IdentityHashMap

/**
 * Client-side GPU texture cache for programmable desk displays.
 *
 * One logical display pixel maps to one texel. The texture is uploaded only when the immutable
 * [DeskDisplayPixels] snapshot changes; normal render frames merely reuse the registered texture.
 */
object DeskDisplayTextureCache {
    private data class Entry(
        val location: ResourceLocation,
        val texture: DynamicTexture,
        val image: NativeImage,
        var pixels: DeskDisplayPixels
    )

    private val entries = IdentityHashMap<ConsoleBlockEntity, MutableMap<Int, Entry>>()

    @JvmStatic
    fun texture(desk: ConsoleBlockEntity, socket: Int, pixels: DeskDisplayPixels): ResourceLocation {
        val displayEntries = entries.getOrPut(desk) { mutableMapOf() }
        var entry = displayEntries[socket]

        if (entry == null || entry.image.width != pixels.width || entry.image.height != pixels.height) {
            if (entry != null) release(entry)
            entry = create(pixels)
            displayEntries[socket] = entry
        } else if (entry.pixels != pixels) {
            writePixels(entry.image, pixels)
            entry.pixels = pixels
            entry.texture.upload()
        }

        return entry.location
    }

    /** Releases cached sockets which no longer contain a programmable raster. */
    @JvmStatic
    fun retain(desk: ConsoleBlockEntity, activeSockets: Set<Int>) {
        val displayEntries = entries[desk] ?: return
        val iterator = displayEntries.iterator()
        while (iterator.hasNext()) {
            val (socket, entry) = iterator.next()
            if (socket in activeSockets) continue
            release(entry)
            iterator.remove()
        }
        if (displayEntries.isEmpty()) entries.remove(desk)
    }

    @JvmStatic
    fun release(desk: ConsoleBlockEntity) {
        entries.remove(desk)?.values?.forEach(::release)
    }

    /**
     * Block entities can disappear without another render call, for example when their chunk is
     * unloaded. Clear those textures on the client tick instead of leaving registered GPU objects
     * behind for the rest of the game session.
     */
    @JvmStatic
    fun clientTick(event: ClientTickEvent.Post) {
        val level = Minecraft.getInstance().level
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val (desk, displayEntries) = iterator.next()
            if (!desk.isRemoved && desk.level === level) continue
            displayEntries.values.forEach(::release)
            iterator.remove()
        }
    }

    private fun create(pixels: DeskDisplayPixels): Entry {
        val image = NativeImage(pixels.width, pixels.height, false)
        writePixels(image, pixels)

        val texture = DynamicTexture(image)
        // Keep one logical display pixel exactly one sharp texel. No blur, no mipmaps.
        texture.setFilter(false, false)
        val location = Minecraft.getInstance().textureManager.register("cc_aeroworks_display", texture)
        texture.upload()
        return Entry(location, texture, image, pixels)
    }

    private fun writePixels(image: NativeImage, pixels: DeskDisplayPixels) {
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                // White is intentionally format-agnostic here. The render quad applies the final
                // orange tint, while zero remains a fully transparent disabled pixel.
                image.setPixelRGBA(x, y, if (pixels.get(x, y)) -1 else 0)
            }
        }
    }

    private fun release(entry: Entry) {
        // TextureManager.release closes the DynamicTexture and its NativeImage.
        Minecraft.getInstance().textureManager.release(entry.location)
    }
}
