package de.teutonstudio.ccaeroworks.display

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.registry.CCModuleTypes
import net.minecraft.nbt.CompoundTag
import java.util.concurrent.ConcurrentHashMap

data class ExtensionBindingHandler(
    val supports: (ConsoleBlockEntity, Int, DisplayBinding.Extension) -> Boolean,
    val describe: (DisplayBinding.Extension) -> Map<String, Any>
)

sealed interface DisplayBinding {
    data object Default : DisplayBinding
    data class LuaHandler(val path: String) : DisplayBinding
    data class Extension(val type: String, val payload: CompoundTag) : DisplayBinding

    fun toTag(): CompoundTag = CompoundTag().apply {
        when (this@DisplayBinding) {
            Default -> putString("type", "default")
            is LuaHandler -> {
                putString("type", "lua_handler")
                putString("path", path)
            }
            is Extension -> {
                merge(payload.copy())
                putString("type", this@DisplayBinding.type)
            }
        }
    }

    companion object {
        fun fromTag(tag: CompoundTag): DisplayBinding? {
            val type = tag.getString("type")
            return when (type) {
                "default" -> Default
                "lua_handler" -> tag.getString("path")
                    .takeIf { it.isNotBlank() && it.length <= DisplayBindings.MAX_HANDLER_PATH_LENGTH }
                    ?.let(::LuaHandler)
                "" -> null
                else -> Extension(type, tag.copy().apply { remove("type") })
            }
        }
    }
}

interface DisplayBindingStateAccess {
    fun ccaeroworks_getDisplayBindings(): Map<Int, DisplayBinding>
    fun ccaeroworks_setDisplayBinding(socket: Int, binding: DisplayBinding)
}

object DisplayBindingExtensions {
    private val handlers = ConcurrentHashMap<String, ExtensionBindingHandler>()

    fun register(
        type: String,
        supports: (ConsoleBlockEntity, Int, DisplayBinding.Extension) -> Boolean,
        describe: (DisplayBinding.Extension) -> Map<String, Any>
    ) {
        require(type.isNotBlank() && type != "default" && type != "lua_handler")
        check(handlers.putIfAbsent(type, ExtensionBindingHandler(supports, describe)) == null) {
            "Display binding extension '$type' is already registered"
        }
    }

    internal fun supports(desk: ConsoleBlockEntity, socket: Int, binding: DisplayBinding.Extension): Boolean =
        handlers[binding.type]?.supports?.invoke(desk, socket, binding) == true

    internal fun describe(binding: DisplayBinding.Extension): Map<String, Any> =
        handlers[binding.type]?.describe?.invoke(binding) ?: linkedMapOf("type" to binding.type)
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

    fun clear(desk: ConsoleBlockEntity, socket: Int): Boolean = set(desk, socket, DisplayBinding.Default)

    fun supports(desk: ConsoleBlockEntity, socket: Int, binding: DisplayBinding): Boolean {
        if (socket !in 0 until desk.socketCount()) return false
        if (binding == DisplayBinding.Default) return true
        val module = desk.module(socket) ?: return false
        return when (binding) {
            DisplayBinding.Default -> true
            is DisplayBinding.LuaHandler ->
                CCModuleTypes.displayType(module.type()) == DeskDisplayType.THREE_DIGIT &&
                    binding.path.isNotBlank() &&
                    binding.path.length <= MAX_HANDLER_PATH_LENGTH
            is DisplayBinding.Extension -> DisplayBindingExtensions.supports(desk, socket, binding)
        }
    }

    fun describe(binding: DisplayBinding): Map<String, Any> = when (binding) {
        DisplayBinding.Default -> linkedMapOf("type" to "default")
        is DisplayBinding.LuaHandler -> linkedMapOf("type" to "lua_handler", "path" to binding.path)
        is DisplayBinding.Extension -> DisplayBindingExtensions.describe(binding)
    }
}
