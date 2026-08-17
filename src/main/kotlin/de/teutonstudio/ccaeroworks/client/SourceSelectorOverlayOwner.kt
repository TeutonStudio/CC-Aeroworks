package de.teutonstudio.ccaeroworks.client

/**
 * Implemented by screens which own an overlay source selector. Container tooltip handling queries
 * this interface so an item slot behind an open popup cannot leak its tooltip through the overlay.
 */
internal interface SourceSelectorOverlayOwner {
    fun ccaeroworks_isSourceSelectorPopupHovered(mouseX: Double, mouseY: Double): Boolean
}
