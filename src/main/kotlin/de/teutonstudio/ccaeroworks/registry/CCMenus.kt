package de.teutonstudio.ccaeroworks.registry

import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.computer.ConsoleInventoryMenu
import net.minecraft.core.registries.Registries
import net.minecraft.world.inventory.MenuType
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

object CCMenus {
    private val MENUS = DeferredRegister.create(Registries.MENU, CCAeroworks.MOD_ID)
    val CONSOLE_INVENTORY = MENUS.register("console_inventory", Supplier<MenuType<ConsoleInventoryMenu>> {
        IMenuTypeExtension.create { id, inventory, buffer -> ConsoleInventoryMenu(id, inventory, buffer.readBlockPos()) }
    })
    fun register(bus: IEventBus) = MENUS.register(bus)
}
