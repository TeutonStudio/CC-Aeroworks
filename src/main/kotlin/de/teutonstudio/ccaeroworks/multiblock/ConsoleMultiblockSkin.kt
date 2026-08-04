package de.teutonstudio.ccaeroworks.multiblock

import net.minecraft.util.StringRepresentable
import net.minecraft.world.level.block.state.properties.EnumProperty

enum class ConsoleMultiblockSkin(
    private val serializedName: String
) : StringRepresentable {
    DEFAULT("default"),
    COMPUTER("computer"),
    ADVANCED("advanced");

    override fun getSerializedName(): String = serializedName
}

object ConsoleMultiblockSkinState {
    @JvmField
    val SKIN: EnumProperty<ConsoleMultiblockSkin> = EnumProperty.create(
        "cc_aeroworks_skin",
        ConsoleMultiblockSkin::class.java
    )

    @JvmStatic
    fun forMembers(members: Iterable<ConsoleMember>): ConsoleMultiblockSkin = when {
        members.any { it.kind == ConsoleMemberKind.ADVANCED_COMPUTER } ->
            ConsoleMultiblockSkin.ADVANCED

        members.any { it.kind == ConsoleMemberKind.COMPUTER } ->
            ConsoleMultiblockSkin.COMPUTER

        else -> ConsoleMultiblockSkin.DEFAULT
    }
}
