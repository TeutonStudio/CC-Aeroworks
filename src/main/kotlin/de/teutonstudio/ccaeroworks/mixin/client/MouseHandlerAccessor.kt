package de.teutonstudio.ccaeroworks.mixin.client

import net.minecraft.client.MouseHandler
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

@Mixin(MouseHandler::class)
interface MouseHandlerAccessor {
    @Accessor("accumulatedDY")
    fun ccaeroworks_getAccumulatedDY(): Double
}
