package com.lastcallsoftware.farandwide.command.client;

import java.util.List;
import java.util.function.Consumer;

import org.lwjgl.glfw.GLFW;

import com.lastcallsoftware.farandwide.route.Route;
import com.lastcallsoftware.farandwide.route.client.RouteEditorScreen;
import com.lastcallsoftware.farandwide.route.client.RouteManagementScreen;
import com.lastcallsoftware.farandwide.route.client.RouteManager;
import com.lastcallsoftware.farandwide.route.client.RouteNavigationHud;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class FarAndWideCommands {
    public static final FarAndWideCommand OPEN_MENU = command(
            null,
            "command.farandwide.open_menu",
            () -> Minecraft.getInstance().setScreenAndShow(new FarAndWideCommandScreen()));

    public static final FarAndWideCommand MANAGE_ROUTES = command(
            GLFW.GLFW_KEY_M,
            "command.farandwide.manage_routes",
            () -> Minecraft.getInstance().setScreenAndShow(new RouteManagementScreen()));

    public static final FarAndWideCommand CREATE_ROUTE = command(
            GLFW.GLFW_KEY_C,
            "command.farandwide.create_route",
            () -> Minecraft.getInstance().setScreenAndShow(new RouteEditorScreen(null)));

    public static final FarAndWideCommand ASSIGN_ROUTE = command(
            GLFW.GLFW_KEY_G,
            "command.farandwide.assign_route",
            () -> withCurrentRoute(RouteManager::assignRoute));

    public static final FarAndWideCommand TOGGLE_ROUTE = command(
            GLFW.GLFW_KEY_A,
            "command.farandwide.toggle_route",
            RouteManager::toggleCurrentAssignment);

    public static final FarAndWideCommand ADD_WAYPOINT = command(
            GLFW.GLFW_KEY_W,
            "command.farandwide.add_waypoint",
            () -> withCurrentRoute(RouteManager::addCurrentPosition));

    public static final FarAndWideCommand REMOVE_WAYPOINT = command(
            GLFW.GLFW_KEY_R,
            "command.farandwide.remove_waypoint",
            () -> withCurrentRoute(RouteManager::removeCurrentPosition));

    public static final FarAndWideCommand TOGGLE_WAYPOINT = command(
            GLFW.GLFW_KEY_T,
            "command.farandwide.toggle_waypoint",
            () -> withCurrentRoute(RouteManager::toggleCurrentPosition));

    public static final FarAndWideCommand TOGGLE_HUD = command(
            GLFW.GLFW_KEY_V,
            "command.farandwide.toggle_hud",
            RouteNavigationHud::toggleVisibility);

    public static final FarAndWideCommand HELP_SCREEN = command(
            GLFW.GLFW_KEY_H,
            "command.farandwide.help_screen",
            () -> Minecraft.getInstance().setScreenAndShow(new FarAndWideHelpScreen()));

    public static List<FarAndWideCommand> all() {
        return List.of(
                MANAGE_ROUTES,
                CREATE_ROUTE,
                ASSIGN_ROUTE,
                TOGGLE_ROUTE,
                ADD_WAYPOINT,
                REMOVE_WAYPOINT,
                TOGGLE_WAYPOINT,
                TOGGLE_HUD,
                HELP_SCREEN
        );
    }

    private static FarAndWideCommand command(Integer key, String nameKey, Runnable action) {
        InputConstants.Key menuKey = key == null
                ? null
                : InputConstants.Type.KEYSYM.getOrCreate(key);

        return new CommandDefinition(
                menuKey,
                Component.translatable(nameKey),
                Component.translatable(nameKey + ".description"),
                action);
    }

    private static void withCurrentRoute(Consumer<Route> action) {
        Route route = RouteManager.getCurrentRoute();
        if (route != null) {
            action.accept(route);
        } else if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendOverlayMessage(
                    Component.translatable("message.farandwide.no_route_selected"));
        }
    }

    private record CommandDefinition(
            InputConstants.Key menuKey,
            Component name,
            Component description,
            Runnable action
    ) implements FarAndWideCommand {
        @Override
        public InputConstants.Key getMenuKey() {
            return menuKey;
        }

        @Override
        public Component getName() {
            return name;
        }

        @Override
        public Component getDescription() {
            return description;
        }

        @Override
        public void execute() {
            action.run();
        }
    }
}
