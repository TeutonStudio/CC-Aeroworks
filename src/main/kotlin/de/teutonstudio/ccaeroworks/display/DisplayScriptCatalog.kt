package de.teutonstudio.ccaeroworks.display

import dan200.computercraft.api.filesystem.Mount
import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.WeakHashMap

/** Metadata for a Lua file which explicitly opts into one of the display APIs. */
data class DisplayScriptDescriptor(
    val path: String,
    val name: String,
    val display: Boolean,
    val touchDisplay: Boolean,
    val imports: List<String> = emptyList(),
    val declaredTouchEvents: List<String> = emptyList()
) {
    fun supports(type: DeskDisplayType): Boolean = when (type) {
        DeskDisplayType.TWO_DIGIT -> display
        DeskDisplayType.THREE_DIGIT -> display || touchDisplay
    }
}

/**
 * Server-authoritative catalog of display scripts on the embedded ComputerControlDesk.
 *
 * Only the computer's writable root mount is scanned. File contents never leave the server: the
 * client receives bounded metadata (path/name/capabilities) and selection is revalidated here.
 */
object DisplayScriptCatalog {
    const val MAX_SCRIPTS: Int = 256
    const val MAX_FILE_SIZE: Long = 64L * 1024L
    const val MAX_DEPTH: Int = 8
    const val MAX_PATH_LENGTH: Int = 256
    private const val CACHE_TICKS: Long = 20L

    private data class Cached(val tick: Long, val entries: List<DisplayScriptDescriptor>)
    private val cache = WeakHashMap<ComputerControlDeskBlockEntity, Cached>()

    fun ownerFor(desk: com.mred231.aeroworks.content.controls.ConsoleBlockEntity): ComputerControlDeskBlockEntity? {
        if (desk is ComputerControlDeskBlockEntity) return desk
        val level = desk.level ?: return null
        val snapshot = ConsoleMultiblockManager.resolve(level, desk.blockPos)
        if (snapshot.state != ConsoleNetworkState.ACTIVE) return null
        return snapshot.owner
    }

    @Synchronized
    fun scan(owner: ComputerControlDeskBlockEntity, force: Boolean = false): List<DisplayScriptDescriptor> {
        val tick = owner.level?.gameTime ?: 0L
        val existing = cache[owner]
        if (!force && existing != null && tick - existing.tick in 0..CACHE_TICKS) return existing.entries

        val computer = owner.getServerComputer() ?: owner.createServerComputer()
        val entries = runCatching {
            val rootMount = computer.createRootMount() ?: return@runCatching emptyList()
            scanMount(rootMount)
        }.getOrElse { emptyList() }
        cache[owner] = Cached(tick, entries)
        return entries
    }

    fun find(
        owner: ComputerControlDeskBlockEntity,
        rawPath: String,
        type: DeskDisplayType
    ): DisplayScriptDescriptor? {
        val path = normalizePath(rawPath) ?: return null
        return scan(owner, force = true).firstOrNull { it.path == path && it.supports(type) }
    }

    fun normalizePath(rawPath: String): String? {
        val normalized = rawPath.trim().replace('\\', '/').trimStart('/')
        if (normalized.isBlank() || normalized.length > MAX_PATH_LENGTH) return null
        val segments = normalized.split('/')
        if (segments.any { it.isBlank() || it == "." || it == ".." }) return null
        return "/$normalized"
    }

    private fun scanMount(mount: Mount): List<DisplayScriptDescriptor> {
        val result = ArrayList<DisplayScriptDescriptor>()

        fun visit(directory: String, depth: Int) {
            if (depth > MAX_DEPTH || result.size >= MAX_SCRIPTS) return
            val children = ArrayList<String>()
            mount.list(directory, children)
            children.sorted().forEach { child ->
                if (result.size >= MAX_SCRIPTS) return@forEach
                val path = if (directory.isEmpty()) child else "$directory/$child"
                if (mount.isDirectory(path)) {
                    visit(path, depth + 1)
                    return@forEach
                }
                if (!child.endsWith(".lua", ignoreCase = true)) return@forEach
                val size = mount.getSize(path)
                if (size <= 0L || size > MAX_FILE_SIZE) return@forEach
                val source = readUtf8(mount, path, size) ?: return@forEach
                val analysis = LuaRequireScanner.scan(source)
                if (!analysis.display) return@forEach
                result += DisplayScriptDescriptor(
                    path = "/$path",
                    name = child.substringBeforeLast('.'),
                    display = analysis.display,
                    touchDisplay = analysis.touchDisplay,
                    imports = analysis.imports,
                    declaredTouchEvents = analysis.declaredTouchEvents
                )
            }
        }

        visit("", 0)
        return result.sortedBy(DisplayScriptDescriptor::path)
    }

