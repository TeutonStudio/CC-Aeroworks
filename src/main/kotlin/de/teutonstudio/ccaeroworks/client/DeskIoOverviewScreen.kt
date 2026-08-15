package de.teutonstudio.ccaeroworks.client

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import com.mred231.aeroworks.content.controls.ConsoleScreenOpener
import de.teutonstudio.ccaeroworks.computer.ControlDeskUiSwitchState
import de.teutonstudio.ccaeroworks.network.RequestDeskIoOverviewPayload
import de.teutonstudio.ccaeroworks.network.SwitchControlDeskUiPayload
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.network.PacketDistributor

/** Unified overview for grouped controls, displays, information sources and wire outputs. */
class DeskIoOverviewScreen(
    private val origin: BlockPos,
    private val snapshotJson: String,
    private val initialCategory: String = CATEGORY_CONTROL,
    private val initialPage: Int = 0
) : Screen(Component.literal("ControlDesk I/O")) {
    private val snapshot: JsonObject = runCatching {
        JsonParser.parseString(snapshotJson).asJsonObject
    }.getOrElse { JsonObject() }

    private val objects: JsonArray = snapshot.getAsJsonArray("objects") ?: JsonArray()
    private var category: String = initialCategory.takeIf(CATEGORIES::contains) ?: CATEGORY_CONTROL
    private var page: Int = initialPage

    override fun init() {
        val panelWidth = minOf(390, width - 24)
        val left = (width - panelWidth) / 2
        val tabY = 42
        val tabGap = 4
        val tabWidth = (panelWidth - tabGap * 3) / 4

        CATEGORIES.forEachIndexed { index, key ->
            val count = count(key)
            addRenderableWidget(
                Button.builder(Component.literal("${categoryLabel(key)} ($count)")) {
                    minecraft?.setScreen(DeskIoOverviewScreen(origin, snapshotJson, key, 0))
                }.bounds(left + index * (tabWidth + tabGap), tabY, tabWidth, 20).build().also {
                    it.active = key != category
                }
            )
        }

        val rows = categoryObjects()
        val maxPage = if (rows.isEmpty()) 0 else (rows.size - 1) / ROWS_PER_PAGE
        page = page.coerceIn(0, maxPage)
        val from = page * ROWS_PER_PAGE
        rows.drop(from).take(ROWS_PER_PAGE).forEachIndexed { index, objectJson ->
            val y = 70 + index * 23
            val button = Button.builder(Component.literal(rowLabel(objectJson))) {
                activateObject(objectJson)
            }.bounds(left, y, panelWidth, 20).build()
            button.active = when (objectJson.string("category")) {
                CATEGORY_CONTROL -> objectJson.string("kind") != "channel_group"
                CATEGORY_DISPLAY -> true
                else -> false
            }
            addRenderableWidget(button)
        }

        if (maxPage > 0) {
            addRenderableWidget(
                Button.builder(Component.literal("<")) {
                    minecraft?.setScreen(DeskIoOverviewScreen(origin, snapshotJson, category, page - 1))
                }.bounds(left, height - 54, 24, 20).build().also { it.active = page > 0 }
            )
            addRenderableWidget(
                Button.builder(Component.literal("${page + 1}/${maxPage + 1}")) {}
                    .bounds(left + 28, height - 54, 52, 20).build().also { it.active = false }
            )
            addRenderableWidget(
                Button.builder(Component.literal(">")) {
                    minecraft?.setScreen(DeskIoOverviewScreen(origin, snapshotJson, category, page + 1))
                }.bounds(left + 84, height - 54, 24, 20).build().also { it.active = page < maxPage }
            )
        }

        val bottomY = height - 28
        addRenderableWidget(
            Button.builder(Component.literal("Refresh")) {
                PacketDistributor.sendToServer(RequestDeskIoOverviewPayload(origin))
            }.bounds(left, bottomY, 80, 20).build()
        )
        addRenderableWidget(
            Button.builder(Component.literal("Computer")) {
                PacketDistributor.sendToServer(SwitchControlDeskUiPayload())
            }.bounds(left + 84, bottomY, 92, 20).build()
        )
        addRenderableWidget(
            Button.builder(Component.literal("Done")) { onClose() }
                .bounds(left + panelWidth - 80, bottomY, 80, 20).build()
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        val panelWidth = minOf(390, width - 24)
        val left = (width - panelWidth) / 2
        guiGraphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF)
        val state = snapshot.string("state").ifEmpty { "unknown" }
        val revision = snapshot.long("revision")
        guiGraphics.drawString(font, "State: $state · revision $revision", left, 29, 0xA0A0A0, false)
        if (categoryObjects().isEmpty()) {
            guiGraphics.drawCenteredString(font, Component.literal("No objects in this category"), width / 2, 88, 0x808080)
        }
    }

    override fun isPauseScreen(): Boolean = false

    private fun activateObject(objectJson: JsonObject) {
        when (objectJson.string("category")) {
            CATEGORY_CONTROL -> if (objectJson.string("kind") != "channel_group") openNativeControls(objectJson)
            CATEGORY_DISPLAY -> minecraft?.setScreen(
                DeskIoDisplayConfigScreen(origin, snapshotJson, objectJson.toString())
            )
        }
    }

    private fun openNativeControls(objectJson: JsonObject) {
        val level = minecraft?.level ?: return
        val pos = BlockPos(objectJson.int("memberX"), objectJson.int("memberY"), objectJson.int("memberZ"))
        val desk = level.getBlockEntity(pos) as? ConsoleBlockEntity ?: return
        ControlDeskUiSwitchState.rememberClientOverview(desk)
        ConsoleScreenOpener.open(desk)
    }

    private fun categoryObjects(): List<JsonObject> = objects
        .mapNotNull { element -> element.takeIf { it.isJsonObject }?.asJsonObject }
        .filter { it.string("category") == category }

    private fun count(key: String): Int {
        val counts = snapshot.getAsJsonObject("counts") ?: return 0
        return counts.get(key)?.takeUnless { it.isJsonNull }?.runCatching { asInt }?.getOrNull() ?: 0
    }

    private fun rowLabel(value: JsonObject): String {
        val label = value.string("label").ifEmpty { value.string("kind") }
        val summary = when (value.string("category")) {
            CATEGORY_CONTROL -> if (value.string("kind") == "channel_group") {
                val available = value.int("availableCount")
                val total = value.int("memberCount")
                "group · $available/$total available"
            } else {
                value.getAsJsonObject("values")?.entrySet()
                    ?.joinToString(", ") { "${it.key}=${it.value.asInt}" }
                    .orEmpty()
            }
            CATEGORY_DISPLAY -> displaySummary(value)
            CATEGORY_INFORMATION -> value.string("summary")
            CATEGORY_OUTPUT -> {
                val signal = value.int("value")
                val connections = value.int("connections")
                val backend = value.string("backend")
                "$signal/15 · $connections link${if (connections == 1) "" else "s"} · $backend"
            }
            else -> ""
        }
        return truncate(if (summary.isBlank()) label else "$label   [$summary]", 70)
    }

    private fun displaySummary(value: JsonObject): String {
        val binding = value.getAsJsonObject("binding") ?: return value.string("kind")
        val content = binding.getAsJsonObject("content")?.string("type").orEmpty()
        val input = binding.getAsJsonObject("input")?.string("type").orEmpty()
        return listOf(content, input).filter(String::isNotBlank).joinToString(" / ")
    }

    companion object {
        private const val ROWS_PER_PAGE = 7
        const val CATEGORY_CONTROL = "control"
        const val CATEGORY_DISPLAY = "display"
        const val CATEGORY_INFORMATION = "information"
        const val CATEGORY_OUTPUT = "output"
        private val CATEGORIES = listOf(CATEGORY_CONTROL, CATEGORY_DISPLAY, CATEGORY_INFORMATION, CATEGORY_OUTPUT)

        internal fun jsonString(value: JsonObject, name: String): String =
            value.get(name)?.takeUnless { it.isJsonNull }?.runCatching { asString }?.getOrNull().orEmpty()

        internal fun jsonInt(value: JsonObject, name: String): Int =
            value.get(name)?.takeUnless { it.isJsonNull }?.runCatching { asInt }?.getOrNull() ?: 0

        private fun JsonObject.string(name: String): String = jsonString(this, name)
        private fun JsonObject.int(name: String): Int = jsonInt(this, name)
        private fun JsonObject.long(name: String): Long =
            get(name)?.takeUnless { it.isJsonNull }?.runCatching { asLong }?.getOrNull() ?: 0L

        private fun categoryLabel(key: String): String = when (key) {
            CATEGORY_CONTROL -> "Channels"
            CATEGORY_DISPLAY -> "Displays"
            CATEGORY_INFORMATION -> "Information"
            CATEGORY_OUTPUT -> "Wire outputs"
            else -> key
        }

        private fun truncate(value: String, maximum: Int): String =
            if (value.length <= maximum) value else value.take(maximum - 1) + "…"
    }
}
