package com.lastcallsoftware.farandwide.route.client;

import com.lastcallsoftware.farandwide.route.CargoFilter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.eclipse.jdt.annotation.NonNull;

/** Searchable item-registry editor for one cargo transfer direction. */
final class CargoFilterScreen extends Screen {
    private static final int PANEL_WIDTH = 320;
    private static final int SLOT_SIZE = 22;
    private static final int ITEM_SIZE = 16;
    private static final int SELECTED_ITEMS_TOP = 58;
    private static final int SEARCH_TOP = 82;
    private static final int GRID_TOP = 108;

    private final CargoWaypointScreen parent;
    private final boolean loading;
    private final CargoFilterEditorState state;
    private EditBox searchField;
    private Button doneButton;
    private boolean showValidationError;
    private int selectedItemScroll;

    CargoFilterScreen(CargoWaypointScreen parent, boolean loading, CargoFilter initialFilter) {
        super(Component.translatable(loading
                ? "screen.farandwide.cargo_filter.load_title"
                : "screen.farandwide.cargo_filter.unload_title"));
        this.parent = parent;
        this.loading = loading;
        state = new CargoFilterEditorState(initialFilter, registryEntries(initialFilter));
    }

    @Override
    protected void init() {
        int panelWidth = panelWidth();
        int left = (width - panelWidth) / 2;

        addRenderableWidget(CycleButton
                .builder(CargoFilterScreen::modeName, state.mode())
                .withValues(CargoFilter.Mode.values())
                .create(left, 30, panelWidth, 20,
                        Component.translatable("screen.farandwide.cargo_filter.mode"),
                        (button, mode) -> {
                            state.setMode(mode);
                            showValidationError = false;
                            updateFilterControls();
                            updateDoneButton();
                        }));

        searchField = addRenderableWidget(new EditBox(font, left, SEARCH_TOP, panelWidth, 20,
                Component.translatable("screen.farandwide.cargo_filter.search")));
        searchField.setMaxLength(128);
        searchField.setHint(Component.translatable("screen.farandwide.cargo_filter.search_hint"));
        searchField.setResponder(state::setSearchQuery);

        int buttonY = height - 28;
        doneButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.done"), button -> applyAndClose())
                .bounds(left, buttonY, (panelWidth - 8) / 2, 20)
                .build());
        addRenderableWidget(Button.builder(
                Component.translatable("gui.cancel"), button -> returnToParent())
                .bounds(left + (panelWidth + 8) / 2, buttonY, (panelWidth - 8) / 2, 20)
                .build());
        updateFilterControls();
        updateDoneButton();
        if (searchField.visible) {
            setInitialFocus(searchField);
        }
    }

    private void updateFilterControls() {
        boolean selectingItems = state.mode() == CargoFilter.Mode.ALLOW_LIST;
        searchField.setVisible(selectingItems);
        if (!selectingItems) {
            searchField.setFocused(false);
        }
    }

    private void updateDoneButton() {
        if (doneButton != null) {
            doneButton.active = state.isValid();
        }
    }

    private void applyAndClose() {
        state.buildFilter().ifPresent(filter -> {
            parent.setSelectedFilter(loading, filter);
            returnToParent();
        });
        if (!state.isValid()) {
            showValidationError = true;
        }
    }

    private void returnToParent() {
        minecraft.setScreenAndShow(parent);
    }

    @Override
    public void onClose() {
        returnToParent();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() != 0 || state.mode() != CargoFilter.Mode.ALLOW_LIST) {
            return false;
        }
        CargoFilterEditorState.Entry entry = isOverSelectedItems(event.x(), event.y())
                ? selectedEntryAt(event.x())
                : isOverGrid(event.x(), event.y()) ? entryAt(event.x(), event.y()) : null;
        if (entry == null) {
            return false;
        }
        state.toggle(entry.itemId());
        showValidationError = false;
        updateDoneButton();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (state.mode() == CargoFilter.Mode.ALLOW_LIST && isOverSelectedItems(mouseX, mouseY)
                && verticalAmount != 0) {
            scrollSelectedItems(verticalAmount > 0 ? -1 : 1);
            return true;
        }
        if (isOverGrid(mouseX, mouseY) && verticalAmount != 0) {
            state.scrollRows(verticalAmount > 0 ? -1 : 1, columns(), visibleRows());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);

        if (state.mode() == CargoFilter.Mode.ALLOW_LIST) {
            extractSelectedItems(graphics, mouseX, mouseY);
        }

        if (state.mode() == CargoFilter.Mode.ALLOW_LIST && state.resultCount() == 0) {
            graphics.centeredText(font,
                    Component.translatable("screen.farandwide.cargo_filter.no_results"),
                    width / 2, GRID_TOP + 8, 0xFFAAAAAA);
        } else if (state.mode() == CargoFilter.Mode.ALLOW_LIST) {
            extractItemGrid(graphics, mouseX, mouseY);
        }

        if (showValidationError || !state.isValid()) {
            graphics.centeredText(font,
                    Component.translatable("screen.farandwide.cargo_filter.empty_selection"),
                    width / 2, height - 42, 0xFFFF5555);
        }
    }

    private void extractItemGrid(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<CargoFilterEditorState.Entry> entries = state.visibleEntries(columns(), visibleRows());
        for (int index = 0; index < entries.size(); index++) {
            CargoFilterEditorState.Entry entry = entries.get(index);
            int x = gridLeft() + index % columns() * SLOT_SIZE;
            int y = GRID_TOP + index / columns() * SLOT_SIZE;
            extractItemEntry(graphics, entry, x, y, state.isSelected(entry.itemId()), mouseX, mouseY);
        }
    }

    private void extractSelectedItems(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<CargoFilterEditorState.Entry> selected = state.selectedEntries();
        int maximumScroll = Math.max(0, selected.size() - columns());
        selectedItemScroll = Math.clamp(selectedItemScroll, 0, maximumScroll);
        int visibleCount = Math.min(columns(), selected.size() - selectedItemScroll);
        for (int index = 0; index < visibleCount; index++) {
            extractItemEntry(graphics, selected.get(selectedItemScroll + index),
                    gridLeft() + index * SLOT_SIZE, SELECTED_ITEMS_TOP, false, mouseX, mouseY);
        }
        if (selectedItemScroll > 0) {
            graphics.text(font, "‹", gridLeft() - 8, SELECTED_ITEMS_TOP + 5, 0xFFAAAAAA);
        }
        if (selectedItemScroll < maximumScroll) {
            graphics.text(font, "›", gridLeft() + columns() * SLOT_SIZE, SELECTED_ITEMS_TOP + 5, 0xFFAAAAAA);
        }
    }

    private void extractItemEntry(GuiGraphicsExtractor graphics, CargoFilterEditorState.Entry entry,
            int x, int y, boolean selected, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE;
        int background = selected ? 0xFF28633A : hovered ? 0xFF555555 : 0xFF292929;
        graphics.fill(x, y, x + SLOT_SIZE - 2, y + SLOT_SIZE - 2, background);
        graphics.outline(x, y, SLOT_SIZE - 2, SLOT_SIZE - 2, selected ? 0xFF55FF77 : 0xFF777777);

        Item item = BuiltInRegistries.ITEM.getOptional(entry.itemId()).orElse(null);
        if (entry.available() && item != null) {
            graphics.item(item.getDefaultInstance(), x + 2, y + 2);
        } else {
            graphics.centeredText(font, "?", x + ITEM_SIZE / 2 + 2, y + 6, 0xFFFFAA00);
        }
        if (hovered) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal(entry.displayName()));
            tooltip.add(Component.literal(entry.itemId().toString()).withStyle(ChatFormatting.GRAY));
            if (!entry.available()) {
                tooltip.add(Component.translatable("screen.farandwide.cargo_filter.item_unavailable")
                        .withStyle(ChatFormatting.GOLD));
            }
            graphics.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
        }
    }

    private CargoFilterEditorState.Entry entryAt(double mouseX, double mouseY) {
        int column = ((int) mouseX - gridLeft()) / SLOT_SIZE;
        int row = ((int) mouseY - GRID_TOP) / SLOT_SIZE;
        if (column < 0 || column >= columns() || row < 0 || row >= visibleRows()) {
            return null;
        }
        List<CargoFilterEditorState.Entry> visible = state.visibleEntries(columns(), visibleRows());
        int index = row * columns() + column;
        return index < visible.size() ? visible.get(index) : null;
    }

    private CargoFilterEditorState.Entry selectedEntryAt(double mouseX) {
        int index = ((int) mouseX - gridLeft()) / SLOT_SIZE + selectedItemScroll;
        List<CargoFilterEditorState.Entry> selected = state.selectedEntries();
        return index >= 0 && index < selected.size() ? selected.get(index) : null;
    }

    private boolean isOverGrid(double mouseX, double mouseY) {
        return mouseX >= gridLeft() && mouseX < gridLeft() + columns() * SLOT_SIZE
                && mouseY >= GRID_TOP && mouseY < GRID_TOP + visibleRows() * SLOT_SIZE;
    }

    private boolean isOverSelectedItems(double mouseX, double mouseY) {
        return mouseX >= gridLeft() && mouseX < gridLeft() + columns() * SLOT_SIZE
                && mouseY >= SELECTED_ITEMS_TOP && mouseY < SELECTED_ITEMS_TOP + SLOT_SIZE;
    }

    private void scrollSelectedItems(int amount) {
        int maximumScroll = Math.max(0, state.selectedEntries().size() - columns());
        selectedItemScroll = Math.clamp(selectedItemScroll + amount, 0, maximumScroll);
    }

    private int panelWidth() {
        return Math.min(PANEL_WIDTH, Math.max(SLOT_SIZE * 4, width - 20));
    }

    private int columns() {
        return Math.max(1, panelWidth() / SLOT_SIZE);
    }

    private int visibleRows() {
        return Math.max(1, (height - GRID_TOP - 54) / SLOT_SIZE);
    }

    private int gridLeft() {
        return (width - columns() * SLOT_SIZE) / 2;
    }

    private static Component modeName(CargoFilter.Mode mode) {
        return Component.translatable("cargo_filter_mode.farandwide."
                + mode.name().toLowerCase(Locale.ROOT));
    }

    private static List<CargoFilterEditorState.Entry> registryEntries(CargoFilter initialFilter) {
        List<CargoFilterEditorState.Entry> entries = new ArrayList<>();
        Set<Identifier> availableIds = new HashSet<>();
        Identifier air = Identifier.withDefaultNamespace("air");
        for (Identifier itemId : BuiltInRegistries.ITEM.keySet()) {
            if (itemId.equals(air)) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM.getValue(itemId);
            ItemStack stack = item.getDefaultInstance();
            entries.add(new CargoFilterEditorState.Entry(itemId, stack.getHoverName().getString(), true));
            availableIds.add(itemId);
        }
        for (Identifier selectedId : initialFilter.itemIds()) {
            if (!availableIds.contains(selectedId)) {
                entries.add(new CargoFilterEditorState.Entry(selectedId, selectedId.toString(), false));
            }
        }
        entries.sort(Comparator
                .comparing((CargoFilterEditorState.Entry entry) -> entry.displayName().toLowerCase(Locale.ROOT))
                .thenComparing((CargoFilterEditorState.@NonNull Entry entry) -> entry.itemId()));
        return entries;
    }
}
