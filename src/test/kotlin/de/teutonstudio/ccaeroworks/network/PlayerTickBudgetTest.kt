package de.teutonstudio.ccaeroworks.network

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class PlayerTickBudgetTest {
    @Test
    fun `packet budget resets on the next tick`() {
        val player = UUID.randomUUID()
        val budget = PlayerTickBudget(maxPacketsPerTick = 2, maxUnitsPerTick = 10)

        assertTrue(budget.tryConsume(player, tick = 20, units = 1))
        assertTrue(budget.tryConsume(player, tick = 20, units = 1))
        assertFalse(budget.tryConsume(player, tick = 20, units = 1))
        assertTrue(budget.tryConsume(player, tick = 21, units = 1))
    }

    @Test
    fun `unit budget rejects oversized bursts`() {
        val player = UUID.randomUUID()
        val budget = PlayerTickBudget(maxPacketsPerTick = 8, maxUnitsPerTick = 16)

        assertTrue(budget.tryConsume(player, tick = 5, units = 10))
        assertFalse(budget.tryConsume(player, tick = 5, units = 7))
        assertTrue(budget.tryConsume(player, tick = 6, units = 16))
        assertFalse(budget.tryConsume(player, tick = 7, units = 17))
    }

    @Test
    fun `players have independent budgets and can be cleared`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val budget = PlayerTickBudget(maxPacketsPerTick = 1, maxUnitsPerTick = 4)

        assertTrue(budget.tryConsume(first, tick = 1, units = 1))
        assertFalse(budget.tryConsume(first, tick = 1, units = 1))
        assertTrue(budget.tryConsume(second, tick = 1, units = 1))

        budget.clearPlayer(first)
        assertTrue(budget.tryConsume(first, tick = 1, units = 1))
    }
}
