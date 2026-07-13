package de.teutonstudio.ccaeroworks.client.display

import de.teutonstudio.ccaeroworks.CCAeroworks
import dev.engine_room.flywheel.lib.model.baked.PartialModel

object DeskDisplayModels {
    private val horizontal = PartialModel.of(CCAeroworks.id("block/module/display_segment_horizontal"))
    private val vertical = PartialModel.of(CCAeroworks.id("block/module/display_segment_vertical"))

    data class Segment(val model: PartialModel, val x: Double, val z: Double)

    private val segments = mapOf(
        'a' to Segment(horizontal, 0.0, 0.14),
        'b' to Segment(vertical, 0.09, 0.07),
        'c' to Segment(vertical, 0.09, -0.07),
        'd' to Segment(horizontal, 0.0, -0.14),
        'e' to Segment(vertical, -0.09, -0.07),
        'f' to Segment(vertical, -0.09, 0.07),
        'g' to Segment(horizontal, 0.0, 0.0)
    )

    private val glyphs = mapOf(
        '0' to "abcdef", '1' to "bc", '2' to "abdeg", '3' to "abcdg", '4' to "bcfg",
        '5' to "acdfg", '6' to "acdefg", '7' to "abc", '8' to "abcdefg", '9' to "abcdfg",
        '-' to "g", ' ' to ""
    )

    @JvmStatic
    fun init() = Unit

    @JvmStatic
    fun segments(character: Char): List<Segment> = glyphs[character].orEmpty().mapNotNull(segments::get)
}
