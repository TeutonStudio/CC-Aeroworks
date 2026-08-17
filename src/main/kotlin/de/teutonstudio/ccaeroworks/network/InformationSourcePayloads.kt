package de.teutonstudio.ccaeroworks.network

import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.computer.ControlDeskUiSwitchState
import de.teutonstudio.ccaeroworks.computer.source.DisplayScriptDependencyView
import de.teutonstudio.ccaeroworks.computer.source.DisplayScriptInformationView
import de.teutonstudio.ccaeroworks.computer.source.DisplayScriptInstanceView
import de.teutonstudio.ccaeroworks.computer.source.InformationSourceKind
import de.teutonstudio.ccaeroworks.computer.source.InformationSourceSnapshot
import de.teutonstudio.ccaeroworks.computer.source.InformationSourceSnapshotBuilder
import de.teutonstudio.ccaeroworks.computer.source.InformationSourceSnapshotState
import de.teutonstudio.ccaeroworks.computer.source.InformationSourceView
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext

class RequestInformationSourceSnapshotPayload : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE = CustomPacketPayload.Type<RequestInformationSourceSnapshotPayload>(
            CCAeroworks.id("request_information_source_snapshot")
        )

        @JvmField
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, RequestInformationSourceSnapshotPayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, RequestInformationSourceSnapshotPayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf) = RequestInformationSourceSnapshotPayload()
                override fun encode(buffer: RegistryFriendlyByteBuf, payload: RequestInformationSourceSnapshotPayload) = Unit
            }

        @JvmStatic
        fun handle(payload: RequestInformationSourceSnapshotPayload, context: IPayloadContext) {
            val player = context.player() as? ServerPlayer ?: return
            val owner = ControlDeskUiSwitchState.activeComputerDesk(player) ?: return
            PacketDistributor.sendToPlayer(
                player,
                InformationSourceSnapshotPayload(InformationSourceSnapshotBuilder.build(owner))
            )
        }
    }
}

