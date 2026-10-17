package de.teutonstudio.ccaeroworks.client

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.AnnotationNode

class DriveByWireSelectionIntegrationContractTest {
    @Test fun `logical channel cycling is injected before the cancellable Aeroworks callback`() {
        val ours = injection(
            "de/teutonstudio/ccaeroworks/mixin/client/DriveByWireClientWireNetworkHandlerMixin",
            "ccaeroworks\$scrollWholeDeskNetwork"
        )
        val aeroworks = injection(
            "com/mred231/aeroworks/foundation/mixin/compat/drivebywire/ClientWireNetworkHandlerMixin",
            "aeroworks\$cycleConsoleChannels"
        )
        assertTrue(value(aeroworks, "cancellable") == true)
        val nativeOrder = value(aeroworks, "order") as? Int ?: 1000
        val logicalOrder = value(ours, "order") as? Int ?: 1000
        assertTrue(logicalOrder < nativeOrder, "The native handler cancels before the logical selection can cycle if both use the default order")
        assertTrue(value(ours, "cancellable") == true)
        assertEquals(1, value(ours, "require"), "Missing the channel hook must be a startup failure, not a silent selection desync")
    }

    private fun injection(className: String, method: String): AnnotationNode {
        val node = ClassNode()
        javaClass.classLoader.getResourceAsStream("$className.class").use {
            assertNotNull(it, className)
            ClassReader(it).accept(node, ClassReader.SKIP_CODE)
        }
        return node.methods.single { it.name == method }.visibleAnnotations.single {
            it.desc == "Lorg/spongepowered/asm/mixin/injection/Inject;"
        }
    }

    private fun value(annotation: AnnotationNode, key: String): Any? =
        annotation.values.chunked(2).firstOrNull { it[0] == key }?.get(1)
}
