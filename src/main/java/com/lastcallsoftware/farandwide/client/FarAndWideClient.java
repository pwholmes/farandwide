package com.lastcallsoftware.farandwide.client;

import com.lastcallsoftware.farandwide.FarAndWide;
import com.lastcallsoftware.farandwide.command.client.FarAndWideKeyBindings;
import com.lastcallsoftware.farandwide.route.client.RouteManager;
import com.lastcallsoftware.farandwide.route.client.WaypointRenderer;
import com.lastcallsoftware.farandwide.vehicle.client.VehicleController;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.eclipse.jdt.annotation.NonNull;
import com.lastcallsoftware.farandwide.route.network.payload.RouteSnapshotPayload;
import com.lastcallsoftware.farandwide.route.network.payload.AssignmentSnapshotPayload;
import com.lastcallsoftware.farandwide.route.network.payload.RouteOperationResultPayload;
import com.lastcallsoftware.farandwide.route.network.payload.VehicleAssignmentsSnapshotPayload;

/*
 * Physical-client bootstrap. This class is the only top-level place that wires
 * client event registration and incoming snapshot handlers. Common bootstrap code
 * must never reference it or any of the feature-local client packages, otherwise a
 * dedicated server can fail while loading classes that do not exist on that side.
 */
@Mod(value = FarAndWide.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = FarAndWide.MODID, value = Dist.CLIENT)
public class FarAndWideClient {
    public FarAndWideClient(IEventBus modEventBus, ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.addListener(FarAndWideClient::onClientLoggedIn);
        NeoForge.EVENT_BUS.addListener(FarAndWideClient::onClientLoggingOut);
        FarAndWideKeyBindings.register(modEventBus);
        PlayerPositionHud.register();
        WaypointRenderer.register();
        VehicleController.register();
    }

    private static void onClientLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        RouteManager.onClientLoggedIn();
    }

    private static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        RouteManager.clearClientState();
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        FarAndWide.LOGGER.info("HELLO FROM CLIENT SETUP");
        FarAndWide.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    @SubscribeEvent
    static void registerClientPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(RouteSnapshotPayload.TYPE,
                (payload, context) -> RouteManager.replaceRoutesFromServer(
                        payload.routes().stream()
                                .map((RouteSnapshotPayload.@NonNull RouteSnapshot route) -> route.toRoute()).toList(),
                        payload.selectedRouteId()));
        event.register(AssignmentSnapshotPayload.TYPE,
                (payload, context) -> RouteManager.replaceAssignmentFromServer(
                        payload.entityId(), payload.stableAssigneeId(), payload.assignment()));
        event.register(VehicleAssignmentsSnapshotPayload.TYPE,
                (payload, context) -> RouteManager.replaceVehicleAssignmentsFromServer(payload.assignments()));
        event.register(RouteOperationResultPayload.TYPE,
                (payload, context) -> RouteManager.handleOperationResult(payload.result()));
    }
}
