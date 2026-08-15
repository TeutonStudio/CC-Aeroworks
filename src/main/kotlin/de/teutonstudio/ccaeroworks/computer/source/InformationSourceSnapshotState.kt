package de.teutonstudio.ccaeroworks.computer.source

enum class InformationSourceKind(val title: String) {
    DISPLAY_LINK("DISPLAY LINKS"),
    STORAGE("STORAGE"),
    RADAR_DATA_LINK("RADAR DATA LINKS"),
    RADAR_NETWORK_CONTROLLER("NETWORK CONTROLLERS")
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

data class InformationSourceSnapshot(
    val sources: List<InformationSourceView>
)

object InformationSourceSnapshotState {
    @Volatile
    private var current = InformationSourceSnapshot(emptyList())

    fun accept(snapshot: InformationSourceSnapshot) {
        current = snapshot.copy(sources = snapshot.sources.toList())
    }

    fun get(): InformationSourceSnapshot = current

    fun clear() {
        current = InformationSourceSnapshot(emptyList())
    }
}
