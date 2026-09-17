package com.lastcallsoftware.farandwide.command.client;

import com.lastcallsoftware.farandwide.FarAndWide;
import com.lastcallsoftware.farandwide.client.FarAndWideScreen;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.resources.Identifier;

/** A linked, self-contained guide to routes, cargo, terminology, and commands. */
public final class FarAndWideHelpScreen extends FarAndWideScreen {
    private static final int CONTENT_TOP = 44;
    private static final int CONTENT_BOTTOM_MARGIN = 60;
    private static final int PANEL_MAX_WIDTH = 400;
    private static final int PANEL_PADDING = 12;
    private static final int INDEX_LINK_GAP = 14;
    private static final int BUTTON_WIDTH = 90;
    private static final int BUTTON_GAP = 6;
    private static final int INDEX_PAGE = 0;
    private static final int ROUTES_PAGE = 1;
    private static final int CARGO_PAGE = 7;
    private static final int ORDERS_PAGE = 11;
    private static final int TIPS_AND_TRICKS_PAGE = 12;

    private static final HelpPage[] PAGES = {
            new HelpPage("screen.farandwide.help.index.title", "screen.farandwide.help.index.body", null),
            new HelpPage("screen.farandwide.help.routes.title", "screen.farandwide.help.routes.body",
                    texture("route_intro", 1254, 1254)),
            new HelpPage("screen.farandwide.help.create.title", "screen.farandwide.help.create.body",
                    texture("route_create", 1322, 1190)),
            new HelpPage("screen.farandwide.help.select.title", "screen.farandwide.help.select.body",
                    texture("route_select", 1402, 1122)),
            new HelpPage("screen.farandwide.help.waypoints.title", "screen.farandwide.help.waypoints.body",
                    texture("route_waypoints", 1402, 1122)),
            new HelpPage("screen.farandwide.help.assign.title", "screen.farandwide.help.assign.body",
                    texture("route_assign", 1402, 1122)),
            new HelpPage("screen.farandwide.help.activate.title", "screen.farandwide.help.activate.body",
                    texture("route_activate", 1402, 1122)),
            new HelpPage("screen.farandwide.help.cargo.title", "screen.farandwide.help.cargo.body",
                    texture("cargo_intro", 1254, 1254)),
            new HelpPage("screen.farandwide.help.cargo.create.title", "screen.farandwide.help.cargo.create.body",
                    texture("cargo_create", 1354, 1161)),
            new HelpPage("screen.farandwide.help.cargo.stations.title", "screen.farandwide.help.cargo.stations.body",
                    texture("cargo_stations", 1354, 1161)),
            new HelpPage("screen.farandwide.help.cargo.operations.title", "screen.farandwide.help.cargo.operations.body",
                    texture("cargo_transfer", 1351, 1164)),
            new HelpPage("screen.farandwide.help.orders.title", "screen.farandwide.help.orders.body",
                    texture("order_intro", 1536, 1024)),
            new HelpPage("screen.farandwide.help.orders.source_inventories.title", "screen.farandwide.help.orders.source_inventories.body",
                    texture("source_inventories", 1672, 941)),
            new HelpPage("screen.farandwide.help.orders.placement.title", "screen.farandwide.help.orders.placement.body",
                    texture("order_placement", 1254, 1254)),
            new HelpPage("screen.farandwide.help.orders.multileg.title", "screen.farandwide.help.orders.multileg.body",
                    texture("order_multileg", 1774, 887)),
            new HelpPage("screen.farandwide.help.orders.handoff.title", "screen.farandwide.help.orders.handoff.body",
                    texture("order_handoff", 1536, 1024)),
            new HelpPage("screen.farandwide.help.tips_and_tricks.page1.title", "screen.farandwide.help.tips_and_tricks.page1.body", null),
            new HelpPage("screen.farandwide.help.tips_and_tricks.page2.title", "screen.farandwide.help.tips_and_tricks.page2.body", null),
            new HelpPage("screen.farandwide.help.tips_and_tricks.page3.title", "screen.farandwide.help.tips_and_tricks.page3.body", null),
            new HelpPage("screen.farandwide.help.tips_and_tricks.page4.title", "screen.farandwide.help.tips_and_tricks.page4.body", null),
            new HelpPage("screen.farandwide.help.tips_and_tricks.page5.title", "screen.farandwide.help.tips_and_tricks.page5.body", null),
            new HelpPage("screen.farandwide.help.tips_and_tricks.page6.title", "screen.farandwide.help.tips_and_tricks.page6.body", null)
    };

    private int pageIndex;
    private Button previousButton;
    private Button nextButton;
    private Button indexButton;
    private Button doneButton;
    private List<Button> sectionButtons = List.of();

    public FarAndWideHelpScreen() {
        super(Component.translatable("screen.farandwide.help.title"));
    }