data class InformationSourceSnapshotPayload(
    val snapshot: InformationSourceSnapshot
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        private const val MAX_SOURCES = 256
        private const val MAX_SCRIPTS = 256
        private const val MAX_ROLES = 16
        private const val MAX_IMPORTS = 32
        private const val MAX_TOUCH_EVENTS = 8
        private const val MAX_INSTANCES = 32
        private const val MAX_DEPENDENCIES = 64
        private const val MAX_PHASES = 8

        @JvmField
        val TYPE = CustomPacketPayload.Type<InformationSourceSnapshotPayload>(
            CCAeroworks.id("information_source_snapshot")
        )

        @JvmField
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, InformationSourceSnapshotPayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, InformationSourceSnapshotPayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf): InformationSourceSnapshotPayload {
                    val sourceCount = boundedCount(buffer, MAX_SOURCES, "information source")
                    val sources = ArrayList<InformationSourceView>(sourceCount)
                    repeat(sourceCount) {
                        val kind = InformationSourceKind(
                            id = buffer.readUtf(64),
                            title = buffer.readUtf(64),
                            order = buffer.readVarInt()
                        )
                        sources += InformationSourceView(
                            id = buffer.readUtf(256),
                            kind = kind,
                            label = buffer.readUtf(128),
                            status = buffer.readUtf(32),
                            x = buffer.readInt(),
                            y = buffer.readInt(),
                            z = buffer.readInt(),
                            side = buffer.readUtf(16),
                            details = buffer.readUtf(160)
                        )
                    }

                    val scriptCount = boundedCount(buffer, MAX_SCRIPTS, "display script")
                    val scripts = ArrayList<DisplayScriptInformationView>(scriptCount)
                    repeat(scriptCount) {
                        val path = buffer.readUtf(256)
                        val name = buffer.readUtf(128)
                        val status = buffer.readUtf(32)
                        val roles = readStrings(buffer, MAX_ROLES, 32, "script roles")
                        val imports = readStrings(buffer, MAX_IMPORTS, 128, "script imports")
                        val declaredTouchEvents = readStrings(buffer, MAX_TOUCH_EVENTS, 32, "declared touch events")
                        val instanceCount = boundedCount(buffer, MAX_INSTANCES, "script instance")
                        val instances = ArrayList<DisplayScriptInstanceView>(instanceCount)
                        repeat(instanceCount) {
                            val deskId = buffer.readUtf(128)
                            val deskIndex = buffer.readInt()
                            val socket = buffer.readInt()
                            val socketName = buffer.readUtf(32)
                            val instanceStatus = buffer.readUtf(32)
                            val dependencyCount = boundedCount(buffer, MAX_DEPENDENCIES, "script dependency")
                            val dependencies = ArrayList<DisplayScriptDependencyView>(dependencyCount)
                            repeat(dependencyCount) {
                                dependencies += DisplayScriptDependencyView(
                                    key = buffer.readUtf(256),
                                    label = buffer.readUtf(128),
                                    kind = buffer.readUtf(32),
                                    phases = readStrings(buffer, MAX_PHASES, 32, "dependency phases")
                                )
                            }
                            instances += DisplayScriptInstanceView(
                                deskId = deskId,
                                deskIndex = deskIndex,
                                socket = socket,
                                socketName = socketName,
                                status = instanceStatus,
                                dependencies = dependencies,
                                touchEvents = readStrings(buffer, MAX_TOUCH_EVENTS, 32, "runtime touch events")
                            )
                        }
                        scripts += DisplayScriptInformationView(
                            path = path,
                            name = name,
                            status = status,
                            roles = roles,
                            imports = imports,
                            declaredTouchEvents = declaredTouchEvents,
                            instances = instances
                        )
                    }
                    return InformationSourceSnapshotPayload(InformationSourceSnapshot(sources, scripts))
                }

                override fun encode(buffer: RegistryFriendlyByteBuf, payload: InformationSourceSnapshotPayload) {
                    val sources = payload.snapshot.sources.take(MAX_SOURCES)
                    buffer.writeVarInt(sources.size)
                    sources.forEach { source ->
                        buffer.writeUtf(source.kind.id.take(64), 64)
                        buffer.writeUtf(source.kind.title.take(64), 64)
                        buffer.writeVarInt(source.kind.order)
                        buffer.writeUtf(source.id.take(256), 256)
                        buffer.writeUtf(source.label.take(128), 128)
                        buffer.writeUtf(source.status.take(32), 32)
                        buffer.writeInt(source.x)
                        buffer.writeInt(source.y)
                        buffer.writeInt(source.z)
                        buffer.writeUtf(source.side.take(16), 16)
                        buffer.writeUtf(source.details.take(160), 160)
                    }

                    val scripts = payload.snapshot.displayScripts.take(MAX_SCRIPTS)
                    buffer.writeVarInt(scripts.size)
                    scripts.forEach { script ->
                        buffer.writeUtf(script.path.take(256), 256)
                        buffer.writeUtf(script.name.take(128), 128)
                        buffer.writeUtf(script.status.take(32), 32)
                        writeStrings(buffer, script.roles, MAX_ROLES, 32)
                        writeStrings(buffer, script.imports, MAX_IMPORTS, 128)
                        writeStrings(buffer, script.declaredTouchEvents, MAX_TOUCH_EVENTS, 32)
                        val instances = script.instances.take(MAX_INSTANCES)
                        buffer.writeVarInt(instances.size)
                        instances.forEach { instance ->
                            buffer.writeUtf(instance.deskId.take(128), 128)
                            buffer.writeInt(instance.deskIndex)
                            buffer.writeInt(instance.socket)
                            buffer.writeUtf(instance.socketName.take(32), 32)
                            buffer.writeUtf(instance.status.take(32), 32)
                            val dependencies = instance.dependencies.take(MAX_DEPENDENCIES)
                            buffer.writeVarInt(dependencies.size)
                            dependencies.forEach { dependency ->
                                buffer.writeUtf(dependency.key.take(256), 256)
                                buffer.writeUtf(dependency.label.take(128), 128)
                                buffer.writeUtf(dependency.kind.take(32), 32)
                                writeStrings(buffer, dependency.phases, MAX_PHASES, 32)
                            }
                            writeStrings(buffer, instance.touchEvents, MAX_TOUCH_EVENTS, 32)
                        }
                    }
                }
            }

        @JvmStatic
        fun handle(payload: InformationSourceSnapshotPayload, context: IPayloadContext) {
            InformationSourceSnapshotState.accept(payload.snapshot)
        }

        private fun boundedCount(buffer: RegistryFriendlyByteBuf, max: Int, label: String): Int {
            val count = buffer.readVarInt()
            require(count in 0..max) { "Invalid $label count: $count" }
            return count
        }

        private fun readStrings(
            buffer: RegistryFriendlyByteBuf,
            maxCount: Int,
            maxLength: Int,
            label: String
        ): List<String> {
            val count = boundedCount(buffer, maxCount, label)
            return List(count) { buffer.readUtf(maxLength) }
        }

        private fun writeStrings(
            buffer: RegistryFriendlyByteBuf,
            values: List<String>,
            maxCount: Int,
            maxLength: Int
        ) {
            val bounded = values.take(maxCount)
            buffer.writeVarInt(bounded.size)
            bounded.forEach { buffer.writeUtf(it.take(maxLength), maxLength) }
        }
    }
}
