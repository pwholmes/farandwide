package com.lastcallsoftware.farandwide;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-owned operational limits for autonomous vehicle chunk loading. */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue VEHICLE_CHUNK_RADIUS = BUILDER
            .comment("Chunk radius kept loaded around each active route vehicle.",
                    "0 loads only the vehicle's chunk; 1 loads a 3x3 window.")
            .defineInRange("vehicleChunkRadius", 1, 0, 4);

    public static final ModConfigSpec.IntValue MAX_CHUNK_LOADED_VEHICLES = BUILDER
            .comment("Maximum number of route vehicles allowed to force-load chunks at once.",
                    "Vehicles over this limit are paused instead of silently unloading.")
            .defineInRange("maxChunkLoadedVehicles", 64, 1, 1_024);

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
