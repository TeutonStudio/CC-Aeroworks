package de.teutonstudio.ccaeroworks.compat.computercraft

data class DeskInputChange(val socket: Int, val channel: String, val value: Int)

object InputSnapshotDiff {
    @JvmStatic
    fun changed(
        previous: Map<Int, Map<String, Int>>,
        current: Map<Int, Map<String, Int>>
    ): List<DeskInputChange> = current.entries
        .sortedBy { it.key }
        .flatMap { (socket, channels) ->
            channels.entries
                .sortedBy { it.key }
                .mapNotNull { (channel, value) ->
                    value.takeIf { previous[socket]?.get(channel) != it }
                        ?.let { DeskInputChange(socket, channel, it) }
                }
        }
}

object DeskInputEventArguments {
    @JvmStatic
    fun create(
        attachmentName: String,
        socket: Int,
        moduleId: String,
        value: Int,
        channel: String
    ): Array<Any> = arrayOf(
        attachmentName,
        socket,
        moduleId,
        value,
        channel,
        de.teutonstudio.ccaeroworks.compat.aeroworks.DeskSockets.name(socket)
    )
}
