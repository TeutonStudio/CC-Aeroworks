package de.teutonstudio.ccaeroworks.client

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos

/** Client-only entry point kept separate from the common payload data object. */
object DeskIoOverviewClient {
    @JvmStatic
    fun open(origin: BlockPos, json: String) {
        Minecraft.getInstance().setScreen(DeskIoOverviewScreen(origin, json))
    }
}
