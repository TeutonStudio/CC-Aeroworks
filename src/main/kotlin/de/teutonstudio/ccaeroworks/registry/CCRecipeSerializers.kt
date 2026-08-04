package de.teutonstudio.ccaeroworks.registry

import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.recipe.ComputerControlDeskRecipe
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

object CCRecipeSerializers {
    private val SERIALIZERS: DeferredRegister<RecipeSerializer<*>> =
        DeferredRegister.create(Registries.RECIPE_SERIALIZER, CCAeroworks.MOD_ID)

    @JvmField
    val COMPUTER_CONTROL_DESK:
        DeferredHolder<RecipeSerializer<*>, RecipeSerializer<ComputerControlDeskRecipe>> =
        SERIALIZERS.register(
            "computer_control_desk",
            Supplier<RecipeSerializer<ComputerControlDeskRecipe>> {
                SimpleCraftingRecipeSerializer(::ComputerControlDeskRecipe)
            }
        )

    fun register(bus: IEventBus) = SERIALIZERS.register(bus)
}
