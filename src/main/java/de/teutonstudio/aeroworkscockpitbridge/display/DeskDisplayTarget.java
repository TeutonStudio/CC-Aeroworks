package de.teutonstudio.aeroworkscockpitbridge.display;

/**
 * Architecture marker for the future Create DisplayTarget implementation.
 *
 * <p>Create 6.0.10 signatures are verified, but the foreign-mod development classpath is not yet
 * part of this MDK. The real implementation must extend Create's abstract DisplayTarget, report
 * rows before columns, and update bridge-owned desk display state. It must not expose a separate
 * CC:Tweaked peripheral.</p>
 */
public final class DeskDisplayTarget {
    private DeskDisplayTarget() {
    }
}
