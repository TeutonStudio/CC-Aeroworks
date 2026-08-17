package de.teutonstudio.ccaeroworks.client.display

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import java.util.concurrent.CopyOnWriteArrayList

object DisplayRenderExtensions {
    private val trackers = CopyOnWriteArrayList<(ConsoleBlockEntity) -> Unit>()

    fun registerTracker(tracker: (ConsoleBlockEntity) -> Unit) {
        trackers += tracker
    }

    fun track(desk: ConsoleBlockEntity) {
        trackers.forEach { it(desk) }
    }
}
