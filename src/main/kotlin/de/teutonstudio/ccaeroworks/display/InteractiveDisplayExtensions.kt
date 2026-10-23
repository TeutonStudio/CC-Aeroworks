package de.teutonstudio.ccaeroworks.display

import com.mred231.aeroworks.content.controls.module.ModuleType
import java.util.concurrent.CopyOnWriteArrayList

/** Optional integrations can expose module types as interactive display surfaces. */
object InteractiveDisplayExtensions {
    private val resolvers = CopyOnWriteArrayList<(ModuleType) -> DeskDisplayType?>()

    fun register(resolver: (ModuleType) -> DeskDisplayType?) {
        resolvers += resolver
    }

    fun displayType(moduleType: ModuleType): DeskDisplayType? =
        resolvers.firstNotNullOfOrNull { it(moduleType) }

    fun isInteractive(moduleType: ModuleType): Boolean = displayType(moduleType) != null
}
