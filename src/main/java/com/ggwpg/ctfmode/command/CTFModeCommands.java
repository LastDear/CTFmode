package com.ggwpg.ctfmode.command;

import com.ggwpg.ctfmode.data.ControlPointPosition;
import com.ggwpg.ctfmode.data.ControlPointSavedData;
import com.ggwpg.ctfmode.event.ControlPointTickHandler;
import com.ggwpg.ctfmode.session.ControlPointSession;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class CTFModeCommands {
    private CTFModeCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ctfmode")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("reload")
                        .executes(context -> reload(context.getSource())))
                .then(Commands.literal("start")
                        .executes(context -> start(context.getSource())))
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource()))));
    }

    private static int reload(CommandSourceStack source) {
        ControlPointTickHandler.reloadConfig();
        source.sendSuccess(() -> Component.literal("CTF Mode config reloaded."), false);
        return 1;
    }

    private static int start(CommandSourceStack source) {
        int started = ControlPointTickHandler.startManualEvents(source.getServer());
        source.sendSuccess(() -> Component.literal("Started " + started + " control point event(s)."), false);
        return started;
    }

    private static int list(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        int savedCount = 0;

        source.sendSuccess(() -> Component.literal("Saved control points:"), false);
        for (ControlPointPosition controlPoint : ControlPointSavedData.get(server.overworld()).getControlPoints()) {
            savedCount++;
            source.sendSuccess(() -> Component.literal("- " + controlPoint.dimension().location() + " " + controlPoint.pos().toShortString()), false);
        }

        source.sendSuccess(() -> Component.literal("Active sessions: " + ControlPointTickHandler.activeSessions().size()), false);
        for (ControlPointSession session : ControlPointTickHandler.activeSessions()) {
            long remainingSeconds = Math.max(0L, (session.endsAtMillis() - System.currentTimeMillis()) / 1000L);
            source.sendSuccess(() -> Component.literal("- " + session.controlPoint().dimension().location() + " " + session.controlPoint().pos().toShortString() + ", " + remainingSeconds + "s left"), false);
        }

        return savedCount;
    }
}
