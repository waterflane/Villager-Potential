package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CureDiscountPolicyTest {
    @Test
    void firstCureAppliesButLaterCuresDoNotStack() {
        assertTrue(CureDiscountPolicy.shouldApplyCureBonus(0));
        assertFalse(CureDiscountPolicy.shouldApplyCureBonus(100));
    }
}
