package de.teutonstudio.ccaeroworks.display

import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class IndividualDisplayResolutionTest {
    @Test
    fun globalModeOverridesStoredSettingsAndIndividualModeClampsDefaultsAndStoredValues() {
        assertEquals(512, DisplayResolutionSettings.effectivePpb(true, 512, 256, 32))
        assertEquals(256, DisplayResolutionSettings.effectivePpb(false, 512, 256, null))
        assertEquals(32, DisplayResolutionSettings.effectivePpb(false, 512, 256, 32))
        assertEquals(128, DisplayResolutionSettings.effectivePpb(false, 512, 128, 256))
    }

    @Test
    fun luaDensityRejectsFractionalNonNumericAndNonFiniteArguments() {
        for (value in listOf(null, "32", true, 15, 257, 16.5, Double.NaN, Double.POSITIVE_INFINITY, -Double.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                DisplayResolutionSettings.validateIndividualPpb(value, 256)
            }
        }
        for (value in listOf(16, 16.0, 255, 256.0)) {
            assertEquals((value as Number).toInt(), DisplayResolutionSettings.validateIndividualPpb(value, 256))
        }
        assertEquals(1024, DisplayResolutionSettings.validateIndividualPpb(1024, 1024))
    }

    @Test
    fun individualRastersRoundTripAtDifferentDensitiesWithoutAcceptingOtherSizes() {
        for (type in DeskDisplayType.entries) for (ppb in listOf(16, 32, 255, 256, 1024)) {
            val size = DeskDisplayResolution(type.pixelWidthAt(ppb), type.pixelHeightAt(ppb))
            val original = DeskDisplayPixels.blank(size.width, size.height)
                .withPixel(size.width - 1, size.height - 1, true)
            assertEquals(original, DeskDisplayPixels.decode(size, original.encode()))
            assertEquals(original, DeskDisplayPixels.fromRows(size, original.rows()))
            assertNull(DeskDisplayPixels.decode(DeskDisplayResolution(size.width + 1, size.height), original.encode()))
        }
    }

    @Test
    fun onlyAnActiveAdvancedOwnerEnablesTouch() {
        for (state in ConsoleNetworkState.entries) {
            assertFalse(DisplayTouchAccess.enabled(state, false))
            assertEquals(state == ConsoleNetworkState.ACTIVE, DisplayTouchAccess.enabled(state, true))
        }
    }

    @Test
    fun smallAndLargeHitBoundsMatchTheirPhysicalSurfacesAndRasterEdges() {
        for (type in DeskDisplayType.entries) {
            val pointer = DeskDisplayPointer(0, "left", type.modulePath, 0.5, 0.5,
                type.pixelWidthAt(32), type.pixelHeightAt(32), type.surfaceWidthParts, type.surfaceHeightParts)
            val left = DeskDisplayGeometry.localX(0.0, type.surfaceWidthParts)
            val right = DeskDisplayGeometry.localX(1.0, type.surfaceWidthParts)
            val top = DeskDisplayGeometry.localZ(0.0, type.surfaceHeightParts)
            val bottom = DeskDisplayGeometry.localZ(1.0, type.surfaceHeightParts)
            val first = DeskDisplayGeometry.pointerFromLocal(pointer, left, top)!!.toTouch()
            val last = DeskDisplayGeometry.pointerFromLocal(pointer, right, bottom)!!.toTouch()
            assertEquals(1, first.x)
            assertEquals(1, first.y)
            assertEquals(pointer.width, last.x)
            assertEquals(pointer.height, last.y)
            assertNull(DeskDisplayGeometry.pointerFromLocal(pointer, left - 0.001, top))
            assertNull(DeskDisplayGeometry.pointerFromLocal(pointer, right, bottom - 0.001))
            assertTrue(DisplayScriptDescriptor("/touch.lua", "touch", false, true).supports(type))
        }
    }
}
