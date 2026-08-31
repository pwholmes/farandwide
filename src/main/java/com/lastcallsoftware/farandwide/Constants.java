package com.lastcallsoftware.farandwide;

/** Shared fixed values governing route, cargo, navigation, persistence, and network behaviour. */
public final class Constants {
    public static final String MOD_ID = "farandwide";
    /** A vehicle arrives when its position is within this distance of a waypoint. */

    private Constants() {
    }

    public static final class Waypoints {
        public static final double DEFAULT_ARRIVAL_RADIUS = 3.5;
        public static final double MIN_ARRIVAL_RADIUS = 1.0;
        public static final double MAX_ARRIVAL_RADIUS = 16.0;
        public static final double ARRIVAL_RADIUS_STEP = 0.5;
        public static final double PLACEMENT_OFFSET_DISTANCE = 1.0;
        public static final double EDIT_RADIUS = 16.0;
        public static final double TARGET_RADIUS = 0.7;
        public static final double MARKER_HEIGHT_OFFSET = 1.5;

        private Waypoints() {
        }
    }

    public static final class Cargo {
        public static final int MAX_SCANNED_SLOTS = 256;
        public static final int MAX_ITEMS_PER_OPERATION = 4_096;
        public static final int MAX_ITEMS_PER_STACK = 64;
        public static final long TRANSFER_INTERVAL_TICKS = 10;
        /** Five seconds at Minecraft's normal 20 server ticks per second. */
        public static final long WAYPOINT_DWELL_TICKS = 100;
        public static final int EQUINE_EQUIPMENT_SLOT_COUNT = 2;

        private Cargo() {
        }
    }

    public static final class Vehicles {
        public static final boolean BOATS_MOVE_WHILE_TURNING = false;
        /** Allows vanilla boat turning momentum to settle without counter-steering. */
        public static final float BOAT_TURN_DEAD_ZONE_DEGREES = 8.0F;
        public static final float SERVER_BOAT_MAX_TURN_PER_TICK = 2.0F;
        public static final double SERVER_BOAT_ACCELERATION = 0.04;
        public static final boolean EQUINES_MOVE_WHILE_TURNING = false;
        public static final boolean PLAYER_MOVES_WHILE_TURNING = false;
        public static final float EQUINE_MAX_TURN_PER_TICK = 4.0F;
        public static final float EQUINE_HEADING_DEAD_ZONE_DEGREES = 1.0F;
        public static final float EQUINE_FACING_TARGET_TOLERANCE = 5.0F;
        public static final double EQUINE_MOVEMENT_SPEED_MODIFIER = 1.0;
        public static final double EQUINE_TURNING_SPEED_RATIO = 0.2;

        private Vehicles() {
        }
    }

    public static final class Network {
        public static final int MAX_ROUTE_NAME_LENGTH = 64;
        public static final int MAX_ROUTES = 1_024;
        public static final int MAX_WAYPOINTS_PER_ROUTE = 16_384;
        public static final int MAX_FILTER_ITEMS = 1_024;
        public static final int MAX_IDENTIFIER_LENGTH = 256;
        public static final int MAX_VEHICLE_ASSIGNMENTS = 1_025;
        public static final int MAX_VEHICLE_NAME_LENGTH = 64;

        private Network() {
        }
    }

    public static final class Persistence {
        public static final int CURRENT_DATA_VERSION = 6;

        private Persistence() {
        }
    }

    public static final class Client {
        public static final int TRAVERSAL_ICON_TEXTURE_SIZE = 1_254;
        public static final int CARGO_WAYPOINT_CONTROL_WIDTH = 240;
        public static final int CARGO_WAYPOINT_SAVE_BUTTON_Y = 144;
        public static final int ROUTE_LIST_TOP = 40;
        public static final int ROUTE_PANEL_WIDTH = 300;
        public static final int ROUTE_BUTTON_WIDTH = 60;
        public static final int ROUTE_BUTTON_GAP = 4;
        public static final int ROUTE_TRAVERSAL_ICON_SIZE = 16;
        public static final int ROUTE_EDITOR_DEFAULT_NAME_COLOR = 0xFF777777;
        public static final boolean DEFAULT_HUD_VISIBLE = true;
        public static final int HUD_MARGIN = 8;
        public static final int NAVIGATION_NEEDLE_TEXTURE_SIZE = 1_254;
        public static final int NAVIGATION_INDICATOR_DISPLAY_SIZE = 16;
        public static final int NAVIGATION_NEEDLE_DISPLAY_SIZE = 16;
        public static final int HUD_TRAVERSAL_ICON_SIZE = 12;
        public static final int HUD_TITLE_GAP = 3;
        public static final int WAYPOINT_COLOR = 0x6600FF00;
        public static final int CARGO_WAYPOINT_COLOR = 0x88FF6600;
        public static final double WAYPOINT_GIZMO_HALF_WIDTH = 0.35;
        public static final double WAYPOINT_GIZMO_HALF_HEIGHT = 0.5;
        public static final double WAYPOINT_GIZMO_HEIGHT_OFFSET = 1.5;
        public static final int WAYPOINT_GIZMO_EDGE_COLOR = 0xCC000000;
        public static final int WAYPOINT_GIZMO_TEXT_COLOR = 0x99000000;
        public static final int CARGO_WAYPOINT_EDGE_COLOR = 0xFFFFAA00;
        public static final int CARGO_WAYPOINT_TEXT_COLOR = 0xFFFFCC66;
        public static final int TARGET_WAYPOINT_COLOR = 0xAAFFFF00;
        public static final int TARGET_WAYPOINT_EDGE_COLOR = 0xFFFFFF00;
        public static final int TARGET_WAYPOINT_TEXT_COLOR = 0xB3FFFF00;
        public static final int EDIT_TARGET_WAYPOINT_COLOR = 0xAA00FFFF;
        public static final int EDIT_TARGET_WAYPOINT_EDGE_COLOR = 0xFF00FFFF;
        public static final int EDIT_TARGET_WAYPOINT_TEXT_COLOR = 0xCC00FFFF;
        public static final float WAYPOINT_GIZMO_EDGE_WIDTH = 1.0F;
        public static final float WAYPOINT_GIZMO_TEXT_SCALE = 0.5F;

        private Client() {
        }
    }
}
