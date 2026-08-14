package de.teutonstudio.ccaeroworks.display

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.registry.CCModuleTypes
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.resources.ResourceLocation

data class RadarSourceKey(
    val dimension: ResourceLocation,
    val ingressPos: BlockPos
) {
    val id: String
        get() = "$dimension@${ingressPos.asLong()}"

    fun toTag(): CompoundTag = CompoundTag().apply {
        putString("dimension", dimension.toString())
        put("ingressPos", NbtUtils.writeBlockPos(ingressPos))
    }

    companion object {
        fun fromTag(tag: CompoundTag): RadarSourceKey? {
            val dimension = ResourceLocation.tryParse(tag.getString("dimension")) ?: return null
            val ingressPos = NbtUtils.readBlockPos(tag, "ingressPos").orElse(null) ?: return null
            return RadarSourceKey(dimension, ingressPos.immutable())
        }
    }
}

/** What provides the visible contents of a display. */
sealed interface DisplayContentSource {
    data object Default : DisplayContentSource

    data class RadarSource(val source: RadarSourceKey) : DisplayContentSource

    fun toTag(): CompoundTag = CompoundTag().apply {
        when (this@DisplayContentSource) {
            Default -> putString("type", "default")
            is RadarSource -> {
                putString("type", "radar_source")
                put("source", source.toTag())
            }
        }
    }

    companion object {
        fun fromTag(tag: CompoundTag): DisplayContentSource? = when (tag.getString("type")) {
            "default", "" -> Default
            "radar_source" -> RadarSourceKey.fromTag(tag.getCompound("source"))?.let(::RadarSource)
            else -> null
        }
    }
}

/** How pointer/touch input from a display is routed. Independent from its visible content source. */
sealed interface DisplayInputBinding {
    data object Raw : DisplayInputBinding

    data class LuaHandler(val path: String) : DisplayInputBinding

    fun toTag(): CompoundTag = CompoundTag().apply {
        when (this@DisplayInputBinding) {
            Raw -> putString("type", "raw")
            is LuaHandler -> {
                putString("type", "lua_handler")
                putString("path", path)
            }
        }
    }

    companion object {
        fun fromTag(tag: CompoundTag): DisplayInputBinding? = when (tag.getString("type")) {
            "raw", "" -> Raw
            "lua_handler" -> tag.getString("path")
                .takeIf { it.isNotBlank() && it.length <= DisplayBindings.MAX_HANDLER_PATH_LENGTH }
                ?.let(::LuaHandler)
            else -> null
        }
    }
}

/**
 * Per-display routing configuration.
 *
 * Content and input deliberately live in separate axes: a radar can use a remote radar ingress
 * while still routing pointer input through a Lua handler, and a normal display can keep raw touch
 * events without changing whoever owns its visible content.
 */
data class DisplayBinding(
    val content: DisplayContentSource = DisplayContentSource.Default,
    val input: DisplayInputBinding = DisplayInputBinding.Raw
) {
    val isDefault: Boolean
        get() = content == DisplayContentSource.Default && input == DisplayInputBinding.Raw

    fun toTag(): CompoundTag = CompoundTag().apply {
        putInt("version", CURRENT_VERSION)
        put("content", content.toTag())
        put("input", input.toTag())
    }

    companion object {
        private const val CURRENT_VERSION = 2

        /** Reads both the new orthogonal format and the old one-of radar_source/lua_handler format. */
        fun fromTag(tag: CompoundTag): DisplayBinding? {
            if (tag.contains("content") || tag.contains("input")) {
                val content = if (tag.contains("content")) {
                    DisplayContentSource.fromTag(tag.getCompound("content")) ?: return null
                } else {
                    DisplayContentSource.Default
                }
                val input = if (tag.contains("input")) {
                    DisplayInputBinding.fromTag(tag.getCompound("input")) ?: return null
                } else {
                    DisplayInputBinding.Raw
                }
                return DisplayBinding(content, input)
            }

            return when (tag.getString("type")) {
                "default", "" -> DisplayBinding()
                "radar_source" -> RadarSourceKey.fromTag(tag.getCompound("source"))
                    ?.let { DisplayBinding(content = DisplayContentSource.RadarSource(it)) }
                "lua_handler" -> tag.getString("path")
                    .takeIf { it.isNotBlank() && it.length <= DisplayBindings.MAX_HANDLER_PATH_LENGTH }
                    ?.let { DisplayBinding(input = DisplayInputBinding.LuaHandler(it)) }
                else -> null
            }
        }
    }
}

