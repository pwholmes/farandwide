package com.lastcallsoftware.farandwide.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lastcallsoftware.farandwide.Constants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class CargoFilterTest {
    private static final Identifier COAL = Identifier.parse("minecraft:coal");
    private static final Identifier IRON = Identifier.parse("minecraft:iron_ingot");

    @Test
    void allItemsFilterAllowsEveryIdentifier() {
        CargoFilter filter = CargoFilter.all();

        assertTrue(filter.isAll());
        assertTrue(filter.allows(COAL));
        assertTrue(filter.allows(Identifier.parse("example:unknown_item")));
    }

    @Test
    void allowListPermitsOnlySelectedIdentifiers() {
        CargoFilter filter = CargoFilter.allowList(List.of(COAL));

        assertFalse(filter.isAll());
        assertTrue(filter.allows(COAL));
        assertFalse(filter.allows(IRON));
    }

    @Test
    void duplicateIdentifiersAreRemovedWithoutChangingOrder() {
        CargoFilter filter = CargoFilter.allowList(List.of(COAL, IRON, COAL));

        assertEquals(List.of(COAL, IRON), filter.itemIds());
    }

    @Test
    void filterItemsAreDefensivelyCopied() {
        List<Identifier> selected = new ArrayList<>(List.of(COAL));
        CargoFilter filter = CargoFilter.allowList(selected);

        selected.add(IRON);

        assertEquals(List.of(COAL), filter.itemIds());
        assertThrows(UnsupportedOperationException.class, () -> filter.itemIds().add(IRON));
    }

    @Test
    void modeAndItemsMustFormAUsefulFilter() {
        assertThrows(IllegalArgumentException.class,
                () -> new CargoFilter(CargoFilter.Mode.ALL, List.of(COAL)));
        assertThrows(IllegalArgumentException.class,
                () -> CargoFilter.allowList(List.of()));
    }

    @Test
    void nullValuesAreRejected() {
        assertThrows(NullPointerException.class, () -> new CargoFilter(null, List.of()));
        assertThrows(NullPointerException.class, () -> new CargoFilter(CargoFilter.Mode.ALL, null));
        assertThrows(NullPointerException.class,
                () -> CargoFilter.allowList(java.util.Arrays.asList(COAL, null)));
        assertThrows(NullPointerException.class, () -> CargoFilter.all().allows(null));
    }

    @Test
    void itemCountCannotExceedTheWireLimit() {
        List<Identifier> identifiers = new ArrayList<>();
        for (int index = 0; index < Constants.Network.MAX_FILTER_ITEMS; index++) {
            identifiers.add(Identifier.parse("farandwide:test_item_" + index));
        }

        assertEquals(Constants.Network.MAX_FILTER_ITEMS, CargoFilter.allowList(identifiers).itemIds().size());

        identifiers.add(Identifier.parse("farandwide:one_too_many"));
        assertThrows(IllegalArgumentException.class, () -> CargoFilter.allowList(identifiers));
    }
}
