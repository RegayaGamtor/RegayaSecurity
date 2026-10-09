package com.regayagamtor.regayasecurity.command;

import com.regayagamtor.regayasecurity.RegayaSecurity;
import com.regayagamtor.regayasecurity.config.MessageManager;
import com.regayagamtor.regayasecurity.model.SearchType;
import com.regayagamtor.regayasecurity.util.PlayerUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /geledahpaksa &lt;nama_pemain&gt; : mulai mode menunggu pukulan. Hanya untuk pengguna berizin
 * dan hanya terhadap target yang terdaftar (bila whitelist diwajibkan di config).
 */
public final class ForceSearchCommand implements TabExecutor {

    private final RegayaSecurity plugin;

    public ForceSearchCommand(RegayaSecurity plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageManager msg = plugin.messages();
        if (!(sender instanceof Player player)) {
            msg.send(sender, "player-only");
            return true;
        }
        // 1. izin penggeledahan paksa
        if (!plugin.permissions().canUseForce(player)) {
            msg.send(player, "force-not-authorized");
            return true;
        }
        if (args.length < 1) {
            msg.send(player, "usage-geledahpaksa");
            return true;
        }

        // 2. target online dan bukan diri sendiri (cek diri sendiri ada di checkPrerequisites)
        Player target = PlayerUtil.findVisible(player, args[0]);
        if (target == null) {
            msg.send(player, "player-not-found");
            return true;
        }

        // 3-6. whitelist target, dunia, jarak, sesi bentrok
        String errorKey = plugin.sessions().checkPrerequisites(player, target, SearchType.FORCE);
        if (errorKey != null) {
            msg.send(player, errorKey,
                    "%player%", target.getName(),
                    "%distance%", plugin.sessions().formatDistance());
            return true;
        }

        plugin.sessions().startForce(player, target);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (!(sender instanceof Player player) || !plugin.permissions().canUseForce(player) || args.length != 1) {
            return result;
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        boolean whitelist = plugin.config().requireForceTargetWhitelist();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(player.getUniqueId()) || !player.canSee(online)) {
                continue;
            }
            if (whitelist && !plugin.forceTargets().isTarget(online.getUniqueId())) {
                continue; // jangan menyarankan pemain yang tidak boleh digeledah paksa
            }
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                result.add(online.getName());
            }
        }
        return result;
    }
}
