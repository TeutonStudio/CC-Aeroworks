package de.teutonstudio.ccaeroworks.mixin.client

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import de.teutonstudio.ccaeroworks.client.creative.AeroworksCreativeSections
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters
import org.spongepowered.asm.mixin.Mixin

@Mixin(CreativeModeTab::class)
abstract class CreativeModeTabSectionsMixin {
    @WrapMethod(method = ["buildContents"])
    private fun arrangeAeroworksSections(parameters: ItemDisplayParameters, original: Operation<Void>) {
        original.call(parameters)
        AeroworksCreativeSections.arrange(this as CreativeModeTab)
    }
}
