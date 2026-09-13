package com.lastcallsoftware.farandwide.route.client;

import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderItemSelectionStateTest {
    private static ItemResource stone;
    private static ItemResource dirt;

    @SuppressWarnings("deprecation")
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (var item : List.of(Items.AIR, Items.STONE, Items.DIRT)) {
            item.builtInRegistryHolder().bindComponents(DataComponents.COMMON_ITEM_COMPONENTS);
        }
        stone = ItemResource.of(Items.STONE);
        dirt = ItemResource.of(Items.DIRT);
    }

    @Test
    void leftClickMovesWholeQuantityThroughTheCursor() {
        var state = new OrderItemSelectionState(Map.of(stone, 100), Map.of());

        state.click(OrderItemSelectionState.Side.AVAILABLE, stone, 0);
        assertEquals(100, state.carried().quantity());
        assertFalse(state.available().containsKey(stone));

        state.click(OrderItemSelectionState.Side.SELECTED, null, 0);
        assertNull(state.carried());
        assertEquals(100, state.selected().get(stone));
    }

    @Test
    void rightClickTakesHalfRoundedUpAndPlacesOne() {
        var state = new OrderItemSelectionState(Map.of(stone, 9), Map.of());

        state.click(OrderItemSelectionState.Side.AVAILABLE, stone, 1);
        assertEquals(5, state.carried().quantity());
        assertEquals(4, state.available().get(stone));

        state.click(OrderItemSelectionState.Side.SELECTED, null, 1);
        assertEquals(4, state.carried().quantity());
        assertEquals(1, state.selected().get(stone));
    }

    @Test
    void shiftClickMovesDirectlyBetweenInventories() {
        var state = new OrderItemSelectionState(Map.of(stone, 100), Map.of());

        state.quickMove(OrderItemSelectionState.Side.AVAILABLE, stone);
        assertEquals(100, state.selected().get(stone));
        assertFalse(state.available().containsKey(stone));

        state.quickMove(OrderItemSelectionState.Side.SELECTED, stone);
        assertEquals(100, state.available().get(stone));
        assertFalse(state.selected().containsKey(stone));
    }

    @Test
    void selectedQuantityIsLimitedWithoutLosingTheRemainder() {
        var state = new OrderItemSelectionState(Map.of(stone, 5_000), Map.of());

        state.click(OrderItemSelectionState.Side.AVAILABLE, stone, 0);
        state.click(OrderItemSelectionState.Side.SELECTED, null, 0);

        assertEquals(4_096, state.selected().get(stone));
        assertEquals(904, state.carried().quantity());
    }

    @Test
    void cancellingACarriedMoveRestoresItsOrigin() {
        var state = new OrderItemSelectionState(Map.of(stone, 64), Map.of());
        state.click(OrderItemSelectionState.Side.AVAILABLE, stone, 1);

        state.returnCarried();

        assertNull(state.carried());
        assertEquals(64, state.available().get(stone));
    }

    @Test
    void leftClickSwapsWithADifferentItem() {
        var state = new OrderItemSelectionState(Map.of(stone, 64, dirt, 32), Map.of(dirt, 8));
        state.click(OrderItemSelectionState.Side.AVAILABLE, stone, 0);

        state.click(OrderItemSelectionState.Side.SELECTED, dirt, 0);

        assertEquals(stone, state.selected().keySet().iterator().next());
        assertEquals(64, state.selected().get(stone));
        assertEquals(dirt, state.carried().resource());
        assertEquals(8, state.carried().quantity());
    }
}
