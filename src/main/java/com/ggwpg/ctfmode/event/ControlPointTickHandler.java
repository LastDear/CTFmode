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
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class ControlPointTickHandler {
    private static final String LEADERBOARD_OBJECTIVE = "ctfmode_event";
    private static final Map<SessionKey, ControlPointSession> ACTIVE_SESSIONS = new HashMap<>();
    private static final Set<SessionKey> COMPLETED_SESSIONS = new HashSet<>();
    private static final Set<String> SENT_WARNINGS = new HashSet<>();
    private static final int[] WARNING_MINUTES = {30, 15, 5, 1};
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
            sendScheduleWarnings(server, today, window, now, start);

            for (ControlPointPosition controlPoint : ControlPointSavedData.get(server.overworld()).getControlPoints()) {
                SessionKey key = new SessionKey(today, window.id(), controlPoint);

                if (active && !COMPLETED_SESSIONS.contains(key)) {
                    ACTIVE_SESSIONS.computeIfAbsent(key, ignored -> {
                        broadcast(server, "Control point event has started!");
                        CTFMode.LOGGER.info("Control point event started: {} at {} in {}", window.id(), controlPoint.pos(), controlPoint.dimension().location());
                        return new ControlPointSession(key, controlPoint, System.currentTimeMillis() + secondsUntilEnd(now, end) * 1000L, randomRewardStack(server));
                    });
                }
            }
        }

        ACTIVE_SESSIONS.entrySet().removeIf(entry -> {
            if (System.currentTimeMillis() >= entry.getValue().endsAtMillis()) {
                finishSession(server, entry.getValue());
                removePreviewEntity(server, entry.getValue());
                COMPLETED_SESSIONS.add(entry.getKey());
                return true;
            }

            tickSession(server, eventConfig, entry.getValue());
            return false;
        });

        updateLeaderboard(server);
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
                    System.currentTimeMillis() + eventConfig.schedule().durationSeconds() * 1000L,
                    randomRewardStack(server)
            ));

            if (previous == null) {
                started++;
                broadcast(server, "Control point event has started!");
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
        SENT_WARNINGS.clear();
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

        tickVisuals(level, eventConfig, session);

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

        ItemStack stack = session.rewardStack();
        if (stack.isEmpty()) {
            CTFMode.LOGGER.warn("Control point reward skipped because rewards.json is empty or invalid");
            return;
        }

        if (!winner.addItem(stack)) {
            winner.drop(stack, false);
        }

        winner.displayClientMessage(Component.translatable("message.ctfmode.winner", seconds), false);
        CTFMode.LOGGER.info("{} won control point event at {} with {} seconds and received {}", winner.getGameProfile().getName(), session.controlPoint().pos(), seconds, stack);
    }

    private static ItemStack randomRewardStack(MinecraftServer server) {
        RewardEntry reward = randomReward();
        if (reward == null) {
            CTFMode.LOGGER.warn("Control point reward skipped because rewards.json is empty");
            return ItemStack.EMPTY;
        }

        return parseRewardStack(server, reward);
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

    private static void tickVisuals(ServerLevel level, ControlPointEventConfig eventConfig, ControlPointSession session) {
        spawnParticleCircle(level, session, eventConfig.schedule().radius());
        tickPreviewItem(level, session);
    }

    private static void spawnParticleCircle(ServerLevel level, ControlPointSession session, double radius) {
        int points = Math.max(24, (int) Math.ceil(radius * 8.0D));
        double centerX = session.controlPoint().pos().getX() + 0.5D;
        double centerY = session.controlPoint().pos().getY() + 0.15D;
        double centerZ = session.controlPoint().pos().getZ() + 0.5D;

        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0D * i) / points;
            double x = centerX + Math.cos(angle) * radius;
            double z = centerZ + Math.sin(angle) * radius;
            level.sendParticles(ParticleTypes.END_ROD, x, centerY, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private static void tickPreviewItem(ServerLevel level, ControlPointSession session) {
        if (session.rewardStack().isEmpty()) {
            return;
        }

        double x = session.controlPoint().pos().getX() + 0.5D;
        double y = session.controlPoint().pos().getY() + 1.45D;
        double z = session.controlPoint().pos().getZ() + 0.5D;
        Entity entity = session.previewEntityId() == null ? null : level.getEntity(session.previewEntityId());
        ItemEntity itemEntity = entity instanceof ItemEntity existingItem && existingItem.isAlive() ? existingItem : null;

        if (itemEntity == null) {
            itemEntity = new ItemEntity(level, x, y, z, session.rewardStack());
            itemEntity.setNoGravity(true);
            itemEntity.setNeverPickUp();
            itemEntity.setUnlimitedLifetime();
            itemEntity.setDeltaMovement(0.0D, 0.0D, 0.0D);
            level.addFreshEntity(itemEntity);
            session.setPreviewEntityId(itemEntity.getUUID());
        }

        itemEntity.setPos(x, y + Math.sin(level.getGameTime() / 10.0D) * 0.12D, z);
        itemEntity.setDeltaMovement(0.0D, 0.0D, 0.0D);
        itemEntity.setYRot((float) ((level.getGameTime() * 8) % 360));
    }

    private static void removePreviewEntity(MinecraftServer server, ControlPointSession session) {
        ServerLevel level = server.getLevel(session.controlPoint().dimension());
        if (level == null || session.previewEntityId() == null) {
            return;
        }

        Entity entity = level.getEntity(session.previewEntityId());
        if (entity != null) {
            entity.discard();
        }
    }

    private static void updateLeaderboard(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(LEADERBOARD_OBJECTIVE);

        if (ACTIVE_SESSIONS.isEmpty()) {
            if (objective != null) {
                scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, null);
                scoreboard.removeObjective(objective);
            }
            return;
        }

        if (objective == null) {
            objective = scoreboard.addObjective(
                    LEADERBOARD_OBJECTIVE,
                    ObjectiveCriteria.DUMMY,
                    Component.literal("CTF Time"),
                    ObjectiveCriteria.RenderType.INTEGER,
                    false,
                    null
            );
        }

        Objective leaderboard = objective;
        scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, leaderboard);
        new ArrayList<>(scoreboard.getTrackedPlayers()).forEach(player -> scoreboard.resetSinglePlayerScore(player, leaderboard));

        Map<UUID, Integer> totals = new HashMap<>();
        for (ControlPointSession session : ACTIVE_SESSIONS.values()) {
            session.playerSeconds().forEach((playerId, seconds) -> totals.merge(playerId, seconds, Integer::sum));
        }

        totals.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(15)
                .forEach(entry -> {
                    String name = playerName(server, entry.getKey());
                    scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(name), leaderboard).set(entry.getValue());
                });
    }

    private static String playerName(MinecraftServer server, UUID playerId) {
        ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(playerId);
        if (onlinePlayer != null) {
            return onlinePlayer.getGameProfile().getName();
        }

        return playerId.toString().substring(0, 8);
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

    private static void sendScheduleWarnings(MinecraftServer server, LocalDate today, ScheduleWindow window, LocalTime now, LocalTime start) {
        long secondsUntilStart = secondsUntilStart(now, start);
        for (int minutes : WARNING_MINUTES) {
            long targetSeconds = minutes * 60L;
            if (secondsUntilStart <= targetSeconds && secondsUntilStart > targetSeconds - 2L) {
                String key = today + "|" + window.id() + "|" + minutes;
                if (SENT_WARNINGS.add(key)) {
                    broadcast(server, "Control point event starts in " + minutes + " minute(s)!");
                }
            }
        }
    }

    private static long secondsUntilStart(LocalTime now, LocalTime start) {
        long seconds = now.until(start, ChronoUnit.SECONDS);
        if (seconds < 0) {
            seconds += 24L * 60L * 60L;
        }

        return seconds;
    }

    private static void broadcast(MinecraftServer server, String message) {
        server.getPlayerList().broadcastSystemMessage(Component.literal("[CTF Mode] " + message), false);
    }

    private static ControlPointEventConfig config() {
        if (config == null) {
            config = ControlPointEventConfig.loadOrCreate();
        }

        return config;
    }
}
