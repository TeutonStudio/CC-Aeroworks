package de.teutonstudio.ccaeroworks.registry

import com.mred231.aeroworks.AeroworksConsoles
import dan200.computercraft.shared.computer.core.ComputerFamily
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlock
import net.minecraft.world.level.block.state.BlockBehaviour
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredBlock
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

object CCBlocks {
    private val BLOCKS: DeferredRegister.Blocks = DeferredRegister.createBlocks(CCAeroworks.MOD_ID)

    private fun properties(): BlockBehaviour.Properties =
        BlockBehaviour.Properties.of().strength(2.0f, 6.0f).noOcclusion()

    @JvmField
    val COMPUTER_CONTROL_DESK: DeferredBlock<ComputerControlDeskBlock> = BLOCKS.register(
        "computer_control_desk",
        Supplier<ComputerControlDeskBlock> {
            ComputerControlDeskBlock(properties(), AeroworksConsoles.DESK, ComputerFamily.NORMAL)
        }
    )

    @JvmField
    val ADVANCED_COMPUTER_CONTROL_DESK: DeferredBlock<ComputerControlDeskBlock> = BLOCKS.register(
        "advanced_computer_control_desk",
        Supplier<ComputerControlDeskBlock> {
            ComputerControlDeskBlock(properties(), AeroworksConsoles.DESK, ComputerFamily.ADVANCED)
        }
    )

    fun register(bus: IEventBus) = BLOCKS.register(bus)
}
