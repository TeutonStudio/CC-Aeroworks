package de.teutonstudio.ccaeroworks.mixin

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import net.minecraft.world.phys.Vec3
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Invoker
import java.util.function.Predicate

@Mixin(value = [ConsoleBlockEntity::class], remap = false)
interface ConsoleBlockEntityInvoker {
    @Invoker("mountSpots")
    fun ccaeroworks_mountSpots(): List<ConsoleBlockEntity.MountSpot>

    @Invoker("nearestMount")
    fun ccaeroworks_nearestMount(
        from: Vec3,
        to: Vec3,
        filter: Predicate<ConsoleBlockEntity.MountSpot>
    ): ConsoleBlockEntity.MountTarget?
}
