package de.teutonstudio.ccaeroworks.compat.jei

import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.registry.CCItems
import mezz.jei.api.IModPlugin
import mezz.jei.api.JeiPlugin
import mezz.jei.api.runtime.IJeiRuntime
import mezz.jei.api.constants.VanillaTypes

/** Legacy combined items remain loadable but are not player-facing ingredients. */
@JeiPlugin
class CCAeroworksJeiPlugin : IModPlugin {
    override fun getPluginUid() = CCAeroworks.id("jei_plugin")
    override fun onRuntimeAvailable(runtime: IJeiRuntime) {
        runtime.ingredientManager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, CCItems.computerConsoles().map { it.defaultInstance })
    }
}
