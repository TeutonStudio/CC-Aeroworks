package de.teutonstudio.ccaeroworks.client.display

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodNode

/** Read both our hooks and the installed Aeroworks binary without initializing Minecraft. */
class DisplayPointerHudIntegrationContractTest {
    @Test
    fun `multiblock renderer replaces the native HUD and reads its complete axis widget list`() {
        val mixin = read("de/teutonstudio/ccaeroworks/mixin/client/ConsoleHudOverlayMixin")
        val native = target(mixin)
        val (_, method) = injectedMethod(mixin, native)
        assertTrue(method.access and Opcodes.ACC_STATIC == 0)
        assertEquals(Type.VOID_TYPE, Type.getReturnType(method.desc))
        assertEquals("render", method.name)
        val shadow = mixin.methods.single { annotation(it, SHADOW) != null }
        val collector = native.methods.single { it.name == shadow.name && it.desc == shadow.desc }
        assertTrue(collector.access and Opcodes.ACC_STATIC != 0)
        assertTrue(shadow.access and Opcodes.ACC_STATIC != 0)
        assertEquals("()Ljava/util/List;", collector.desc)
    }

    @Test
    fun `widget bridge shadows native record fields and implements its HUD interface`() {
        val mixin = widgetMixin()
        val native = target(mixin)
        val shadows = mixin.fields.filter { annotations(it.visibleAnnotations, it.invisibleAnnotations).any { annotation -> annotation.desc == SHADOW } }
        assertEquals(3, shadows.size)
        for (shadow in shadows) {
            val field = native.fields.single { it.name == shadow.name }
            assertEquals(field.desc, shadow.desc, shadow.name)
            assertEquals(field.signature, shadow.signature, shadow.name)
            assertEquals(field.access and Opcodes.ACC_STATIC, shadow.access and Opcodes.ACC_STATIC, shadow.name)
            assertTrue(field.access and Opcodes.ACC_FINAL != 0, shadow.name)
            assertTrue(annotations(shadow.visibleAnnotations, shadow.invisibleAnnotations).any { it.desc == FINAL }, shadow.name)
        }

        val bridge = read(mixin.interfaces.single())
        assertTrue(bridge.access and Opcodes.ACC_INTERFACE != 0)
        for (api in bridge.methods.filter { it.access and Opcodes.ACC_ABSTRACT != 0 }) {
            val implementation = mixin.methods.single { it.name == api.name && it.desc == api.desc }
            assertTrue(implementation.access and Opcodes.ACC_PUBLIC != 0, api.name)
            val fieldRead = implementation.instructions.toArray().filterIsInstance<FieldInsnNode>()
                .single { it.opcode == Opcodes.GETFIELD }
            val shadow = shadows.single { it.name == fieldRead.name }
            assertEquals(shadow.desc, fieldRead.desc, api.name)
            assertEquals(Type.getType(shadow.desc), Type.getReturnType(api.desc), api.name)
        }

        val (_, width) = injectedMethod(mixin, native)
        assertEquals(Type.INT_TYPE, Type.getReturnType(width.desc))
        assertTrue(Type.getArgumentTypes(width.desc).isEmpty())
    }

    @Test
    fun `session accessors match the native static multiblock address fields`() {
        val mixin = read("de/teutonstudio/ccaeroworks/mixin/client/ConsoleControlClientAccessor")
        val native = target(mixin)
        val accessors = mixin.methods.mapNotNull { method -> annotation(method, ACCESSOR)?.let { method to it } }
        assertEquals(2, accessors.size)
        for ((accessor, annotation) in accessors) {
            val field = native.fields.single { it.name == value(annotation, "value") }
            assertTrue(field.access and Opcodes.ACC_STATIC != 0, field.name)
            assertTrue(accessor.access and Opcodes.ACC_STATIC != 0, accessor.name)
            assertTrue(Type.getArgumentTypes(accessor.desc).isEmpty(), accessor.name)
            assertEquals(Type.getType(field.desc), Type.getReturnType(accessor.desc), accessor.name)
            field.signature?.let { assertEquals("()$it", accessor.signature, accessor.name) }
        }
    }

