package de.teutonstudio.ccaeroworks.computer.channel

import de.teutonstudio.ccaeroworks.computer.ConsoleComputer

interface ChannelGroupOwnerAccess {
    fun ccaeroworks_channelGroups(): ChannelGroupBank
}

fun ConsoleComputer.channelGroups(): ChannelGroupBank =
    groups
