package de.teutonstudio.ccaeroworks.computer

import dan200.computercraft.api.lua.ILuaAPI
import dan200.computercraft.api.lua.LuaFunction
import de.teutonstudio.ccaeroworks.display.DisplayScriptCatalog
import java.util.WeakHashMap

data class DisplayScriptObservedDependency(
    val key: String,
    val kind: String,
    val phases: List<String>
)

data class DisplayScriptRuntimeObservation(
    val path: String,
    val roles: List<String>,
    val deskId: String,
    val socket: Int,
    val dependencies: List<DisplayScriptObservedDependency>,
    val touchEvents: List<String>
)

object DisplayScriptDiagnosticsRegistry {
    private val runtimes = WeakHashMap<ComputerControlDeskBlockEntity, DisplayScriptDiagnosticsRuntime>()

    @Synchronized
    fun forOwner(owner: ComputerControlDeskBlockEntity): DisplayScriptDiagnosticsRuntime =
        runtimes.getOrPut(owner) { DisplayScriptDiagnosticsRuntime() }

    @Synchronized
    fun snapshot(owner: ComputerControlDeskBlockEntity): List<DisplayScriptRuntimeObservation> =
        runtimes[owner]?.snapshot().orEmpty()
}

class DisplayScriptDiagnosticsRuntime {
    private data class RuntimeKey(val path: String, val deskId: String, val socket: Int)
    private data class PendingDependency(val key: String, val kind: String)
    private data class PendingExecution(
        val key: RuntimeKey,
        val role: String,
        val phase: String,
        val scope: String,
        val dependencies: LinkedHashMap<String, PendingDependency> = linkedMapOf()
    )
    private data class ScopeObservation(
        val phase: String,
        val dependencies: Map<String, PendingDependency>
    )
    private class MutableObservation {
        val roles = linkedSetOf<String>()
        val scopes = linkedMapOf<String, ScopeObservation>()
        val touchEvents = linkedSetOf<String>()
    }

    private val stack = ArrayDeque<PendingExecution>()
    private val observations = linkedMapOf<RuntimeKey, MutableObservation>()

    @Synchronized
    fun begin(path: String, role: String, deskId: String, socket: Int, phase: String, scope: String): Boolean {
        val normalizedPath = DisplayScriptCatalog.normalizePath(path) ?: return false
        val normalizedDesk = deskId.trim().take(MAX_DESK_ID_LENGTH)
        if (normalizedDesk.isEmpty()) return false
        val key = RuntimeKey(normalizedPath, normalizedDesk, socket)
        val observation = observations[key] ?: run {
            if (observations.size >= MAX_INSTANCES) return false
            MutableObservation().also { observations[key] = it }
        }
        val normalizedRole = normalizeToken(role, "runtime")
        observation.roles.add(normalizedRole)
        stack.addLast(
            PendingExecution(
                key = key,
                role = normalizedRole,
                phase = normalizeToken(phase, "runtime"),
                scope = scope.trim().take(MAX_SCOPE_LENGTH).ifEmpty { "runtime" }
            )
        )
        return true
    }

    @Synchronized
    fun finish() {
        val execution = stack.removeLastOrNull() ?: return
        val observation = observations[execution.key] ?: return
        if (execution.scope !in observation.scopes && observation.scopes.size >= MAX_SCOPES_PER_INSTANCE) return
        observation.roles.add(execution.role)
        observation.scopes[execution.scope] = ScopeObservation(
            phase = execution.phase,
            dependencies = execution.dependencies.toMap()
        )
    }

    @Synchronized
    fun read(key: String, kind: String) {
        val execution = stack.lastOrNull() ?: return
        val normalizedKey = key.trim().take(MAX_DEPENDENCY_LENGTH)
        if (normalizedKey.isEmpty()) return
        if (normalizedKey !in execution.dependencies && execution.dependencies.size >= MAX_DEPENDENCIES_PER_SCOPE) return
        execution.dependencies[normalizedKey] = PendingDependency(
            key = normalizedKey,
            kind = normalizeToken(kind, "custom")
        )
    }

