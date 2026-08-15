package de.teutonstudio.ccaeroworks.compat.jei

import dan200.computercraft.shared.ModRegistry
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes
import de.teutonstudio.ccaeroworks.recipe.ComputerControlDeskRecipe
import de.teutonstudio.ccaeroworks.registry.CCItems
import mezz.jei.api.IModPlugin
import mezz.jei.api.JeiPlugin
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder
import mezz.jei.api.gui.ingredient.ICraftingGridHelper
import mezz.jei.api.recipe.IFocusGroup
import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension
import mezz.jei.api.registration.IVanillaCategoryExtensionRegistration
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.RecipeHolder

/**
 * Teaches JEI how the dynamic ComputerControlDesk recipe maps the CC computer tier
 * to the matching desk tier. The real recipe stays dynamic so item components can
 * still be copied from both input stacks when crafting.
 */
@JeiPlugin
class CCAeroworksJeiPlugin : IModPlugin {
    override fun getPluginUid(): ResourceLocation = CCAeroworks.id("jei_plugin")

    override fun registerVanillaCategoryExtensions(registration: IVanillaCategoryExtensionRegistration) {
        registration.craftingCategory.addExtension(
            ComputerControlDeskRecipe::class.java,
            ComputerControlDeskCraftingExtension
        )
    }
}

private object ComputerControlDeskCraftingExtension : ICraftingCategoryExtension<ComputerControlDeskRecipe> {
    override fun setRecipe(
        recipeHolder: RecipeHolder<ComputerControlDeskRecipe>,
        builder: IRecipeLayoutBuilder,
        craftingGridHelper: ICraftingGridHelper,
        focuses: IFocusGroup
    ) {
        val controlDesk = ItemStack(AeroworksTypes.vanillaControlDeskBlock())
        val normalComputer = ItemStack(ModRegistry.Items.COMPUTER_NORMAL.get())
        val advancedComputer = ItemStack(ModRegistry.Items.COMPUTER_ADVANCED.get())
        val normalDesk = ItemStack(CCItems.COMPUTER_CONTROL_DESK.get())
        val advancedDesk = ItemStack(CCItems.ADVANCED_COMPUTER_CONTROL_DESK.get())

        val inputSlots = craftingGridHelper.createAndSetInputs(
            builder,
            listOf(
                listOf(controlDesk),
                listOf(normalComputer, advancedComputer)
            ),
            0,
            0
        )
        val outputSlot = craftingGridHelper.createAndSetOutputs(
            builder,
            listOf(normalDesk, advancedDesk)
        )

        // Keep the displayed CC computer and result tier synchronized while JEI cycles variants.
        if (inputSlots.size > 1) {
            builder.createFocusLink(inputSlots[1], outputSlot)
        }
        builder.setShapeless()
    }
}
