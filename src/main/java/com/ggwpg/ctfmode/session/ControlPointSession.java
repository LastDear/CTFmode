package com.ggwpg.ctfmode.session;

import com.ggwpg.ctfmode.data.ControlPointPosition;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ControlPointSession {
    private final SessionKey key;
    private final ControlPointPosition controlPoint;
    private final long endsAtMillis;
    private final Map<UUID, Integer> playerSeconds = new HashMap<>();

    public ControlPointSession(SessionKey key, ControlPointPosition controlPoint, long endsAtMillis) {
        this.key = key;
        this.controlPoint = controlPoint;
        this.endsAtMillis = endsAtMillis;
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

    public void addSecond(UUID playerId) {
        playerSeconds.merge(playerId, 1, Integer::sum);
    }

    public int secondsFor(UUID playerId) {
        return playerSeconds.getOrDefault(playerId, 0);
    }

    public Optional<Map.Entry<UUID, Integer>> winner() {
        return playerSeconds.entrySet().stream().max(Map.Entry.comparingByValue());
    }

    public record SessionKey(LocalDate date, String scheduleId, ControlPointPosition controlPoint) {
    }
}
