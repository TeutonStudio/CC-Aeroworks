package de.teutonstudio.ccaeroworks.contraption

interface MobileContraptionRuntimeAccess {
    fun ccaeroworks_mobileRuntime(): MobileContraptionRuntime?
    fun ccaeroworks_setMobileRuntime(runtime: MobileContraptionRuntime?)
}
