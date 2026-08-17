package de.teutonstudio.ccaeroworks.client

import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.display.DisplayScriptCatalogState
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

internal fun scriptSourceOptions(
    deskPos: BlockPos,
    socket: Int,
    selectedPath: String
): List<SourceSelectorOption<String>> {
    val entries = DisplayScriptCatalogState.get(deskPos, socket)
    val result = ArrayList<SourceSelectorOption<String>>(entries.size + 2)
    result += SourceSelectorOption(
        key = "",
        value = "",
        presentation = SourceSelectorPresentation(
            title = Component.literal(if (entries.isEmpty()) "Keine gültigen Skripte" else "Keine Skriptquelle"),
            subtitle = Component.literal(if (entries.isEmpty()) "Kein kompatibles Lua-Skript gefunden" else "Standard / deaktiviert"),
            icon = SourceSelectorIcon.Sprite(SCRIPT_ICON)
        )
    )
    if (selectedPath.isNotBlank() && entries.none { it.path == selectedPath }) {
        result += SourceSelectorOption(
            key = selectedPath,
            value = selectedPath,
            presentation = SourceSelectorPresentation(
                title = Component.literal("Fehlende Skriptquelle"),
                subtitle = Component.literal(selectedPath),
                icon = SourceSelectorIcon.Sprite(SCRIPT_ICON)
            ),
            selectable = false
        )
    }
    entries.forEach { descriptor ->
        result += SourceSelectorOption(
            key = descriptor.path,
            value = descriptor.path,
            presentation = SourceSelectorPresentation(
                title = Component.literal(descriptor.name),
                subtitle = Component.literal(descriptor.path),
                icon = SourceSelectorIcon.Sprite(SCRIPT_ICON)
            )
        )
    }
    return result
}

private val SCRIPT_ICON: ResourceLocation = CCAeroworks.id("source_selector/script")
