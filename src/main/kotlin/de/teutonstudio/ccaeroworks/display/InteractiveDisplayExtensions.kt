package de.teutonstudio.ccaeroworks.display

import com.mred231.aeroworks.content.controls.ModuleType
import java.util.concurrent.CopyOnWriteArrayList

/** Optional integrations can mark module types as using the large interactive display surface. */
object InteractiveDisplayExtensions {
    private val predicates = CopyOnWriteArrayList<(ModuleType) -> Boolean>()

    fun register(predicate: (ModuleType) -> Boolean) {
        predicates += predicate
    }

    fun isInteractive(moduleType: ModuleType): Boolean = predicates.any { it(moduleType) }
}
