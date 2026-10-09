package com.regayagamtor.regayasecurity.listener;

import com.regayagamtor.regayasecurity.RegayaSecurity;
import com.regayagamtor.regayasecurity.model.SearchSession;
import com.regayagamtor.regayasecurity.model.SearchSession.State;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/** Pemantauan jarak dan dunia selama sesi. Pencarian sesi hanya satu lookup HashMap per gerakan. */
public final class PlayerMovementListener implements Listener {

    private final RegayaSecurity plugin;

    public PlayerMovementListener(RegayaSecurity plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedPosition()) {
            return;
        }
        Player player = event.getPlayer();
        SearchSession session = plugin.sessions().getSession(player.getUniqueId());
        if (session == null || session.isPendingEnd()) {
            return;
        }
        State state = session.state();
        if (state == State.REQUESTED || state == State.ENDED) {
            return; // jarak baru dipantau setelah permintaan diterima / sejak mode paksa dimulai
        }
        plugin.sessions().checkMove(session, player, event.getTo());
    }

    /** Teleport diperiksa satu tick kemudian, saat posisi baru sudah berlaku. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        SearchSession session = plugin.sessions().getSession(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> plugin.sessions().validateAndEnd(session));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        SearchSession session = plugin.sessions().getSession(event.getPlayer().getUniqueId());
        if (session != null) {
            plugin.sessions().validateAndEnd(session);
        }
    }
}
