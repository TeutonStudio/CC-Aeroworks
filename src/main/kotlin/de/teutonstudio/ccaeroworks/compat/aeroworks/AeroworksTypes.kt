package de.teutonstudio.ccaeroworks.compat.aeroworks

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import com.mred231.aeroworks.content.controls.ConsoleDeskBlock
import de.teutonstudio.ccaeroworks.CCAeroworks
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType

object AeroworksTypes {
    @JvmField
    val CONTROL_DESK_ID: ResourceLocation =
        ResourceLocation.fromNamespaceAndPath("aeroworks", "control_desk")

    @JvmField
    val CONSOLE_BLOCK_ENTITY_ID: ResourceLocation =
        ResourceLocation.fromNamespaceAndPath("aeroworks", "console")

    @Suppress("UNCHECKED_CAST")
    fun consoleBlockEntityType(): BlockEntityType<ConsoleBlockEntity> {
        val value = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(CONSOLE_BLOCK_ENTITY_ID)
        return value as? BlockEntityType<ConsoleBlockEntity>
            ?: error("[CC-Aeroworks] Missing or incompatible block entity type $CONSOLE_BLOCK_ENTITY_ID")
    }

    fun isVanillaControlDesk(block: Block): Boolean =
        block is ConsoleDeskBlock && !isComputerControlDesk(block)

    fun isVanillaControlDesk(item: Item): Boolean =
        item is BlockItem && isVanillaControlDesk(item.block)

    fun isComputerControlDesk(block: Block): Boolean {
        val id = BuiltInRegistries.BLOCK.getKey(block)
        return id.namespace == CCAeroworks.MOD_ID &&
            (id.path == "computer_control_desk" || id.path == "advanced_computer_control_desk")
    }

    fun isControlDesk(block: Block): Boolean = block is ConsoleDeskBlock
}
