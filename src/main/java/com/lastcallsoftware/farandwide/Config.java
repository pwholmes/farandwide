package com.lastcallsoftware.farandwide;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.TranslatableEnum;

/** User-editable client preferences and server-owned operational limits. */
public final class Config {
    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.Builder SERVER_BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue PAUSE_MOD_SCREENS = CLIENT_BUILDER
            .comment("Whether Far And Wide screens pause an integrated single-player server.",
                    "Disabled by default so routes and vehicles continue running while a screen is open.")
            .translation("farandwide.configuration.pauseModScreens")
            .define("pauseModScreens", false);

    public static final ModConfigSpec.IntValue VEHICLE_CHUNK_RADIUS = SERVER_BUILDER
            .comment("Chunk-loading area around each active route vehicle.",
                    "0 disables chunk loading; 1 loads only the vehicle's chunk; 2 loads a 3x3 window.")
            .translation("farandwide.configuration.vehicleChunkRadius")
            .defineInRange("vehicleChunkRadius", 2, 0, 5);

    public static final ModConfigSpec.IntValue MAX_CHUNK_LOADED_VEHICLES = SERVER_BUILDER
            .comment("Maximum number of route vehicles allowed to force-load chunks at once.",
                    "Vehicles over this limit are paused instead of silently unloading.")
            .translation("farandwide.configuration.maxChunkLoadedVehicles")
            .defineInRange("maxChunkLoadedVehicles", 64, 1, 1_024);

    public static final ModConfigSpec.BooleanValue AUTO_SELECT_VEHICLE_ROUTE_ON_MOUNT = SERVER_BUILDER
            .comment("Whether mounting a Vehicle with a Route automatically selects that Route for the player.",
                    "Disabled by default so mounting does not replace the player's current Route selection.")
            .translation("farandwide.configuration.autoSelectVehicleRouteOnMount")
            .define("autoSelectVehicleRouteOnMount", false);

    public static final ModConfigSpec.EnumValue<EquineRouteSpeed> EQUINE_ROUTE_SPEED = SERVER_BUILDER
            .comment("Movement speed for automated horses, donkeys, and mules.",
                    "Choose from 1.0x, 1.5x, or 2.0x.",
                    "1.0x is the original autonomous speed; 2.0x matches their automated mounted speed.")
            .translation("farandwide.configuration.equineRouteSpeed")
            .defineEnum("equineRouteSpeed", EquineRouteSpeed.ONE_AND_A_HALF_X);

    /** Returns the configured speed relative to the original autonomous equine speed. */
    public static double equineRouteSpeedMultiplier() {
        return EQUINE_ROUTE_SPEED.get().multiplier();
    }

    /** The fixed automated-equine speeds presented by the configuration screen. */
    public enum EquineRouteSpeed implements TranslatableEnum {
        ONE_X(1.0, "1.0x"),
        ONE_AND_A_HALF_X(1.5, "1.5x"),
        TWO_X(2.0, "2.0x");

        private final double multiplier;
        private final String label;

        EquineRouteSpeed(double multiplier, String label) {
            this.multiplier = multiplier;
            this.label = label;
        }

        public double multiplier() {
            return multiplier;
        }

        @Override
        public Component getTranslatedName() {
            return Component.literal(label);
        }
    }

    static final ModConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();
    static final ModConfigSpec SERVER_SPEC = SERVER_BUILDER.build();

    private Config() {
    }
}
