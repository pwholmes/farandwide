package com.lastcallsoftware.farandwide.vehicle.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClientBoatActuatorTest {
    @Test
    void mountedBoatTurnInputIsAppliedEveryOtherTick() {
        assertTrue(ClientBoatActuator.shouldApplyTurnInput(0));
        assertFalse(ClientBoatActuator.shouldApplyTurnInput(1));
        assertTrue(ClientBoatActuator.shouldApplyTurnInput(2));
        assertFalse(ClientBoatActuator.shouldApplyTurnInput(3));
    }
}
