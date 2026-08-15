package de.teutonstudio.ccaeroworks.client

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos

/** Client-only entry point kept separate from the common payload data object. */
object DeskIoOverviewClient {
    @Volatile
    private var preferredCategory: String? = null

    @JvmStatic
    fun preferCategory(category: String) {
        preferredCategory = category
    }

    @JvmStatic
    fun open(origin: BlockPos, json: String) {
        val category = preferredCategory
        preferredCategory = null
        Minecraft.getInstance().setScreen(
            DeskIoOverviewScreen(
                origin,
                json,
                category ?: DeskIoOverviewScreen.CATEGORY_CONTROL
            )
        )
    }
}