    @Synchronized
    fun setTouchHandlers(
        path: String,
        deskId: String,
        socket: Int,
        tap: Boolean,
        draw: Boolean,
        doubleTap: Boolean,
        pointer: Boolean
    ) {
        val normalizedPath = DisplayScriptCatalog.normalizePath(path) ?: return
        val normalizedDesk = deskId.trim().take(MAX_DESK_ID_LENGTH)
        if (normalizedDesk.isEmpty()) return
        val key = RuntimeKey(normalizedPath, normalizedDesk, socket)
        val observation = observations[key] ?: run {
            if (observations.size >= MAX_INSTANCES) return
            MutableObservation().also { observations[key] = it }
        }
        observation.touchEvents.clear()
        if (tap) observation.touchEvents += "tap"
        if (draw) observation.touchEvents += "draw"
        if (doubleTap) observation.touchEvents += "double_tap"
        if (pointer) observation.touchEvents += "pointer"
    }

    @Synchronized
    fun reset() {
        stack.clear()
        observations.clear()
    }

    @Synchronized
    fun snapshot(): List<DisplayScriptRuntimeObservation> = observations.map { (key, value) ->
        data class MutableDependency(val key: String, var kind: String, val phases: MutableSet<String>)
        val dependencies = linkedMapOf<String, MutableDependency>()
        value.scopes.values.forEach { scope ->
            scope.dependencies.values.forEach { dependency ->
                val merged = dependencies.getOrPut(dependency.key) {
                    MutableDependency(dependency.key, dependency.kind, linkedSetOf())
                }
                if (merged.kind == "custom" && dependency.kind != "custom") merged.kind = dependency.kind
                merged.phases += scope.phase
            }
        }
        DisplayScriptRuntimeObservation(
            path = key.path,
            roles = value.roles.sorted(),
            deskId = key.deskId,
            socket = key.socket,
            dependencies = dependencies.values
                .map { dependency ->
                    DisplayScriptObservedDependency(
                        key = dependency.key,
                        kind = dependency.kind,
                        phases = dependency.phases.sortedWith(compareBy(::phaseOrder, String::toString))
                    )
                }
                .sortedBy(DisplayScriptObservedDependency::key),
            touchEvents = value.touchEvents.toList()
        )
    }.sortedWith(compareBy(DisplayScriptRuntimeObservation::path, DisplayScriptRuntimeObservation::deskId, DisplayScriptRuntimeObservation::socket))

    private fun normalizeToken(value: String, fallback: String): String =
        value.trim().lowercase().take(MAX_TOKEN_LENGTH).ifEmpty { fallback }

    private fun phaseOrder(phase: String): Int = when (phase) {
        "composition" -> 0
        "layout" -> 1
        "draw" -> 2
        "load" -> 3
        "event" -> 4
        else -> 5
    }

    private companion object {
        const val MAX_INSTANCES = 128
        const val MAX_SCOPES_PER_INSTANCE = 256
        const val MAX_DEPENDENCIES_PER_SCOPE = 64
        const val MAX_DEPENDENCY_LENGTH = 256
        const val MAX_DESK_ID_LENGTH = 128
        const val MAX_SCOPE_LENGTH = 256
        const val MAX_TOKEN_LENGTH = 32
    }
}

class DisplayScriptDiagnosticsLuaApi(
    private val access: ComputerConsoleAccess
) : ILuaAPI {
    override fun getNames(): Array<String> = emptyArray()

    override fun getModuleName(): String = "cc_aeroworks.display_diagnostics"

    override fun startup() {
        runtime()?.reset()
    }

    @LuaFunction
    fun begin(path: String, role: String, deskId: String, socket: Int, phase: String, scope: String): Boolean =
        runtime()?.begin(path, role, deskId, socket, phase, scope) == true

    @LuaFunction
    fun finish() {
        runtime()?.finish()
    }

    @LuaFunction
    fun read(key: String, kind: String) {
        runtime()?.read(key, kind)
    }

    @LuaFunction
    fun setTouchHandlers(
        path: String,
        deskId: String,
        socket: Int,
        tap: Boolean,
        draw: Boolean,
        doubleTap: Boolean,
        pointer: Boolean
    ) {
        runtime()?.setTouchHandlers(path, deskId, socket, tap, draw, doubleTap, pointer)
    }

    private fun runtime(): DisplayScriptDiagnosticsRuntime? =
        access.owner()?.let(DisplayScriptDiagnosticsRegistry::forOwner)
}
