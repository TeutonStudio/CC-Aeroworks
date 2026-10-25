package de.teutonstudio.ccaeroworks.ctfcompat.control

import com.mred231.aeroworks.content.controls.channel.ControlChannel
import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.display.ControlScriptBindings
import de.teutonstudio.ccaeroworks.display.DisplayBinding
import de.teutonstudio.ccaeroworks.display.DisplayBindingExtensions
import java.util.UUID
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag

enum class TrainFunction {
    THROTTLE,
    STEER,
    BRAKE,
    STATION
}

data class TrainControlBinding(
    val controllerId: UUID,
    val assignments: Map<String, TrainFunction>
)

object TrainControlBindings {
    const val TYPE = "create_train_functionality_dashboard"
    const val MAX_ASSIGNMENTS = 64

    fun register() {
        DisplayBindingExtensions.register(
            TYPE,
            supports = { desk, socket, extension ->
                val module = desk.module(socket) ?: return@register false
                val binding = decode(extension) ?: return@register false
                ControlScriptBindings.supports(module) && binding.assignments.all { (channelId, function) ->
                    val channel = module.channels().firstOrNull { it.id() == channelId }
                    channel != null && supports(channel.kind(), function)
                }
            },
            describe = { extension ->
                val binding = decode(extension)
                linkedMapOf<String, Any>("type" to TYPE).apply {
                    if (binding != null) {
                        put("controllerId", binding.controllerId.toString())
                        put("assignments", binding.assignments.mapValues { it.value.name.lowercase() })
                    }
                }
            }
        )
    }

    fun extension(binding: TrainControlBinding): DisplayBinding.Extension =
        DisplayBinding.Extension(TYPE, encode(binding))

    fun from(binding: DisplayBinding): TrainControlBinding? =
        (binding as? DisplayBinding.Extension)?.takeIf { it.type == TYPE }?.let(::decode)

    fun encode(binding: TrainControlBinding): CompoundTag = CompoundTag().apply {
        putUUID("controller", binding.controllerId)
        val entries = ListTag()
        binding.assignments.entries.sortedBy { it.key }.take(MAX_ASSIGNMENTS).forEach { (channel, function) ->
            entries.add(CompoundTag().apply {
                putString("channel", channel)
                putString("function", function.name.lowercase())
            })
        }
        put("assignments", entries)
    }

    fun decode(extension: DisplayBinding.Extension): TrainControlBinding? {
        if (extension.type != TYPE) return null
        val tag = extension.payload
        if (!tag.hasUUID("controller")) return null
        val assignments = linkedMapOf<String, TrainFunction>()
        if (tag.contains("assignments", Tag.TAG_LIST.toInt())) {
            val entries = tag.getList("assignments", Tag.TAG_COMPOUND.toInt())
            if (entries.size > MAX_ASSIGNMENTS) return null
            for (index in 0 until entries.size) {
                val entry = entries.getCompound(index)
                val channel = entry.getString("channel")
                if (channel.isBlank() || channel.length > 128 || channel in assignments) return null
                val function = runCatching {
                    TrainFunction.valueOf(entry.getString("function").uppercase())
                }.getOrNull() ?: return null
                assignments[channel] = function
            }
        }
        return TrainControlBinding(tag.getUUID("controller"), assignments)
    }

    fun supports(kind: ControlChannel.Kind, function: TrainFunction): Boolean = when (function) {
        TrainFunction.THROTTLE, TrainFunction.STEER -> kind == ControlChannel.Kind.AXIS
        TrainFunction.BRAKE -> kind == ControlChannel.Kind.AXIS || kind == ControlChannel.Kind.BUTTON
        TrainFunction.STATION -> kind == ControlChannel.Kind.BUTTON
    }
}
