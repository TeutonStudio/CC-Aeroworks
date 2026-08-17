package de.teutonstudio.ccaeroworks.radarcompat.client

interface RadarSourceSelectorOverlayOwner {
    fun ccaeroworks_isRadarSourceSelectorPopupHovered(mouseX: Double, mouseY: Double): Boolean
}
