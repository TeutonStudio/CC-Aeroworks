package de.teutonstudio.ccaeroworks.debug

import de.teutonstudio.ccaeroworks.CCAeroworks

/**
 * End-to-end diagnostics for the combined display touch path.
 *
 * Touch/draw traces are debug-only. They must never pollute the normal Minecraft latest.log or
 * compete with the ComputerCraft/server main-thread work which actually processes the stroke.
 */
object TouchInputDiagnostics {
    const val PREFIX: String = "[TouchTrace]"

    @JvmStatic
    fun info(stage: String, message: String) {
        if (isDrawHotPathNoise(stage, message)) return
        CCAeroworks.LOGGER.debug("{}[{}] {}", PREFIX, stage, message)
    }

    @JvmStatic
    fun warn(stage: String, message: String) {
        CCAeroworks.LOGGER.debug("{}[{}][warn] {}", PREFIX, stage, message)
    }

    private fun isDrawHotPathNoise(stage: String, message: String): Boolean = when (stage) {
        "pixels" -> true
        "client" -> message.contains("send draw stage=sample")
        "server" ->
            message.contains("accepted draw SAMPLE") ||
                (message.contains("received draw ") && message.contains(" end=false") && !message.contains(" seq=0 "))
        "dispatch" ->
            message.startsWith("binding ") ||
                (message.contains("action=draw") && message.contains(" end=false") && !message.contains(" seq=0 "))
        "peripheral" ->
            message.startsWith("delivery summary ") ||
                (message.contains("action=draw") && message.contains(" end=false") && !message.contains(" seq=0 "))
        else -> false
    }
}
