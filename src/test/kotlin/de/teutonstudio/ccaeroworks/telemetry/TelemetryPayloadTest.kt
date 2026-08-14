package de.teutonstudio.ccaeroworks.telemetry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelemetryPayloadTest {
    @Test
    fun fillLevelExposesRawRangeFractionAndPercent() {
        val payload = FillLevelTelemetryPayload(
            contentType = "fluid",
            current = 4_000,
            minimum = 0,
            maximum = 16_000
        )

        val lua = payload.toLua()
        assertEquals("fluid", lua["contentType"])
        assertEquals(4_000L, lua["current"])
        assertEquals(0L, lua["minimum"])
        assertEquals(16_000L, lua["maximum"])
        assertEquals(0.25, lua["fraction"])
        assertEquals(25.0, lua["percent"])
    }

    @Test
    fun fillLevelMatchesCreateByClampingOnlyTheLowerBound() {
        val payload = FillLevelTelemetryPayload(
            contentType = "fluid",
            current = 20_000,
            minimum = 0,
            maximum = 16_000
        )

        val lua = payload.toLua()
        assertEquals(1.25, lua["fraction"])
        assertEquals(125.0, lua["percent"])
    }

    @Test
    fun fillLevelWithEmptyRangeDoesNotDivideByZero() {
        val payload = FillLevelTelemetryPayload(
            contentType = "custom",
            current = 5,
            minimum = 5,
            maximum = 5
        )

        val lua = payload.toLua()
        assertEquals(0.0, lua["fraction"])
        assertEquals(0.0, lua["percent"])
    }

    @Test
    fun itemListKeepsFullCountsWhenEntriesAreTruncated() {
        val payload = ItemListTelemetryPayload(
            totalCount = 600,
            entryCount = 3,
            entries = listOf(
                TelemetryItemEntry("minecraft:gold_ingot", "Gold Ingot", 300),
                TelemetryItemEntry("minecraft:copper_ingot", "Copper Ingot", 200)
            ),
            truncated = true
        )

        val lua = payload.toLua()
        assertEquals(600L, lua["totalCount"])
        assertEquals(3, lua["entryCount"])
        assertTrue(lua["truncated"] as Boolean)
        assertEquals(2, (lua["entries"] as List<*>).size)
    }

    @Test
    fun fluidAmountExposesRawAmountAndBucketConvenienceValue() {
        val payload = FluidAmountTelemetryPayload(64_000)
        val lua = payload.toLua()

        assertEquals(64_000L, lua["amount"])
        assertEquals(64.0, lua["buckets"])
    }

    @Test
    fun completeFluidListIsNotMarkedTruncated() {
        val payload = FluidListTelemetryPayload(
            totalAmount = 72_000,
            entryCount = 2,
            entries = listOf(
                TelemetryFluidEntry("minecraft:water", "Water", 64_000),
                TelemetryFluidEntry("minecraft:lava", "Lava", 8_000)
            ),
            truncated = false
        )

        assertFalse(payload.toLua()["truncated"] as Boolean)
    }
}
