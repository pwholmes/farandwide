package com.lastcallsoftware.farandwide.route.client;

import com.lastcallsoftware.farandwide.Constants;

import net.minecraft.gizmos.Gizmo;
import net.minecraft.gizmos.GizmoPrimitives;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.phys.Vec3;

public record WaypointGizmo(
        Vec3 position, int ordinal, boolean cargo, boolean navigationTarget, boolean editTarget) implements Gizmo {

    private static final int WAYPOINT_COLOR = Constants.Client.WAYPOINT_COLOR;
    private static final int CARGO_COLOR = Constants.Client.CARGO_WAYPOINT_COLOR;
    private static final double HALF_WIDTH = Constants.Client.WAYPOINT_GIZMO_HALF_WIDTH;
    private static final double HALF_HEIGHT = Constants.Client.WAYPOINT_GIZMO_HALF_HEIGHT;
    private static final double HEIGHT_OFFSET = Constants.Client.WAYPOINT_GIZMO_HEIGHT_OFFSET;
    private static final int EDGE_COLOR = Constants.Client.WAYPOINT_GIZMO_EDGE_COLOR;
    private static final int TEXT_COLOR = Constants.Client.WAYPOINT_GIZMO_TEXT_COLOR;
    private static final int CARGO_EDGE_COLOR = Constants.Client.CARGO_WAYPOINT_EDGE_COLOR;
    private static final int CARGO_TEXT_COLOR = Constants.Client.CARGO_WAYPOINT_TEXT_COLOR;
    private static final int TARGET_COLOR = Constants.Client.TARGET_WAYPOINT_COLOR;
    private static final int TARGET_EDGE_COLOR = Constants.Client.TARGET_WAYPOINT_EDGE_COLOR;
    private static final int TARGET_TEXT_COLOR = Constants.Client.TARGET_WAYPOINT_TEXT_COLOR;
    private static final int EDIT_TARGET_COLOR = Constants.Client.EDIT_TARGET_WAYPOINT_COLOR;
    private static final int EDIT_TARGET_EDGE_COLOR = Constants.Client.EDIT_TARGET_WAYPOINT_EDGE_COLOR;
    private static final int EDIT_TARGET_TEXT_COLOR = Constants.Client.EDIT_TARGET_WAYPOINT_TEXT_COLOR;
    private static final float EDGE_WIDTH = Constants.Client.WAYPOINT_GIZMO_EDGE_WIDTH;
    private static final float TEXT_SCALE = Constants.Client.WAYPOINT_GIZMO_TEXT_SCALE;

    @Override
    public void emit(GizmoPrimitives primitives, float alphaMultiplier) {
       Vec3 center = position.add(0, HEIGHT_OFFSET, 0);

       Vec3 top = center.add(0, HALF_HEIGHT, 0);
       Vec3 bottom = center.add(0, -HALF_HEIGHT, 0);
       Vec3 left = center.add(-HALF_WIDTH, 0, 0);
       Vec3 right = center.add(HALF_WIDTH, 0, 0);
       Vec3 front = center.add(0, 0, -HALF_WIDTH);
       Vec3 back = center.add(0, 0, HALF_WIDTH);

       Vec3[] pyramid1 = {top, left, front, right, back, left};
       Vec3[] pyramid2 = {bottom, left, front, right, back, left};

        int fillColor = editTarget ? EDIT_TARGET_COLOR
                : navigationTarget ? TARGET_COLOR
                : cargo ? CARGO_COLOR : WAYPOINT_COLOR;
        int edgeColor = editTarget ? EDIT_TARGET_EDGE_COLOR
                : navigationTarget ? TARGET_EDGE_COLOR
                : cargo ? CARGO_EDGE_COLOR : EDGE_COLOR;
        int textColor = editTarget ? EDIT_TARGET_TEXT_COLOR
                : navigationTarget ? TARGET_TEXT_COLOR
                : cargo ? CARGO_TEXT_COLOR : TEXT_COLOR;

        primitives.addTriangleFan(pyramid1, fillColor);
        primitives.addTriangleFan(pyramid2, fillColor);

        primitives.addLine(top, left, edgeColor, EDGE_WIDTH);
        primitives.addLine(top, front, edgeColor, EDGE_WIDTH);
        primitives.addLine(top, right, edgeColor, EDGE_WIDTH);
        primitives.addLine(top, back, edgeColor, EDGE_WIDTH);

        primitives.addLine(bottom, left, edgeColor, EDGE_WIDTH);
        primitives.addLine(bottom, front, edgeColor, EDGE_WIDTH);
        primitives.addLine(bottom, right, edgeColor, EDGE_WIDTH);
        primitives.addLine(bottom, back, edgeColor, EDGE_WIDTH);

        primitives.addLine(left, front, edgeColor, EDGE_WIDTH);
        primitives.addLine(front, right, edgeColor, EDGE_WIDTH);
        primitives.addLine(right, back, edgeColor, EDGE_WIDTH);
        primitives.addLine(back, left, edgeColor, EDGE_WIDTH);

        if (cargo) {
            // Cargo markers have a wider square loading-band silhouette in
            // addition to their orange palette and C-prefixed ordinal.
            double bandRadius = HALF_WIDTH + 0.12;
            Vec3 northWest = center.add(-bandRadius, 0, -bandRadius);
            Vec3 northEast = center.add(bandRadius, 0, -bandRadius);
            Vec3 southEast = center.add(bandRadius, 0, bandRadius);
            Vec3 southWest = center.add(-bandRadius, 0, bandRadius);
            primitives.addLine(northWest, northEast, edgeColor, EDGE_WIDTH);
            primitives.addLine(northEast, southEast, edgeColor, EDGE_WIDTH);
            primitives.addLine(southEast, southWest, edgeColor, EDGE_WIDTH);
            primitives.addLine(southWest, northWest, edgeColor, EDGE_WIDTH);
        }

        primitives.addText(
                center.add(0, HALF_HEIGHT/2, 0),
                markerLabel(ordinal, cargo),
                TextGizmo.Style.forColorAndCentered(textColor).withScale(TEXT_SCALE));
    }

    static String markerLabel(int ordinal, boolean cargo) {
        return cargo ? "C" + ordinal : Integer.toString(ordinal);
    }
}
