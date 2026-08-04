package de.teutonstudio.ccaeroworks.recipe

import dan200.computercraft.shared.ModRegistry
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes
import de.teutonstudio.ccaeroworks.registry.CCItems
import de.teutonstudio.ccaeroworks.registry.CCRecipeSerializers
import net.minecraft.core.HolderLookup
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.CraftingBookCategory
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.item.crafting.CustomRecipe
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.level.Level

class ComputerControlDeskRecipe(category: CraftingBookCategory) : CustomRecipe(category) {
    override fun matches(input: CraftingInput, level: Level): Boolean =
        findIngredients(input) != null

    override fun assemble(input: CraftingInput, registries: HolderLookup.Provider): ItemStack {
        val ingredients = findIngredients(input) ?: return ItemStack.EMPTY
        val result = ItemStack(
            if (ingredients.advanced) CCItems.ADVANCED_COMPUTER_CONTROL_DESK.get()
            else CCItems.COMPUTER_CONTROL_DESK.get()
        )

        // Aeroworks' controller_contents and any future desk components are copied first.
        result.applyComponents(ingredients.desk.components)
        // Computer ID, label, terminal size and capacity then come from the CC computer.
        result.applyComponents(ingredients.computer.components)
        return result
    }

    override fun canCraftInDimensions(width: Int, height: Int): Boolean = width * height >= 2

    override fun getSerializer(): RecipeSerializer<ComputerControlDeskRecipe> =
        CCRecipeSerializers.COMPUTER_CONTROL_DESK.get()

    private fun findIngredients(input: CraftingInput): Ingredients? {
        var desk = ItemStack.EMPTY
        var computer = ItemStack.EMPTY
        var advanced = false

        for (slot in 0 until input.size()) {
            val stack = input.getItem(slot)
            if (stack.isEmpty) continue

            when {
                AeroworksTypes.isVanillaControlDesk(stack.item) -> {
                    if (!desk.isEmpty) return null
                    desk = stack
                }
                stack.`is`(ModRegistry.Items.COMPUTER_NORMAL.get()) -> {
                    if (!computer.isEmpty) return null
                    computer = stack
                    advanced = false
                }
                stack.`is`(ModRegistry.Items.COMPUTER_ADVANCED.get()) -> {
                    if (!computer.isEmpty) return null
                    computer = stack
                    advanced = true
                }
                else -> return null
            }
        }

        return if (!desk.isEmpty && !computer.isEmpty) Ingredients(desk, computer, advanced) else null
    }

    private data class Ingredients(
        val desk: ItemStack,
        val computer: ItemStack,
        val advanced: Boolean
    )
}
