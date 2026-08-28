package com.lastcallsoftware.farandwide.route.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.lastcallsoftware.farandwide.route.CargoOperation;
import java.util.Arrays;
import java.util.function.Predicate;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.junit.jupiter.api.Test;

class CargoTransferServiceTest {
    @Test
    void partialDestinationMovesOnlyAcceptedAmountWithoutLoss() {
        TestHandler source = handler(10, TestResource.IRON);
        TestHandler destination = handler(60, TestResource.IRON);
        var result = transfer(source, destination, CargoOperation.UNLOAD, resource -> true, resource -> true);
        assertEquals(4, result.unloaded());
        assertEquals(6, source.getAmountAsInt(0));
        assertEquals(64, destination.getAmountAsInt(0));
    }

    @Test
    void fullDestinationDoesNotMoveOrLoseItems() {
        TestHandler source = handler(10, TestResource.IRON);
        TestHandler destination = handler(64, TestResource.IRON);

        var result = transfer(source, destination, CargoOperation.UNLOAD,
                resource -> true, resource -> true);

        assertEquals(0, result.totalMoved());
        assertEquals(10, source.getAmountAsInt(0));
        assertEquals(64, destination.getAmountAsInt(0));
    }

    @Test
    void filterLeavesNonMatchingItemsInSource() {
        TestHandler source = new TestHandler(2);
        source.set(0, TestResource.IRON, 8);
        source.set(1, TestResource.COAL, 6);
        TestHandler destination = new TestHandler(2);
        transfer(source, destination, CargoOperation.UNLOAD,
                resource -> true, resource -> resource == TestResource.COAL);
        assertEquals(8, source.getAmountAsInt(0));
        assertEquals(0, source.getAmountAsInt(1));
        assertEquals(6, destination.getAmountAsInt(0));
    }

    @Test
    void invalidDestinationSlotsRejectItemsWithoutLoss() {
        TestHandler source = handler(7, TestResource.IRON);
        TestHandler destination = new TestHandler(1);
        destination.acceptsItems = false;

        var result = transfer(source, destination, CargoOperation.UNLOAD,
                resource -> true, resource -> true);

        assertEquals(0, result.totalMoved());
        assertEquals(7, source.getAmountAsInt(0));
        assertEquals(0, destination.getAmountAsInt(0));
    }

    @Test
    void unloadThenLoadUsesCurrentStationContentsAfterUnloading() {
        TestHandler vehicle = handler(10, TestResource.COAL);
        TestHandler station = new TestHandler(1);
        var result = transfer(vehicle, station, CargoOperation.UNLOAD_THEN_LOAD,
                resource -> true, resource -> true);
        assertEquals(10, result.unloaded());
        assertEquals(10, result.loaded());
        assertEquals(10, vehicle.getAmountAsInt(0));
        assertEquals(0, station.getAmountAsInt(0));
    }

    @Test
    void unloadThenLoadDoesNotRestrictLoadingToStationEntryContents() {
        TestHandler vehicle = handler(10, TestResource.COAL);
        TestHandler station = handler(5, TestResource.COAL);
        var result = transfer(vehicle, station, CargoOperation.UNLOAD_THEN_LOAD,
                resource -> true, resource -> true);
        assertEquals(10, result.unloaded());
        assertEquals(15, result.loaded());
        assertEquals(15, vehicle.getAmountAsInt(0));
        assertEquals(0, station.getAmountAsInt(0));
    }

    @Test
    void unloadThenLoadUsesSeparateStationsForEachDirection() {
        TestHandler vehicle = handler(10, TestResource.COAL);
        TestHandler unloadStation = new TestHandler(1);
        TestHandler loadStation = handler(6, TestResource.IRON);

        var result = CargoTransferService.transferResources(
                vehicle, java.util.Optional.of(loadStation), java.util.Optional.of(unloadStation),
                CargoOperation.UNLOAD_THEN_LOAD, resource -> true, resource -> true);

        assertEquals(10, result.unloaded());
        assertEquals(6, result.loaded());
        assertEquals(6, vehicle.getAmountAsInt(0));
        assertEquals(TestResource.IRON, vehicle.getResource(0));
        assertEquals(10, unloadStation.getAmountAsInt(0));
        assertEquals(0, loadStation.getAmountAsInt(0));
    }

