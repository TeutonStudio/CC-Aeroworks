package de.teutonstudio.ccaeroworks.client

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.display.DisplayBinding
import java.util.concurrent.CopyOnWriteArrayList
import net.minecraft.network.chat.Component

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

/** Additional channel rows displayed below the mutually exclusive source selector. */
internal data class DisplaySourceExtraRow(
    val message: Component,
    val selectedKey: () -> String,
    val options: (selectedKey: String) -> List<SourceSelectorOption<DisplaySourceSelection>>
)

internal fun interface DisplaySourceExtraRowsExtension {
    fun rows(desk: ConsoleBlockEntity, socket: Int): List<DisplaySourceExtraRow>
}

internal object DisplaySourceSelectorExtensions {
    private val extensions = CopyOnWriteArrayList<DisplaySourceSelectorExtension>()
    private val extraRows = CopyOnWriteArrayList<DisplaySourceExtraRowsExtension>()
    private val selectionListeners = CopyOnWriteArrayList<(ConsoleBlockEntity, Int, String) -> Unit>()

    fun register(extension: DisplaySourceSelectorExtension) {
        extensions += extension
    }

    fun registerRows(extension: DisplaySourceExtraRowsExtension) {
        extraRows += extension
    }

    fun onSelection(listener: (ConsoleBlockEntity, Int, String) -> Unit) {
        selectionListeners += listener
    }

    fun notifySelection(desk: ConsoleBlockEntity, socket: Int, key: String) {
        selectionListeners.forEach { it(desk, socket, key) }
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

    fun rows(desk: ConsoleBlockEntity, socket: Int): List<DisplaySourceExtraRow> =
        extraRows.flatMap { it.rows(desk, socket) }
}
