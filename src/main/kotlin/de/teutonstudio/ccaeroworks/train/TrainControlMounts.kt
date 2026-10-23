package de.teutonstudio.ccaeroworks.train

import com.mred231.aeroworks.content.controls.console.ConsoleBlock
import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import com.mred231.aeroworks.content.controls.module.MountedModule
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.registry.CCModuleTypes
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.world.level.block.state.BlockState

/** Identifies the exact desk/module combination which may act as Create train controls. */
object TrainControlMounts {
    private const val MODULES_TAG = "Modules"
    private const val HEADS_TAG = "Heads"
    private const val TYPE_TAG = "Type"
    private val MODULE_ID = CCAeroworks.id("train_controls").toString()

    enum class Alignment { FORWARD, BACKWARD, SIDEWAYS }

    @JvmStatic
    fun hasTrainControls(desk: ConsoleBlockEntity): Boolean =
        (0 until desk.socketCount()).any { socket -> containsTrainControls(desk.module(socket)) }

    @JvmStatic
    fun hasTrainControls(state: BlockState, tag: CompoundTag?): Boolean =
        isSuitableConsole(state) && tag != null && hasTrainControls(tag)

    @JvmStatic
    fun hasTrainControls(tag: CompoundTag): Boolean {
        val modules = tag.getList(MODULES_TAG, Tag.TAG_COMPOUND.toInt())
        return (0 until modules.size).any { index -> containsTrainControls(modules.getCompound(index)) }
    }

    @JvmStatic
    fun isSuitableConsole(state: BlockState): Boolean =
        state.block is ConsoleBlock &&
            state.hasProperty(ConsoleBlock.CEILING) &&
            !state.getValue(ConsoleBlock.CEILING)

    @JvmStatic
    fun alignment(assemblyDirection: Direction, consoleFacing: Direction): Alignment = when {
        consoleFacing.axis != assemblyDirection.axis -> Alignment.SIDEWAYS
        consoleFacing == assemblyDirection -> Alignment.FORWARD
        else -> Alignment.BACKWARD
    }

    private fun containsTrainControls(module: MountedModule?): Boolean {
        if (module == null) return false
        if (module.type() === CCModuleTypes.TRAIN_CONTROLS || module.type() == CCModuleTypes.TRAIN_CONTROLS) return true
        return module.heads().values.any(::containsTrainControls)
    }

    private fun containsTrainControls(module: CompoundTag): Boolean {
        if (module.getString(TYPE_TAG) == MODULE_ID) return true
        val heads = module.getCompound(HEADS_TAG)
        return heads.allKeys.any { key -> containsTrainControls(heads.getCompound(key)) }
    }
}
