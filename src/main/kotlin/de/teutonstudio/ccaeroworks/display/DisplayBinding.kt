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

sealed interface DisplayBinding {
    data object Default : DisplayBinding

    data class RadarSource(val source: RadarSourceKey) : DisplayBinding

    data class LuaHandler(val path: String) : DisplayBinding

    fun toTag(): CompoundTag = CompoundTag().apply {
        when (this@DisplayBinding) {
            Default -> putString("type", "default")
            is RadarSource -> {
                putString("type", "radar_source")
                put("source", source.toTag())
            }
            is LuaHandler -> {
                putString("type", "lua_handler")
                putString("path", path)
            }
        }
    }

    companion object {
        fun fromTag(tag: CompoundTag): DisplayBinding? = when (tag.getString("type")) {
            "default" -> Default
            "radar_source" -> RadarSourceKey.fromTag(tag.getCompound("source"))?.let(::RadarSource)
            "lua_handler" -> tag.getString("path")
                .takeIf { it.isNotBlank() && it.length <= DisplayBindings.MAX_HANDLER_PATH_LENGTH }
                ?.let(::LuaHandler)
            else -> null
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
            ?: DisplayBinding.Default
        return if (supports(desk, socket, binding)) binding else DisplayBinding.Default
    }

    fun set(desk: ConsoleBlockEntity, socket: Int, binding: DisplayBinding): Boolean {
        if (socket !in 0 until desk.socketCount() || !supports(desk, socket, binding)) return false
        val state = desk as? DisplayBindingStateAccess ?: return false
        state.ccaeroworks_setDisplayBinding(socket, binding)
        return true
    }

    fun clear(desk: ConsoleBlockEntity, socket: Int): Boolean =
        set(desk, socket, DisplayBinding.Default)

    fun supports(desk: ConsoleBlockEntity, socket: Int, binding: DisplayBinding): Boolean {
        if (socket !in 0 until desk.socketCount()) return false
        if (binding == DisplayBinding.Default) return true
        val module = desk.module(socket) ?: return false
        return when (binding) {
            DisplayBinding.Default -> true
            is DisplayBinding.RadarSource -> CCModuleTypes.radarDisplayType(module.type()) != null
            is DisplayBinding.LuaHandler ->
                CCModuleTypes.displayType(module.type()) == DeskDisplayType.THREE_DIGIT &&
                    binding.path.isNotBlank() &&
                    binding.path.length <= MAX_HANDLER_PATH_LENGTH
        }
    }

    fun describe(binding: DisplayBinding): Map<String, Any> = when (binding) {
        DisplayBinding.Default -> linkedMapOf("type" to "default")
        is DisplayBinding.RadarSource -> linkedMapOf(
            "type" to "radar_source",
            "source" to binding.source.id,
            "dimension" to binding.source.dimension.toString(),
            "x" to binding.source.ingressPos.x,
            "y" to binding.source.ingressPos.y,
            "z" to binding.source.ingressPos.z
        )
        is DisplayBinding.LuaHandler -> linkedMapOf(
            "type" to "lua_handler",
            "path" to binding.path
        )
    }
}