    @Override
    protected void init() {
        int buttonsWidth = BUTTON_WIDTH * 3 + BUTTON_GAP * 2;
        int left = (width - buttonsWidth) / 2;
        int buttonY = height - 30;

        previousButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.farandwide.help.previous"),
                button -> showPage(pageIndex - 1))
                .bounds(left, buttonY, BUTTON_WIDTH, 20)
                .build());
        indexButton = addRenderableWidget(Button.builder(
                linkText("screen.farandwide.help.index_link"),
                button -> showPage(INDEX_PAGE))
                .bounds(left + BUTTON_WIDTH + BUTTON_GAP, buttonY, BUTTON_WIDTH, 20)
                .build());
        doneButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.farandwide.help.done"),
                button -> onClose())
                .bounds(left + BUTTON_WIDTH + BUTTON_GAP, buttonY, BUTTON_WIDTH, 20)
                .build());
        nextButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.farandwide.help.next"),
                button -> showPage(pageIndex + 1))
                .bounds(left + (BUTTON_WIDTH + BUTTON_GAP) * 2, buttonY, BUTTON_WIDTH, 20)
                .build());

        int linkY = height - CONTENT_BOTTOM_MARGIN - PANEL_PADDING - font.lineHeight - INDEX_LINK_GAP * 3;
        sectionButtons = List.of(
                sectionButton("screen.farandwide.help.index.routes", ROUTES_PAGE, linkY),
                sectionButton("screen.farandwide.help.index.cargo", CARGO_PAGE, linkY + INDEX_LINK_GAP),
                sectionButton("screen.farandwide.help.index.orders", ORDERS_PAGE, linkY + INDEX_LINK_GAP * 2),
                sectionButton("screen.farandwide.help.index.tips_and_tricks", TIPS_AND_TRICKS_PAGE,
                        linkY + INDEX_LINK_GAP * 3));
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
        minecraft.setScreenAndShow(null);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        HelpPage page = PAGES[pageIndex];
        Component pageTitle = Component.translatable(page.titleKey());
        Component pageBody = Component.translatable(page.bodyKey());
        int panelWidth = Math.min(PANEL_MAX_WIDTH, width - 40);
        int panelHeight = Math.max(40, height - CONTENT_TOP - CONTENT_BOTTOM_MARGIN);
        int panelX = (width - panelWidth) / 2;
        int panelY = CONTENT_TOP;
        int bodyWidth = panelWidth - PANEL_PADDING * 2;
        List<FormattedCharSequence> bodyLines = font.split(pageBody, bodyWidth);
        int titleY = 16;
        int bodyY = 32;

        graphics.centeredText(font, title, width / 2, titleY, 0xFFFFFFFF);
        graphics.centeredText(font, pageTitle, width / 2, bodyY, 0xFFFFD27F);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xCC000000);

        if (pageIndex == INDEX_PAGE) {
            drawIndexPage(graphics, bodyLines, panelY, mouseX, mouseY, partialTick);
        } else if (page.image() == null) {
            drawCenteredLines(graphics, bodyLines, panelY + PANEL_PADDING);
        } else {
            drawImagePage(graphics, page.image(), bodyLines, panelY, panelWidth, panelHeight);
        }

        graphics.centeredText(
                font,
                Component.translatable("screen.farandwide.help.page", pageIndex + 1, PAGES.length),
                width / 2,
                height - 48,
                0xFFAAAAAA);
    }

    private void drawIndexPage(GuiGraphicsExtractor graphics, List<FormattedCharSequence> bodyLines, int panelY,
            int mouseX, int mouseY, float partialTick) {
        drawCenteredLines(graphics, bodyLines, panelY + PANEL_PADDING);

        // Screen renders widgets before this page content, so redraw the index
        // links after its panel to keep them visible and clickable-looking.
        for (Button button : sectionButtons) {
            button.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void drawImagePage(GuiGraphicsExtractor graphics, HelpImage image,
            List<FormattedCharSequence> bodyLines, int panelY, int panelWidth, int panelHeight) {
        int textHeight = bodyLines.size() * font.lineHeight;
        int availableHeight = panelHeight - PANEL_PADDING * 2 - 8 - textHeight;
        int maxImageWidth = Math.max(1, panelWidth - PANEL_PADDING * 2);
        int maxImageHeight = Math.max(1, Math.min(270, availableHeight));
        double scale = Math.min(
                (double) maxImageWidth / image.width(),
                (double) maxImageHeight / image.height());
        int imageWidth = Math.max(1, (int) Math.round(image.width() * scale));
        int imageHeight = Math.max(1, (int) Math.round(image.height() * scale));
        int imageX = (width - imageWidth) / 2;
        int imageY = panelY + PANEL_PADDING;

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                image.identifier(),
                imageX,
                imageY,
                0,
                0,
                imageWidth,
                imageHeight,
                image.width(),
                image.height(),
                image.width(),
                image.height());
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
        indexButton.visible = pageIndex != INDEX_PAGE;
        doneButton.visible = pageIndex == INDEX_PAGE;
        for (Button button : sectionButtons) {
            button.visible = pageIndex == INDEX_PAGE;
        }
    }

    private Button sectionButton(String labelKey, int targetPage, int y) {
        Component label = linkText(labelKey);
        int linkWidth = font.width(label);
        return addRenderableWidget(new PlainTextButton(
                (width - linkWidth) / 2, y, linkWidth, font.lineHeight,
                label, button -> showPage(targetPage), font));
    }

    private static Component linkText(String translationKey) {
        return Component.translatable(translationKey)
                .withStyle(style -> style.withColor(0xFFD27F));
    }

    private static HelpImage texture(String name, int width, int height) {
        Identifier identifier = Identifier.fromNamespaceAndPath(
                FarAndWide.MODID,
                "textures/gui/help/" + name + ".png");
        return new HelpImage(identifier, width, height);
    }

    private record HelpPage(String titleKey, String bodyKey, HelpImage image) {
    }

    private record HelpImage(Identifier identifier, int width, int height) {
    }
}
