package de.teutonstudio.ccaeroworks.multiblock

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import com.mred231.aeroworks.content.controls.console.DeskSkin
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity

/**
 * Keeps both Copycat zones on every loaded segment of one explicit assembly.
 * The guard is important because notifying a native ConsoleBlockEntity invokes
 * onSkinChanged again through the mixin hook.
 */
object ConsoleAssemblyStyles {
    private val applying = ThreadLocal.withInitial { false }

    @JvmStatic
    fun isApplying(): Boolean = applying.get()

    @JvmStatic
    fun onNativeSkinChanged(blockEntity: ConsoleBlockEntity) {
        if (isApplying()) return
        val level = blockEntity.level as? ServerLevel ?: return
        propagate(level, blockEntity.blockPos)
    }

    /**
     * Replaces Aeroworks' one-block payment for an explicit multi-segment row. The complete
     * transaction is validated before any inventory or skin is mutated.
     *
     * @return true when the native single-block mutation must be cancelled.
     */
    @JvmStatic
    fun applyPaidSkin(
        source: ConsoleBlockEntity,
        zone: DeskSkin.Zone,
        material: net.minecraft.world.level.block.state.BlockState,
        held: ItemStack,
        entity: LivingEntity
    ): Boolean {
        val level = source.level as? ServerLevel ?: return false
        if (ConsoleAssemblyRuntime.id(level, source.blockPos) == null) return false
        val row = ConsoleAssemblyRuntime.members(level, source.blockPos)
        val targets = row.positions.mapNotNull { pos ->
            (level.getBlockEntity(pos) as? ConsoleAssemblyStyleAccess)?.let { pos to it }
        }
        if (!row.complete || row.tooLong || targets.size != row.positions.size) {
            (entity as? Player)?.displayClientMessage(
                Component.literal("The complete console row must be loaded before changing its Copycat style"),
                true
            )
            return true
        }
        val creative = (entity as? Player)?.isCreative == true
        val price = targets.size
        if (!creative && held.count < price) {
            (entity as? Player)?.displayClientMessage(
                Component.literal("This row needs $price matching material blocks"),
                true
            )
            return true
        }

        val revision = targets.maxOfOrNull { it.second.ccaeroworks_getStyleRevision() }?.plus(1) ?: 1L
        applying {
            targets.forEach { (pos, target) ->
                val skin = target.ccaeroworks_getAssemblySkin()
                val returned = skin.clear(zone)
                if (!creative && !returned.isEmpty) returnItem(entity, returned)
                skin.set(zone, material, held.copyWithCount(1))
                target.ccaeroworks_setStyleRevision(revision)
                notifyChanged(level, pos, target)
            }
            if (!creative) held.consume(price, entity)
        }
        return true
    }

    @JvmStatic
    fun inherit(
        level: ServerLevel,
        sourcePos: BlockPos,
        targetPos: BlockPos,
        placer: LivingEntity? = null
    ): Boolean = inheritAll(level, sourcePos, listOf(targetPos), placer)

    /** Atomically pays for and copies a row style to several newly recruited segments. */
    @JvmStatic
    fun inheritAll(
        level: ServerLevel,
        sourcePos: BlockPos,
        targetPositions: Collection<BlockPos>,
        placer: LivingEntity? = null
    ): Boolean {
        val source = level.getBlockEntity(sourcePos) as? ConsoleAssemblyStyleAccess ?: return true
        val targets = targetPositions.map { pos ->
            val target = ConsoleAssemblyRuntime.access(level, pos) as? ConsoleAssemblyStyleAccess ?: return false
            pos to target
        }
        val payments = targets.flatMap { DeskSkin.Zone.entries }.mapNotNull { zone ->
            source.ccaeroworks_getAssemblySkin().item(zone).takeUnless(ItemStack::isEmpty)
        }
        val player = placer as? Player
        if (payments.isNotEmpty() && player?.isCreative != true) {
            val required = mutableListOf<Pair<ItemStack, Int>>()
            payments.forEach { payment ->
                val index = required.indexOfFirst { (item, _) ->
                    ItemStack.isSameItemSameComponents(item, payment)
                }
                if (index < 0) required += payment to 1
                else required[index] = required[index].first to required[index].second + 1
            }
            if (player == null || required.any { (item, amount) -> count(player, item) < amount }) {
                player?.displayClientMessage(
                    Component.literal("Joining this row needs matching material for every new Copycat zone"),
                    true
                )
                return false
            }
        }
        return applying {
            if (player != null && !player.isCreative) {
                payments.forEach { required -> take(player, required, 1) }
            }
            val revision = source.ccaeroworks_getStyleRevision()
            targets.forEach { (targetPos, target) ->
                copySkin(source, target)
                target.ccaeroworks_setStyleRevision(revision)
                notifyChanged(level, targetPos, target)
            }
            true
        }
    }

