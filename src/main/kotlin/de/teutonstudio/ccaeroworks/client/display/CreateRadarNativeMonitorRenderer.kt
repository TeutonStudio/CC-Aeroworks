package de.teutonstudio.ccaeroworks.client.display

import com.mojang.blaze3d.vertex.PoseStack
import com.mred231.aeroworks.content.controls.ConsoleBlock
import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskAccess
import de.teutonstudio.ccaeroworks.display.RadarDisplaySnapshot
import de.teutonstudio.ccaeroworks.display.RadarDisplayType
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.Property
import net.neoforged.fml.ModList
import org.joml.Quaternionf
import org.joml.Vector3f
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.WeakHashMap
import kotlin.math.abs

/**
 * Draws RadarDisplay modules with Create: Radars' own MonitorRenderer.
 *
 * No Create: Radars class is present in this class' signatures. The optional
 * dependency is resolved lazily and cached only after ModList confirms that the
 * mod is installed. This keeps CC-Aeroworks loadable without Create: Radars.
 *
 * The virtual monitor is never inserted into the level. It is only a native
 * MonitorBlockEntity state container hydrated through the monitor's own client
 * NBT reader, so MonitorRenderer receives exactly the state shape it expects.
 */
object CreateRadarNativeMonitorRenderer {
    private const val MOD_ID = "create_radar"
    private const val MONITOR_CLASS = "com.happysg.radar.block.monitor.MonitorBlockEntity"
    private const val MONITOR_BLOCK_CLASS = "com.happysg.radar.block.monitor.MonitorBlock"
    private const val MONITOR_RENDERER_CLASS = "com.happysg.radar.block.monitor.MonitorRenderer"
    private const val IRADAR_CLASS = "com.happysg.radar.block.radar.behavior.IRadar"
    private const val MOD_BLOCKS_CLASS = "com.happysg.radar.registry.ModBlocks"
    private const val MOD_BLOCK_ENTITY_TYPES_CLASS = "com.happysg.radar.registry.ModBlockEntityTypes"

    // Native MonitorRenderer uses y=0.94 as its background depth. Translate that
    // plane onto the top of the RadarDisplay module without changing its own
    // internal z-fighting offsets (grid/sweep/tracks use 0.945..0.95).
    private const val NATIVE_BACKGROUND_DEPTH = 0.94
    private const val MODULE_SURFACE_Y = 2.12 / 16.0
    private const val SMALL_SURFACE_SIZE = 0.39f
    private const val LARGE_SURFACE_SIZE = 0.40f

    private var contract: Contract? = null
    private var contractResolutionAttempted = false
    private var lastFailureSignature: String? = null

    private val virtualMonitors = WeakHashMap<ConsoleBlockEntity, MutableMap<Int, VirtualMonitor>>()

    @JvmStatic
    fun render(
        desk: ConsoleBlockEntity,
        poseStack: PoseStack,
        buffers: MultiBufferSource,
        partialTicks: Float
    ): Boolean {
        if (!ModList.get().isLoaded(MOD_ID)) return false
        val level = desk.level ?: return false
        val native = resolveContract() ?: return false
        val sockets = desk.sockets()
        val gameTime = level.gameTime
        var renderedAny = false

        for (surface in AeroworksDeskAccess.radarSurfaces(desk)) {
            val snapshot = surface.snapshot ?: continue
            if (!RadarDisplaySnapshot.isFresh(snapshot, gameTime)) continue
            val radarPos = snapshot.radarPos ?: continue
            val radar = level.getBlockEntity(radarPos) ?: continue
            if (!native.iradarClass.isInstance(radar)) {
                reportFailure(
                    IllegalStateException(
                        "Radar block entity at $radarPos is ${radar.javaClass.name}, expected ${native.iradarClass.name}"
                    )
                )
                continue
            }

            val socket = sockets.getOrNull(surface.socket) ?: continue
            val facing = effectiveFacing(desk, surface.socket)
            val virtual = virtualMonitor(native, desk, surface.socket, facing) ?: continue
            if (!hydrate(native, virtual, snapshot)) continue
            val renderer = nativeRenderer(native, virtual.monitor) ?: continue

            poseStack.pushPose()
            try {
                poseStack.translate(0.5, 0.5, 0.5)
                poseStack.mulPose(ConsoleBlock.rotationFor(desk.blockState))
                poseStack.translate(
                    socket.offset().x - 0.5,
                    socket.offset().y - 0.5,
                    socket.offset().z - 0.5
                )
                poseStack.mulPose(socket.orientation())
                poseStack.translate(-0.5, 0.0, -0.5)
                applySurfaceTransform(poseStack, surface.type)

                native.renderRadarDisplay.invoke(
                    renderer,
                    radar,
                    virtual.monitor,
                    poseStack,
                    buffers,
                    partialTicks
                )
                renderedAny = true
                lastFailureSignature = null
            } catch (throwable: Throwable) {
                reportFailure(unwrap(throwable))
            } finally {
                poseStack.popPose()
            }
        }

        return renderedAny
    }

