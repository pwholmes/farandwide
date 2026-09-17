package com.lastcallsoftware.farandwide.route.server;

import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.route.CargoBehavior;
import com.lastcallsoftware.farandwide.route.CargoOperation;
import com.lastcallsoftware.farandwide.route.server.ServerRouteTraversalController.CargoTransferSession;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class CargoTransferSessionTest {
    private static final long ARRIVAL_TICK = 100;
    private static final long TICKS_PER_SECOND = 20;
    private static final long INTERVAL = secondsToTicks(Constants.Cargo.TRANSFER_DELAY_SECONDS);
    private static final long DWELL = secondsToTicks(Constants.Cargo.DWELL_SECONDS);

    @ParameterizedTest
    @EnumSource(value = CargoOperation.class, names = {"UNLOAD", "UNLOAD_THEN_LOAD"})
    void noMovableUnloadDwellsBeforeDeparture(CargoOperation operation) {
        CargoTransferSession session = session(operation);

        assertFalse(session.tick(ARRIVAL_TICK, () -> 0, () -> 0));
        assertFalse(session.tick(ARRIVAL_TICK + DWELL - 1, () -> fail("Still dwelling"), () -> fail("Still dwelling")));
        assertTrue(session.tick(ARRIVAL_TICK + DWELL, () -> 0, () -> 0));
    }

    @ParameterizedTest
    @EnumSource(value = CargoOperation.class, names = {"LOAD", "UNLOAD"})
    void eachSuccessfulTransferDelaysNextTransferAndDeparture(CargoOperation operation) {
        CargoTransferSession session = session(operation);
        AtomicInteger attempts = new AtomicInteger();
        IntSupplier transfer = () -> attempts.getAndIncrement() < 2 ? Constants.Cargo.ITEMS_PER_TRANSFER : 0;
        IntSupplier unload = operation == CargoOperation.LOAD ? () -> 0 : transfer;
        IntSupplier load = operation == CargoOperation.UNLOAD ? () -> 0 : transfer;

        long firstTransferTick = ARRIVAL_TICK;
        assertFalse(session.tick(firstTransferTick, unload, load));
        assertEquals(1, attempts.get());
        assertFalse(session.tick(firstTransferTick + INTERVAL - 1, unload, load));
        assertEquals(1, attempts.get());
        assertFalse(session.tick(firstTransferTick + INTERVAL, unload, load));
        assertEquals(2, attempts.get());
        assertFalse(session.tick(firstTransferTick + 2 * INTERVAL - 1, unload, load));
        assertEquals(2, attempts.get());
        if (operation == CargoOperation.UNLOAD) {
            assertFalse(session.tick(firstTransferTick + 2 * INTERVAL, unload, load));
            assertEquals(3, attempts.get());
            assertTrue(session.tick(firstTransferTick + 2 * INTERVAL + DWELL, unload, load));
        } else {
            assertTrue(session.tick(firstTransferTick + 2 * INTERVAL, unload, load));
            assertEquals(3, attempts.get());
        }
    }

    @Test
    void emptyUnloadStageDwellsBeforeLoading() {
        CargoTransferSession session = session(CargoOperation.UNLOAD_THEN_LOAD);
        AtomicInteger loaded = new AtomicInteger();

        assertFalse(session.tick(ARRIVAL_TICK, () -> 0, () -> loaded.incrementAndGet()));

        assertEquals(0, loaded.get());
        assertFalse(session.tick(ARRIVAL_TICK + DWELL - 1,
                () -> fail("Unloading is finished"), () -> fail("Still dwelling")));
        assertFalse(session.tick(ARRIVAL_TICK + DWELL,
                () -> fail("Unloading is finished"), () -> loaded.incrementAndGet()));
        assertEquals(1, loaded.get());
        assertTrue(session.tick(ARRIVAL_TICK + DWELL + INTERVAL, () -> fail("Unloading is finished"), () -> 0));
    }

    @Test
    void unloadThenLoadDwellsOnceBetweenTransferStages() {
        CargoTransferSession session = session(CargoOperation.UNLOAD_THEN_LOAD);
        List<String> attempts = new ArrayList<>();
        AtomicInteger unloaded = new AtomicInteger();
        AtomicInteger loaded = new AtomicInteger();
        IntSupplier unload = () -> {
            attempts.add("unload");
            return unloaded.getAndIncrement() == 0 ? 4 : 0;
        };
        IntSupplier load = () -> {
            attempts.add("load");
            return loaded.getAndIncrement() == 0 ? 4 : 0;
        };

        assertFalse(session.tick(ARRIVAL_TICK, unload, load));
        assertEquals(List.of("unload"), attempts);
        assertFalse(session.tick(ARRIVAL_TICK + INTERVAL - 1, unload, load));
        assertEquals(List.of("unload"), attempts);
        assertFalse(session.tick(ARRIVAL_TICK + INTERVAL, unload, load));
        assertEquals(List.of("unload", "unload"), attempts);
        assertFalse(session.tick(ARRIVAL_TICK + INTERVAL + DWELL - 1, unload, load));
        assertEquals(List.of("unload", "unload"), attempts);
        assertFalse(session.tick(ARRIVAL_TICK + INTERVAL + DWELL, unload, load));
        assertEquals(List.of("unload", "unload", "load"), attempts);
        assertTrue(session.tick(ARRIVAL_TICK + 2 * INTERVAL + DWELL, unload, load));
        assertEquals(List.of("unload", "unload", "load", "load"), attempts);
    }

    @Test
    void unloadWithNoLoadFinishesAfterTransferDelayAndDwell() {
        CargoTransferSession session = session(CargoOperation.UNLOAD_THEN_LOAD);

        assertFalse(session.tick(ARRIVAL_TICK, () -> 1, () -> fail("Must unload first")));
        assertFalse(session.tick(ARRIVAL_TICK + INTERVAL, () -> 0, () -> fail("Still dwelling")));
        assertTrue(session.tick(ARRIVAL_TICK + INTERVAL + DWELL, () -> fail("Unloading is finished"), () -> 0));
    }

    @Test
    void emptyUnloadThenLoadStillDwells() {
        CargoTransferSession session = session(CargoOperation.UNLOAD_THEN_LOAD);

        assertFalse(session.tick(ARRIVAL_TICK, () -> 0, () -> fail("Still dwelling")));
        assertFalse(session.tick(ARRIVAL_TICK + DWELL - 1,
                () -> fail("Unloading is finished"), () -> fail("Still dwelling")));
        assertTrue(session.tick(ARRIVAL_TICK + DWELL, () -> fail("Unloading is finished"), () -> 0));
    }

    @Test
    void departureFromOneWayAnchorSkipsUnloadingAndDwell() {
        CargoTransferSession session = new CargoTransferSession(1, 1,
                CargoBehavior.unfiltered(CargoOperation.UNLOAD_THEN_LOAD), true);

        assertTrue(session.tick(ARRIVAL_TICK, () -> fail("Departure must not unload"), () -> 0));
    }

    private static CargoTransferSession session(CargoOperation operation) {
        return new CargoTransferSession(1, 1, CargoBehavior.unfiltered(operation));
    }

    private static long secondsToTicks(double seconds) {
        return (long) Math.ceil(seconds * TICKS_PER_SECOND);
    }
}
