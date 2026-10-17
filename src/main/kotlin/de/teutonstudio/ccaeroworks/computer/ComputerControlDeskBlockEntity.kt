package de.teutonstudio.ccaeroworks.computer

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState

/** Registry compatibility for old worlds. Converted to a native console on the server. */
class ComputerControlDeskBlockEntity(type: BlockEntityType<*>, pos: BlockPos, state: BlockState) :
    ConsoleBlockEntity(type, pos, state)
