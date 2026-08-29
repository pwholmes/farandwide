package com.lastcallsoftware.farandwide.route.client;

import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.route.CargoFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/** UI-independent state for searching and editing one cargo item filter. */
final class CargoFilterEditorState {
    private final List<Entry> entries;
    private final Map<Identifier, Entry> entriesById;
    private final LinkedHashSet<Identifier> selectedIds;
    private CargoFilter.Mode mode;
    private String searchQuery = "";
    private int scrollRow;

    CargoFilterEditorState(CargoFilter initialFilter, List<Entry> entries) {
        Objects.requireNonNull(initialFilter, "initialFilter");
        this.entries = List.copyOf(entries);
        entriesById = new HashMap<>();
        for (Entry entry : this.entries) {
            entriesById.put(entry.itemId(), entry);
        }
        selectedIds = new LinkedHashSet<>(initialFilter.itemIds());
        mode = initialFilter.mode();
    }

    CargoFilter.Mode mode() {
        return mode;
    }

    void setMode(CargoFilter.Mode mode) {
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    void setSearchQuery(String searchQuery) {
        this.searchQuery = Objects.requireNonNull(searchQuery, "searchQuery").trim().toLowerCase(Locale.ROOT);
        scrollRow = 0;
    }

    void toggle(Identifier itemId) {
        Objects.requireNonNull(itemId, "itemId");
        if (!selectedIds.remove(itemId)) {
            if (selectedIds.size() < Constants.Network.MAX_FILTER_ITEMS) {
                selectedIds.add(itemId);
            }
        }
    }

    boolean isSelected(Identifier itemId) {
        return selectedIds.contains(itemId);
    }

    int selectedCount() {
        return selectedIds.size();
    }

    /** Selected entries in their persisted selection order. */
    List<Entry> selectedEntries() {
        List<Entry> result = new ArrayList<>(selectedIds.size());
        for (Identifier selectedId : selectedIds) {
            Entry entry = entriesById.get(selectedId);
            if (entry != null) {
                result.add(entry);
            }
        }
        return result;
    }

    boolean isValid() {
        return mode == CargoFilter.Mode.ALL || !selectedIds.isEmpty();
    }

    Optional<CargoFilter> buildFilter() {
        if (!isValid()) {
            return Optional.empty();
        }
        return Optional.of(mode == CargoFilter.Mode.ALL
                ? CargoFilter.all()
                : CargoFilter.allowList(List.copyOf(selectedIds)));
    }

    List<Entry> visibleEntries(int columns, int rows) {
        if (columns <= 0 || rows <= 0) {
            return List.of();
        }
        List<Entry> filtered = filteredEntries();
        clampScroll(columns, rows, filtered.size());
        int start = Math.min(scrollRow * columns, filtered.size());
        int end = Math.min(start + columns * rows, filtered.size());
        return filtered.subList(start, end);
    }

    void scrollRows(int amount, int columns, int visibleRows) {
        if (columns <= 0 || visibleRows <= 0 || amount == 0) {
            return;
        }
        int resultCount = filteredEntries().size();
        int totalRows = (resultCount + columns - 1) / columns;
        int maxScroll = Math.max(0, totalRows - visibleRows);
        scrollRow = Math.clamp(scrollRow + amount, 0, maxScroll);
    }

    int scrollRow() {
        return scrollRow;
    }

    int resultCount() {
        return filteredEntries().size();
    }

    private List<Entry> filteredEntries() {
        if (searchQuery.isEmpty()) {
            return entries;
        }
        List<Entry> result = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.searchText().contains(searchQuery)) {
                result.add(entry);
            }
        }
        return result;
    }

    private void clampScroll(int columns, int visibleRows, int resultCount) {
        int totalRows = (resultCount + columns - 1) / columns;
        scrollRow = Math.min(scrollRow, Math.max(0, totalRows - visibleRows));
    }

    record Entry(Identifier itemId, String displayName, boolean available) {
        Entry {
            Objects.requireNonNull(itemId, "itemId");
            Objects.requireNonNull(displayName, "displayName");
        }

        String searchText() {
            return (displayName + " " + itemId).toLowerCase(Locale.ROOT);
        }
    }
}
