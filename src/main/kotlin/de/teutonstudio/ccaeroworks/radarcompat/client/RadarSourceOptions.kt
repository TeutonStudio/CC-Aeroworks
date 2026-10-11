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
        choice.ingressPos == null -> Component.translatable("radar_source.cc_aeroworks.local")
        descriptor != null -> Component.translatable("radar_source.cc_aeroworks.desk", descriptor.memberIndex)
        else -> Component.translatable("radar_source.cc_aeroworks.unavailable")
    }
    val position = descriptor?.ingressPos ?: choice.ingressPos
    val status = descriptor?.status?.let {
        Component.translatable("radar_status.cc_aeroworks.${it.name.lowercase(java.util.Locale.ROOT)}")
    } ?: Component.translatable("radar_source.cc_aeroworks.unavailable")
    val subtitle = if (position == null) status else Component.translatable(
        "radar_source.cc_aeroworks.position_status", position.x, position.y, position.z, status
    )
    return SourceSelectorOption(
        key = radarSourceKey(choice.ingressPos),
        value = choice,
        presentation = SourceSelectorPresentation(title, subtitle, SourceSelectorIcon.Item(stack)),
        selectable = descriptor != null || choice.ingressPos == null
    )
}

/** Keep unavailable stored selections visible instead of silently displaying another source. */
internal fun radarSourceChoices(
    deskPos: BlockPos,
    descriptors: List<RadarSourceDescriptor>,
    selectedIngress: BlockPos?
): List<RadarSourceChoice> = buildList {
    add(RadarSourceChoice(null, descriptors.firstOrNull { it.ingressPos == deskPos }))
    descriptors.distinctBy { it.ingressPos }.filter { it.ingressPos != deskPos }.forEach {
        add(RadarSourceChoice(it.ingressPos, it))
    }
    if (selectedIngress != null && none { it.ingressPos == selectedIngress }) {
        add(RadarSourceChoice(selectedIngress, descriptors.firstOrNull { it.ingressPos == selectedIngress }))
    }
}

private fun networkControllerStack(): ItemStack {
    val block = BuiltInRegistries.BLOCK.getOptional(NETWORK_CONTROLLER_ID).orElse(null)
    return if (block == null) ItemStack(Items.COMPASS) else ItemStack(block).takeUnless(ItemStack::isEmpty) ?: ItemStack(Items.COMPASS)
}
private val NETWORK_CONTROLLER_ID = ResourceLocation.fromNamespaceAndPath("create_radar", "network_filterer")
