package de.teutonstudio.ccaeroworks.compat.teutonlib

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.test.Test
import kotlin.test.assertTrue

/** Keeps TeutonLib implementation packages out of new CC-Aeroworks integration code. */
class TeutonLibArchitectureBoundaryTest {
    @Test
    fun `built jar uses only public TeutonLib API packages`() {
        val artifact = Path.of(requireNotNull(System.getProperty("ccaeroworks.architectureArtifact")) {
            "Gradle must provide the CC-Aeroworks architecture artifact"
        })
        val references = JarFile(artifact.toFile()).use { jar ->
            jar.entries().asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".class") }
                .flatMap { entry ->
                    val content = jar.getInputStream(entry).use { it.readBytes() }
                        .toString(StandardCharsets.ISO_8859_1)
                    teutonLibClass.findAll(content).map { it.value }
                }
                .toSortedSet()
        }
        val implementationReferences = references.filterNot { it.startsWith(API_PREFIX) }.toSortedSet()

        assertTrue(
            implementationReferences.isEmpty(),
            "CC-Aeroworks artifact references non-API TeutonLib classes: ${implementationReferences.joinToString()}"
        )
    }

    private companion object {
        const val API_PREFIX = "de/TeutonStudio/TeutonLib/api/"
        val teutonLibClass = Regex("de/TeutonStudio/TeutonLib/[A-Za-z0-9_$/]+")
    }
}
