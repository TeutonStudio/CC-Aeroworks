package de.teutonstudio.ccaeroworks.mixin

import com.mred231.aeroworks.content.controls.console.ConsoleBlock
import dan200.computercraft.shared.common.IBundledRedstoneBlock
import de.teutonstudio.ccaeroworks.computer.ConsoleComputers
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import org.spongepowered.asm.mixin.Mixin

@Mixin(value = [ConsoleBlock::class], remap = false)
abstract class ConsoleBlockBundledComputerMixin : IBundledRedstoneBlock {
    override fun getBundledRedstoneOutput(world: Level, pos: BlockPos, side: Direction): Int =
        ConsoleComputers.get(world.getBlockEntity(pos))?.bundledRedstoneOutput(side) ?: 0
}
