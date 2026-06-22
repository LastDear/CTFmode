package com.ggwpg.ctfmode.event;

import com.ggwpg.ctfmode.CTFMode;
import com.ggwpg.ctfmode.config.ControlPointEventConfig;
import com.ggwpg.ctfmode.config.ControlPointEventConfig.RewardEntry;
import com.ggwpg.ctfmode.config.ControlPointEventConfig.ScheduleWindow;
import com.ggwpg.ctfmode.data.ControlPointPosition;
import com.ggwpg.ctfmode.data.ControlPointSavedData;
import com.ggwpg.ctfmode.session.ControlPointSession;
import com.ggwpg.ctfmode.session.ControlPointSession.SessionKey;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class ControlPointTickHandler {
    private static final Map<SessionKey, ControlPointSession> ACTIVE_SESSIONS = new HashMap<>();
    private static final Set<SessionKey> COMPLETED_SESSIONS = new HashSet<>();
    private static ControlPointEventConfig config;
    private static int ticks;

    private ControlPointTickHandler() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ticks++;
        if (ticks < 20) {
            return;
        }

        ticks = 0;
        MinecraftServer server = event.getServer();
        tickSecond(server);
    }

    private static void tickSecond(MinecraftServer server) {
        ControlPointEventConfig eventConfig = config();
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        for (ScheduleWindow window : eventConfig.schedule().schedules()) {
            LocalTime start = window.startTime();
            LocalTime end = start.plusSeconds(eventConfig.schedule().durationSeconds());
            boolean active = isWithinWindow(now, start, end);

            for (ControlPointPosition controlPoint : ControlPointSavedData.get(server.overworld()).getControlPoints()) {
                SessionKey key = new SessionKey(today, window.id(), controlPoint);

                if (active && !COMPLETED_SESSIONS.contains(key)) {
                    ACTIVE_SESSIONS.computeIfAbsent(key, ignored -> {
                        CTFMode.LOGGER.info("Control point event started: {} at {} in {}", window.id(), controlPoint.pos(), controlPoint.dimension().location());
                        return new ControlPointSession(key, controlPoint, System.currentTimeMillis() + secondsUntilEnd(now, end) * 1000L);
                    });
                }
            }
        }

        ACTIVE_SESSIONS.entrySet().removeIf(entry -> {
            if (System.currentTimeMillis() >= entry.getValue().endsAtMillis()) {
                finishSession(server, entry.getValue());
                COMPLETED_SESSIONS.add(entry.getKey());
                return true;
            }

            tickSession(server, eventConfig, entry.getValue());
            return false;
        });
    }

    public static int startManualEvents(MinecraftServer server) {
        ControlPointEventConfig eventConfig = config();
        LocalDate today = LocalDate.now();
        int started = 0;

        for (ControlPointPosition controlPoint : ControlPointSavedData.get(server.overworld()).getControlPoints()) {
            if (hasActiveSession(controlPoint)) {
                continue;
            }

            SessionKey key = new SessionKey(today, "manual_" + System.currentTimeMillis(), controlPoint);
            ControlPointSession previous = ACTIVE_SESSIONS.putIfAbsent(key, new ControlPointSession(
                    key,
                    controlPoint,
                    System.currentTimeMillis() + eventConfig.schedule().durationSeconds() * 1000L
            ));

            if (previous == null) {
                started++;
                CTFMode.LOGGER.info("Manual control point event started at {} in {}", controlPoint.pos(), controlPoint.dimension().location());
            }
        }

        return started;
    }

    private static boolean hasActiveSession(ControlPointPosition controlPoint) {
        return ACTIVE_SESSIONS.values().stream().anyMatch(session -> session.controlPoint().equals(controlPoint));
    }

    public static void reloadConfig() {
        config = ControlPointEventConfig.loadOrCreate();
    }

    public static ControlPointEventConfig currentConfig() {
        return config();
    }

    public static Collection<ControlPointSession> activeSessions() {
        return Collections.unmodifiableCollection(ACTIVE_SESSIONS.values());
    }

    private static void tickSession(MinecraftServer server, ControlPointEventConfig eventConfig, ControlPointSession session) {
        ServerLevel level = server.getLevel(session.controlPoint().dimension());
        if (level == null) {
            return;
        }

        double radius = eventConfig.schedule().radius();
        double radiusSqr = radius * radius;

        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(session.controlPoint().pos()) <= radiusSqr) {
                session.addSecond(player.getUUID());
                player.displayClientMessage(Component.translatable("message.ctfmode.active_time", session.secondsFor(player.getUUID())), true);
            }
        }
    }

    private static void finishSession(MinecraftServer server, ControlPointSession session) {
        session.winner().ifPresentOrElse(
                winner -> rewardWinner(server, winner.getKey(), winner.getValue(), session),
                () -> CTFMode.LOGGER.info("Control point event ended with no participants at {}", session.controlPoint().pos())
        );
    }

    private static void rewardWinner(MinecraftServer server, UUID winnerId, int seconds, ControlPointSession session) {
        ServerPlayer winner = server.getPlayerList().getPlayer(winnerId);
        if (winner == null) {
            CTFMode.LOGGER.info("Control point winner {} is offline, reward skipped", winnerId);
            return;
        }

        RewardEntry reward = randomReward();
        if (reward == null) {
            CTFMode.LOGGER.warn("Control point reward skipped because rewards.json is empty");
            return;
        }

        ItemStack stack = parseRewardStack(server, reward);
        if (stack.isEmpty()) {
            return;
        }

        if (!winner.addItem(stack)) {
            winner.drop(stack, false);
        }

        winner.displayClientMessage(Component.translatable("message.ctfmode.winner", seconds), false);
        CTFMode.LOGGER.info("{} won control point event at {} with {} seconds and received {} x{}", winner.getGameProfile().getName(), session.controlPoint().pos(), seconds, reward.item(), reward.count());
    }

    private static ItemStack parseRewardStack(MinecraftServer server, RewardEntry reward) {
        try {
            ItemParser.ItemResult itemResult = new ItemParser(server.registryAccess()).parse(new StringReader(reward.item()));
            return new ItemStack(itemResult.item(), Math.max(1, reward.count()), itemResult.components());
        } catch (CommandSyntaxException exception) {
            CTFMode.LOGGER.warn("Invalid reward item string: {}", reward.item(), exception);
            return ItemStack.EMPTY;
        }
    }

    private static RewardEntry randomReward() {
        List<RewardEntry> rewards = config().rewards().rewards();
        if (rewards == null || rewards.isEmpty()) {
            return null;
        }

        return rewards.get(ThreadLocalRandom.current().nextInt(rewards.size()));
    }

    private static boolean isWithinWindow(LocalTime now, LocalTime start, LocalTime end) {
        if (start.equals(end)) {
            return true;
        }

        if (end.isAfter(start)) {
            return !now.isBefore(start) && now.isBefore(end);
        }

        return !now.isBefore(start) || now.isBefore(end);
    }

    private static long secondsUntilEnd(LocalTime now, LocalTime end) {
        long seconds = now.until(end, ChronoUnit.SECONDS);
        if (seconds <= 0) {
            seconds += 24L * 60L * 60L;
        }

        return Math.max(1L, seconds);
    }

    private static ControlPointEventConfig config() {
        if (config == null) {
            config = ControlPointEventConfig.loadOrCreate();
        }

        return config;
    }
}
