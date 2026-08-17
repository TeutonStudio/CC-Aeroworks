package de.teutonstudio.ccaeroworks.debug

import de.teutonstudio.ccaeroworks.CCAeroworks

/**
 * Temporary end-to-end diagnostics for the combined display touch path.
 *
 * Deliberately logs at INFO/WARN instead of DEBUG so traces are present in the ordinary
 * latest.log without launching Minecraft with a debug logging configuration. Keep every line
 * under the stable [TouchTrace] prefix so a user can grep one interaction out of a large log.
 */
object TouchInputDiagnostics {
    const val PREFIX: String = "[TouchTrace]"

    @JvmStatic
    fun info(stage: String, message: String) {
        CCAeroworks.LOGGER.info("{}[{}] {}", PREFIX, stage, message)
    }

    @JvmStatic
    fun warn(stage: String, message: String) {
        CCAeroworks.LOGGER.warn("{}[{}] {}", PREFIX, stage, message)
    }
}
