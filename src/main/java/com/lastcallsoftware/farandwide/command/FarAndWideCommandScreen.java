package com.lastcallsoftware.farandwide.command;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public class FarAndWideCommandScreen extends Screen {

    public FarAndWideCommandScreen() {
        super(Component.translatable("screen.farandwide.command_menu_title"));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }

        for (FarAndWideCommand command : FarAndWideCommands.all()) {
            if (key == command.getMenuKey().getValue()) {
                command.execute();
                // If the command opened a new screen, it has already replaced this
                // one — calling onClose() would close the new screen instead.
                if (minecraft.gui.screen() == this) {
                    onClose();
                }
                return true;
            }
        }

        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick) {

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int menuWidth = 150;
        int menuHeight = 38 + FarAndWideCommands.all().size() * (font.lineHeight + 4);

        int left = (width - menuWidth) / 2;
        int top = (height - menuHeight) / 2;
        int helpWidth = 10;
        int helpX = left + menuWidth - helpWidth - 6;

        graphics.fill(
                left,
                top,
                left + menuWidth,
                top + menuHeight,
                0xCC000000
        );

        int y = top + 4;

        graphics.text(
                font,
                title,
                left + 15,
                y,
                0xFFFFFFFF
        );

        y += 4;

        for (FarAndWideCommand command : FarAndWideCommands.all()) {
            InputConstants.Key key = command.getMenuKey();
            if (key == null) {
                continue; // Skip commands without a key binding
            }

            y+= font.lineHeight + 4;

            graphics.text(
                    font,
                    key.getDisplayName(),
                    left + 15,
                    y,
                    0xFFFFFFFF
            );

            graphics.text(
                    font,
                    command.getName(),
                    left + 40,
                    y,
                    0xFFFFFFFF
            );

            // Minecraft's built-in font reliably includes '?', so use a small
            // square help affordance rather than a Unicode circled-question glyph.
            graphics.fill(helpX, y - 1, helpX + helpWidth, y + font.lineHeight + 1, 0xFFFFFFFF);
            graphics.fill(helpX + 1, y, helpX + helpWidth - 1, y + font.lineHeight, 0xFF555555);
            graphics.text(font, Component.literal("?"), helpX + 3, y, 0xFFFFFFFF);

            if (mouseX >= helpX && mouseX < helpX + helpWidth
                    && mouseY >= y - 1 && mouseY < y + font.lineHeight + 1) {
                graphics.setTooltipForNextFrame(
                        font,
                        command.getDescription(),
                        mouseX,
                        mouseY
                );
            }
        }

        graphics.text(
                font,
                Component.translatable("screen.farandwide.close_command_menu"),
                left + 15,
                top + menuHeight - font.lineHeight - 4,
                0xFFAAAAAA
        );
    }
}
