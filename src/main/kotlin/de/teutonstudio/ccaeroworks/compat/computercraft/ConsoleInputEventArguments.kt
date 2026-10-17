package de.teutonstudio.ccaeroworks.compat.computercraft

import de.teutonstudio.ccaeroworks.compat.aeroworks.DeskSockets

/** Keep the original seven event arguments stable; script dispatch metadata is appended. */
object ConsoleInputEventArguments {
    fun create(
        deskId: String,
        deskIndex: Int,
        socket: Int,
        moduleId: String,
        value: Int?,
        channel: String,
        handlerPath: String,
        previousValue: Int?
    ): Array<Any?> = arrayOf(
        deskId, deskIndex, socket, DeskSockets.name(socket), moduleId, value, channel,
        handlerPath, previousValue
    )
}
