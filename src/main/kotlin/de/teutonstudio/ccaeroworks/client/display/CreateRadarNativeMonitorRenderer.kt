package de.teutonstudio.ccaeroworks.client.display

import com.mojang.blaze3d.vertex.PoseStack
import com.mred231.aeroworks.content.controls.ConsoleBlock
import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskAccess
import de.teutonstudio.ccaeroworks.compat.createradar.RadarTrace
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
import java.lang.reflect.Modifier
import java.util.WeakHashMap
import kotlin.math.abs

/**
 * Draws RadarDisplay modules with Create: Radars' own MonitorRenderer.
 *
 * During the current diagnostic phase this class emits deliberately exhaustive
 * [CCA-RADAR-TRACE] records. The trace covers every gate between a client
 * RadarDisplay snapshot and MonitorRenderer.renderRadarDisplay(...).
 */
object CreateRadarNativeMonitorRenderer {
    private const val MOD_ID = "create_radar"
    private const val MONITOR_CLASS = "com.happysg.radar.block.monitor.MonitorBlockEntity"
    private const val MONITOR_BLOCK_CLASS = "com.happysg.radar.block.monitor.MonitorBlock"
    private const val MONITOR_RENDERER_CLASS = "com.happysg.radar.block.monitor.MonitorRenderer"
    private const val IRADAR_CLASS = "com.happysg.radar.block.radar.behavior.IRadar"
    private const val MOD_BLOCKS_CLASS = "com.happysg.radar.registry.ModBlocks"
    private const val MOD_BLOCK_ENTITY_TYPES_CLASS = "com.happysg.radar.registry.ModBlockEntityTypes"

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
        val modLoaded = ModList.get().isLoaded(MOD_ID)
        val level = desk.level
        RadarTrace.periodic(
            "D00_RENDER_ENTER",
            level,
            desk.blockPos,
            10L,
            "modLoaded=$modLoaded levelNull=${level == null} partialTicks=$partialTicks buffers=${buffers.javaClass.name} " +
                "radarSurfaceCount=${AeroworksDeskAccess.radarSurfaces(desk).size}"
        )
        if (!modLoaded) {
            RadarTrace.periodic("D01_SKIP_MOD", level, desk.blockPos, 20L, "create_radar is not loaded")
            return false
        }
        if (level == null) {
            RadarTrace.event("D01_SKIP_LEVEL", null, desk.blockPos, "desk.level is null")
            return false
        }

        val native = resolveContract(level, desk.blockPos) ?: run {
            RadarTrace.periodic("D02_SKIP_CONTRACT", level, desk.blockPos, 20L, "native reflection contract unavailable")
            return false
        }
        val sockets = desk.sockets()
        val gameTime = level.gameTime
        val surfaces = AeroworksDeskAccess.radarSurfaces(desk)
        var renderedAny = false

        RadarTrace.periodic(
            "D03_SURFACE_ENUM",
            level,
            desk.blockPos,
            10L,
            "surfaces=${surfaces.map { "socket=${it.socket},type=${it.type},snapshot=${snapshotSummary(it.snapshot, gameTime)}" }} " +
                "socketCount=${sockets.size}"
        )

