package de.teutonstudio.ccaeroworks.debug

import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.config.CCServerConfig

/**
 * End-to-end diagnostics for the combined display touch path.
 *
 * Touch/draw traces are opt-in through debug.display. High-frequency stroke samples remain
 * suppressed so diagnostics do not compete with the work which actually processes the stroke.
 */
object TouchInputDiagnostics {
    const val PREFIX: String = "[TouchTrace]"

    @JvmStatic
    fun info(stage: String, message: String) {
        if (!CCServerConfig.debugDisplayEnabled()) return
        if (isDrawHotPathNoise(stage, message)) return
        CCAeroworks.LOGGER.info("{}[{}] {}", PREFIX, stage, message)
    }

    @JvmStatic
    fun warn(stage: String, message: String) {
        if (!CCServerConfig.debugDisplayEnabled()) return
        CCAeroworks.LOGGER.info("{}[{}][warn] {}", PREFIX, stage, message)
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