interface DisplayBindingStateAccess {
    fun ccaeroworks_getDisplayBindings(): Map<Int, DisplayBinding>

    fun ccaeroworks_setDisplayBinding(socket: Int, binding: DisplayBinding)
}

object DisplayBindings {
    const val MAX_HANDLER_PATH_LENGTH: Int = 256

    fun get(desk: ConsoleBlockEntity, socket: Int): DisplayBinding {
        val binding = (desk as? DisplayBindingStateAccess)
            ?.ccaeroworks_getDisplayBindings()
            ?.get(socket)
            ?: DisplayBinding()
        return if (supports(desk, socket, binding)) binding else DisplayBinding()
    }

    fun set(desk: ConsoleBlockEntity, socket: Int, binding: DisplayBinding): Boolean {
        if (socket !in 0 until desk.socketCount() || !supports(desk, socket, binding)) return false
        val state = desk as? DisplayBindingStateAccess ?: return false
        state.ccaeroworks_setDisplayBinding(socket, binding)
        return true
    }

    fun setContent(desk: ConsoleBlockEntity, socket: Int, content: DisplayContentSource): Boolean =
        set(desk, socket, get(desk, socket).copy(content = content))

    fun setInput(desk: ConsoleBlockEntity, socket: Int, input: DisplayInputBinding): Boolean =
        set(desk, socket, get(desk, socket).copy(input = input))

    fun clear(desk: ConsoleBlockEntity, socket: Int): Boolean =
        set(desk, socket, DisplayBinding())

    fun clearContent(desk: ConsoleBlockEntity, socket: Int): Boolean =
        setContent(desk, socket, DisplayContentSource.Default)

    fun clearInput(desk: ConsoleBlockEntity, socket: Int): Boolean =
        setInput(desk, socket, DisplayInputBinding.Raw)

    fun supports(desk: ConsoleBlockEntity, socket: Int, binding: DisplayBinding): Boolean {
        if (socket !in 0 until desk.socketCount()) return false
        val module = desk.module(socket) ?: return binding.isDefault

        val contentSupported = when (binding.content) {
            DisplayContentSource.Default -> true
            is DisplayContentSource.RadarSource -> CCModuleTypes.radarDisplayType(module.type()) != null
        }
        if (!contentSupported) return false

        return when (val input = binding.input) {
            DisplayInputBinding.Raw -> true
            is DisplayInputBinding.LuaHandler ->
                CCModuleTypes.displayType(module.type()) == DeskDisplayType.THREE_DIGIT &&
                    input.path.isNotBlank() &&
                    input.path.length <= MAX_HANDLER_PATH_LENGTH
        }
    }

    fun describe(binding: DisplayBinding): Map<String, Any> = linkedMapOf<String, Any>().apply {
        // Keep the old top-level shape for callers which only understand one binding axis.
        when {
            binding.content is DisplayContentSource.RadarSource && binding.input == DisplayInputBinding.Raw -> {
                val source = binding.content.source
                put("type", "radar_source")
                put("source", source.id)
                put("dimension", source.dimension.toString())
                put("x", source.ingressPos.x)
                put("y", source.ingressPos.y)
                put("z", source.ingressPos.z)
            }
            binding.content == DisplayContentSource.Default && binding.input is DisplayInputBinding.LuaHandler -> {
                put("type", "lua_handler")
                put("path", binding.input.path)
            }
            binding.isDefault -> put("type", "default")
            else -> put("type", "composite")
        }
        put("content", describeContent(binding.content))
        put("input", describeInput(binding.input))
    }

    private fun describeContent(content: DisplayContentSource): Map<String, Any> = when (content) {
        DisplayContentSource.Default -> linkedMapOf("type" to "default")
        is DisplayContentSource.RadarSource -> linkedMapOf(
            "type" to "radar_source",
            "source" to content.source.id,
            "dimension" to content.source.dimension.toString(),
            "x" to content.source.ingressPos.x,
            "y" to content.source.ingressPos.y,
            "z" to content.source.ingressPos.z
        )
    }

    private fun describeInput(input: DisplayInputBinding): Map<String, Any> = when (input) {
        DisplayInputBinding.Raw -> linkedMapOf("type" to "raw")
        is DisplayInputBinding.LuaHandler -> linkedMapOf(
            "type" to "lua_handler",
            "path" to input.path
        )
    }
}