    private fun readUtf8(mount: Mount, path: String, size: Long): String? = runCatching {
        mount.openForRead(path).use { channel ->
            val buffer = ByteBuffer.allocate(size.toInt())
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) < 0) break
            }
            buffer.flip()
            StandardCharsets.UTF_8.decode(buffer).toString()
        }
    }.getOrNull()
}

/**
 * Small bounded Lua lexer for literal require calls and touch callback declarations. It deliberately
 * does not pretend to solve Lua data-flow: imports are static evidence, runtime diagnostics are the
 * source of truth for actually observed dependencies.
 */
internal object LuaRequireScanner {
    data class Analysis(
        val imports: List<String>,
        val declaredTouchEvents: List<String>
    ) {
        val display: Boolean
            get() = imports.any { it == "display" || it == "touchdisplay" }
        val touchDisplay: Boolean
            get() = "touchdisplay" in imports
    }

    fun scan(source: String): Analysis {
        var index = 0
        val imports = linkedSetOf<String>()
        val touchEvents = linkedSetOf<String>()
        while (index < source.length) {
            index = skipTrivia(source, index)
            if (index >= source.length) break
            val c = source[index]
            when {
                c == '\'' || c == '"' -> index = skipQuoted(source, index)
                c == '[' -> {
                    val longString = readLongBracket(source, index)
                    index = longString?.second ?: (index + 1)
                }
                c == '_' || c.isLetter() -> {
                    val start = index++
                    while (index < source.length && (source[index] == '_' || source[index].isLetterOrDigit())) index++
                    val identifier = source.substring(start, index)
                    if (identifier == "require") {
                        val parsed = requireArgument(source, index)
                        if (parsed != null) {
                            val module = parsed.first.trim().take(MAX_IMPORT_LENGTH)
                            if (module.isNotEmpty() && imports.size < MAX_IMPORTS) imports += module
                            index = parsed.second
                        }
                    } else if (identifier in TOUCH_CALLBACKS) {
                        val assignment = skipTrivia(source, index)
                        if (assignment < source.length && source[assignment] == '=') touchEvents += identifier
                    }
                }
                else -> index++
            }
        }
        return Analysis(imports.toList(), touchEvents.toList())
    }

    private fun requireArgument(source: String, start: Int): Pair<String, Int>? {
        var index = skipTrivia(source, start)
        val parenthesized = index < source.length && source[index] == '('
        if (parenthesized) index = skipTrivia(source, index + 1)
        if (index >= source.length) return null
        if (source[index] == '\'' || source[index] == '"') return readQuoted(source, index)
        return readLongBracket(source, index)
    }

    private fun skipTrivia(source: String, start: Int): Int {
        var index = start
        while (index < source.length) {
            if (source[index].isWhitespace()) {
                index++
                continue
            }
            if (index + 1 < source.length && source[index] == '-' && source[index + 1] == '-') {
                val longComment = readLongBracket(source, index + 2)
                if (longComment != null) {
                    index = longComment.second
                } else {
                    val end = source.indexOf('\n', index + 2)
                    index = if (end < 0) source.length else end + 1
                }
                continue
            }
            break
        }
        return index
    }

    private fun skipQuoted(source: String, start: Int): Int = readQuoted(source, start)?.second ?: source.length

    private fun readQuoted(source: String, start: Int): Pair<String, Int>? {
        if (start >= source.length || (source[start] != '\'' && source[start] != '"')) return null
        val quote = source[start]
        var index = start + 1
        val value = StringBuilder()
        while (index < source.length) {
            val c = source[index++]
            if (c == quote) return value.toString() to index
            if (c == '\\' && index < source.length) value.append(source[index++]) else value.append(c)
        }
        return value.toString() to source.length
    }

    private fun readLongBracket(source: String, start: Int): Pair<String, Int>? {
        if (start >= source.length || source[start] != '[') return null
        var cursor = start + 1
        var equals = 0
        while (cursor < source.length && source[cursor] == '=') {
            equals++
            cursor++
        }
        if (cursor >= source.length || source[cursor] != '[') return null
        val contentStart = cursor + 1
        val closing = "]" + "=".repeat(equals) + "]"
        val end = source.indexOf(closing, contentStart)
        return if (end < 0) {
            source.substring(contentStart) to source.length
        } else {
            source.substring(contentStart, end) to (end + closing.length)
        }
    }

    private val TOUCH_CALLBACKS = setOf("onTap", "onDoubleTap", "onPointer")
    private const val MAX_IMPORTS = 32
    private const val MAX_IMPORT_LENGTH = 128
}
