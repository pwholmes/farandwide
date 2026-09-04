package com.lastcallsoftware.farandwide;

import org.slf4j.Logger;

import com.lastcallsoftware.farandwide.route.network.RouteNetwork;
import com.lastcallsoftware.farandwide.route.persistence.FarAndWideSavedData;
import com.lastcallsoftware.farandwide.route.persistence.FarAndWideAttachments;
import com.lastcallsoftware.farandwide.route.server.ServerRouteTraversalController;
import com.lastcallsoftware.farandwide.route.server.ServerMountTransitionController;
import com.lastcallsoftware.farandwide.route.server.PlayerDeathRouteController;
import com.lastcallsoftware.farandwide.vehicle.server.VehicleChunkLoadingManager;
import com.lastcallsoftware.farandwide.vehicle.server.BoatNameTagController;
import com.mojang.logging.LogUtils;

// import net.minecraft.core.registries.BuiltInRegistries;
//import net.minecraft.core.registries.Registries;
//import net.minecraft.network.chat.Component;
// import net.minecraft.world.food.FoodProperties;
// import net.minecraft.world.item.BlockItem;
//import net.minecraft.world.item.CreativeModeTab;
// import net.minecraft.world.item.CreativeModeTabs;
// import net.minecraft.world.item.Item;
// import net.minecraft.world.level.block.Block;
// import net.minecraft.world.level.block.Blocks;
//import net.minecraft.world.level.block.state.BlockBehaviour;
// import net.minecraft.world.level.material.MapColor;
//import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
// import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
// import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
// import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
// import net.neoforged.neoforge.registries.DeferredBlock;
//import net.neoforged.neoforge.registries.DeferredHolder;
// import net.neoforged.neoforge.registries.DeferredItem;
// import net.neoforged.neoforge.registries.DeferredRegister;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(FarAndWide.MODID)
public class FarAndWide {
    // Define mod id in a common place for everything to reference
    public static final String MODID = Constants.MOD_ID;
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    // Demo: Create Deferred Registers for example blocks and items.
    // public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    // public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "farandwide" namespace
    //public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Creates a new Block with the id "farandwide:example_block", combining the namespace and path
    // public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock(
    //         "example_block", p -> p.mapColor(MapColor.STONE));
    // Creates a new BlockItem with the id "farandwide:example_block", combining the namespace and path
    // public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(
    //         "example_block", EXAMPLE_BLOCK);

    // Creates a new food item with the id "farandwide:example_id", nutrition 1 and saturation 2
    // public static final DeferredItem<Item> EXAMPLE_ITEM = ITEMS.registerSimpleItem(
    //         "example_item", p -> p.food(new FoodProperties.Builder()
    //                 .alwaysEdible().nutrition(1).saturationModifier(2f).build()));

    // Creates a creative tab with the id "farandwide:example_tab" for the example item, that is placed after the combat tab
    // public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
    //         .title(Component.translatable("itemGroup.farandwide")) //The language key for the title of your CreativeModeTab
    //         .withTabsBefore(CreativeModeTabs.COMBAT)
    //         .icon(() -> EXAMPLE_ITEM.get().getDefaultInstance())
    //         .displayItems((parameters, output) -> {
    //             output.accept(EXAMPLE_ITEM.get());// Add the example item to the tab. For your own tabs, this method is preferred over the event
    //         }).build());

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public FarAndWide(IEventBus modEventBus, ModContainer modContainer) {
        // Demo: Register the sample common setup method.
        // modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(RouteNetwork::registerPayloads);

        // Register the Deferred Register to the mod event bus so blocks get registered
        // BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        // ITEMS.register(modEventBus);
        FarAndWideAttachments.ATTACHMENT_TYPES.register(modEventBus);
        VehicleChunkLoadingManager.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SERVER_SPEC);
        // Register the Deferred Register to the mod event bus so tabs get registered
        //CREATIVE_MODE_TABS.register(modEventBus);

        ServerRouteTraversalController.register();
        ServerMountTransitionController.register();
        PlayerDeathRouteController.register();
        BoatNameTagController.register();

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (FarandWide) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        // modEventBus.addListener(this::addCreative);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        // A shared client-and-server preference would use its own COMMON spec here.
    }

    // Demo common setup retained for reference.
    // private void commonSetup(FMLCommonSetupEvent event) {
    //     LOGGER.info("HELLO FROM COMMON SETUP");
    //
    //     if (Config.LOG_DIRT_BLOCK.getAsBoolean()) {
    //         LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
    //     }
    //
    //     LOGGER.info("{}{}", Config.MAGIC_NUMBER_INTRODUCTION.get(), Config.MAGIC_NUMBER.getAsInt());
    //     Config.ITEM_STRINGS.get().forEach(item -> LOGGER.info("ITEM >> {}", item));
    // }

    // Add the example block item to the building blocks tab
    // private void addCreative(BuildCreativeModeTabContentsEvent event) {
    //     if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
    //         event.accept(EXAMPLE_BLOCK_ITEM);
    //     }
    // }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        FarAndWideSavedData data = FarAndWideSavedData.get(event.getServer());
        LOGGER.info("Loaded Far and Wide route data ({} routes, next route ID {})",
                data.getRoutes().size(), data.getNextRouteId());
        VehicleChunkLoadingManager.logStatus();
    }
}
