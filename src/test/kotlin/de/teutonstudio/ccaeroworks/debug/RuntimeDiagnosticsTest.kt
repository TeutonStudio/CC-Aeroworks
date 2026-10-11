package de.teutonstudio.ccaeroworks.debug

import com.electronwill.nightconfig.core.CommentedConfig
import de.teutonstudio.ccaeroworks.config.CCServerConfig
import de.teutonstudio.ccaeroworks.radarcompat.createradar.RadarTrace
import net.neoforged.fml.config.IConfigSpec
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuntimeDiagnosticsTest {
    @Test
    fun diagnosticsAreOptInIndependentAndLazyAcrossReloads() {
        val spec = CCServerConfig.SPEC
        val logger = LogManager.getLogger("CC-Aeroworks") as Logger
        val originalLevel = logger.level
        val events = mutableListOf<LogEvent>()
        val appender = object : AbstractAppender("diagnostics-test", null, null, true, Property.EMPTY_ARRAY) {
            override fun append(event: LogEvent) {
                events += event.toImmutable()
            }
        }
        appender.start()
        logger.addAppender(appender)
        logger.level = Level.DEBUG
        try {
            spec.acceptConfig(null)
            assertFalse(CCServerConfig.debugRadarEnabled())
            assertFalse(CCServerConfig.debugDisplayEnabled())
            RadarTrace.event("unloaded", null, null) { error("Disabled trace evaluated its message") }
            RadarTrace.periodic("unloaded", null, null, 20L) { error("Disabled periodic trace evaluated its message") }
            TouchInputDiagnostics.info("test", "unloaded")
            TouchInputDiagnostics.warn("test", "unloaded warning")
            assertTrue(events.isEmpty())

            val config = CommentedConfig.inMemory()
            spec.correct(config)
            assertFalse(config.get<Boolean>("debug.radar"))
            assertFalse(config.get<Boolean>("debug.display"))
            // NeoForge's loaded-config implementation is package-private. Use an in-memory instance
            // without a ModContainer; correction happens before loading so save() is never needed.
            val constructor = Class.forName("net.neoforged.fml.config.LoadedConfig").declaredConstructors.single()
            constructor.isAccessible = true
            val loaded = constructor.newInstance(config, null, null) as IConfigSpec.ILoadedConfig
            spec.acceptConfig(loaded)

            var evaluated = 0
            for ((radar, display) in listOf(false to false, true to false, false to true, true to true, false to false)) {
                config.set<Boolean>("debug.radar", radar)
                config.set<Boolean>("debug.display", display)
                spec.afterReload()
                assertEquals(radar, CCServerConfig.debugRadarEnabled())
                assertEquals(display, CCServerConfig.debugDisplayEnabled())
                events.clear()
                val before = evaluated
                RadarTrace.event("matrix", null, null) { evaluated++; "radar diagnostic" }
                TouchInputDiagnostics.info("test", "display diagnostic")
                TouchInputDiagnostics.warn("test", "display diagnostic warning")
                TouchInputDiagnostics.info("pixels", "filtered stroke sample")
                assertEquals(if (radar) 1 else 0, evaluated - before)
                assertEquals((if (radar) 1 else 0) + (if (display) 2 else 0), events.size)
                assertTrue(events.all { it.level == Level.INFO })
            }

            // A disabled periodic call must not consume the first interval when enabled later.
            RadarTrace.periodic("toggle-periodic", null, null, 20L) { error("Disabled periodic trace evaluated its message") }
            CCServerConfig.debugRadar.set(true)
            events.clear()
            RadarTrace.periodic("toggle-periodic", null, null, 20L) { "first enabled interval" }
            RadarTrace.periodic("toggle-periodic", null, null, 20L) { error("Throttled trace evaluated its message") }
            assertEquals(1, events.size)
        } finally {
            spec.acceptConfig(null)
            logger.removeAppender(appender)
            logger.level = originalLevel
            appender.stop()
        }
        assertFalse(CCServerConfig.debugRadarEnabled())
        assertFalse(CCServerConfig.debugDisplayEnabled())
    }
}
