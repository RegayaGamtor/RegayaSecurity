package com.regayagamtor.regayasecurity.gui;

import com.regayagamtor.regayasecurity.config.MessageManager;
import com.regayagamtor.regayasecurity.model.SearchSession;
import com.regayagamtor.regayasecurity.util.ItemUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * GUI konfirmasi yang dibuka pada target: TERIMA / TOLAK, berisi nama penggeledah,
 * alasan, dan sisa waktu. Semua klik dibatalkan dan hanya dua tombol yang berfungsi.
 */
public final class SearchRequestGUI {

    public static final int SIZE = 27;
    public static final int SLOT_ACCEPT = 11;
    public static final int SLOT_INFO = 13;
    public static final int SLOT_DENY = 15;

    /** Penanda inventory ini milik sesi tertentu (pengenal berbasis UUID sesi). */
    public static final class Holder implements InventoryHolder {
        private final UUID sessionId;
        private Inventory inventory;

        Holder(UUID sessionId) {
            this.sessionId = sessionId;
        }

        public UUID sessionId() {
            return sessionId;
        }

        void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private SearchRequestGUI() {
    }

    public static Inventory create(MessageManager msg, SearchSession session) {
        Holder holder = new Holder(session.id());
        Inventory inventory = Bukkit.createInventory(holder, SIZE, msg.component("gui-request-title"));
        holder.setInventory(inventory);

        ItemStack filler = ItemUtil.icon(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), null);
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, filler.clone());
        }

        inventory.setItem(SLOT_ACCEPT, ItemUtil.icon(Material.LIME_DYE,
                msg.component("gui-request-accept-name"),
                msg.lore("gui-request-accept-lore", "%player%", session.searcherName())));
        inventory.setItem(SLOT_DENY, ItemUtil.icon(Material.RED_DYE,
                msg.component("gui-request-deny-name"),
                msg.lore("gui-request-deny-lore", "%player%", session.searcherName())));
        updateInfo(msg, inventory, session);
        return inventory;
    }

    /** Perbarui item informasi (dipanggil tiap detik untuk hitung mundur). */
    public static void updateInfo(MessageManager msg, Inventory inventory, SearchSession session) {
        String reason = session.request().reason();
        if (reason == null || reason.isBlank()) {
            reason = msg.raw("reason-none");
        }
        inventory.setItem(SLOT_INFO, ItemUtil.icon(Material.NAME_TAG,
                msg.component("gui-request-info-name"),
                msg.lore("gui-request-info-lore",
                        "%player%", session.searcherName(),
                        "%reason%", reason,
                        "%seconds%", String.valueOf(Math.max(0, session.remainingSeconds())))));
    }
}
