package com.regayagamtor.regayasecurity.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Penyimpanan daftar pengguna penggeledahan paksa dan daftar target (data.yml).
 * Kunci selalu UUID; nama hanya label untuk memudahkan admin membaca file.
 */
public final class ForceTargetStorage {

    public record Data(Map<UUID, String> users, Map<UUID, String> targets) {
    }

    private final JavaPlugin plugin;
    private final File file;

    public ForceTargetStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public Data load() {
        Map<UUID, String> users = new LinkedHashMap<>();
        Map<UUID, String> targets = new LinkedHashMap<>();
        if (!file.exists()) {
            return new Data(users, targets);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            // Jangan menimpa file rusak dengan data kosong: pindahkan dulu sebagai cadangan.
            File backup = new File(plugin.getDataFolder(), "data.yml.rusak-" + System.currentTimeMillis());
            boolean moved = file.renameTo(backup);
            plugin.getLogger().severe("data.yml tidak dapat dibaca (" + ex.getMessage() + "). "
                    + (moved ? "File dipindahkan ke " + backup.getName() : "File TIDAK dapat dipindahkan.")
                    + " Daftar penggeledahan paksa dimulai kosong.");
            return new Data(users, targets);
        }
        readSection(yaml.getConfigurationSection("force-users"), users);
        readSection(yaml.getConfigurationSection("force-targets"), targets);
        return new Data(users, targets);
    }

    private void readSection(ConfigurationSection section, Map<UUID, String> out) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                out.put(UUID.fromString(key), section.getString(key, "?"));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("data.yml: UUID tidak valid diabaikan: " + key);
            }
        }
    }

    /** Simpan secara atomik: tulis ke file sementara lalu pindahkan menimpa data.yml. */
    public void save(Map<UUID, String> users, Map<UUID, String> targets) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(java.util.List.of(
                "Data RegayaSecurity. Jangan diedit saat server berjalan;",
                "gunakan /regayasecurity add|remove|target add|target remove."));
        for (Map.Entry<UUID, String> entry : users.entrySet()) {
            yaml.set("force-users." + entry.getKey(), entry.getValue());
        }
        for (Map.Entry<UUID, String> entry : targets.entrySet()) {
            yaml.set("force-targets." + entry.getKey(), entry.getValue());
        }

        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Tidak dapat membuat folder data plugin");
        }
        File temp = new File(plugin.getDataFolder(), "data.yml.tmp");
        yaml.save(temp);
        try {
            Files.move(temp.toPath(), file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
