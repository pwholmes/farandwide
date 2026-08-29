package com.lastcallsoftware.farandwide.vehicle.client;

import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.route.RouteAssignment;
import com.lastcallsoftware.farandwide.route.Waypoint;
import com.lastcallsoftware.farandwide.route.client.RouteManager;
import com.lastcallsoftware.farandwide.vehicle.VehicleActuator;
import com.lastcallsoftware.farandwide.vehicle.HorseControls;
import com.lastcallsoftware.farandwide.vehicle.navigation.DirectWaypointNavigator;
import com.lastcallsoftware.farandwide.vehicle.navigation.NavigationIntent;
import com.lastcallsoftware.farandwide.vehicle.navigation.VehicleNavigator;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Applies navigation input for vehicles whose vanilla physics is client-authoritative. */
public final class VehicleController {
    private static final List<VehicleNavigator> NAVIGATORS = List.of(new DirectWaypointNavigator());
    private static final List<VehicleActuator> ACTUATORS = List.of(
            new ClientBoatActuator(), new ClientHorseActuator(), new ClientPlayerActuator());
    private static int autoNavigatedEntityId = Integer.MIN_VALUE;

    private VehicleController() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(VehicleController::onClientTick);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        installPlayerInputWrapper(minecraft.player);

        Entity ridden = minecraft.player.getVehicle() == null ? minecraft.player : minecraft.player.getVehicle();

        RouteAssignment assignment = RouteManager.getAssignment(ridden.getId());
        VehicleActuator actuator = ACTUATORS.stream()
                .filter(candidate -> candidate.supports(ridden) && candidate.isAuthoritative(ridden))
                .findFirst().orElse(null);
        if (actuator == null) {
            return;
        }

        if (assignment == null || !assignment.isActive()) {
            if (autoNavigatedEntityId == ridden.getId()) {
                actuator.stop(ridden);
                autoNavigatedEntityId = Integer.MIN_VALUE;
            }
            return;
        }

        Waypoint target = RouteManager.getTargetWaypoint(assignment);
        autoNavigatedEntityId = ridden.getId();

        if (target == null || !target.dimension().equals(ridden.level().dimension().identifier())) {
            actuator.stop(ridden);
            return;
        }

        // Ridden vehicle physics are client-authoritative. Stop locally as soon as
        // the waypoint is reached while the server processes arrival and advances
        // the assignment; otherwise stale steering circles around the target.
        if (target.hasArrived(ridden.position())) {
            actuator.stop(ridden);
            return;
        }

        VehicleNavigator navigator = NAVIGATORS.stream().filter(candidate -> candidate.supports(ridden))
                .findFirst().orElse(null);
        if (navigator != null) {
            NavigationIntent intent = navigator.navigate(ridden, target);
            actuator.apply(ridden, intent);
        }
    }

    private static void installPlayerInputWrapper(LocalPlayer player) {
        if (!(player.input instanceof AutoNavigationInput)) {
            player.input = new AutoNavigationInput(player, player.input);
        }
    }

    /** Preserves normal input and adds forward movement only for active player navigation. */
    private static final class AutoNavigationInput extends ClientInput {
        private final LocalPlayer player;
        private final ClientInput delegate;

        private AutoNavigationInput(LocalPlayer player, ClientInput delegate) {
            this.player = player;
            this.delegate = delegate;
        }

        @Override
        public void tick() {
            delegate.tick();
            keyPresses = delegate.keyPresses;
            moveVector = delegate.getMoveVector();

            if (player.getVehicle() != null) {
                return;
            }
            RouteAssignment assignment = RouteManager.getAssignment(player.getId());
            Waypoint target = assignment != null && assignment.isActive()
                    ? RouteManager.getTargetWaypoint(assignment)
                    : null;
            if (target == null || !target.dimension().equals(player.level().dimension().identifier())) {
                return;
            }

            NavigationIntent intent = NavigationIntent.toward(player.position(), target.position());
            HorseControls.Input navigationInput = HorseControls.from(
                    player.getYRot(), intent, Constants.Vehicles.PLAYER_MOVES_WHILE_TURNING);
            boolean moveForward = navigationInput.forward() > 0.0F;
            moveVector = moveForward ? new Vec2(0.0F, 1.0F) : Vec2.ZERO;
            keyPresses = new Input(
                    moveForward, false, false, false,
                    keyPresses.jump(), keyPresses.shift(), keyPresses.sprint());
        }
    }
}
