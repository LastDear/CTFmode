package com.ggwpg.ctfmode.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ggwpg.ctfmode.CTFMode;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.List;
import net.neoforged.fml.loading.FMLPaths;

public final class ControlPointEventConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get().resolve(CTFMode.MODID);
    private static final Path SCHEDULE_PATH = CONFIG_DIR.resolve("schedule.json");
    private static final Path REWARDS_PATH = CONFIG_DIR.resolve("rewards.json");

    private final ScheduleConfig schedule;
    private final RewardConfig rewards;

    private ControlPointEventConfig(ScheduleConfig schedule, RewardConfig rewards) {
        this.schedule = schedule;
        this.rewards = rewards;
    }

    public static ControlPointEventConfig loadOrCreate() {
        try {
            Files.createDirectories(CONFIG_DIR);
            createDefaultFiles();
            return new ControlPointEventConfig(read(SCHEDULE_PATH, ScheduleConfig.class), read(REWARDS_PATH, RewardConfig.class));
        } catch (IOException exception) {
            CTFMode.LOGGER.error("Failed to load CTF Mode JSON config, using defaults", exception);
            return defaults();
        }
    }

    public static ControlPointEventConfig defaults() {
        return new ControlPointEventConfig(
                new ScheduleConfig(900, 8.0D, List.of(new ScheduleWindow("daily_evening", "20:00"))),
                new RewardConfig(List.of(
                        new RewardEntry("minecraft:diamond", 1),
                        new RewardEntry("minecraft:emerald", 3),
                        new RewardEntry("minecraft:golden_apple", 1),
                        new RewardEntry("minecraft:diamond_sword[enchantments={levels:{\"minecraft:sharpness\":10}}]", 1)
                ))
        );
    }

    public ScheduleConfig schedule() {
        return schedule;
    }

    public RewardConfig rewards() {
        return rewards;
    }

    private static void createDefaultFiles() throws IOException {
        if (Files.notExists(SCHEDULE_PATH)) {
            write(SCHEDULE_PATH, defaults().schedule());
        }

        if (Files.notExists(REWARDS_PATH)) {
            write(REWARDS_PATH, defaults().rewards());
        }
    }

    private static <T> T read(Path path, Class<T> type) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            return GSON.fromJson(reader, type);
        }
    }

    private static void write(Path path, Object value) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(value, writer);
        }
    }

    public record ScheduleConfig(int durationSeconds, double radius, List<ScheduleWindow> schedules) {
    }

    public record ScheduleWindow(String id, String start) {
        public LocalTime startTime() {
            return LocalTime.parse(start);
        }
    }

    public record RewardConfig(List<RewardEntry> rewards) {
    }

    public record RewardEntry(String item, int count) {
    }
}
