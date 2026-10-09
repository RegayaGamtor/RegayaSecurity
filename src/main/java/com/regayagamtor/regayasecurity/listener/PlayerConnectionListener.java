package com.regayagamtor.regayasecurity.listener;

import com.regayagamtor.regayasecurity.RegayaSecurity;
import com.regayagamtor.regayasecurity.model.CancelReason;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Pembatalan sesi saat pemain terputus atau mati. */
public final class PlayerConnectionListener implements Listener {

    private final RegayaSecurity plugin;

    public PlayerConnectionListener(RegayaSecurity plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.sessions().endAllFor(event.getPlayer().getUniqueId(), CancelReason.DISCONNECT);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        plugin.sessions().endAllFor(event.getEntity().getUniqueId(), CancelReason.DEATH);
    }
}
