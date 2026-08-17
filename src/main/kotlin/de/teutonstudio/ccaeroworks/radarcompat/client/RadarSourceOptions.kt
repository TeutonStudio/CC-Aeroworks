package de.teutonstudio.ccaeroworks.radarcompat.client

import de.teutonstudio.ccaeroworks.client.SourceSelectorIcon
import de.teutonstudio.ccaeroworks.client.SourceSelectorOption
import de.teutonstudio.ccaeroworks.client.SourceSelectorPresentation
import de.teutonstudio.ccaeroworks.radarcompat.display.RadarSourceDescriptor
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

internal data class RadarSourceChoice(val ingressPos: BlockPos?, val descriptor: RadarSourceDescriptor?)
internal fun radarSourceKey(ingressPos: BlockPos?): String = ingressPos?.let { "${it.x},${it.y},${it.z}" } ?: "local"

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
        presentation = SourceSelectorPresentation(title, subtitle, SourceSelectorIcon.Item(stack)),
        selectable = descriptor != null || choice.ingressPos == null
    )
}

private fun networkControllerStack(): ItemStack {
    val block = BuiltInRegistries.BLOCK.getOptional(NETWORK_CONTROLLER_ID).orElse(null)
    return if (block == null) ItemStack(Items.COMPASS) else ItemStack(block).takeUnless(ItemStack::isEmpty) ?: ItemStack(Items.COMPASS)
}
private val NETWORK_CONTROLLER_ID = ResourceLocation.fromNamespaceAndPath("create_radar", "network_filterer")
