package de.teutonstudio.ccaeroworks.registry

import com.mred231.aeroworks.content.controls.ModuleItem
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.item.GuideBookContent
import de.teutonstudio.ccaeroworks.item.GuideBookItem
import net.minecraft.core.component.DataComponents
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
