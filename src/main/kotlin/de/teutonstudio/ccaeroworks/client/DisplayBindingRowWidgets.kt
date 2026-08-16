package de.teutonstudio.ccaeroworks.client

import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.display.DisplayScriptCatalogState
import de.teutonstudio.ccaeroworks.display.RadarSourceDescriptor
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

internal data class RadarSourceChoice(
    val ingressPos: BlockPos?,
    val descriptor: RadarSourceDescriptor?
)

internal fun radarSourceKey(ingressPos: BlockPos?): String =
    ingressPos?.let { "${it.x},${it.y},${it.z}" } ?: "local"

internal fun radarSourceOption(choice: RadarSourceChoice): SourceSelectorOption<RadarSourceChoice> {
    val descriptor = choice.descriptor
    val stack = networkControllerStack()
    val title = when {
        descriptor != null && stack.item != Items.COMPASS -> stack.hoverName
        descriptor != null -> Component.literal("Network Controller")
        choice.ingressPos == null -> Component.literal("Lokales Netzwerk")
        else -> Component.literal("Nicht verfügbare Radarquelle")
    }
    val subtitle = when {
        descriptor != null -> Component.literal("Netzwerk ${descriptor.id}")
        choice.ingressPos == null -> Component.literal("Netzwerk lokal")
        else -> Component.literal("Netzwerk ${choice.ingressPos.x},${choice.ingressPos.y},${choice.ingressPos.z}")
    }
    return SourceSelectorOption(
        key = radarSourceKey(choice.ingressPos),
        value = choice,
        presentation = SourceSelectorPresentation(
            title = title,
            subtitle = subtitle,
            icon = SourceSelectorIcon.Item(stack)
        ),
        selectable = descriptor != null || choice.ingressPos == null
    )
}

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
            subtitle = Component.literal(
                if (entries.isEmpty()) "Kein kompatibles Lua-Skript gefunden" else "Standard / deaktiviert"
            ),
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

private fun networkControllerStack(): ItemStack {
    val block = BuiltInRegistries.BLOCK.getOptional(NETWORK_CONTROLLER_ID).orElse(null)
    if (block != null) {
        val stack = ItemStack(block)
        if (!stack.isEmpty) return stack
    }
    return ItemStack(Items.COMPASS)
}

private val NETWORK_CONTROLLER_ID: ResourceLocation =
    ResourceLocation.fromNamespaceAndPath("create_radar", "network_filterer")
private val SCRIPT_ICON: ResourceLocation = CCAeroworks.id("source_selector/script")