        for (surface in surfaces) {
            val snapshot = surface.snapshot
            RadarTrace.periodic(
                "D10_SURFACE",
                level,
                desk.blockPos,
                5L,
                "socket=${surface.socket} type=${surface.type} snapshot=${snapshotSummary(snapshot, gameTime)}"
            )
            if (snapshot == null) {
                RadarTrace.periodic(
                    "D11_SKIP_NO_SNAPSHOT",
                    level,
                    desk.blockPos,
                    10L,
                    "socket=${surface.socket}; RadarDeskStateAccess returned null snapshot"
                )
                continue
            }

            val fresh = RadarDisplaySnapshot.isFresh(snapshot, gameTime)
            if (!fresh) {
                val freshnessBase = snapshot.receivedAtClientTick.takeIf { it >= 0L } ?: snapshot.updatedAt
                RadarTrace.periodic(
                    "D12_SKIP_NOT_FRESH",
                    level,
                    desk.blockPos,
                    5L,
                    "socket=${surface.socket} connected=${snapshot.connected} operational=${snapshot.operational} status=${snapshot.status} " +
                        "range=${snapshot.range} serverTick=${snapshot.updatedAt} clientReceipt=${snapshot.receivedAtClientTick} " +
                        "clientNow=$gameTime age=${gameTime - freshnessBase} threshold=${RadarDisplaySnapshot.STALE_AFTER_TICKS}"
                )
                continue
            }

            val radarPos = snapshot.radarPos
            if (radarPos == null) {
                RadarTrace.periodic("D13_SKIP_RADAR_POS", level, desk.blockPos, 5L, "fresh snapshot unexpectedly has radarPos=null")
                continue
            }
            val radar = level.getBlockEntity(radarPos)
            if (radar == null) {
                RadarTrace.periodic(
                    "D14_SKIP_RADAR_BE",
                    level,
                    desk.blockPos,
                    5L,
                    "client level has no BlockEntity at radarPos=$radarPos loaded=${level.isLoaded(radarPos)} blockState=${level.getBlockState(radarPos)}"
                )
                continue
            }
            if (!native.iradarClass.isInstance(radar)) {
                val failure = IllegalStateException(
                    "Radar block entity at $radarPos is ${radar.javaClass.name}, expected ${native.iradarClass.name}"
                )
                RadarTrace.event("D15_SKIP_NOT_IRADAR", level, desk.blockPos, RadarTrace.throwable(failure))
                reportFailure(failure, level, desk.blockPos)
                continue
            }
            RadarTrace.periodic(
                "D15_CLIENT_RADAR",
                level,
                desk.blockPos,
                10L,
                "radarPos=$radarPos class=${radar.javaClass.name} state=${level.getBlockState(radarPos)} " +
                    "range=${safeInvokeByName(radar, "getRange")} running=${safeInvokeByName(radar, "isRunning")} " +
                    "type=${safeInvokeByName(radar, "getRadarType")} angle=${safeInvokeByName(radar, "getGlobalAngle")} " +
                    "direction=${safeInvokeByName(radar, "getradarDirection")} relative=${safeInvokeByName(radar, "renderRelativeToMonitor")}"
            )

            val socket = sockets.getOrNull(surface.socket)
            if (socket == null) {
                RadarTrace.event(
                    "D16_SKIP_SOCKET",
                    level,
                    desk.blockPos,
                    "surface.socket=${surface.socket} but sockets.size=${sockets.size}"
                )
                continue
            }
            val facing = effectiveFacing(desk, surface.socket)
            RadarTrace.periodic(
                "D16_SOCKET_TRANSFORM",
                level,
                desk.blockPos,
                10L,
                "socket=${surface.socket} type=${surface.type} offset=${socket.offset()} orientation=${socket.orientation()} " +
                    "deskRotation=${ConsoleBlock.rotationFor(desk.blockState)} effectiveFacing=$facing " +
                    "surfaceScale=${surfaceScale(surface.type)} surfaceY=$MODULE_SURFACE_Y nativeBackgroundDepth=$NATIVE_BACKGROUND_DEPTH"
            )

            val virtual = virtualMonitor(native, desk, surface.socket, facing) ?: run {
                RadarTrace.periodic("D17_SKIP_VIRTUAL", level, desk.blockPos, 5L, "virtual MonitorBlockEntity could not be created")
                continue
            }
            if (!hydrate(native, virtual, snapshot, desk)) {
                RadarTrace.periodic("D18_SKIP_HYDRATE", level, desk.blockPos, 5L, "native MonitorBlockEntity.read(...) failed or had no level/radar")
                continue
            }
            val renderer = nativeRenderer(native, virtual.monitor, desk) ?: run {
                RadarTrace.periodic("D19_SKIP_RENDERER", level, desk.blockPos, 5L, "BlockEntityRenderDispatcher returned no compatible MonitorRenderer")
                continue
            }

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

                RadarTrace.periodic(
                    "D30_BEFORE_NATIVE_RENDER",
                    level,
                    desk.blockPos,
                    5L,
                    "socket=${surface.socket} renderer=${renderer.javaClass.name} radar=${radar.javaClass.name} " +
                        "virtualMonitor=${virtual.monitor.javaClass.name} partialTicks=$partialTicks pose=${poseStack.last().pose()} " +
                        "normal=${poseStack.last().normal()} monitorDiagnostics=${nativeMonitorDiagnostics(native, virtual.monitor)}"
                )

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
                RadarTrace.periodic(
                    "D31_NATIVE_RENDER_OK",
                    level,
                    desk.blockPos,
                    5L,
                    "MonitorRenderer.renderRadarDisplay returned normally for socket=${surface.socket}; renderedAny=true"
                )
            } catch (throwable: Throwable) {
                val cause = unwrap(throwable)
                RadarTrace.event("D99_NATIVE_RENDER_EXCEPTION", level, desk.blockPos, RadarTrace.throwable(cause))
                reportFailure(cause, level, desk.blockPos)
            } finally {
                poseStack.popPose()
            }
        }

        RadarTrace.periodic(
            "D40_RENDER_RETURN",
            level,
            desk.blockPos,
            10L,
            "returning renderedAny=$renderedAny surfaces=${surfaces.size}"
        )
        return renderedAny
    }

    private fun applySurfaceTransform(poseStack: PoseStack, type: RadarDisplayType) {
        val scale = surfaceScale(type)
        poseStack.translate(0.5, MODULE_SURFACE_Y - NATIVE_BACKGROUND_DEPTH, 0.5)
        poseStack.scale(scale, 1.0f, scale)
        poseStack.translate(-0.5, 0.0, -0.5)
    }

    private fun surfaceScale(type: RadarDisplayType): Float = when (type) {
        RadarDisplayType.SMALL -> SMALL_SURFACE_SIZE
        RadarDisplayType.LARGE -> LARGE_SURFACE_SIZE
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
            RadarTrace.periodic(
                "D20_VIRTUAL_REUSE",
                level,
                desk.blockPos,
                20L,
                "socket=$socket monitorIdentity=${System.identityHashCode(existing.monitor)} facing=$facing state=${existing.monitor.blockState}"
            )
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
            RadarTrace.event(
                "D20_VIRTUAL_CREATE",
                level,
                desk.blockPos,
                "socket=$socket monitorIdentity=${System.identityHashCode(monitor)} class=${monitor.javaClass.name} " +
                    "type=${monitor.type} state=$state facing=$facing levelSet=${monitor.level === level}"
            )
            VirtualMonitor(monitor, facing).also { bySocket[socket] = it }
        } catch (throwable: Throwable) {
            val cause = unwrap(throwable)
            RadarTrace.event("D99_VIRTUAL_CREATE_EXCEPTION", level, desk.blockPos, RadarTrace.throwable(cause))
            reportFailure(cause, level, desk.blockPos)
            null
        }
    }

    private fun hydrate(
        native: Contract,
        virtual: VirtualMonitor,
        snapshot: RadarDisplaySnapshot,
        desk: ConsoleBlockEntity
    ): Boolean {
        val level = virtual.monitor.level ?: run {
            RadarTrace.event("D21_HYDRATE_NO_LEVEL", desk.level, desk.blockPos, "virtual monitor has no level")
            return false
        }
        val contentKey = snapshot.nativeContentKey()
        if (virtual.contentKey == contentKey) {
            RadarTrace.periodic(
                "D21_HYDRATE_REUSE",
                level,
                desk.blockPos,
                10L,
                "contentKey=$contentKey unchanged; monitorDiagnostics=${nativeMonitorDiagnostics(native, virtual.monitor)}"
            )
            return true
        }

        val radarPos = snapshot.radarPos ?: run {
            RadarTrace.event("D21_HYDRATE_NO_RADAR", level, desk.blockPos, "snapshot.radarPos is null")
            return false
        }
        val monitorTag = CompoundTag().apply {
            putBoolean("HasRadarPos", true)
            put("radarPos", NbtUtils.writeBlockPos(radarPos))
            put("Filter", snapshot.detectionTag.copy())
            snapshot.selectedTrackId?.let { putString("SelectedEntity", it) }
            putInt("Size", 1)
            put("tracks", snapshot.nativeTracks.copy())
            put("SafeZones", ListTag())
        }
        RadarTrace.event(
            "D22_HYDRATE_INPUT",
            level,
            desk.blockPos,
            "contentKey=$contentKey previousKey=${virtual.contentKey} monitorTag=${RadarTrace.tag(monitorTag)}"
        )

        return try {
            native.readClientState.invoke(
                virtual.monitor,
                monitorTag,
                level.registryAccess(),
                true
            )
            virtual.contentKey = contentKey
            RadarTrace.event(
                "D23_HYDRATE_OK",
                level,
                desk.blockPos,
                "native MonitorBlockEntity.read(..., clientPacket=true) returned normally; " +
                    "fields=${describeObjectFields(virtual.monitor)} diagnostics=${nativeMonitorDiagnostics(native, virtual.monitor)}"
            )
            true
        } catch (throwable: Throwable) {
            val cause = unwrap(throwable)
            RadarTrace.event("D99_HYDRATE_EXCEPTION", level, desk.blockPos, RadarTrace.throwable(cause))
            reportFailure(cause, level, desk.blockPos)
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

    private fun nativeRenderer(native: Contract, monitor: BlockEntity, desk: ConsoleBlockEntity): Any? {
        return try {
            val renderer = Minecraft.getInstance().blockEntityRenderDispatcher.getRenderer(monitor)
                ?: throw IllegalStateException("No renderer is registered for the virtual Create: Radars monitor")
            RadarTrace.periodic(
                "D24_RENDERER_LOOKUP",
                desk.level,
                desk.blockPos,
                20L,
                "monitorType=${monitor.type} rendererClass=${renderer.javaClass.name} expected=${native.monitorRendererClass.name}"
            )
            if (!native.monitorRendererClass.isInstance(renderer)) {
                throw IllegalStateException(
                    "Virtual monitor renderer is ${renderer.javaClass.name}, expected ${native.monitorRendererClass.name}"
                )
            }
            renderer
        } catch (throwable: Throwable) {
            val cause = unwrap(throwable)
            RadarTrace.event("D99_RENDERER_LOOKUP_EXCEPTION", desk.level, desk.blockPos, RadarTrace.throwable(cause))
            reportFailure(cause, desk.level, desk.blockPos)
            null
        }
    }

    private fun resolveContract(level: net.minecraft.world.level.Level, deskPos: BlockPos): Contract? {
        contract?.let {
            RadarTrace.periodic(
                "D02_CONTRACT_CACHED",
                level,
                deskPos,
                100L,
                "using cached native contract monitor=${it.monitorClass.name} renderer=${it.monitorRendererClass.name} iradar=${it.iradarClass.name}"
            )
            return it
        }
        if (contractResolutionAttempted) return null
        contractResolutionAttempted = true

        RadarTrace.event(
            "D02_CONTRACT_BEGIN",
            level,
            deskPos,
            "resolving Create: Radars native monitor reflection contract with loader=${CreateRadarNativeMonitorRenderer::class.java.classLoader}"
        )
        return try {
            val loader = CreateRadarNativeMonitorRenderer::class.java.classLoader
            val monitorClass = Class.forName(MONITOR_CLASS, true, loader)
            val monitorBlockClass = Class.forName(MONITOR_BLOCK_CLASS, true, loader)
            val monitorRendererClass = Class.forName(MONITOR_RENDERER_CLASS, true, loader)
            val iradarClass = Class.forName(IRADAR_CLASS, true, loader)
            RadarTrace.event(
                "D02_CONTRACT_CLASSES",
                level,
                deskPos,
                "monitor=${monitorClass.name} monitorBlock=${monitorBlockClass.name} renderer=${monitorRendererClass.name} iradar=${iradarClass.name}"
            )

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
                java.lang.Boolean.TYPE
            ).also {
                if (!it.trySetAccessible()) throw IllegalAccessException("Cannot access MonitorBlockEntity.read")
            }
            val render = monitorRendererClass.getDeclaredMethod(
                "renderRadarDisplay",
                iradarClass,
                monitorClass,
                PoseStack::class.java,
                MultiBufferSource::class.java,
                java.lang.Float.TYPE
            ).also {
                if (!it.trySetAccessible()) throw IllegalAccessException("Cannot access MonitorRenderer.renderRadarDisplay")
            }

            val resolved = Contract(
                monitorClass = monitorClass,
                monitorRendererClass = monitorRendererClass,
                iradarClass = iradarClass,
                monitorConstructor = constructor,
                monitorType = monitorType,
                monitorBlock = monitorBlock,
                facingProperty = facingProperty,
                readClientState = read,
                renderRadarDisplay = render,
                monitorGetRadar = monitorClass.getMethod("getRadar"),
                monitorGetTracks = monitorClass.getMethod("getTracks"),
                monitorGetSize = monitorClass.getMethod("getSize"),
                monitorIsLinked = monitorClass.getMethod("isLinked"),
                monitorIsController = monitorClass.getMethod("isController"),
                monitorGetShip = monitorClass.getMethod("getShip")
            )
            contract = resolved
            RadarTrace.event(
                "D02_CONTRACT_OK",
                level,
                deskPos,
                "monitorType=$monitorType monitorBlock=$monitorBlock facingProperty=${facingProperty.name} " +
                    "constructor=$constructor read=${methodSignature(read)} render=${methodSignature(render)} " +
                    "getRadar=${methodSignature(resolved.monitorGetRadar)} getTracks=${methodSignature(resolved.monitorGetTracks)} " +
                    "getSize=${methodSignature(resolved.monitorGetSize)} isLinked=${methodSignature(resolved.monitorIsLinked)} " +
                    "isController=${methodSignature(resolved.monitorIsController)} getShip=${methodSignature(resolved.monitorGetShip)}"
            )
            resolved
        } catch (throwable: Throwable) {
            val cause = unwrap(throwable)
            RadarTrace.event("D99_CONTRACT_EXCEPTION", level, deskPos, RadarTrace.throwable(cause))
            reportFailure(cause, level, deskPos)
            null
        }
    }

    private fun registryEntry(className: String, fieldName: String, loader: ClassLoader): Any? {
        val holder = Class.forName(className, true, loader).getField(fieldName).get(null)
        val getter = holder.javaClass.methods.firstOrNull { it.name == "get" && it.parameterCount == 0 }
            ?: throw NoSuchMethodException("${holder.javaClass.name}#get()")
        return getter.invoke(holder)
    }

    private fun nativeMonitorDiagnostics(native: Contract, monitor: BlockEntity): String = buildString {
        append("isLinked=").append(safeInvoke(native.monitorIsLinked, monitor))
        append(",isController=").append(safeInvoke(native.monitorIsController, monitor))
        append(",size=").append(safeInvoke(native.monitorGetSize, monitor))
        append(",tracks=").append(summarizeValue(safeInvoke(native.monitorGetTracks, monitor)))
        append(",getRadar=").append(summarizeValue(safeInvoke(native.monitorGetRadar, monitor)))
        append(",ship=").append(summarizeValue(safeInvoke(native.monitorGetShip, monitor)))
    }

    private fun safeInvoke(method: Method, receiver: Any): Any? = runCatching {
        method.invoke(receiver)
    }.getOrElse { "<${it.javaClass.simpleName}:${it.message}>" }

    private fun safeInvokeByName(receiver: Any, methodName: String): Any? = runCatching {
        val method = receiver.javaClass.methods.firstOrNull { it.name == methodName && it.parameterCount == 0 }
            ?: receiver.javaClass.declaredMethods.firstOrNull { it.name == methodName && it.parameterCount == 0 }
            ?: return@runCatching "<missing>"
        if (!method.canAccess(receiver)) method.trySetAccessible()
        method.invoke(receiver)
    }.getOrElse { "<${it.javaClass.simpleName}:${it.message}>" }

    private fun describeObjectFields(instance: Any): String {
        val parts = mutableListOf<String>()
        var current: Class<*>? = instance.javaClass
        while (current != null && current != Any::class.java) {
            for (field in current.declaredFields) {
                if (Modifier.isStatic(field.modifiers)) continue
                val value = runCatching {
                    if (!field.canAccess(instance)) field.trySetAccessible()
                    field.get(instance)
                }.getOrElse { "<${it.javaClass.simpleName}:${it.message}>" }
                parts += "${current.simpleName}.${field.name}=${summarizeValue(value)}"
            }
            current = current.superclass
        }
        return parts.joinToString(prefix = "{", postfix = "}", separator = ", ")
    }

    private fun summarizeValue(value: Any?): String = when (value) {
        null -> "null"
        is CompoundTag -> RadarTrace.tag(value, 12000)
        is Collection<*> -> "Collection(size=${value.size}, values=${value.take(64)})"
        is Map<*, *> -> "Map(size=${value.size}, entries=${value.entries.take(64)})"
        else -> value.toString().take(12000)
    }

    private fun snapshotSummary(snapshot: RadarDisplaySnapshot?, gameTime: Long): String = if (snapshot == null) {
        "null"
    } else {
        val base = snapshot.receivedAtClientTick.takeIf { it >= 0L } ?: snapshot.updatedAt
        "status=${snapshot.status},connected=${snapshot.connected},operational=${snapshot.operational},radar=${snapshot.radarPos}," +
            "range=${snapshot.range},tracks=${snapshot.trackCount},selected=${snapshot.selectedTrackId},serverTick=${snapshot.updatedAt}," +
            "clientReceipt=${snapshot.receivedAtClientTick},age=${gameTime - base},fresh=${RadarDisplaySnapshot.isFresh(snapshot, gameTime)}"
    }

    private fun methodSignature(method: Method): String =
        "${method.declaringClass.name}#${method.name}(${method.parameterTypes.joinToString { it.name }}):${method.returnType.name}"

    private fun reportFailure(cause: Throwable, level: net.minecraft.world.level.Level?, deskPos: BlockPos?) {
        val signature = "${cause.javaClass.name}:${cause.message.orEmpty()}"
        if (signature != lastFailureSignature) {
            RadarTrace.event("D99_FAILURE", level, deskPos, "${RadarTrace.throwable(cause)}")
        }
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
        val renderRadarDisplay: Method,
        val monitorGetRadar: Method,
        val monitorGetTracks: Method,
        val monitorGetSize: Method,
        val monitorIsLinked: Method,
        val monitorIsController: Method,
        val monitorGetShip: Method
    )

    private data class VirtualMonitor(
        val monitor: BlockEntity,
        val facing: Direction,
        var contentKey: Int = Int.MIN_VALUE
    )
}
