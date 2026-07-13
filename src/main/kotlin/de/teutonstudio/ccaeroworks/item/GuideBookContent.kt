package de.teutonstudio.ccaeroworks.item

import net.minecraft.network.chat.Component
import net.minecraft.server.network.Filterable
import net.minecraft.world.item.component.WrittenBookContent

object GuideBookContent {
    fun create(): WrittenBookContent = WrittenBookContent(
        Filterable.passThrough("CC-Aeroworks API"),
        "TeutonStudio",
        0,
        (1..8).map { page -> Filterable.passThrough(Component.translatable("book.cc_aeroworks.page_$page")) },
        true
    )
}
