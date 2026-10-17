package de.teutonstudio.ccaeroworks.display

import dan200.computercraft.api.filesystem.Mount
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ControlScriptCatalogTest {
    @TempDir lateinit var directory: Path

    @Test fun `ordinary Lua files are catalogued without display imports while display metadata survives`() {
        Files.writeString(directory.resolve("gear.lua"), "local event = ...; print(event.value)")
        Files.createDirectory(directory.resolve("scripts"))
        Files.writeString(directory.resolve("scripts/flight.lua"), "return { onInput = function(event) end }")
        Files.writeString(directory.resolve("touch.lua"), "local touch = require('touchdisplay'); return { onTap = function() end }")
        Files.writeString(directory.resolve("notes.txt"), "not Lua")
        Files.writeString(directory.resolve("empty.lua"), "")
        Files.writeString(directory.resolve("large.lua"), "-".repeat(DisplayScriptCatalog.MAX_FILE_SIZE.toInt() + 1))
        val entries = DisplayScriptCatalog.scanMount(mount())
        assertEquals(listOf("/gear.lua", "/scripts/flight.lua", "/touch.lua"), entries.map { it.path })
        assertFalse(entries.first().display)
        assertTrue(entries.last().touchDisplay)
        assertEquals(listOf("onTap"), entries.last().declaredTouchEvents)
        assertEquals(listOf("/touch.lua"), entries.filter { it.supports(DeskDisplayType.THREE_DIGIT) }.map { it.path })
    }

    @Test fun `binding paths normalize once and reject traversal`() {
        assertEquals("/scripts/flight.lua", DisplayScriptCatalog.normalizePath(" /scripts\\flight.lua "))
        listOf("", "../escape.lua", "scripts/../escape.lua", "scripts//flight.lua", "./flight.lua").forEach {
            assertNull(DisplayScriptCatalog.normalizePath(it), it)
        }
    }

    private fun mount() = object : Mount {
        override fun exists(path: String) = Files.exists(directory.resolve(path))
        override fun isDirectory(path: String) = Files.isDirectory(directory.resolve(path))
        override fun list(path: String, contents: MutableList<String>) {
            Files.list(directory.resolve(path)).use { entries -> entries.forEach { contents.add(it.fileName.toString()) } }
        }
        override fun getSize(path: String) = Files.size(directory.resolve(path))
        override fun openForRead(path: String) = Files.newByteChannel(directory.resolve(path))
    }
}
