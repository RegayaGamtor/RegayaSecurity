package com.regayagamtor.regayasecurity.listener;

import com.regayagamtor.regayasecurity.RegayaSecurity;
import com.regayagamtor.regayasecurity.gui.SearchInventoryGUI;
import com.regayagamtor.regayasecurity.gui.SearchRequestGUI;
import com.regayagamtor.regayasecurity.model.CancelReason;
import com.regayagamtor.regayasecurity.model.SearchSession;
import com.regayagamtor.regayasecurity.model.SearchSession.State;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Pengaman inventory: semua klik/seret pada GUI ditangani plugin (tidak pernah dieksekusi vanilla),
 * GUI usang ditutup, dan perubahan inventory sisi target memicu penggambaran ulang GUI.
 */
public final class InventoryListener implements Listener {

    private final RegayaSecurity plugin;

    public InventoryListener(RegayaSecurity plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ klik / seret

    /**
     * Prioritas HIGHEST dan sengaja tidak memakai ignoreCancelled: untuk GUI milik plugin ini,
     * event SELALU berakhir dibatalkan (kecuali klik di inventory penggeledah sendiri yang aman),
     * apa pun yang dilakukan plugin lain sebelumnya.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (holder instanceof SearchInventoryGUI.Holder searchHolder) {
            plugin.sync().handleSearchClick(event, searchHolder);
        } else if (holder instanceof SearchRequestGUI.Holder requestHolder) {
            handleRequestClick(event, requestHolder);
        }
    }

    private void handleRequestClick(InventoryClickEvent event, SearchRequestGUI.Holder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        SearchSession session = plugin.sessions().getById(holder.sessionId());
        if (session == null || session.state() != State.REQUESTED
                || !session.targetId().equals(player.getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
            return;
        }
        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT) {
            return;
        }
        int raw = event.getRawSlot();
        if (raw == SearchRequestGUI.SLOT_ACCEPT) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.sessions().accept(session, player));
        } else if (raw == SearchRequestGUI.SLOT_DENY) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.sessions().deny(session));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (holder instanceof SearchInventoryGUI.Holder) {
            plugin.sync().handleDrag(event);
        } else if (holder instanceof SearchRequestGUI.Holder) {
            event.setCancelled(true);
        }
    }

    /** Pertahanan berlapis: tidak ada mekanisme pemindahan item (hopper, plugin lain) yang boleh menyentuh GUI. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMoveItem(InventoryMoveItemEvent event) {
        if (isOurs(event.getSource()) || isOurs(event.getDestination())) {
            event.setCancelled(true);
        }
    }

    private boolean isOurs(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder(false);
        return holder instanceof SearchInventoryGUI.Holder || holder instanceof SearchRequestGUI.Holder;
    }

    // ------------------------------------------------------------------ penutupan GUI

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (holder instanceof SearchInventoryGUI.Holder searchHolder) {
            SearchSession session = plugin.sessions().getById(searchHolder.sessionId());
            if (session != null && session.state() == State.ACTIVE) {
                // Penggeledah menutup GUI (atau GUI diganti): sesi berakhir. Item yang sudah
                // dipindahkan tetap milik pemilik barunya; kursor dikembalikan oleh vanilla.
                plugin.sessions().end(session, CancelReason.CLOSED_BY_SEARCHER, false);
            }
        } else if (holder instanceof SearchRequestGUI.Holder requestHolder) {
            SearchSession session = plugin.sessions().getById(requestHolder.sessionId());
            if (session != null && session.state() == State.REQUESTED) {
                // Menutup jendela tanpa memilih dianggap menolak (persetujuan harus eksplisit).
                plugin.sessions().end(session, CancelReason.DENIED_BY_CLOSE, false);
            }
        }
        // Menutup crafting/penyimpanan dapat mengembalikan item ke inventory pemain.
        if (event.getPlayer() instanceof Player player) {
            plugin.sync().markDirty(player.getUniqueId());
        }
    }

    // ------------------------------------------------------------------ perubahan sisi target

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClickMonitor(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            plugin.sync().markDirty(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDragMonitor(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            plugin.sync().markDirty(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        plugin.sync().markDirty(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            plugin.sync().markDirty(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        plugin.sync().markDirty(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        plugin.sync().markDirty(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemBreak(PlayerItemBreakEvent event) {
        plugin.sync().markDirty(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        plugin.sync().markDirty(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        plugin.sync().markDirty(event.getPlayer().getUniqueId());
    }
}
