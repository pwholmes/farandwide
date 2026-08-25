package com.lastcallsoftware.farandwide.route;

import net.minecraft.gizmos.Gizmo;
import net.minecraft.gizmos.GizmoPrimitives;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.phys.Vec3;

public record WaypointGizmo(Vec3 position, int ordinal, boolean target) implements Gizmo {

    private static final int WAYPOINT_COLOR = 0x6600FF00; // Green color for the waypoint gizmo
    private static final double HALF_WIDTH = 0.35;
    private static final double HALF_HEIGHT = 0.5;
    private static final double HEIGHT_OFFSET = 1.5;
    private static final int EDGE_COLOR = 0xCC000000;
    private static final int TEXT_COLOR = 0x99000000;
    private static final int TARGET_COLOR = 0xAAFFFF00;
    private static final int TARGET_EDGE_COLOR = 0xFFFFFF00;
    private static final int TARGET_TEXT_COLOR = 0xB3FFFF00;
    private static final float EDGE_WIDTH = 1.0f;
    private static final float TEXT_SCALE = 0.5f;

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

        int fillColor = target ? TARGET_COLOR : WAYPOINT_COLOR;
        int edgeColor = target ? TARGET_EDGE_COLOR : EDGE_COLOR;
        int textColor = target ? TARGET_TEXT_COLOR : TEXT_COLOR;

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

        primitives.addText(
                center.add(0, HALF_HEIGHT/2, 0),
                Integer.toString(ordinal),
                TextGizmo.Style.forColorAndCentered(textColor).withScale(TEXT_SCALE));
    }
}
