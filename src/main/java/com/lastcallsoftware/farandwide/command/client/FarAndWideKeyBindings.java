package com.lastcallsoftware.farandwide.command.client;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.lastcallsoftware.farandwide.FarAndWide;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.NeoForge;

public final class FarAndWideKeyBindings {
    public static final KeyMapping.Category FAR_AND_WIDE_CATEGORY = new KeyMapping.Category(
            Identifier.fromNamespaceAndPath(FarAndWide.MODID, "keybindings"));

    private static final List<BindingDefinition> BINDINGS = new ArrayList<>();

    static {
        // Add each binding here. The binding(...) call registers it in BINDINGS, so it
        // is automatically added to the Controls screen and processed each tick.
        // For an unassigned default, use InputConstants.UNKNOWN as the third argument.
        binding("key.farandwide.open_commands",
                KeyModifier.CONTROL,
                key(GLFW.GLFW_KEY_F),
                FarAndWideCommands.OPEN_MENU::execute);

        binding("key.farandwide.manage_routes",
                KeyModifier.NONE,
                InputConstants.UNKNOWN,
                FarAndWideCommands.MANAGE_ROUTES::execute);

        binding("key.farandwide.create_route",
                KeyModifier.NONE,
                InputConstants.UNKNOWN,
                FarAndWideCommands.CREATE_ROUTE::execute);

        binding("key.farandwide.deselect_route",
                KeyModifier.NONE,
                InputConstants.UNKNOWN,
                FarAndWideCommands.DESELECT_ROUTE::execute);

        binding("key.farandwide.assign_route",
                KeyModifier.NONE,
                InputConstants.UNKNOWN,
                FarAndWideCommands.ASSIGN_ROUTE::execute);

        binding("key.farandwide.add_or_remove_waypoint",
                KeyModifier.NONE,
                key(GLFW.GLFW_KEY_K),
                FarAndWideCommands.ADD_OR_REMOVE_WAYPOINT::execute);

        binding("key.farandwide.toggle_route",
                KeyModifier.CONTROL,
                key(GLFW.GLFW_KEY_A),
                FarAndWideCommands.TOGGLE_ROUTE::execute);

        binding("key.farandwide.toggle_hud",
                KeyModifier.NONE,
                InputConstants.UNKNOWN,
                FarAndWideCommands.TOGGLE_HUD::execute);

        binding("key.farandwide.help_screen",
                KeyModifier.NONE,
                InputConstants.UNKNOWN,
                FarAndWideCommands.HELP_SCREEN::execute);
    }

    private FarAndWideKeyBindings() {
    }

    public static void register(IEventBus eventBus) {
        eventBus.addListener(FarAndWideKeyBindings::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(FarAndWideKeyBindings::onClientTick);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(FAR_AND_WIDE_CATEGORY);
        BINDINGS.forEach(binding -> event.register(binding.mapping()));
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        BINDINGS.forEach(BindingDefinition::processClicks);
    }

    private static BindingDefinition binding(
            String nameKey,
            KeyModifier modifier,
            InputConstants.Key defaultKey,
            Runnable action) {
        BindingDefinition definition = new BindingDefinition(
                new KeyMapping(
                        nameKey,
                        KeyConflictContext.IN_GAME,
                        modifier,
                        defaultKey,
                        FAR_AND_WIDE_CATEGORY),
                action);
        BINDINGS.add(definition);
        return definition;
    }

    private static InputConstants.Key key(int glfwKey) {
        return InputConstants.Type.KEYSYM.getOrCreate(glfwKey);
    }

    private record BindingDefinition(KeyMapping mapping, Runnable action) {
        void processClicks() {
            while (mapping.consumeClick()) {
                action.run();
            }
        }
    }
}
