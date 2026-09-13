package de.teutonstudio.ccaeroworks.compat.aeroworks

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import com.mred231.aeroworks.content.controls.console.ConsoleDeskBlock
import de.teutonstudio.ccaeroworks.computer.ComputerConsoleVariant
import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlock
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

    fun controlDeskBlock(path: String): ConsoleDeskBlock {
        val requested = ResourceLocation.fromNamespaceAndPath("aeroworks", path)
        val registered = BuiltInRegistries.BLOCK.get(requested)
        if (registered is ConsoleDeskBlock && !isComputerControlDesk(registered)) {
            return registered
        }

        return BuiltInRegistries.BLOCK
            .filterIsInstance<ConsoleDeskBlock>()
            .firstOrNull { block ->
                    BuiltInRegistries.BLOCK.getKey(block) == requested &&
                    !isComputerControlDesk(block)
            }
            ?: error("[CC-Aeroworks] Missing Aeroworks console block $requested")
    }

    fun vanillaControlDeskBlock(): ConsoleDeskBlock = controlDeskBlock("control_desk")

    fun variant(item: Item): ComputerConsoleVariant? {
        val block = (item as? BlockItem)?.block ?: return null
        if (!isVanillaControlDesk(block)) return null
        val path = BuiltInRegistries.BLOCK.getKey(block).path
        return ComputerConsoleVariant.entries.firstOrNull { it.aeroworksPath == path }
    }

    fun isVanillaControlDesk(block: Block): Boolean =
        block is ConsoleDeskBlock && !isComputerControlDesk(block)

    fun isVanillaControlDesk(item: Item): Boolean =
        item is BlockItem && isVanillaControlDesk(item.block)

    fun isComputerControlDesk(block: Block): Boolean = block is ComputerControlDeskBlock

    fun isControlDesk(block: Block): Boolean = block is ConsoleDeskBlock
}