    @Test
    fun `native grouping uses the same canonical assembly on the client and server`() {
        val mixin = read("de/teutonstudio/ccaeroworks/mixin/ConsoleGroupMixin")
        val (_, method) = injectedMethod(mixin, target(mixin))
        assertEquals("of", method.name)
        assertTrue(method.access and Opcodes.ACC_STATIC != 0)
        val config = javaClass.getResourceAsStream("/cc_aeroworks.mixins.json")!!.bufferedReader().use { it.readText() }
        val commonMixins = config.substringAfter("\"mixins\":").substringBefore("\"client\":")
        assertTrue(commonMixins.contains("\"ConsoleGroupMixin\""), "Both sides must flatten sockets in the same assembly order")
    }

    /** Resolve the selector from our annotation, then compare every handler argument to the binary. */
    private fun injectedMethod(mixin: ClassNode, native: ClassNode): Pair<MethodNode, MethodNode> {
        val (handler, injection) = mixin.methods.mapNotNull { method -> annotation(method, INJECT)?.let { method to it } }.single()
        val selector = (value(injection, "method") as List<*>).single()
        val method = native.methods.single { it.name + it.desc == selector }
        assertEquals(method.access and Opcodes.ACC_STATIC, handler.access and Opcodes.ACC_STATIC, handler.name)
        assertEquals(true, value(injection, "cancellable"), handler.name)
        val at = (value(injection, "at") as List<*>).single() as AnnotationNode
        assertEquals("HEAD", value(at, "value"), handler.name)

        val expected = Type.getArgumentTypes(method.desc)
        val actual = Type.getArgumentTypes(handler.desc)
        assertEquals(expected.size + 1, actual.size, handler.name)
        expected.forEachIndexed { index, type ->
            if (type != actual[index]) {
                assertEquals(Type.OBJECT, type.sort, handler.name)
                assertEquals(Type.getType(Any::class.java), actual[index], handler.name)
                assertTrue(parameterAnnotations(handler, index).any { it.desc == COERCE }, handler.name)
            }
        }
        val callback = if (Type.getReturnType(method.desc) == Type.VOID_TYPE) "CallbackInfo" else "CallbackInfoReturnable"
        assertEquals("Lorg/spongepowered/asm/mixin/injection/callback/$callback;", actual.last().descriptor)
        assertEquals(Type.VOID_TYPE, Type.getReturnType(handler.desc))
        return handler to method
    }

    private fun widgetMixin(): ClassNode = read("de/teutonstudio/ccaeroworks/mixin/client/ConsoleHudWidgetMixin")

    private fun target(mixin: ClassNode): ClassNode {
        val annotation = annotations(mixin.visibleAnnotations, mixin.invisibleAnnotations).single { it.desc == MIXIN }
        assertEquals(false, value(annotation, "remap"), mixin.name)
        val classes = (value(annotation, "value") as? List<*>)?.map { (it as Type).internalName }.orEmpty()
        val names = (value(annotation, "targets") as? List<*>)?.map { (it as String).replace('.', '/') }.orEmpty()
        return read((classes + names).single())
    }

    private fun read(name: String): ClassNode = ClassNode().also { node ->
        val stream = javaClass.classLoader.getResourceAsStream("$name.class")
        assertNotNull(stream, name)
        stream!!.use { ClassReader(it).accept(node, ClassReader.SKIP_DEBUG) }
    }

    private fun annotation(method: MethodNode, descriptor: String): AnnotationNode? =
        annotations(method.visibleAnnotations, method.invisibleAnnotations).singleOrNull { it.desc == descriptor }

    private fun parameterAnnotations(method: MethodNode, index: Int): List<AnnotationNode> =
        annotations(method.visibleParameterAnnotations?.getOrNull(index), method.invisibleParameterAnnotations?.getOrNull(index))

    private fun annotations(visible: List<AnnotationNode>?, invisible: List<AnnotationNode>?): List<AnnotationNode> =
        visible.orEmpty() + invisible.orEmpty()

    private fun value(annotation: AnnotationNode, key: String): Any? =
        annotation.values.orEmpty().chunked(2).firstOrNull { it[0] == key }?.get(1)

    private companion object {
        const val MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;"
        const val SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;"
        const val FINAL = "Lorg/spongepowered/asm/mixin/Final;"
        const val INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;"
        const val COERCE = "Lorg/spongepowered/asm/mixin/injection/Coerce;"
        const val ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;"
    }
}
