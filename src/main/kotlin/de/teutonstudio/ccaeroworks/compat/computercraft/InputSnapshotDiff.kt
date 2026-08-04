package de.teutonstudio.ccaeroworks.compat.computercraft

import de.teutonstudio.ccaeroworks.compat.aeroworks.DeskInputSnapshot

data class DeskInputChange(
    val socket: Int,
    val channel: String,
    val moduleId: String,
    val value: Int?
)

object InputSnapshotDiff {
    @JvmStatic
    fun changed(
        previous: Map<Int, DeskInputSnapshot>,
        current: Map<Int, DeskInputSnapshot>
    ): List<DeskInputChange> = (previous.keys + current.keys)
        .toSortedSet()
        .flatMap { socket ->
            val oldModule = previous[socket]
            val newModule = current[socket]
            (oldModule?.channels.orEmpty().keys + newModule?.channels.orEmpty().keys)
                .toSortedSet()
                .mapNotNull { channel ->
                    val oldValue = oldModule?.channels?.get(channel)
                    val newValue = newModule?.channels?.get(channel)
                    val moduleChanged = oldModule?.moduleId != newModule?.moduleId
                    if (oldValue == newValue && !moduleChanged) {
                        null
                    } else {
                        DeskInputChange(
                            socket = socket,
                            channel = channel,
                            moduleId = if (newValue == null) {
                                oldModule?.moduleId.orEmpty()
                            } else {
                                newModule?.moduleId.orEmpty()
                            },
                            value = newValue
                        )
                    }
                }
        }
}

object DeskInputEventArguments {
    @JvmStatic
    fun create(
        attachmentName: String,
        socket: Int,
        moduleId: String,
        value: Int?,
        channel: String
    ): Array<Any?> = arrayOf(
        attachmentName,
        socket,
        moduleId,
        value,
        channel,
        de.teutonstudio.ccaeroworks.compat.aeroworks.DeskSockets.name(socket)
    )
}
