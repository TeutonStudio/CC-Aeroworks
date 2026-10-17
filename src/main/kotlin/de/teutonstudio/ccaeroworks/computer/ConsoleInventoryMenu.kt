package de.teutonstudio.ccaeroworks.computer

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
import de.teutonstudio.ccaeroworks.registry.CCMenus
import net.minecraft.core.BlockPos
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

/** A live view of the row: never copies the computer into a temporary server inventory. */
class ConsoleInventoryMenu(id: Int, val inventory: Inventory, val anchor: BlockPos, private val host: ConsoleBlockEntity? = null) :
    AbstractContainerMenu(CCMenus.CONSOLE_INVENTORY.get(), id) {
    private fun snapshot() = ConsoleMultiblockManager.resolve(inventory.player.level(), anchor)
    private fun editable(): Boolean = host != null && !host.isRemoved &&
        snapshot().state in listOf(ConsoleNetworkState.NONE, ConsoleNetworkState.ACTIVE)

    private val computerInventory: Container = if (host == null) SimpleContainer(1) else object : Container {
        override fun getContainerSize() = 1
        override fun isEmpty() = getItem(0).isEmpty
        override fun getItem(slot: Int): ItemStack = snapshot().owner?.item() ?: ItemStack.EMPTY
        override fun removeItem(slot: Int, amount: Int): ItemStack {
            if (amount <= 0 || !editable()) return ItemStack.EMPTY
            return snapshot().owner?.host?.let(ConsoleComputers::remove) ?: ItemStack.EMPTY
        }
        override fun removeItemNoUpdate(slot: Int) = removeItem(slot, 1)
        override fun setItem(slot: Int, stack: ItemStack) {
            if (!editable()) return
            if (stack.isEmpty) { removeItem(slot, 1); return }
            if (!ConsoleComputers.accepts(stack)) return
            snapshot().owner?.host?.let(ConsoleComputers::remove)
            ConsoleComputers.install(host, stack.copyWithCount(1))
        }
        override fun setChanged() {}
        override fun stillValid(player: Player) = this@ConsoleInventoryMenu.stillValid(player)
        override fun clearContent() { removeItem(0, 1) }
    }

    init {
        addSlot(object : Slot(computerInventory, 0, 206, 20) {
            override fun mayPlace(stack: ItemStack) = ConsoleComputers.accepts(stack) && (host == null || editable())
            override fun mayPickup(player: Player) = host == null || editable()
            override fun getMaxStackSize() = 1
        })
        for (row in 0..2) for (column in 0..8)
            addSlot(Slot(inventory, column + row * 9 + 9, 17 + column * 18, 166 + row * 18))
        for (column in 0..8) addSlot(Slot(inventory, column, 17 + column * 18, 224))
    }

    override fun stillValid(player: Player): Boolean {
        if (host == null) return true
        val level = player.level()
        return level === host.level && level.hasChunkAt(anchor) && level.getBlockEntity(anchor) === host &&
            level.mayInteract(player, anchor) && player.distanceToSqr(anchor.center) <=
            (player.blockInteractionRange() + 1.0) * (player.blockInteractionRange() + 1.0)
    }

    override fun clicked(slotId: Int, button: Int, clickType: net.minecraft.world.inventory.ClickType, player: Player) {
        if (!stillValid(player)) return
        super.clicked(slotId, button, clickType, player)
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack {
        if (!stillValid(player) || index !in slots.indices) return ItemStack.EMPTY
        val slot = slots[index]
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY
        val stack = slot.item
        val original = stack.copy()
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size, true)) return ItemStack.EMPTY
        } else {
            if (!ConsoleComputers.accepts(stack) || !moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY
        }
        if (stack.isEmpty) slot.setByPlayer(ItemStack.EMPTY) else slot.setChanged()
        slot.onTake(player, stack)
        return original
    }
}
