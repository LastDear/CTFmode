package com.ggwpg.ctfmode.event;

import com.ggwpg.ctfmode.data.ControlPointSavedData;
import com.ggwpg.ctfmode.registry.ModBlocks;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class ControlPointEvents {
    private ControlPointEvents() {
    }

    @SubscribeEvent
    public static void onBreakControlPoint(BlockEvent.BreakEvent event) {
        if (!event.getState().is(ModBlocks.CONTROL_POINT.get())) {
            return;
        }

        if (!event.getPlayer().hasPermissions(2)) {
            event.setCanceled(true);
            event.getPlayer().displayClientMessage(Component.translatable("message.ctfmode.admin_only"), true);
            return;
        }

        if (event.getLevel() instanceof ServerLevel level) {
            ControlPointSavedData.get(level).remove(level, event.getPos());
        }
    }
}
