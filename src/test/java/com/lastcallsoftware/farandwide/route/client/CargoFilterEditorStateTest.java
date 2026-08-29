package com.lastcallsoftware.farandwide.route.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lastcallsoftware.farandwide.route.CargoFilter;
import com.lastcallsoftware.farandwide.Constants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class CargoFilterEditorStateTest {
    private static final Identifier COAL = Identifier.parse("minecraft:coal");
    private static final Identifier IRON = Identifier.parse("minecraft:iron_ingot");
    private static final Identifier COPPER = Identifier.parse("minecraft:copper_ingot");
    private static final List<CargoFilterEditorState.Entry> ENTRIES = List.of(
            entry(COAL, "Coal"), entry(COPPER, "Copper Ingot"), entry(IRON, "Iron Ingot"));

    @Test
    void modeChangesRetainSelectionButAllBuildsAnUnfilteredValue() {
        CargoFilterEditorState state = new CargoFilterEditorState(CargoFilter.allowList(List.of(COAL)), ENTRIES);

        state.setMode(CargoFilter.Mode.ALL);
        assertEquals(CargoFilter.all(), state.buildFilter().orElseThrow());

        state.setMode(CargoFilter.Mode.ALLOW_LIST);
        assertEquals(List.of(COAL), state.buildFilter().orElseThrow().itemIds());
    }

    @Test
    void selectedModeRequiresAtLeastOneItem() {
        CargoFilterEditorState state = new CargoFilterEditorState(CargoFilter.all(), ENTRIES);

        state.setMode(CargoFilter.Mode.ALLOW_LIST);
        assertFalse(state.isValid());
        assertTrue(state.buildFilter().isEmpty());

        state.toggle(IRON);
        assertTrue(state.isValid());
        assertEquals(List.of(IRON), state.buildFilter().orElseThrow().itemIds());
    }

    @Test
    void togglingAddsAndRemovesItemsInSelectionOrder() {
        CargoFilterEditorState state = new CargoFilterEditorState(CargoFilter.all(), ENTRIES);
        state.setMode(CargoFilter.Mode.ALLOW_LIST);

        state.toggle(IRON);
        state.toggle(COAL);
        state.toggle(IRON);

        assertEquals(List.of(COAL), state.buildFilter().orElseThrow().itemIds());
    }

    @Test
    void selectedEntriesExposeTheSelectedItemsInSelectionOrder() {
        CargoFilterEditorState state = new CargoFilterEditorState(CargoFilter.allowList(List.of(IRON, COAL)), ENTRIES);

        assertEquals(List.of(IRON, COAL), ids(state.selectedEntries()));
    }

    @Test
    void searchMatchesDisplayNamesAndRegistryIdentifiersIgnoringCase() {
        CargoFilterEditorState state = new CargoFilterEditorState(CargoFilter.all(), ENTRIES);

        state.setSearchQuery("IRON");
        assertEquals(List.of(IRON), ids(state.visibleEntries(4, 2)));

        state.setSearchQuery("minecraft:co");
        assertEquals(List.of(COAL, COPPER), ids(state.visibleEntries(4, 2)));
    }

    @Test
    void scrollingIsClampedAndSearchResetsIt() {
        CargoFilterEditorState state = new CargoFilterEditorState(CargoFilter.all(), ENTRIES);

        state.scrollRows(10, 1, 1);
        assertEquals(2, state.scrollRow());
        assertEquals(List.of(IRON), ids(state.visibleEntries(1, 1)));

        state.scrollRows(-10, 1, 1);
        assertEquals(0, state.scrollRow());

        state.scrollRows(2, 1, 1);
        state.setSearchQuery("coal");
        assertEquals(0, state.scrollRow());
    }

    @Test
    void selectionCannotGrowPastTheWireLimit() {
        List<Identifier> selected = new ArrayList<>();
        for (int index = 0; index < Constants.Network.MAX_FILTER_ITEMS; index++) {
            selected.add(Identifier.parse("farandwide:selected_" + index));
        }
        CargoFilterEditorState state = new CargoFilterEditorState(CargoFilter.allowList(selected), ENTRIES);

        state.toggle(COAL);

        assertEquals(Constants.Network.MAX_FILTER_ITEMS, state.selectedCount());
        assertFalse(state.isSelected(COAL));
    }

    @Test
    void unavailableSelectedIdentifiersSurviveUntilExplicitlyRemoved() {
        Identifier unavailable = Identifier.parse("missing_mod:machine_part");
        CargoFilterEditorState.Entry unavailableEntry =
                new CargoFilterEditorState.Entry(unavailable, unavailable.toString(), false);
        CargoFilterEditorState state = new CargoFilterEditorState(
                CargoFilter.allowList(List.of(unavailable)), List.of(unavailableEntry));

        assertEquals(List.of(unavailable), state.buildFilter().orElseThrow().itemIds());
        state.setSearchQuery("missing_mod");
        assertEquals(List.of(unavailable), ids(state.visibleEntries(4, 1)));

        state.toggle(unavailable);
        assertFalse(state.isValid());
    }

    private static CargoFilterEditorState.Entry entry(Identifier id, String name) {
        return new CargoFilterEditorState.Entry(id, name, true);
    }

    private static List<Identifier> ids(List<CargoFilterEditorState.Entry> entries) {
        return entries.stream().map(CargoFilterEditorState.Entry::itemId).toList();
    }
}
