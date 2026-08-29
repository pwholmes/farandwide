package com.lastcallsoftware.farandwide.vehicle.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.world.level.ChunkPos;

class VehicleChunkLoadingManagerTest {
    @Test
    void windowContainsCenterAndAllEightNeighbors() {
        Set<ChunkPos> window = VehicleChunkLoadingManager.windowAround(new ChunkPos(12, -4));

        assertEquals(9, window.size());
        for (int x = 11; x <= 13; x++) {
            for (int z = -5; z <= -3; z++) {
                assertTrue(window.contains(new ChunkPos(x, z)));
            }
        }
    }

    @Test
    void configuredRadiusControlsWindowSize() {
        assertEquals(1, VehicleChunkLoadingManager.windowAround(new ChunkPos(0, 0), 0).size());
        assertEquals(25, VehicleChunkLoadingManager.windowAround(new ChunkPos(0, 0), 2).size());
    }

    @Test
    void capacityAllowsExistingOwnersButRejectsNewOwnersAtLimit() {
        assertTrue(VehicleChunkLoadingManager.canTrack(true, 10, 10));
        assertTrue(VehicleChunkLoadingManager.canTrack(false, 9, 10));
        org.junit.jupiter.api.Assertions.assertFalse(VehicleChunkLoadingManager.canTrack(false, 10, 10));
    }
}
