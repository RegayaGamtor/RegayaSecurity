package com.regayagamtor.regayasecurity.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Pencarian pemain yang aman (tanpa lookup web yang memblokir main thread). */
public final class PlayerUtil {

    private PlayerUtil() {
    }

    /**
     * Cari pemain online berdasarkan nama persis (tidak peka huruf besar/kecil).
     * Pemain yang tidak terlihat oleh pencari (vanish) dan NPC dianggap tidak ada.
     */
    public static Player findVisible(Player viewer, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Player target = Bukkit.getPlayerExact(name);
        if (target == null || !target.isOnline()) {
            return null;
        }
        if (viewer != null && !viewer.canSee(target)) {
            return null;
        }
        if (target.hasMetadata("NPC")) {
            return null;
        }
        return target;
    }

    /** Cari UUID dari peta nama tersimpan (tidak peka huruf besar/kecil). */
    public static UUID findStoredUuid(Map<UUID, String> stored, String name) {
        String wanted = name.toLowerCase(Locale.ROOT);
        for (Map.Entry<UUID, String> entry : stored.entrySet()) {
            if (entry.getValue() != null && entry.getValue().toLowerCase(Locale.ROOT).equals(wanted)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
