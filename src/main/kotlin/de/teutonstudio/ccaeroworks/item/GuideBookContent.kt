package de.teutonstudio.ccaeroworks.item

import net.minecraft.network.chat.Component
import net.minecraft.server.network.Filterable
import net.minecraft.world.item.component.WrittenBookContent
import net.neoforged.fml.ModList

object GuideBookContent {
    fun create(): WrittenBookContent {
        val pages = buildList {
            addAll(1..6)
            if (ModList.get().isLoaded("create_radar")) add(7)
            add(8)
        }
        return WrittenBookContent(
            Filterable.passThrough("CC-Aeroworks Manual / API"),
            "TeutonStudio",
            0,
            pages.map { page -> Filterable.passThrough(Component.translatable("book.cc_aeroworks.page_$page")) },
            true
        )
    }
}
