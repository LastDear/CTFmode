package com.ggwpg.ctfmode.session;

import com.ggwpg.ctfmode.data.ControlPointPosition;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;

public class ControlPointSession {
    private final SessionKey key;
    private final ControlPointPosition controlPoint;
    private final long endsAtMillis;
    private final ItemStack rewardStack;
    private final Map<UUID, Integer> playerSeconds = new HashMap<>();
    private UUID previewEntityId;

    public ControlPointSession(SessionKey key, ControlPointPosition controlPoint, long endsAtMillis, ItemStack rewardStack) {
        this.key = key;
        this.controlPoint = controlPoint;
        this.endsAtMillis = endsAtMillis;
        this.rewardStack = rewardStack.copy();
    }

    public SessionKey key() {
        return key;
    }

    public ControlPointPosition controlPoint() {
        return controlPoint;
    }

    public long endsAtMillis() {
        return endsAtMillis;
    }

    public ItemStack rewardStack() {
        return rewardStack.copy();
    }

    public UUID previewEntityId() {
        return previewEntityId;
    }

    public void setPreviewEntityId(UUID previewEntityId) {
        this.previewEntityId = previewEntityId;
    }

    public void addSecond(UUID playerId) {
        playerSeconds.merge(playerId, 1, Integer::sum);
    }

    public int secondsFor(UUID playerId) {
        return playerSeconds.getOrDefault(playerId, 0);
    }

    public Optional<Map.Entry<UUID, Integer>> winner() {
        return playerSeconds.entrySet().stream().max(Map.Entry.comparingByValue());
    }

    public Map<UUID, Integer> playerSeconds() {
        return Collections.unmodifiableMap(playerSeconds);
    }

    public record SessionKey(LocalDate date, String scheduleId, ControlPointPosition controlPoint) {
    }
}
