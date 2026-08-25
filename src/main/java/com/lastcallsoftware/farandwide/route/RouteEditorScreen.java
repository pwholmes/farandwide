package com.lastcallsoftware.farandwide.route;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.network.chat.Component;

/** A small name editor used for both creating and renaming routes. */
public class RouteEditorScreen extends Screen {
    private static final int DEFAULT_NAME_COLOR = 0xFF777777;

    private final Route route;
    private EditBox nameField;
    private Button saveButton;
    private String validationError;
    private boolean focusNameFieldOnNextTick;
    private boolean defaultNameActive;

    public RouteEditorScreen(Route route) {
        super(Component.translatable(
                route == null
                        ? "screen.farandwide.route_editor.create_title"
                        : "screen.farandwide.route_editor.edit_title"));
        this.route = route;
    }

    @Override
    protected void init() {
        int fieldWidth = 220;
        int fieldX = (width - fieldWidth) / 2;
        int fieldY = height / 2 - 12;

        nameField = addRenderableWidget(new EditBox(
                font,
                fieldX,
                fieldY,
                fieldWidth,
                20,
                Component.translatable("screen.farandwide.route_editor.name")) {
            @Override
            public boolean charTyped(CharacterEvent event) {
                if (defaultNameActive && canConsumeInput() && event.isAllowedChatCharacter()) {
                    defaultNameActive = false;
                    setTextColor(EditBox.DEFAULT_TEXT_COLOR);
                    setValue("");
                }
                return super.charTyped(event);
            }
        });
        nameField.setMaxLength(64);
        if (route == null) {
            defaultNameActive = true;
            nameField.setTextColor(DEFAULT_NAME_COLOR);
            nameField.setValue(nextDefaultRouteName());
        } else {
            nameField.setValue(route.getName());
        }
        nameField.setResponder(value -> {
            validationError = null;
            updateSaveButtonState();
        });

        int buttonY = fieldY + 32;
        saveButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.farandwide.route_editor.save"),
                button -> saveRoute())
                .bounds(fieldX, buttonY, 106, 20)
                .build());
        addRenderableWidget(Button.builder(
                Component.translatable("screen.farandwide.route_editor.cancel"),
                button -> returnToRouteList())
                .bounds(fieldX + 114, buttonY, 106, 20)
                .build());

        updateSaveButtonState();
        // A command-menu key can still have a queued charTyped event after this
        // screen opens. Focus on the next tick so that character cannot be
        // inserted into the new name field.
        focusNameFieldOnNextTick = true;
    }

    private static String nextDefaultRouteName() {
        int ordinal = 1;
        while (RouteManager.getRoute("Route " + ordinal) != null) {
            ordinal++;
        }
        return "Route " + ordinal;
    }

    @Override
    public void tick() {
        if (focusNameFieldOnNextTick) {
            setInitialFocus(nameField);
            focusNameFieldOnNextTick = false;
        }
    }

    private void updateSaveButtonState() {
        if (saveButton != null) {
            saveButton.active = !nameField.getValue().trim().isEmpty();
        }
    }

    private void saveRoute() {
        String name = nameField.getValue().trim();
        if (name.isEmpty()) {
            validationError = Component.translatable("screen.farandwide.route_editor.name_required").getString();
            return;
        }

        if (route == null) {
            Route newRoute = new Route();
            newRoute.setName(name);
            RouteManager.addRoute(newRoute);
            RouteManager.setSelectedRoute(newRoute);
        } else {
            route.setName(name);
        }
        returnToRouteList();
    }

    private void returnToRouteList() {
        minecraft.setScreenAndShow(new RouteManagementScreen());
    }

    @Override
    public void onClose() {
        returnToRouteList();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(font, title, (width - font.width(title)) / 2, height / 2 - 40, 0xFFFFFFFF);
        graphics.text(
                font,
                Component.translatable("screen.farandwide.route_editor.name"),
                (width - 220) / 2,
                height / 2 - 24,
                0xFFFFFFFF);
        if (validationError != null) {
            graphics.text(font, validationError, (width - font.width(validationError)) / 2, height / 2 + 58, 0xFFFF5555);
        }
    }
}
