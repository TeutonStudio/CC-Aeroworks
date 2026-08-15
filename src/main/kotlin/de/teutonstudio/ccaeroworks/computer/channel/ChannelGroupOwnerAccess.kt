package de.teutonstudio.ccaeroworks.computer.channel

import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity

interface ChannelGroupOwnerAccess {
    fun ccaeroworks_channelGroups(): ChannelGroupBank
}

fun ComputerControlDeskBlockEntity.channelGroups(): ChannelGroupBank =
    (this as ChannelGroupOwnerAccess).ccaeroworks_channelGroups()
