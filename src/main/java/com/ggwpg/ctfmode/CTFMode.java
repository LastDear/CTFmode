package com.ggwpg.ctfmode;

import com.ggwpg.ctfmode.command.CTFModeCommands;
import com.ggwpg.ctfmode.event.ControlPointEvents;
import com.ggwpg.ctfmode.event.ControlPointTickHandler;
import com.ggwpg.ctfmode.registry.ModBlocks;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(CTFMode.MODID)
public class CTFMode {
    public static final String MODID = "ctfmode";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CTFMode(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.register(modEventBus);
        NeoForge.EVENT_BUS.register(ControlPointEvents.class);
        NeoForge.EVENT_BUS.register(ControlPointTickHandler.class);
        NeoForge.EVENT_BUS.register(CTFModeCommands.class);

        LOGGER.info("CTF Mode loaded");
    }
}