    @Test
    void timedTransferMovesNoMoreThanOneStackPerStep() {
        TestHandler source = new TestHandler(2);
        source.set(0, TestResource.IRON, 64);
        source.set(1, TestResource.IRON, 64);
        TestHandler destination = new TestHandler(2);

        assertEquals(64, CargoTransferService.transferOneStack(source, destination, resource -> true));
        assertEquals(0, source.getAmountAsInt(0));
        assertEquals(64, source.getAmountAsInt(1));
        assertEquals(64, destination.getAmountAsInt(0));
        assertEquals(0, destination.getAmountAsInt(1));

        assertEquals(64, CargoTransferService.transferOneStack(source, destination, resource -> true));
        assertEquals(0, source.getAmountAsInt(1));
        assertEquals(64, destination.getAmountAsInt(1));
    }

    @Test
    void timedUnloadThenLoadUsesCurrentStationContents() {
        TestHandler vehicle = handler(10, TestResource.COAL);
        TestHandler station = handler(5, TestResource.COAL);

        assertEquals(10, CargoTransferService.transferOneStack(vehicle, station, resource -> true));
        assertEquals(15, CargoTransferService.transferOneStack(station, vehicle, resource -> true));

        assertEquals(15, vehicle.getAmountAsInt(0));
        assertEquals(0, station.getAmountAsInt(0));
    }

    private static CargoTransferService.TransferResult transfer(TestHandler vehicle, TestHandler station,
            CargoOperation operation, Predicate<TestResource> loadFilter, Predicate<TestResource> unloadFilter) {
        return CargoTransferService.transferResources(vehicle, station, operation, loadFilter, unloadFilter);
    }

    private static TestHandler handler(int amount, TestResource resource) {
        TestHandler handler = new TestHandler(1);
        handler.set(0, resource, amount);
        return handler;
    }

    private enum TestResource implements Resource {
        EMPTY, IRON, COAL;

        @Override
        public boolean isEmpty() { return this == EMPTY; }
    }

    private static final class TestHandler implements ResourceHandler<TestResource> {
        private final TestResource[] resources;
        private final int[] amounts;
        private boolean acceptsItems = true;
        private final SnapshotJournal<State> journal = new SnapshotJournal<>() {
            @Override protected State createSnapshot() { return new State(resources.clone(), amounts.clone()); }
            @Override protected void revertToSnapshot(State snapshot) {
                System.arraycopy(snapshot.resources(), 0, resources, 0, resources.length);
                System.arraycopy(snapshot.amounts(), 0, amounts, 0, amounts.length);
            }
        };

        TestHandler(int slots) {
            resources = new TestResource[slots];
            Arrays.fill(resources, TestResource.EMPTY);
            amounts = new int[slots];
        }

        void set(int slot, TestResource resource, int amount) {
            resources[slot] = amount == 0 ? TestResource.EMPTY : resource;
            amounts[slot] = amount;
        }

        @Override public int size() { return resources.length; }
        @Override public TestResource getResource(int index) { return resources[index]; }
        @Override public long getAmountAsLong(int index) { return amounts[index]; }
        @Override public long getCapacityAsLong(int index, TestResource resource) { return 64; }
        @Override public boolean isValid(int index, TestResource resource) {
            return acceptsItems && !resource.isEmpty();
        }

        @Override
        public int insert(int index, TestResource resource, int amount, TransactionContext transaction) {
            if (!isValid(index, resource) || (!resources[index].isEmpty() && resources[index] != resource)) return 0;
            int inserted = Math.min(amount, 64 - amounts[index]);
            if (inserted > 0) {
                journal.updateSnapshots(transaction);
                resources[index] = resource;
                amounts[index] += inserted;
            }
            return inserted;
        }

        @Override
        public int extract(int index, TestResource resource, int amount, TransactionContext transaction) {
            if (resources[index] != resource) return 0;
            int extracted = Math.min(amount, amounts[index]);
            if (extracted > 0) {
                journal.updateSnapshots(transaction);
                amounts[index] -= extracted;
                if (amounts[index] == 0) resources[index] = TestResource.EMPTY;
            }
            return extracted;
        }

        private record State(TestResource[] resources, int[] amounts) {}
    }
}
