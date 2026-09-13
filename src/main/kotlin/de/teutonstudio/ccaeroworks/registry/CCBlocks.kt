package de.teutonstudio.ccaeroworks.registry

import dan200.computercraft.shared.computer.core.ComputerFamily
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes
import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlock
import de.teutonstudio.ccaeroworks.computer.ComputerConsoleVariant
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
            ComputerControlDeskBlock(
                properties(),
                AeroworksTypes.vanillaControlDeskBlock().type(),
                ComputerFamily.NORMAL,
                ComputerConsoleVariant.DESK
            )
        }
    )

    @JvmField
    val ADVANCED_COMPUTER_CONTROL_DESK: DeferredBlock<ComputerControlDeskBlock> = BLOCKS.register(
        "advanced_computer_control_desk",
        Supplier<ComputerControlDeskBlock> {
            ComputerControlDeskBlock(
                properties(),
                AeroworksTypes.vanillaControlDeskBlock().type(),
                ComputerFamily.ADVANCED,
                ComputerConsoleVariant.DESK
            )
        }
    )

    @JvmField
    val COMPUTER_COPYCAT_CONTROL_DESK = registerConsole(ComputerConsoleVariant.COPYCAT_DESK, ComputerFamily.NORMAL)

    @JvmField
    val ADVANCED_COMPUTER_COPYCAT_CONTROL_DESK = registerConsole(ComputerConsoleVariant.COPYCAT_DESK, ComputerFamily.ADVANCED)

    @JvmField
    val COMPUTER_CONTROL_STAND = registerConsole(ComputerConsoleVariant.STAND, ComputerFamily.NORMAL)

    @JvmField
    val ADVANCED_COMPUTER_CONTROL_STAND = registerConsole(ComputerConsoleVariant.STAND, ComputerFamily.ADVANCED)

    @JvmField
    val COMPUTER_COPYCAT_CONTROL_STAND = registerConsole(ComputerConsoleVariant.COPYCAT_STAND, ComputerFamily.NORMAL)

    @JvmField
    val ADVANCED_COMPUTER_COPYCAT_CONTROL_STAND = registerConsole(ComputerConsoleVariant.COPYCAT_STAND, ComputerFamily.ADVANCED)

    private fun registerConsole(
        variant: ComputerConsoleVariant,
        family: ComputerFamily
    ): DeferredBlock<ComputerControlDeskBlock> {
        val prefix = if (family == ComputerFamily.ADVANCED) "advanced_" else ""
        return BLOCKS.register(
            prefix + variant.itemPath,
            Supplier {
                ComputerControlDeskBlock(
                    properties(),
                    AeroworksTypes.controlDeskBlock(variant.aeroworksPath).type(),
                    family,
                    variant
                )
            }
        )
    }

    fun computerConsole(
        variant: ComputerConsoleVariant,
        family: ComputerFamily
    ): DeferredBlock<ComputerControlDeskBlock> = when (variant) {
        ComputerConsoleVariant.DESK -> if (family == ComputerFamily.ADVANCED) ADVANCED_COMPUTER_CONTROL_DESK else COMPUTER_CONTROL_DESK
        ComputerConsoleVariant.COPYCAT_DESK -> if (family == ComputerFamily.ADVANCED) ADVANCED_COMPUTER_COPYCAT_CONTROL_DESK else COMPUTER_COPYCAT_CONTROL_DESK
        ComputerConsoleVariant.STAND -> if (family == ComputerFamily.ADVANCED) ADVANCED_COMPUTER_CONTROL_STAND else COMPUTER_CONTROL_STAND
        ComputerConsoleVariant.COPYCAT_STAND -> if (family == ComputerFamily.ADVANCED) ADVANCED_COMPUTER_COPYCAT_CONTROL_STAND else COMPUTER_COPYCAT_CONTROL_STAND
    }

    fun register(bus: IEventBus) = BLOCKS.register(bus)
}
