package com.regayagamtor.regayasecurity.manager;

import com.regayagamtor.regayasecurity.storage.ForceTargetStorage;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Daftar (1) pengguna penggeledahan paksa yang didaftarkan admin dan
 * (2) target yang boleh digeledah paksa. Identitas = UUID.
 */
public final class ForceTargetManager {

    private final JavaPlugin plugin;
    private final ForceTargetStorage storage;
    private final Map<UUID, String> users = new LinkedHashMap<>();
    private final Map<UUID, String> targets = new LinkedHashMap<>();

    public ForceTargetManager(JavaPlugin plugin, ForceTargetStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public void load() {
        ForceTargetStorage.Data data = storage.load();
        users.clear();
        users.putAll(data.users());
        targets.clear();
        targets.putAll(data.targets());
    }

    /** @return true jika baru ditambahkan (false jika sudah terdaftar; nama diperbarui). */
    public boolean addUser(UUID id, String name) {
        return users.put(id, name) == null;
    }

    public boolean removeUser(UUID id) {
        return users.remove(id) != null;
    }

    public boolean isUser(UUID id) {
        return users.containsKey(id);
    }

    /** @return true jika baru ditambahkan. */
    public boolean addTarget(UUID id, String name) {
        return targets.put(id, name) == null;
    }

    public boolean removeTarget(UUID id) {
        return targets.remove(id) != null;
    }

    public boolean isTarget(UUID id) {
        return targets.containsKey(id);
    }

    public Map<UUID, String> userNames() {
        return users;
    }

    public Map<UUID, String> targetNames() {
        return targets;
    }

    /** Simpan ke data.yml. @return false jika gagal (sudah dicatat di console). */
    public boolean persist() {
        try {
            storage.save(users, targets);
            return true;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Gagal menyimpan data.yml", ex);
            return false;
        }
    }
}
