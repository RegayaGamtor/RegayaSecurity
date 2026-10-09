package com.regayagamtor.regayasecurity.manager;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Seluruh pemeriksaan izin dilakukan di sisi server melalui kelas ini
 * (tidak pernah mengandalkan data GUI sebagai bukti otorisasi).
 */
public final class PermissionManager {

    public static final String USE = "regayasecurity.use";
    public static final String FORCE = "regayasecurity.force";
    public static final String ADMIN = "regayasecurity.admin";
    public static final String TARGET_MANAGE = "regayasecurity.target.manage";
    public static final String RELOAD = "regayasecurity.reload";

    private final ForceTargetManager forceTargets;

    public PermissionManager(ForceTargetManager forceTargets) {
        this.forceTargets = forceTargets;
    }

    /** Penggeledahan biasa. */
    public boolean canUseSearch(Player player) {
        return player.hasPermission(USE);
    }

    /**
     * Penggeledahan paksa: izin regayasecurity.force ATAU terdaftar lewat
     * /regayasecurity add. Ini hanya membuka hak memakai perintah; target tetap
     * harus terdaftar (lihat ForceTargetManager) bila whitelist target diwajibkan.
     */
    public boolean canUseForce(Player player) {
        return player.hasPermission(FORCE) || forceTargets.isUser(player.getUniqueId());
    }

    public boolean isAdmin(CommandSender sender) {
        return sender.hasPermission(ADMIN);
    }

    public boolean canManageTargets(CommandSender sender) {
        return sender.hasPermission(TARGET_MANAGE);
    }

    public boolean canReload(CommandSender sender) {
        return sender.hasPermission(RELOAD);
    }
}
