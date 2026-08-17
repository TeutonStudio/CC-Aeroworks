package de.teutonstudio.ccaeroworks.client

import java.util.concurrent.CopyOnWriteArrayList

object SourceSelectorOverlayExtensions {
    private val hoverChecks = CopyOnWriteArrayList<(Any, Double, Double) -> Boolean>()

    fun register(check: (Any, Double, Double) -> Boolean) {
        hoverChecks += check
    }

    fun isPopupHovered(screen: Any, mouseX: Double, mouseY: Double): Boolean {
        if ((screen as? SourceSelectorOverlayOwner)?.ccaeroworks_isSourceSelectorPopupHovered(mouseX, mouseY) == true) {
  return true
        }
        return hoverChecks.any { it(screen, mouseX, mouseY) }
    }
}
