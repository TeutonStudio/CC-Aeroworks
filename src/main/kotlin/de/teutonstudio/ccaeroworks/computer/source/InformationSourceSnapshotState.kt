package de.teutonstudio.ccaeroworks.computer.source

enum class InformationSourceKind(val title: String) {
    DISPLAY_LINK("DISPLAY LINKS"),
    STORAGE("STORAGE"),
    RADAR_DATA_LINK("RADAR DATA LINKS"),
    RADAR_NETWORK_CONTROLLER("NETWORK CONTROLLERS"),
    GPS("GPS"),
    DISPLAY_SCRIPT("DISPLAY SCRIPTS")
}

data class InformationSourceView(
    val id: String,
    val kind: InformationSourceKind,
    val label: String,
    val status: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val side: String,
    val details: String
)

data class DisplayScriptDependencyView(
    val key: String,
    val label: String,
    val kind: String,
    val phases: List<String>
)

data class DisplayScriptInstanceView(
    val deskId: String,
    val deskIndex: Int,
    val socket: Int,
    val socketName: String,
    val status: String,
    val dependencies: List<DisplayScriptDependencyView>,
    val touchEvents: List<String>
)

data class DisplayScriptInformationView(
    val path: String,
    val name: String,
    val status: String,
    val roles: List<String>,
    val imports: List<String>,
    val declaredTouchEvents: List<String>,
    val instances: List<DisplayScriptInstanceView>
)

data class InformationSourceSnapshot(
    val sources: List<InformationSourceView>,
    val displayScripts: List<DisplayScriptInformationView> = emptyList()
)

object InformationSourceSnapshotState {
    @Volatile
    private var current = InformationSourceSnapshot(emptyList())

    fun accept(snapshot: InformationSourceSnapshot) {
        current = snapshot.copy(
            sources = snapshot.sources.toList(),
            displayScripts = snapshot.displayScripts.map { script ->
                script.copy(
                    roles = script.roles.toList(),
                    imports = script.imports.toList(),
                    declaredTouchEvents = script.declaredTouchEvents.toList(),
                    instances = script.instances.map { instance ->
                        instance.copy(
                            dependencies = instance.dependencies.map { dependency ->
                                dependency.copy(phases = dependency.phases.toList())
                            },
                            touchEvents = instance.touchEvents.toList()
                        )
                    }
                )
            }
        )
    }

    fun get(): InformationSourceSnapshot = current

    fun clear() {
        current = InformationSourceSnapshot(emptyList())
    }
}
