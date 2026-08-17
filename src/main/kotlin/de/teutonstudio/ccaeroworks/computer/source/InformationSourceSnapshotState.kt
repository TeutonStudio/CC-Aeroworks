package de.teutonstudio.ccaeroworks.computer.source

data class InformationSourceKind(val id: String, val title: String, val order: Int)

object InformationSourceKinds {
    val DISPLAY_LINK = InformationSourceKind("display_link", "DISPLAY LINKS", 10)
    val STORAGE = InformationSourceKind("storage", "STORAGE", 20)
    val GPS = InformationSourceKind("gps", "GPS", 30)
    val DISPLAY_SCRIPT = InformationSourceKind("display_script", "DISPLAY SCRIPTS", 40)
    val CORE = listOf(DISPLAY_LINK, STORAGE, GPS, DISPLAY_SCRIPT)
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
