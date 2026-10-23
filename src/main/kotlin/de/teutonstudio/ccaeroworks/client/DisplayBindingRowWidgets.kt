package de.teutonstudio.ccaeroworks.client

import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.display.DisplayScriptCatalogState
import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.PacketDistributor
import de.teutonstudio.ccaeroworks.network.SetDisplayTouchScriptPayload

internal fun scriptSourceOptions(
    desk: ConsoleBlockEntity,
    socket: Int,
    selectedKey: String
): List<SourceSelectorOption<DisplaySourceSelection>> {
    val deskPos = desk.blockPos
    val entries = DisplayScriptCatalogState.get(deskPos, socket)
    val extensions = DisplaySourceSelectorExtensions.options(desk, socket, selectedKey)
    val result = ArrayList<SourceSelectorOption<DisplaySourceSelection>>(entries.size + extensions.size + 2)
    result += SourceSelectorOption(
        key = "",
        value = DisplaySourceSelection {
            PacketDistributor.sendToServer(SetDisplayTouchScriptPayload(deskPos, socket, ""))
        },
        presentation = SourceSelectorPresentation(
            title = Component.literal("Keine externe Steuerung"),
            subtitle = Component.literal("Standard / deaktiviert"),
            icon = SourceSelectorIcon.Sprite(SCRIPT_ICON)
        )
    )
    if (
        selectedKey.isNotBlank() &&
        entries.none { it.path == selectedKey } &&
        extensions.none { it.key == selectedKey }
    ) {
        result += SourceSelectorOption(
            key = selectedKey,
            value = DisplaySourceSelection {},
            presentation = SourceSelectorPresentation(
                title = Component.literal("Fehlende Display-Steuerung"),
                subtitle = Component.literal(selectedKey),
                icon = SourceSelectorIcon.Sprite(SCRIPT_ICON)
            ),
            selectable = false
        )
    }
    entries.forEach { descriptor ->
        result += SourceSelectorOption(
            key = descriptor.path,
            value = DisplaySourceSelection {
                PacketDistributor.sendToServer(SetDisplayTouchScriptPayload(deskPos, socket, descriptor.path))
            },
            presentation = SourceSelectorPresentation(
                title = Component.literal(descriptor.name),
                subtitle = Component.literal(descriptor.path),
                icon = SourceSelectorIcon.Sprite(SCRIPT_ICON)
            )
        )
    }
    result += extensions
    return result
}

private val SCRIPT_ICON: ResourceLocation = CCAeroworks.id("source_selector/script")
