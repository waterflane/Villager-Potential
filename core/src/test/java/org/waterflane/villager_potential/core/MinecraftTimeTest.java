package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftTimeTest {
    @Test
    void usesTheServerTickClockAsTheOnlyTimeBase() {
        assertEquals(20L, MinecraftTime.TICKS_PER_SECOND);
        assertEquals(1_200L, MinecraftTime.TICKS_PER_MINUTE);
        assertEquals(24_000L, MinecraftTime.TICKS_PER_DAY);
    }

    @Test
    void daytimeRepeatsWithoutCountingNightTicks() {
        assertTrue(MinecraftTime.isDaytime(0L));
        assertTrue(MinecraftTime.isDaytime(11_999L));
        assertFalse(MinecraftTime.isDaytime(12_000L));
        assertFalse(MinecraftTime.isDaytime(23_999L));
        assertTrue(MinecraftTime.isDaytime(24_000L));
    }
}
