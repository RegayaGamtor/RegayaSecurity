package com.regayagamtor.regayasecurity.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Membaca dan memvalidasi config.yml. Nilai tidak valid diganti default + peringatan. */
public final class ConfigManager {

    private final JavaPlugin plugin;

    private double maxDistance;
    private double maxDistanceSquared;
    private int requestTimeoutSeconds;
    private int forceSearchDurationSeconds;
    private int forceHitTimeoutSeconds;
    private int syncIntervalTicks;
    private int sessionCheckIntervalTicks;
    private boolean cancelOnWorldChange;
    private boolean cancelOnDisconnect;
    private boolean cancelOnDeath;
    private boolean requireForceTargetWhitelist;
    private boolean logSearchActions;
    private int requestCooldownSeconds;
    private int maxConflicts;
    private boolean strictArmorSlots;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        maxDistance = readDouble(c, "max-distance", 3.0, 0.5, 32.0);
        maxDistanceSquared = maxDistance * maxDistance;
        requestTimeoutSeconds = readInt(c, "request-timeout-seconds", 30, 5, 300);
        forceSearchDurationSeconds = readInt(c, "force-search-duration-seconds", 5, 1, 60);
        forceHitTimeoutSeconds = readInt(c, "force-hit-timeout-seconds", 15, 3, 120);
        syncIntervalTicks = readInt(c, "sync-interval-ticks", 2, 1, 20);
        sessionCheckIntervalTicks = readInt(c, "session-check-interval-ticks", 5, 1, 20);
        requestCooldownSeconds = readInt(c, "request-cooldown-seconds", 10, 0, 600);
        maxConflicts = readInt(c, "max-conflicts", 10, 1, 100);

        cancelOnWorldChange = readForcedTrue(c, "cancel-on-world-change");
        cancelOnDisconnect = readForcedTrue(c, "cancel-on-disconnect");
        cancelOnDeath = readForcedTrue(c, "cancel-on-death");

        requireForceTargetWhitelist = readBoolean(c, "require-force-target-whitelist", true);
        logSearchActions = readBoolean(c, "log-search-actions", true);
        strictArmorSlots = readBoolean(c, "strict-armor-slots", true);

        if (!requireForceTargetWhitelist) {
            plugin.getLogger().warning("require-force-target-whitelist=false: SEMUA pemain dapat digeledah paksa "
                    + "oleh pengguna berizin. Pastikan ini memang disengaja.");
        }
    }

    private double readDouble(FileConfiguration c, String key, double def, double min, double max) {
        Object raw = c.get(key);
        if (raw instanceof Number number) {
            double value = number.doubleValue();
            if (!Double.isNaN(value) && !Double.isInfinite(value) && value >= min && value <= max) {
                return value;
            }
        }
        warn(key, raw, def, "angka " + min + " sampai " + max);
        return def;
    }

    private int readInt(FileConfiguration c, String key, int def, int min, int max) {
        Object raw = c.get(key);
        if (raw instanceof Number number) {
            double value = number.doubleValue();
            if (value == Math.rint(value) && value >= min && value <= max) {
                return (int) value;
            }
        }
        warn(key, raw, def, "bilangan bulat " + min + " sampai " + max);
        return def;
    }

    private boolean readBoolean(FileConfiguration c, String key, boolean def) {
        Object raw = c.get(key);
        if (raw instanceof Boolean value) {
            return value;
        }
        warn(key, raw, def, "true atau false");
        return def;
    }

    /** Pengaturan keselamatan: false diabaikan karena sesi tidak aman dilanjutkan. */
    private boolean readForcedTrue(FileConfiguration c, String key) {
        boolean value = readBoolean(c, key, true);
        if (!value) {
            plugin.getLogger().warning(key + "=false diabaikan (dipaksa true): sesi penggeledahan tidak aman "
                    + "dilanjutkan setelah kondisi tersebut.");
        }
        return true;
    }

    private void warn(String key, Object raw, Object def, String expected) {
        plugin.getLogger().warning("config.yml: nilai '" + key + "' tidak valid (" + raw + "), seharusnya "
                + expected + ". Memakai default: " + def);
    }

    public double maxDistance() {
        return maxDistance;
    }

    public double maxDistanceSquared() {
        return maxDistanceSquared;
    }

    public int requestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    public int forceSearchDurationSeconds() {
        return forceSearchDurationSeconds;
    }

    public int forceHitTimeoutSeconds() {
        return forceHitTimeoutSeconds;
    }

    public int syncIntervalTicks() {
        return syncIntervalTicks;
    }

    public int sessionCheckIntervalTicks() {
        return sessionCheckIntervalTicks;
    }

    public boolean cancelOnWorldChange() {
        return cancelOnWorldChange;
    }

    public boolean cancelOnDisconnect() {
        return cancelOnDisconnect;
    }

    public boolean cancelOnDeath() {
        return cancelOnDeath;
    }

    public boolean requireForceTargetWhitelist() {
        return requireForceTargetWhitelist;
    }

    public boolean logSearchActions() {
        return logSearchActions;
    }

    public int requestCooldownSeconds() {
        return requestCooldownSeconds;
    }

    public int maxConflicts() {
        return maxConflicts;
    }

    public boolean strictArmorSlots() {
        return strictArmorSlots;
    }
}
