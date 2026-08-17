package de.teutonstudio.ccaeroworks.radarcompat.registry

import com.mred231.aeroworks.content.controls.ModuleItem
import de.teutonstudio.ccaeroworks.CCAeroworks
import net.minecraft.world.item.Item
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredItem
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

/** Radar items keep the cc_aeroworks namespace so existing worlds retain their registry IDs. */
object RadarItems {
    private val ITEMS: DeferredRegister.Items = DeferredRegister.createItems(CCAeroworks.MOD_ID)

    @JvmField
    val SMALL_RADAR_DISPLAY: DeferredItem<ModuleItem> = ITEMS.register(
        "small_radar_display",
        Supplier { ModuleItem(RadarModuleTypes.SMALL_RADAR, Item.Properties()) }
    )

    @JvmField
    val LARGE_RADAR_DISPLAY: DeferredItem<ModuleItem> = ITEMS.register(
        "large_radar_display",
        Supplier { ModuleItem(RadarModuleTypes.LARGE_RADAR, Item.Properties()) }
    )

    fun register(bus: IEventBus) = ITEMS.register(bus)
}
