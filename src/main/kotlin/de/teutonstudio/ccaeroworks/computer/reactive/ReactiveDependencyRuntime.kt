package de.teutonstudio.ccaeroworks.computer.reactive

import dan200.computercraft.api.lua.IComputerSystem
import de.teutonstudio.ccaeroworks.CCAeroworks

enum class ReactivePhase(val wireName: String) {
    COMPOSITION("composition"),
    LAYOUT("layout"),
    DRAW("draw");

    companion object {
        fun parse(value: String): ReactivePhase = entries.firstOrNull { it.wireName == value.lowercase() }
            ?: throw IllegalArgumentException("Unknown reactive phase '$value'")
    }
}

data class ReactiveScope(
    val id: String,
    val phase: ReactivePhase
)

class ReactiveDependencyRuntime(
    private val system: IComputerSystem
) {
    private val scopeStack = ArrayDeque<ReactiveScope>()
    private val dependenciesByScope = linkedMapOf<ReactiveScope, MutableSet<String>>()
    private val scopesByDependency = linkedMapOf<String, MutableSet<ReactiveScope>>()
    private val invalidated = linkedSetOf<ReactiveScope>()
    private var eventQueued = false

    @Synchronized
    fun beginScope(id: String, phase: ReactivePhase) {
        require(id.isNotBlank()) { "Reactive scope id must not be blank" }
        val scope = ReactiveScope(id, phase)
        removeDependencies(scope)
        dependenciesByScope[scope] = linkedSetOf()
        scopeStack.addLast(scope)
    }

    @Synchronized
    fun endScope() {
        check(scopeStack.isNotEmpty()) { "No reactive scope is active" }
        scopeStack.removeLast()
    }

    @Synchronized
    fun read(dependency: String) {
        val normalized = dependency.trim()
        if (normalized.isEmpty()) return
        val scope = scopeStack.lastOrNull() ?: return
        val dependencies = dependenciesByScope.getOrPut(scope) { linkedSetOf() }
        if (!dependencies.add(normalized)) return
        scopesByDependency.getOrPut(normalized) { linkedSetOf() }.add(scope)
    }

    @Synchronized
    fun changed(dependency: String) {
        val normalized = dependency.trim()
        if (normalized.isEmpty()) return
        val affected = scopesByDependency[normalized].orEmpty()
        if (affected.isEmpty()) return
        invalidated.addAll(affected)
        queueInvalidationEvent()
    }

    @Synchronized
    fun forgetScope(id: String) {
        val scopes = dependenciesByScope.keys.filter { it.id == id }
        scopes.forEach(::removeDependencies)
        invalidated.removeIf { it.id == id }
    }

    @Synchronized
    fun consumeInvalidations(): List<Map<String, Any>> {
        val result = invalidated
            .sortedWith(compareBy<ReactiveScope>({ phaseOrder(it.phase) }, ReactiveScope::id))
            .map { scope ->
                linkedMapOf<String, Any>(
                    "id" to scope.id,
                    "phase" to scope.phase.wireName
                )
            }
        invalidated.clear()
        eventQueued = false
        return result
    }

    @Synchronized
    fun describeDependencies(): Map<String, List<Map<String, String>>> =
        scopesByDependency.mapValuesTo(linkedMapOf()) { (_, scopes) ->
            scopes.sortedBy(ReactiveScope::id).map { scope ->
                linkedMapOf(
                    "id" to scope.id,
                    "phase" to scope.phase.wireName
                )
            }
        }

    @Synchronized
    fun reset() {
        scopeStack.clear()
        dependenciesByScope.clear()
        scopesByDependency.clear()
        invalidated.clear()
        eventQueued = false
    }

    private fun removeDependencies(scope: ReactiveScope) {
        val old = dependenciesByScope.remove(scope).orEmpty()
        old.forEach { dependency ->
            scopesByDependency[dependency]?.let { scopes ->
                scopes.remove(scope)
                if (scopes.isEmpty()) scopesByDependency.remove(dependency)
            }
        }
    }

    private fun queueInvalidationEvent() {
        if (eventQueued) return
        eventQueued = true
        system.queueEvent(CCAeroworks.UI_INVALIDATED_EVENT)
    }

    private fun phaseOrder(phase: ReactivePhase): Int = when (phase) {
        ReactivePhase.COMPOSITION -> 0
        ReactivePhase.LAYOUT -> 1
        ReactivePhase.DRAW -> 2
    }
}
