package de.teutonstudio.ccaeroworks.registry

import com.mred231.aeroworks.content.controls.module.ModuleItem
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.item.GuideBookContent
import de.teutonstudio.ccaeroworks.item.GuideBookItem
import de.teutonstudio.ccaeroworks.computer.ComputerConsoleVariant
import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlock
import dan200.computercraft.shared.computer.core.ComputerFamily
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredItem
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

object CCItems {
    private val ITEMS: DeferredRegister.Items = DeferredRegister.createItems(CCAeroworks.MOD_ID)

    @JvmField
    val TWO_DIGIT_DISPLAY: DeferredItem<ModuleItem> = ITEMS.register(
        "two_digit_display",
        Supplier { ModuleItem(CCModuleTypes.TWO_DIGIT, Item.Properties()) }
    )

    @JvmField
    val THREE_DIGIT_DISPLAY: DeferredItem<ModuleItem> = ITEMS.register(
        "three_digit_display",
        Supplier { ModuleItem(CCModuleTypes.THREE_DIGIT, Item.Properties()) }
    )


    @JvmField
    val COMPUTER_CONTROL_DESK: DeferredItem<BlockItem> = ITEMS.register(
        "computer_control_desk",
        Supplier { BlockItem(CCBlocks.COMPUTER_CONTROL_DESK.get(), Item.Properties().stacksTo(1)) }
    )

    @JvmField
    val ADVANCED_COMPUTER_CONTROL_DESK: DeferredItem<BlockItem> = ITEMS.register(
        "advanced_computer_control_desk",
        Supplier { BlockItem(CCBlocks.ADVANCED_COMPUTER_CONTROL_DESK.get(), Item.Properties().stacksTo(1)) }
    )

    @JvmField
    val COMPUTER_COPYCAT_CONTROL_DESK = registerConsoleItem("computer_copycat_control_desk", CCBlocks.COMPUTER_COPYCAT_CONTROL_DESK)

    @JvmField
    val ADVANCED_COMPUTER_COPYCAT_CONTROL_DESK = registerConsoleItem("advanced_computer_copycat_control_desk", CCBlocks.ADVANCED_COMPUTER_COPYCAT_CONTROL_DESK)

    @JvmField
    val COMPUTER_CONTROL_STAND = registerConsoleItem("computer_control_stand", CCBlocks.COMPUTER_CONTROL_STAND)

    @JvmField
    val ADVANCED_COMPUTER_CONTROL_STAND = registerConsoleItem("advanced_computer_control_stand", CCBlocks.ADVANCED_COMPUTER_CONTROL_STAND)

    @JvmField
    val COMPUTER_COPYCAT_CONTROL_STAND = registerConsoleItem("computer_copycat_control_stand", CCBlocks.COMPUTER_COPYCAT_CONTROL_STAND)

    @JvmField
    val ADVANCED_COMPUTER_COPYCAT_CONTROL_STAND = registerConsoleItem("advanced_computer_copycat_control_stand", CCBlocks.ADVANCED_COMPUTER_COPYCAT_CONTROL_STAND)

    private fun registerConsoleItem(
        path: String,
        block: net.neoforged.neoforge.registries.DeferredBlock<ComputerControlDeskBlock>
    ): DeferredItem<BlockItem> = ITEMS.register(
        path,
        Supplier { BlockItem(block.get(), Item.Properties().stacksTo(1)) }
    )

    fun computerConsole(variant: ComputerConsoleVariant, family: ComputerFamily): Item = when (variant) {
        ComputerConsoleVariant.DESK -> if (family == ComputerFamily.ADVANCED) ADVANCED_COMPUTER_CONTROL_DESK.get() else COMPUTER_CONTROL_DESK.get()
        ComputerConsoleVariant.COPYCAT_DESK -> if (family == ComputerFamily.ADVANCED) ADVANCED_COMPUTER_COPYCAT_CONTROL_DESK.get() else COMPUTER_COPYCAT_CONTROL_DESK.get()
        ComputerConsoleVariant.STAND -> if (family == ComputerFamily.ADVANCED) ADVANCED_COMPUTER_CONTROL_STAND.get() else COMPUTER_CONTROL_STAND.get()
        ComputerConsoleVariant.COPYCAT_STAND -> if (family == ComputerFamily.ADVANCED) ADVANCED_COMPUTER_COPYCAT_CONTROL_STAND.get() else COMPUTER_COPYCAT_CONTROL_STAND.get()
    }

    fun computerConsoles(): List<Item> = ComputerConsoleVariant.entries.flatMap { variant ->
        listOf(
            computerConsole(variant, ComputerFamily.NORMAL),
            computerConsole(variant, ComputerFamily.ADVANCED)
        )
    }

    @JvmField
    val GUIDE_BOOK: DeferredItem<GuideBookItem> = ITEMS.register(
        "guide_book",
        Supplier {
            GuideBookItem(
                Item.Properties()
                    .stacksTo(1)
                    .component(DataComponents.WRITTEN_BOOK_CONTENT, GuideBookContent.create())
            )
        }
    )

    fun register(bus: IEventBus) = ITEMS.register(bus)
}
