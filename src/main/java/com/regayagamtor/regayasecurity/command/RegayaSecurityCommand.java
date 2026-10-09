package com.regayagamtor.regayasecurity.command;

import com.regayagamtor.regayasecurity.RegayaSecurity;
import com.regayagamtor.regayasecurity.config.MessageManager;
import com.regayagamtor.regayasecurity.manager.ForceTargetManager;
import com.regayagamtor.regayasecurity.manager.PermissionManager;
import com.regayagamtor.regayasecurity.model.SearchSession;
import com.regayagamtor.regayasecurity.util.PlayerUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /regayasecurity reload | add | remove | target add|remove | info. */
public final class RegayaSecurityCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("reload", "add", "remove", "target", "info");
    private static final List<String> TARGET_SUBCOMMANDS = List.of("add", "remove");

    private final RegayaSecurity plugin;

    public RegayaSecurityCommand(RegayaSecurity plugin) {
        this.plugin = plugin;
    }

    /** Pasangan UUID + nama terakhir yang diketahui. */
    private record Resolved(UUID id, String name) {
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageManager msg = plugin.messages();
        PermissionManager perms = plugin.permissions();
        if (args.length == 0) {
            if (perms.isAdmin(sender) || perms.canReload(sender) || perms.canManageTargets(sender)) {
                msg.sendLines(sender, "usage-admin");
            } else {
                msg.send(sender, "no-permission");
            }
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                if (!perms.canReload(sender)) {
                    msg.send(sender, "no-permission");
                    return true;
                }
                plugin.reloadAll();
                msg.send(sender, "admin-reloaded");
            }
            case "add" -> {
                if (!perms.isAdmin(sender)) {
                    msg.send(sender, "no-permission");
                    return true;
                }
                if (args.length < 2) {
                    msg.sendLines(sender, "usage-admin");
                    return true;
                }
                handleUser(sender, args[1], true);
            }
            case "remove" -> {
                if (!perms.isAdmin(sender)) {
                    msg.send(sender, "no-permission");
                    return true;
                }
                if (args.length < 2) {
                    msg.sendLines(sender, "usage-admin");
                    return true;
                }
                handleUser(sender, args[1], false);
            }
            case "target" -> {
                if (!perms.canManageTargets(sender)) {
                    msg.send(sender, "no-permission");
                    return true;
                }
                if (args.length < 3) {
                    msg.sendLines(sender, "usage-admin");
                    return true;
                }
                String action = args[1].toLowerCase(Locale.ROOT);
                if (action.equals("add")) {
                    handleTarget(sender, args[2], true);
                } else if (action.equals("remove")) {
                    handleTarget(sender, args[2], false);
                } else {
                    msg.sendLines(sender, "usage-admin");
                }
            }
            case "info" -> {
                if (!perms.isAdmin(sender)) {
                    msg.send(sender, "no-permission");
                    return true;
                }
                if (args.length < 2) {
                    msg.sendLines(sender, "usage-admin");
                    return true;
                }
                handleInfo(sender, args[1]);
            }
            default -> msg.sendLines(sender, "usage-admin");
        }
        return true;
    }

    // ------------------------------------------------------------------ add / remove pengguna paksa

    private void handleUser(CommandSender sender, String name, boolean add) {
        MessageManager msg = plugin.messages();
        ForceTargetManager registry = plugin.forceTargets();
        Resolved resolved = resolve(name, registry.userNames());
        if (resolved == null) {
            msg.send(sender, "player-unknown");
            return;
        }
        if (add) {
            boolean added = registry.addUser(resolved.id(), resolved.name());
            msg.send(sender, added ? "admin-user-added" : "admin-user-exists", "%player%", resolved.name());
            if (added) {
                persistOrWarn(sender);
            }
        } else {
            boolean removed = registry.removeUser(resolved.id());
            msg.send(sender, removed ? "admin-user-removed" : "admin-user-missing", "%player%", resolved.name());
            if (removed) {
                persistOrWarn(sender);
                // Sesi paksa yang sedang berjalan akan dihentikan oleh pemeriksaan izin berkala.
            }
        }
    }

    private void handleTarget(CommandSender sender, String name, boolean add) {
        MessageManager msg = plugin.messages();
        ForceTargetManager registry = plugin.forceTargets();
        Resolved resolved = resolve(name, registry.targetNames());
        if (resolved == null) {
            msg.send(sender, "player-unknown");
            return;
        }
        if (add) {
            boolean added = registry.addTarget(resolved.id(), resolved.name());
            msg.send(sender, added ? "admin-target-added" : "admin-target-exists", "%player%", resolved.name());
            if (added) {
                persistOrWarn(sender);
            }
        } else {
            boolean removed = registry.removeTarget(resolved.id());
            msg.send(sender, removed ? "admin-target-removed" : "admin-target-missing", "%player%", resolved.name());
            if (removed) {
                persistOrWarn(sender);
            }
        }
    }

    private void persistOrWarn(CommandSender sender) {
        if (!plugin.forceTargets().persist()) {
            plugin.messages().send(sender, "admin-save-failed");
        }
    }

    // ------------------------------------------------------------------ info

    private void handleInfo(CommandSender sender, String name) {
        MessageManager msg = plugin.messages();
        Resolved resolved = resolve(name, plugin.forceTargets().userNames());
        if (resolved == null) {
            resolved = resolve(name, plugin.forceTargets().targetNames());
        }
        if (resolved == null) {
            msg.send(sender, "player-unknown");
            return;
        }

        Player online = Bukkit.getPlayer(resolved.id());
        String yes = msg.raw("word-yes");
        String no = msg.raw("word-no");
        String permUse;
        String permForce;
        if (online != null) {
            permUse = plugin.permissions().canUseSearch(online) ? yes : no;
            permForce = online.hasPermission(PermissionManager.FORCE) ? yes : no;
        } else {
            permUse = "?";
            permForce = "?";
        }

        msg.sendLines(sender, "admin-info",
                "%player%", resolved.name(),
                "%uuid%", resolved.id().toString(),
                "%online%", online != null ? msg.raw("word-online") : msg.raw("word-offline"),
                "%perm-use%", permUse,
                "%perm-force%", permForce,
                "%registered-user%", plugin.forceTargets().isUser(resolved.id()) ? yes : no,
                "%registered-target%", plugin.forceTargets().isTarget(resolved.id()) ? yes : no,
                "%session%", describeSession(resolved.id()));
    }

    private String describeSession(UUID player) {
        MessageManager msg = plugin.messages();
        SearchSession session = plugin.sessions().getSession(player);
        if (session == null) {
            return msg.raw("info-session-none");
        }
        boolean searcher = session.searcherId().equals(player);
        String stateKey = switch (session.state()) {
            case REQUESTED -> "state-requested";
            case AWAITING_HIT -> "state-awaiting-hit";
            case PROGRESS -> "state-progress";
            default -> "state-active";
        };
        // format() membersihkan nilai placeholder, jadi kata-kata bawaan diteruskan sebagai teks biasa.
        return msg.format("info-session-active",
                "%type%", msg.raw(session.type().messageKey()),
                "%state%", msg.raw(stateKey),
                "%role%", msg.raw(searcher ? "role-searcher" : "role-target"),
                "%other%", searcher ? session.targetName() : session.searcherName());
    }

    // ------------------------------------------------------------------ resolusi pemain

    /**
     * Cari UUID pemain TANPA lookup web yang memblokir server: pemain online, lalu cache
     * server (Paper), lalu nama yang sudah tersimpan di daftar (untuk remove/info).
     */
    private Resolved resolve(String name, java.util.Map<UUID, String> stored) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return new Resolved(online.getUniqueId(), online.getName());
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null) {
            String cachedName = cached.getName() != null ? cached.getName() : name;
            return new Resolved(cached.getUniqueId(), cachedName);
        }
        UUID storedId = PlayerUtil.findStoredUuid(stored, name);
        if (storedId != null) {
            return new Resolved(storedId, stored.get(storedId));
        }
        return null;
    }

    // ------------------------------------------------------------------ tab complete

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        PermissionManager perms = plugin.permissions();
        if (!(perms.isAdmin(sender) || perms.canReload(sender) || perms.canManageTargets(sender))) {
            return result;
        }
        if (args.length == 1) {
            addMatching(result, SUBCOMMANDS, args[0]);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("target")) {
            addMatching(result, TARGET_SUBCOMMANDS, args[1]);
        } else if (args.length == 2 && List.of("add", "remove", "info").contains(args[0].toLowerCase(Locale.ROOT))) {
            addPlayers(result, args[1]);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("target")) {
            addPlayers(result, args[2]);
        }
        return result;
    }

    private void addMatching(List<String> out, List<String> options, String typed) {
        String prefix = typed.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.startsWith(prefix)) {
                out.add(option);
            }
        }
    }

    private void addPlayers(List<String> out, String typed) {
        String prefix = typed.toLowerCase(Locale.ROOT);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(online.getName());
            }
        }
    }
}
