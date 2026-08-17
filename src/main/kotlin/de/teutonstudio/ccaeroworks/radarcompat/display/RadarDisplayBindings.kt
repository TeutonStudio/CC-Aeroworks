package de.teutonstudio.ccaeroworks.radarcompat.display

import de.teutonstudio.ccaeroworks.display.DisplayBinding
import de.teutonstudio.ccaeroworks.display.DisplayBindingExtensions
import de.teutonstudio.ccaeroworks.radarcompat.registry.RadarModuleTypes
import net.minecraft.nbt.CompoundTag

object RadarDisplayBindings {
    const val TYPE = "radar_source"

    fun register() {
        DisplayBindingExtensions.register(
            TYPE,
            supports = { desk, socket, _ ->
                desk.module(socket)?.let { RadarModuleTypes.radarDisplayType(it.type()) } != null
            },
            describe = { binding ->
                val source = source(binding)
                if (source == null) linkedMapOf("type" to TYPE) else linkedMapOf(
                    "type" to TYPE,
                    "source" to source.id,
                    "dimension" to source.dimension.toString(),
                    "x" to source.ingressPos.x,
                    "y" to source.ingressPos.y,
                    "z" to source.ingressPos.z
                )
            }
        )
    }

    fun binding(source: RadarSourceKey): DisplayBinding.Extension = DisplayBinding.Extension(
        TYPE,
        CompoundTag().apply { put("source", source.toTag()) }
    )

    fun source(binding: DisplayBinding): RadarSourceKey? {
        val extension = binding as? DisplayBinding.Extension ?: return null
        if (extension.type != TYPE || !extension.payload.contains("source")) return null
        return RadarSourceKey.fromTag(extension.payload.getCompound("source"))
    }
}
