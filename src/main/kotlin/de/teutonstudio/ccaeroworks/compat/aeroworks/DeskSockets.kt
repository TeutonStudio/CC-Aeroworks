package de.teutonstudio.ccaeroworks.compat.aeroworks

object DeskSockets {
    private val names: List<String> = listOf("left", "right", "big")

    fun name(index: Int): String = names.getOrElse(index) { "socket_$index" }

    fun index(name: String): Int? = names.indexOf(name.lowercase()).takeIf { it >= 0 }

    fun entries(socketCount: Int): List<Map<String, Any>> = (0 until socketCount).map { index ->
        linkedMapOf("name" to name(index), "index" to index)
    }
}
