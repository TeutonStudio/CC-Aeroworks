package de.teutonstudio.ccaeroworks.compat.computercraft

data class LuaModuleSnapshot(
    val socket: Int,
    val id: String,
    val kind: String,
    val values: Map<String, Int> = emptyMap(),
    val displayWidth: Int? = null,
    val displayText: String? = null
)

object LuaModuleDescription {
    @JvmStatic
    fun validSockets(socketCount: Int, modules: Iterable<LuaModuleSnapshot>): List<LuaModuleSnapshot> =
        modules.filter { it.socket in 0 until socketCount }.sortedBy { it.socket }

    @JvmStatic
    fun describe(module: LuaModuleSnapshot): Map<String, Any> = linkedMapOf<String, Any>(
        "socket" to module.socket,
        "id" to module.id,
        "kind" to module.kind,
        "display" to (module.displayWidth != null)
    ).apply {
        if (module.displayWidth != null) {
            put("width", module.displayWidth)
            put("text", module.displayText.orEmpty())
        } else if (module.values.size == 1) {
            put("value", module.values.values.first())
        } else if (module.values.isNotEmpty()) {
            put("values", LinkedHashMap(module.values))
        }
    }
}
