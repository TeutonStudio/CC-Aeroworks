package de.teutonstudio.ccaeroworks.compat.computercraft

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import java.util.concurrent.CopyOnWriteArrayList

object ControlDeskPeripheralFactory {
    private val extensions = CopyOnWriteArrayList<(ConsoleBlockEntity) -> ControlDeskPeripheral?>()

    fun registerExtension(factory: (ConsoleBlockEntity) -> ControlDeskPeripheral?) {
        extensions += factory
    }

    fun create(desk: ConsoleBlockEntity): ControlDeskPeripheral =
        extensions.asSequence().mapNotNull { it(desk) }.firstOrNull() ?: ControlDeskPeripheral(desk)
}
