package de.teutonstudio.ccaeroworks.client

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.display.DisplayBinding
import java.util.concurrent.CopyOnWriteArrayList

/** A selection in the shared display-controller dropdown. */
internal fun interface DisplaySourceSelection {
    fun apply()
}

/**
 * Optional integrations contribute pseudo-script controllers without making the core UI depend
 * on their mod. A contributed key must be namespaced (for example `extension:radar_source:...`)
 * so it can never collide with a Lua path.
 */
internal data class DisplaySourceSelectorExtension(
    val selectedKey: (DisplayBinding) -> String?,
    val options: (
        desk: ConsoleBlockEntity,
        socket: Int,
        selectedKey: String
    ) -> List<SourceSelectorOption<DisplaySourceSelection>>
)

internal object DisplaySourceSelectorExtensions {
    private val extensions = CopyOnWriteArrayList<DisplaySourceSelectorExtension>()

    fun register(extension: DisplaySourceSelectorExtension) {
        extensions += extension
    }

    fun selectedKey(binding: DisplayBinding): String = when (binding) {
        DisplayBinding.Default -> ""
        is DisplayBinding.LuaHandler -> binding.path
        is DisplayBinding.Extension -> extensions.asSequence()
            .mapNotNull { it.selectedKey(binding) }
            .firstOrNull()
            ?: "extension:${binding.type}"
    }

    fun options(
        desk: ConsoleBlockEntity,
        socket: Int,
        selectedKey: String
    ): List<SourceSelectorOption<DisplaySourceSelection>> =
        extensions.flatMap { it.options(desk, socket, selectedKey) }
}