    private fun applySurfaceTransform(poseStack: PoseStack, type: RadarDisplayType) {
        val scale = when (type) {
            RadarDisplayType.SMALL -> SMALL_SURFACE_SIZE
            RadarDisplayType.LARGE -> LARGE_SURFACE_SIZE
        }
        poseStack.translate(0.5, MODULE_SURFACE_Y - NATIVE_BACKGROUND_DEPTH, 0.5)
        poseStack.scale(scale, 1.0f, scale)
        poseStack.translate(-0.5, 0.0, -0.5)
    }

    private fun effectiveFacing(desk: ConsoleBlockEntity, socketIndex: Int): Direction {
        val socket = desk.sockets().getOrNull(socketIndex) ?: return Direction.NORTH
        val rotation = Quaternionf(ConsoleBlock.rotationFor(desk.blockState)).mul(socket.orientation())
        val forward = rotation.transform(Vector3f(0.0f, 0.0f, -1.0f))
        return if (abs(forward.x) > abs(forward.z)) {
            if (forward.x >= 0.0f) Direction.EAST else Direction.WEST
        } else {
            if (forward.z >= 0.0f) Direction.SOUTH else Direction.NORTH
        }
    }

    private fun virtualMonitor(
        native: Contract,
        desk: ConsoleBlockEntity,
        socket: Int,
        facing: Direction
    ): VirtualMonitor? {
        val level = desk.level ?: return null
        val bySocket = virtualMonitors.getOrPut(desk) { mutableMapOf() }
        val existing = bySocket[socket]
        if (existing != null && existing.facing == facing && existing.monitor.level === level) {
            return existing
        }

        return try {
            val state = native.monitorBlock.defaultBlockState().setValue(native.facingProperty, facing)
            val monitor = native.monitorConstructor.newInstance(
                native.monitorType,
                desk.blockPos,
                state
            ) as BlockEntity
            monitor.setLevel(level)
            VirtualMonitor(monitor, facing).also { bySocket[socket] = it }
        } catch (throwable: Throwable) {
            reportFailure(unwrap(throwable))
            null
        }
    }

    private fun hydrate(
        native: Contract,
        virtual: VirtualMonitor,
        snapshot: RadarDisplaySnapshot
    ): Boolean {
        val level = virtual.monitor.level ?: return false
        val contentKey = snapshot.nativeContentKey()
        if (virtual.contentKey == contentKey) return true

        val radarPos = snapshot.radarPos ?: return false
        val monitorTag = CompoundTag().apply {
            putBoolean("HasRadarPos", true)
            put("radarPos", NbtUtils.writeBlockPos(radarPos))
            put("Filter", snapshot.detectionTag.copy())
            snapshot.selectedTrackId?.let { putString("SelectedEntity", it) }
            putInt("Size", 1)
            put("tracks", snapshot.nativeTracks.copy())
            put("SafeZones", ListTag())
        }

        return try {
            native.readClientState.invoke(
                virtual.monitor,
                monitorTag,
                level.registryAccess(),
                true
            )
            virtual.contentKey = contentKey
            true
        } catch (throwable: Throwable) {
            reportFailure(unwrap(throwable))
            false
        }
    }

    private fun RadarDisplaySnapshot.nativeContentKey(): Int {
        var result = radarPos?.hashCode() ?: 0
        result = 31 * result + detectionTag.hashCode()
        result = 31 * result + (selectedTrackId?.hashCode() ?: 0)
        result = 31 * result + nativeTracks.hashCode()
        result = 31 * result + trackCount
        result = 31 * result + status.hashCode()
        return result
    }

