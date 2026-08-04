package de.teutonstudio.ccaeroworks.registry

import com.mojang.serialization.Codec
import de.teutonstudio.ccaeroworks.CCAeroworks
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.Registries
import net.minecraft.network.codec.ByteBufCodecs
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

object CCDataComponents {
    private val COMPONENTS: DeferredRegister<DataComponentType<*>> =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CCAeroworks.MOD_ID)

    @JvmField
    val DESK_ID: DeferredHolder<DataComponentType<*>, DataComponentType<String>> = COMPONENTS.register(
        "desk_id",
        Supplier<DataComponentType<String>> {
            DataComponentType.builder<String>()
                .persistent(Codec.STRING)
                .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                .build()
        }
    )

    @JvmField
    val COMPUTER_POWERED: DeferredHolder<DataComponentType<*>, DataComponentType<Boolean>> = COMPONENTS.register(
        "computer_powered",
        Supplier<DataComponentType<Boolean>> {
            DataComponentType.builder<Boolean>()
                .persistent(Codec.BOOL)
                .networkSynchronized(ByteBufCodecs.BOOL)
                .build()
        }
    )

    fun register(bus: IEventBus) = COMPONENTS.register(bus)
}
