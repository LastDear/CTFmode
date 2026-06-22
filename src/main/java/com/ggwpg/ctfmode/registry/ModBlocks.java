package com.ggwpg.ctfmode.registry;

import com.ggwpg.ctfmode.CTFMode;
import com.ggwpg.ctfmode.block.ControlPointBlock;
import com.ggwpg.ctfmode.item.AdminOnlyBlockItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CTFMode.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CTFMode.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CTFMode.MODID);

    public static final DeferredBlock<Block> CONTROL_POINT = BLOCKS.register("control_point", () -> new ControlPointBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(-1.0F, 3600000.0F)
                    .sound(SoundType.METAL)
    ));

    public static final DeferredItem<BlockItem> CONTROL_POINT_ITEM = ITEMS.register("control_point", () -> new AdminOnlyBlockItem(
            CONTROL_POINT.get(),
            new Item.Properties()
    ));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CTFMODE_TAB = CREATIVE_MODE_TABS.register("ctfmode", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.ctfmode"))
            .withTabsBefore(CreativeModeTabs.FUNCTIONAL_BLOCKS)
            .icon(() -> CONTROL_POINT_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> output.accept(CONTROL_POINT_ITEM.get()))
            .build());

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        modEventBus.addListener(ModBlocks::addToCreativeTab);
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(CONTROL_POINT_ITEM);
        }
    }
}