    private fun nativeRenderer(native: Contract, monitor: BlockEntity): Any? {
        return try {
            val renderer = Minecraft.getInstance().blockEntityRenderDispatcher.getRenderer(monitor)
                ?: throw IllegalStateException("No renderer is registered for the virtual Create: Radars monitor")
            if (!native.monitorRendererClass.isInstance(renderer)) {
                throw IllegalStateException(
                    "Virtual monitor renderer is ${renderer.javaClass.name}, expected ${native.monitorRendererClass.name}"
                )
            }
            renderer
        } catch (throwable: Throwable) {
            reportFailure(unwrap(throwable))
            null
        }
    }

    private fun resolveContract(): Contract? {
        contract?.let { return it }
        if (contractResolutionAttempted) return null
        contractResolutionAttempted = true

        return try {
            val loader = CreateRadarNativeMonitorRenderer::class.java.classLoader
            val monitorClass = Class.forName(MONITOR_CLASS, true, loader)
            val monitorBlockClass = Class.forName(MONITOR_BLOCK_CLASS, true, loader)
            val monitorRendererClass = Class.forName(MONITOR_RENDERER_CLASS, true, loader)
            val iradarClass = Class.forName(IRADAR_CLASS, true, loader)

            val monitorType = registryEntry(MOD_BLOCK_ENTITY_TYPES_CLASS, "MONITOR", loader) as? BlockEntityType<*>
                ?: throw IllegalStateException("ModBlockEntityTypes.MONITOR did not resolve to BlockEntityType")
            val monitorBlock = registryEntry(MOD_BLOCKS_CLASS, "MONITOR", loader) as? Block
                ?: throw IllegalStateException("ModBlocks.MONITOR did not resolve to Block")

            @Suppress("UNCHECKED_CAST")
            val facingProperty = monitorBlockClass.getField("FACING").get(null) as? Property<Direction>
                ?: throw IllegalStateException("MonitorBlock.FACING is unavailable")

            val constructor = monitorClass.getConstructor(
                BlockEntityType::class.java,
                BlockPos::class.java,
                BlockState::class.java
            )
            val read = monitorClass.getDeclaredMethod(
                "read",
                CompoundTag::class.java,
                HolderLookup.Provider::class.java,
                Boolean::class.javaPrimitiveType
            ).also {
                if (!it.trySetAccessible()) throw IllegalAccessException("Cannot access MonitorBlockEntity.read")
            }
            val render = monitorRendererClass.getDeclaredMethod(
                "renderRadarDisplay",
                iradarClass,
                monitorClass,
                PoseStack::class.java,
                MultiBufferSource::class.java,
                Float::class.javaPrimitiveType
            ).also {
                if (!it.trySetAccessible()) throw IllegalAccessException("Cannot access MonitorRenderer.renderRadarDisplay")
            }

            Contract(
                monitorClass = monitorClass,
                monitorRendererClass = monitorRendererClass,
                iradarClass = iradarClass,
                monitorConstructor = constructor,
                monitorType = monitorType,
                monitorBlock = monitorBlock,
                facingProperty = facingProperty,
                readClientState = read,
                renderRadarDisplay = render
            ).also { contract = it }
        } catch (throwable: Throwable) {
            reportFailure(unwrap(throwable))
            null
        }
    }

    private fun registryEntry(className: String, fieldName: String, loader: ClassLoader): Any? {
        val holder = Class.forName(className, true, loader).getField(fieldName).get(null)
        val getter = holder.javaClass.methods.firstOrNull { it.name == "get" && it.parameterCount == 0 }
            ?: throw NoSuchMethodException("${holder.javaClass.name}#get()")
        return getter.invoke(holder)
    }

    private fun reportFailure(cause: Throwable) {
        val signature = "${cause.javaClass.name}:${cause.message.orEmpty()}"
        if (signature == lastFailureSignature) return
        lastFailureSignature = signature
        CCAeroworks.LOGGER.warn(
            "[CC-Aeroworks] Native Create: Radars monitor rendering failed; RadarDisplay overlay was skipped",
            cause
        )
    }

    private fun unwrap(throwable: Throwable): Throwable =
        if (throwable is InvocationTargetException) throwable.targetException ?: throwable else throwable

    private data class Contract(
        val monitorClass: Class<*>,
        val monitorRendererClass: Class<*>,
        val iradarClass: Class<*>,
        val monitorConstructor: Constructor<*>,
        val monitorType: BlockEntityType<*>,
        val monitorBlock: Block,
        val facingProperty: Property<Direction>,
        val readClientState: Method,
        val renderRadarDisplay: Method
    )

    private data class VirtualMonitor(
        val monitor: BlockEntity,
        val facing: Direction,
        var contentKey: Int = Int.MIN_VALUE
    )
}
