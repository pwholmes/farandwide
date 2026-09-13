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
    private static final long INTERVAL = Constants.Cargo.TRANSFER_INTERVAL_TICKS;

    @ParameterizedTest
    @EnumSource(CargoOperation.class)
    void noMovableCargoFinishesOnArrival(CargoOperation operation) {
        assertTrue(session(operation).tick(ARRIVAL_TICK, () -> 0, () -> 0));
    }

    @ParameterizedTest
    @EnumSource(value = CargoOperation.class, names = {"LOAD", "UNLOAD"})
    void eachMovedStackDelaysNextTransferAndDeparture(CargoOperation operation) {
        CargoTransferSession session = session(operation);
        AtomicInteger attempts = new AtomicInteger();
        IntSupplier transfer = () -> attempts.getAndIncrement() < 2 ? 64 : 0;
        IntSupplier unexpected = () -> fail("The other transfer direction must not run");
        IntSupplier unload = operation == CargoOperation.UNLOAD ? transfer : unexpected;
        IntSupplier load = operation == CargoOperation.LOAD ? transfer : unexpected;

        assertFalse(session.tick(ARRIVAL_TICK, unload, load));
        assertEquals(1, attempts.get());
        assertFalse(session.tick(ARRIVAL_TICK + INTERVAL - 1, unload, load));
        assertEquals(1, attempts.get());
        assertFalse(session.tick(ARRIVAL_TICK + INTERVAL, unload, load));
        assertEquals(2, attempts.get());
        assertFalse(session.tick(ARRIVAL_TICK + 2 * INTERVAL - 1, unload, load));
        assertEquals(2, attempts.get());
        assertTrue(session.tick(ARRIVAL_TICK + 2 * INTERVAL, unload, load));
        assertEquals(3, attempts.get());
    }

    @Test
    void emptyUnloadStageLoadsImmediately() {
        CargoTransferSession session = session(CargoOperation.UNLOAD_THEN_LOAD);
        AtomicInteger loaded = new AtomicInteger();

        assertFalse(session.tick(ARRIVAL_TICK, () -> 0, () -> loaded.incrementAndGet()));

        assertEquals(1, loaded.get());
        assertTrue(session.tick(ARRIVAL_TICK + INTERVAL, () -> fail("Unloading is finished"), () -> 0));
    }

    @Test
    void unloadThenLoadHasOnlySuccessfulTransferDelays() {
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
        assertEquals(List.of("unload", "unload", "load"), attempts);
        assertTrue(session.tick(ARRIVAL_TICK + 2 * INTERVAL, unload, load));
        assertEquals(List.of("unload", "unload", "load", "load"), attempts);
    }

    @Test
    void unloadWithNoLoadFinishesAfterLastTransferDelay() {
        CargoTransferSession session = session(CargoOperation.UNLOAD_THEN_LOAD);

        assertFalse(session.tick(ARRIVAL_TICK, () -> 1, () -> fail("Must unload first")));
        assertTrue(session.tick(ARRIVAL_TICK + INTERVAL, () -> 0, () -> 0));
    }

    private static CargoTransferSession session(CargoOperation operation) {
        return new CargoTransferSession(1, 1, CargoBehavior.unfiltered(operation));
    }
}
