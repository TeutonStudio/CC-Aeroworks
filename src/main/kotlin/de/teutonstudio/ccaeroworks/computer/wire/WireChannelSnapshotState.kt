package de.teutonstudio.ccaeroworks.computer.wire

/** Latest server snapshot used by the ComputerControlDesk channel manager. */
object WireChannelSnapshotState {
    @Volatile
    private var current: WireChannelBankView = WireChannelBankView("none", false, emptyList())

    fun accept(snapshot: WireChannelBankView) {
        current = snapshot.copy(channels = snapshot.channels.toList())
    }

    fun get(): WireChannelBankView = current

    fun clear() {
        current = WireChannelBankView("none", false, emptyList())
    }
}