    /** Reject partially loaded or oversized rows instead of producing a partial style update. */
    @JvmStatic
    fun propagate(level: ServerLevel, sourcePos: BlockPos): Boolean {
        val row = ConsoleAssemblyRuntime.members(level, sourcePos)
        if (!row.complete || row.tooLong || row.positions.size > ConsoleAssemblyRuntime.MAX_SEGMENTS) return false
        val source = level.getBlockEntity(sourcePos) as? ConsoleAssemblyStyleAccess ?: return false
        val revision = row.positions.mapNotNull {
            (level.getBlockEntity(it) as? ConsoleAssemblyStyleAccess)?.ccaeroworks_getStyleRevision()
        }.maxOrNull()?.plus(1) ?: 1L
        return applying {
            row.positions.forEach { pos ->
                val target = level.getBlockEntity(pos) as? ConsoleAssemblyStyleAccess ?: return@forEach
                if (target !== source) copySkin(source, target)
                target.ccaeroworks_setStyleRevision(revision)
                notifyChanged(level, pos, target)
            }
            true
        }
    }

    /** Return style items owned by a Create segment before it falls back to native geometry. */
    @JvmStatic
    fun releaseControlStyle(level: ServerLevel, pos: BlockPos) {
        releaseControlStyle(level, pos, null)
    }

    @JvmStatic
    fun releaseControlStyle(level: ServerLevel, pos: BlockPos, breaker: Player?) {
        val target = level.getBlockEntity(pos) as? ConsoleAssemblyStyleAccess ?: return
        if (target is ConsoleBlockEntity) return
        applying {
            DeskSkin.Zone.entries.forEach { zone ->
                val returned = target.ccaeroworks_getAssemblySkin().clear(zone)
                if (!returned.isEmpty && breaker?.isCreative != true) Block.popResource(level, pos, returned)
            }
            target.ccaeroworks_setStyleRevision(target.ccaeroworks_getStyleRevision() + 1)
            notifyChanged(level, pos, target)
        }
    }

    /** Clears one zone throughout a row and returns the source segment's item to native code. */
    @JvmStatic
    fun clearPaidSkin(source: ConsoleBlockEntity, zone: DeskSkin.Zone, entity: LivingEntity?): ItemStack? {
        val level = source.level as? ServerLevel ?: return null
        if (ConsoleAssemblyRuntime.id(level, source.blockPos) == null) return null
        val row = ConsoleAssemblyRuntime.members(level, source.blockPos)
        if (!row.complete || row.tooLong) {
            (entity as? Player)?.displayClientMessage(
                Component.literal("The complete console row must be loaded before removing its Copycat style"),
                true
            )
            return ItemStack.EMPTY
        }
        var sourceReturn = ItemStack.EMPTY
        val creative = (entity as? Player)?.isCreative == true
        val targets = row.positions.mapNotNull { pos ->
            (level.getBlockEntity(pos) as? ConsoleAssemblyStyleAccess)?.let { pos to it }
        }
        val revision = targets.maxOfOrNull { it.second.ccaeroworks_getStyleRevision() }?.plus(1) ?: 1L
        applying {
            targets.forEach { (pos, target) ->
                val returned = target.ccaeroworks_getAssemblySkin().clear(zone)
                if (pos == source.blockPos) sourceReturn = returned
                else if (!creative && !returned.isEmpty && entity != null) returnItem(entity, returned)
                target.ccaeroworks_setStyleRevision(revision)
                notifyChanged(level, pos, target)
            }
        }
        return sourceReturn
    }

    private fun copySkin(source: ConsoleAssemblyStyleAccess, target: ConsoleAssemblyStyleAccess) {
        val sourceSkin = source.ccaeroworks_getAssemblySkin()
        val targetSkin = target.ccaeroworks_getAssemblySkin()
        DeskSkin.Zone.entries.forEach { zone ->
            targetSkin.clear(zone)
            val material = sourceSkin.material(zone) ?: return@forEach
            val paidItem = sourceSkin.item(zone)
            targetSkin.set(zone, material, if (paidItem.isEmpty) ItemStack.EMPTY else paidItem.copyWithCount(1))
        }
    }

    private fun notifyChanged(level: ServerLevel, pos: BlockPos, access: ConsoleAssemblyStyleAccess) {
        val blockEntity = access as? BlockEntity ?: return
        if (blockEntity is ConsoleBlockEntity) blockEntity.onSkinChanged() else blockEntity.setChanged()
        blockEntity.requestModelDataUpdate()
        level.sendBlockUpdated(pos, blockEntity.blockState, blockEntity.blockState, Block.UPDATE_CLIENTS)
    }

    private fun returnItem(entity: LivingEntity, stack: ItemStack) {
        val player = entity as? Player
        if (player == null || !player.inventory.add(stack)) entity.spawnAtLocation(stack)
    }

    private fun count(player: Player, required: ItemStack): Int =
        player.inventory.items.filter { ItemStack.isSameItemSameComponents(it, required) }.sumOf(ItemStack::getCount)

    private fun take(player: Player, required: ItemStack, amount: Int) {
        var remaining = amount
        player.inventory.items.forEach { candidate ->
            if (remaining <= 0 || !ItemStack.isSameItemSameComponents(candidate, required)) return@forEach
            val removed = minOf(candidate.count, remaining)
            candidate.shrink(removed)
            remaining -= removed
        }
        player.inventory.setChanged()
    }

    private inline fun <T> applying(action: () -> T): T {
        val previous = applying.get()
        applying.set(true)
        return try {
            action()
        } finally {
            applying.set(previous)
        }
    }
}
