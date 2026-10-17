package de.teutonstudio.ccaeroworks.computer

/** Left and right are in the original console row's facing-relative order. */
internal object ComputerTransferSelection {
    fun <T> target(left: List<T>, right: List<T>): T? =
        if (left.size >= right.size) left.firstOrNull() else right.firstOrNull()
}
