package de.teutonstudio.ccaeroworks.client

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import de.teutonstudio.ccaeroworks.display.DisplayBindings
import de.teutonstudio.ccaeroworks.network.RequestDeskIoOverviewPayload
import de.teutonstudio.ccaeroworks.network.SetDisplayScriptSourcePayload
import de.teutonstudio.ccaeroworks.network.SetDisplayTouchScriptPayload
import de.teutonstudio.ccaeroworks.network.SetRadarDisplaySourcePayload
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.network.PacketDistributor

/** Detail editor for the two independent display binding axes. */
class DeskIoDisplayConfigScreen(
    private val origin: BlockPos,
    private val overviewJson: String,
    objectJson: String
) : Screen(Component.literal("Display configuration")) {
    private val display: JsonObject = JsonParser.parseString(objectJson).asJsonObject
    private val binding: JsonObject = display.getAsJsonObject("binding") ?: JsonObject()
    private val content: JsonObject = binding.getAsJsonObject("content") ?: JsonObject()
    private val input: JsonObject = binding.getAsJsonObject("input") ?: JsonObject()
    private val radarSources: JsonArray = display.getAsJsonArray("radarSources") ?: JsonArray()

    private val deskPos = BlockPos(int(display, "memberX"), int(display, "memberY"), int(display, "memberZ"))
    private val socket = int(display, "socket")
    private val moduleId = string(display, "moduleId")
    private val isRadar = string(display, "kind") == "radar_display"
    private val supportsScriptSource = !isRadar && moduleId.endsWith(":three_digit_display")
    private val supportsInput = moduleId.endsWith(":three_digit_display") || moduleId.endsWith(":large_radar_display")

    private var contentScript = string(content, "type") == "script_source"
    private var inputScript = string(input, "type") == "lua_handler"
    private var radarIndex = initialRadarIndex()
    private lateinit var contentPath: EditBox
    private lateinit var inputPath: EditBox
    private var radarButton: Button? = null

    override fun init() {
        val panelWidth = minOf(360, width - 24)
        val left = (width - panelWidth) / 2
        var y = 54

        if (isRadar) {
            radarButton = addRenderableWidget(
                Button.builder(radarLabel()) {
                    radarIndex = (radarIndex + 1) % (radarSources.size() + 1)
                    radarButton?.setMessage(radarLabel())
                }.bounds(left, y + 14, panelWidth, 20).build()
            )
            y += 48
        } else if (supportsScriptSource) {
            val modeButton = Button.builder(contentModeLabel()) { button ->
                contentScript = !contentScript
                button.setMessage(contentModeLabel())
                contentPath.active = contentScript
            }.bounds(left, y + 14, 110, 20).build()
            addRenderableWidget(modeButton)
            contentPath = EditBox(font, left + 116, y + 14, panelWidth - 116, 20, Component.literal("Source script")).apply {
                setMaxLength(DisplayBindings.MAX_SCRIPT_PATH_LENGTH)
                setValue(string(content, "path"))
                active = contentScript
                setHint(Component.literal("/ui/main.lua"))
            }
            addRenderableWidget(contentPath)
            y += 48
        }

        if (supportsInput) {
            val modeButton = Button.builder(inputModeLabel()) { button ->
                inputScript = !inputScript
                button.setMessage(inputModeLabel())
                inputPath.active = inputScript
            }.bounds(left, y + 14, 110, 20).build()
            addRenderableWidget(modeButton)
            inputPath = EditBox(font, left + 116, y + 14, panelWidth - 116, 20, Component.literal("Input script")).apply {
                setMaxLength(DisplayBindings.MAX_HANDLER_PATH_LENGTH)
                setValue(string(input, "path"))
                active = inputScript
                setHint(Component.literal("/ui/touch.lua"))
            }
            addRenderableWidget(inputPath)
            y += 48
        }

        val bottom = height - 30
        addRenderableWidget(
            Button.builder(Component.literal("Save")) { save() }
                .bounds(left, bottom, 90, 20).build()
        )
        addRenderableWidget(
            Button.builder(Component.literal("Cancel")) {
                minecraft?.setScreen(DeskIoOverviewScreen(origin, overviewJson, DeskIoOverviewScreen.CATEGORY_DISPLAY))
            }.bounds(left + panelWidth - 90, bottom, 90, 20).build()
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        val panelWidth = minOf(360, width - 24)
        val left = (width - panelWidth) / 2
        guiGraphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFF)
        guiGraphics.drawCenteredString(
            font,
            Component.literal("${string(display, "label")} · ${string(display, "socketName")}"),
            width / 2,
            32,
            0xA0A0A0
        )

        var y = 54
        if (isRadar || supportsScriptSource) {
            guiGraphics.drawString(font, "Content source", left, y, 0xD0D0D0, false)
            y += 48
        }
        if (supportsInput) {
            guiGraphics.drawString(font, "Input handler", left, y, 0xD0D0D0, false)
        }
        if (!isRadar && !supportsScriptSource && !supportsInput) {
            guiGraphics.drawCenteredString(font, Component.literal("No configurable routing for this display"), width / 2, 72, 0x808080)
        }
    }

    override fun isPauseScreen(): Boolean = false

    private fun save() {
        if (isRadar) {
            PacketDistributor.sendToServer(SetRadarDisplaySourcePayload(deskPos, socket, radarSourcePos(radarIndex)))
        } else if (supportsScriptSource) {
            val path = if (contentScript) contentPath.value.trim() else ""
            if (contentScript && path.isEmpty()) return
            PacketDistributor.sendToServer(SetDisplayScriptSourcePayload(deskPos, socket, path))
        }

        if (supportsInput) {
            val path = if (inputScript) inputPath.value.trim() else ""
            if (inputScript && path.isEmpty()) return
            PacketDistributor.sendToServer(SetDisplayTouchScriptPayload(deskPos, socket, path))
        }

        PacketDistributor.sendToServer(RequestDeskIoOverviewPayload(origin))
    }

    private fun initialRadarIndex(): Int {
        if (string(content, "type") != "radar_source") return 0
        val selected = string(content, "source")
        for (index in 0 until radarSources.size()) {
            val source = radarSources[index].asJsonObject
            if (string(source, "id") == selected) return index + 1
        }
        return 0
    }

    private fun radarSourcePos(index: Int): BlockPos? {
        if (index <= 0 || index > radarSources.size()) return null
        val source = radarSources[index - 1].asJsonObject
        return BlockPos(int(source, "x"), int(source, "y"), int(source, "z"))
    }

    private fun radarLabel(): Component {
        if (radarIndex == 0) return Component.literal("Radar: local")
        val source = radarSources[radarIndex - 1].asJsonObject
        return Component.literal("Radar: desk #${int(source, "memberIndex")} · ${string(source, "status")}")
    }

    private fun contentModeLabel(): Component = Component.literal(if (contentScript) "Script" else "Manual/API")

    private fun inputModeLabel(): Component = Component.literal(if (inputScript) "Lua handler" else "Raw events")

    private fun string(value: JsonObject, name: String): String = DeskIoOverviewScreen.jsonString(value, name)
    private fun int(value: JsonObject, name: String): Int = DeskIoOverviewScreen.jsonInt(value, name)
}
