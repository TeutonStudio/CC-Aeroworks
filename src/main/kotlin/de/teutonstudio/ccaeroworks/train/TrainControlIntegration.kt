package de.teutonstudio.ccaeroworks.train

import com.mred231.aeroworks.content.controls.console.ConsoleBlock
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour
import net.minecraft.core.registries.BuiltInRegistries
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent

object TrainControlIntegration {
    @JvmStatic
    fun commonSetup(event: FMLCommonSetupEvent) {
        event.enqueueWork {
            val interaction = TrainControlsMovingInteraction()
            BuiltInRegistries.BLOCK
                .filterIsInstance<ConsoleBlock>()
                .filter { MovingInteractionBehaviour.REGISTRY.get(it) == null }
                .forEach { MovingInteractionBehaviour.REGISTRY.register(it, interaction) }
        }
    }
}
