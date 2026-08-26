package com.lastcallsoftware.farandwide.command.client;

import com.lastcallsoftware.farandwide.FarAndWide;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.resources.Identifier;

/**
 * A short, self-contained introduction to the route workflow.
 *
 * <p>The command menu opens this screen with H. Help is deliberately a linear
 * sequence so new players see the route lifecycle in order: create, select,
 * add waypoints, assign, then activate.
 */
public final class FarAndWideHelpScreen extends Screen {
    private static final int IMAGE_TEXTURE_WIDTH = 1400;
    private static final int IMAGE_TEXTURE_HEIGHT = 1200;
    private static final int CONTENT_TOP = 44;
    private static final int BUTTON_WIDTH = 90;
    private static final int BUTTON_GAP = 6;

    private static final HelpPage[] PAGES = {
            new HelpPage("screen.farandwide.help.intro.title", "screen.farandwide.help.intro.body", null),
            new HelpPage("screen.farandwide.help.create.title", "screen.farandwide.help.create.body",
                    texture("route_create")),
            new HelpPage("screen.farandwide.help.select.title", "screen.farandwide.help.select.body",
                    texture("route_select")),
            new HelpPage("screen.farandwide.help.waypoints.title", "screen.farandwide.help.waypoints.body",
                    texture("route_waypoints")),
            new HelpPage("screen.farandwide.help.assign.title", "screen.farandwide.help.assign.body",
                    texture("route_assign")),
            new HelpPage("screen.farandwide.help.activate.title", "screen.farandwide.help.activate.body",
                    texture("route_activate"))
    };

    private int pageIndex;
    private Button previousButton;
    private Button nextButton;

    public FarAndWideHelpScreen() {
        super(Component.translatable("screen.farandwide.help.title"));
    }

    @Override
    protected void init() {
        int buttonsWidth = BUTTON_WIDTH * 2 + BUTTON_GAP;
        int left = (width - buttonsWidth) / 2;
        int buttonY = height - 30;

        previousButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.farandwide.help.previous"),
                button -> showPage(pageIndex - 1))
                .bounds(left, buttonY, BUTTON_WIDTH, 20)
                .build());
        nextButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.farandwide.help.next"),
                button -> showPage(pageIndex + 1))
                .bounds(left + BUTTON_WIDTH + BUTTON_GAP, buttonY, BUTTON_WIDTH, 20)
                .build());
        updateButtonState();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(new FarAndWideCommandScreen());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        HelpPage page = PAGES[pageIndex];
        Component pageTitle = Component.translatable(page.titleKey());
        Component pageBody = Component.translatable(page.bodyKey());
        int bodyWidth = page.image() == null
                ? Math.min(360, width - 40) - 24
                : Math.min(380, width - 40);
        List<FormattedCharSequence> bodyLines = font.split(pageBody, bodyWidth);
        int titleY = 16;
        int bodyY = 32;

        graphics.centeredText(font, title, width / 2, titleY, 0xFFFFFFFF);
        graphics.centeredText(font, pageTitle, width / 2, bodyY, 0xFFFFD27F);

        if (page.image() == null) {
            drawIntroPanel(graphics, bodyLines);
        } else {
            drawImagePage(graphics, page.image(), bodyLines);
        }

        graphics.centeredText(
                font,
                Component.translatable("screen.farandwide.help.page", pageIndex + 1, PAGES.length),
                width / 2,
                height - 48,
                0xFFAAAAAA);
    }

    private void drawIntroPanel(GuiGraphicsExtractor graphics, List<FormattedCharSequence> bodyLines) {
        int panelWidth = Math.min(360, width - 40);
        int panelHeight = 28 + bodyLines.size() * font.lineHeight;
        int panelX = (width - panelWidth) / 2;
        int panelY = CONTENT_TOP + 28;
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xCC000000);
        drawCenteredLines(graphics, bodyLines, panelY + 14);
    }

    private void drawImagePage(GuiGraphicsExtractor graphics, Identifier image, List<FormattedCharSequence> bodyLines) {
        int availableHeight = Math.max(80, height - CONTENT_TOP - 104 - bodyLines.size() * font.lineHeight);
        int imageHeight = Math.min(270, availableHeight);
        int imageWidth = imageHeight * IMAGE_TEXTURE_WIDTH / IMAGE_TEXTURE_HEIGHT;
        int imageX = (width - imageWidth) / 2;
        int imageY = CONTENT_TOP;

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                image,
                imageX,
                imageY,
                0,
                0,
                imageWidth,
                imageHeight,
                IMAGE_TEXTURE_WIDTH,
                IMAGE_TEXTURE_HEIGHT,
                IMAGE_TEXTURE_WIDTH,
                IMAGE_TEXTURE_HEIGHT);
        drawCenteredLines(graphics, bodyLines, imageY + imageHeight + 8);
    }

    private void drawCenteredLines(GuiGraphicsExtractor graphics, List<FormattedCharSequence> lines, int y) {
        for (FormattedCharSequence line : lines) {
            graphics.text(font, line, (width - font.width(line)) / 2, y, 0xFFFFFFFF);
            y += font.lineHeight;
        }
    }

    private void showPage(int requestedPageIndex) {
        pageIndex = Math.clamp(requestedPageIndex, 0, PAGES.length - 1);
        updateButtonState();
    }

    private void updateButtonState() {
        previousButton.active = pageIndex > 0;
        nextButton.active = pageIndex < PAGES.length - 1;
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(FarAndWide.MODID, "textures/gui/help/" + name + ".png");
    }

    private record HelpPage(String titleKey, String bodyKey, Identifier image) {
    }
}
