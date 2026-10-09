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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** /geledah &lt;nama_pemain&gt; [alasan] : meminta izin penggeledahan (butuh persetujuan target). */
public final class SearchCommand implements TabExecutor {

    private final RegayaSecurity plugin;

    public SearchCommand(RegayaSecurity plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageManager msg = plugin.messages();
        if (!(sender instanceof Player player)) {
            msg.send(sender, "player-only");
            return true;
        }
        if (!plugin.permissions().canUseSearch(player)) {
            msg.send(player, "no-permission");
            return true;
        }
        if (args.length < 1) {
            msg.send(player, "usage-geledah");
            return true;
        }

        Player target = PlayerUtil.findVisible(player, args[0]);
        if (target == null) {
            msg.send(player, "player-not-found");
            return true;
        }

        int cooldown = plugin.sessions().cooldownRemainingSeconds(player.getUniqueId());
        if (cooldown > 0) {
            msg.send(player, "request-cooldown", "%seconds%", String.valueOf(cooldown));
            return true;
        }

        String errorKey = plugin.sessions().checkPrerequisites(player, target, SearchType.NORMAL);
        if (errorKey != null) {
            msg.send(player, errorKey,
                    "%player%", target.getName(),
                    "%distance%", plugin.sessions().formatDistance());
            return true;
        }

        String reason = null;
        if (args.length > 1) {
            reason = MessageManager.cleanReason(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        }
        plugin.sessions().createNormalRequest(player, target, reason);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (!(sender instanceof Player player) || !plugin.permissions().canUseSearch(player) || args.length != 1) {
            return result;
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(player.getUniqueId()) || !player.canSee(online)) {
                continue;
            }
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                result.add(online.getName());
            }
        }
        return result;
    }
}
