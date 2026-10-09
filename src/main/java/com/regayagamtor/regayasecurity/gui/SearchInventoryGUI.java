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
import org.bukkit.inventory.PlayerInventory;

import java.util.UUID;

/**
 * GUI penggeledahan (6 baris). GUI ini TIDAK menyimpan item miliknya sendiri:
 * setiap slot hanyalah gambar dari slot inventory ASLI target, dan setiap aksi klik
 * ditulis langsung ke inventory asli target oleh InventorySyncManager (write-through).
 *
 * Tata letak:
 *  baris 0 : [helm][chestplate][leggings][sepatu][-][offhand][-][info][tutup]
 *  baris 1 : pemisah + label inventory utama
 *  baris 2-4 : inventory utama target (27 slot)
 *  baris 5 : hotbar target (9 slot)
 */
public final class SearchInventoryGUI {

    public static final int SIZE = 54;

    public static final int SLOT_HELMET = 0;
    public static final int SLOT_CHESTPLATE = 1;
    public static final int SLOT_LEGGINGS = 2;
    public static final int SLOT_BOOTS = 3;
    public static final int SLOT_OFFHAND = 5;
    public static final int SLOT_INFO = 7;
    public static final int SLOT_CLOSE = 8;
    public static final int SLOT_MAIN_LABEL = 13;

    private static final int MAIN_GUI_START = 18;
    private static final int MAIN_GUI_END = 44;
    private static final int HOTBAR_GUI_START = 45;
    private static final int HOTBAR_GUI_END = 53;

    /** Semua slot GUI yang mewakili slot inventory asli target. */
    private static final int[] DATA_SLOTS = buildDataSlots();

    /** Penanda inventory ini milik sesi tertentu. */
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

    private final MessageManager msg;
    private final Holder holder;
    private final Inventory inventory;
    /** Isi terakhir yang digambar pada tiap slot; dipakai untuk mendeteksi perubahan/konflik. */
    private final ItemStack[] snapshots = new ItemStack[SIZE];

    public SearchInventoryGUI(MessageManager msg, SearchSession session) {
        this.msg = msg;
        this.holder = new Holder(session.id());
        this.inventory = Bukkit.createInventory(holder, SIZE,
                msg.component("gui-search-title", "%player%", session.targetName()));
        this.holder.setInventory(inventory);
        buildStaticItems(session);
    }

    public Inventory getInventory() {
        return inventory;
    }

    public Holder holder() {
        return holder;
    }

    private static int[] buildDataSlots() {
        int[] slots = new int[5 + 27 + 9];
        int index = 0;
        slots[index++] = SLOT_HELMET;
        slots[index++] = SLOT_CHESTPLATE;
        slots[index++] = SLOT_LEGGINGS;
        slots[index++] = SLOT_BOOTS;
        slots[index++] = SLOT_OFFHAND;
        for (int slot = MAIN_GUI_START; slot <= HOTBAR_GUI_END; slot++) {
            slots[index++] = slot;
        }
        return slots;
    }

    /**
     * Petakan slot GUI ke indeks PlayerInventory Bukkit
     * (0-8 hotbar, 9-35 utama, 36 sepatu, 37 leggings, 38 chestplate, 39 helm, 40 offhand).
     *
     * @return -1 jika slot GUI bukan slot data (hiasan/tombol)
     */
    public static int toInventorySlot(int guiSlot) {
        switch (guiSlot) {
            case SLOT_HELMET:
                return ItemUtil.SLOT_HELMET;
            case SLOT_CHESTPLATE:
                return ItemUtil.SLOT_CHESTPLATE;
            case SLOT_LEGGINGS:
                return ItemUtil.SLOT_LEGGINGS;
            case SLOT_BOOTS:
                return ItemUtil.SLOT_BOOTS;
            case SLOT_OFFHAND:
                return ItemUtil.SLOT_OFFHAND;
            default:
                break;
        }
        if (guiSlot >= MAIN_GUI_START && guiSlot <= MAIN_GUI_END) {
            return guiSlot - MAIN_GUI_START + 9;
        }
        if (guiSlot >= HOTBAR_GUI_START && guiSlot <= HOTBAR_GUI_END) {
            return guiSlot - HOTBAR_GUI_START;
        }
        return -1;
    }

    private void buildStaticItems(SearchSession session) {
        ItemStack filler = ItemUtil.icon(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), null);
        inventory.setItem(4, filler.clone());
        inventory.setItem(6, filler.clone());
        for (int slot = 9; slot <= 17; slot++) {
            inventory.setItem(slot, filler.clone());
        }
        inventory.setItem(SLOT_MAIN_LABEL, ItemUtil.icon(Material.CHEST,
                msg.component("gui-main-label"), msg.lore("gui-main-label-lore")));
        inventory.setItem(SLOT_INFO, ItemUtil.icon(Material.IRON_SWORD,
                msg.component("gui-search-info-name"),
                msg.lore("gui-search-info-lore",
                        "%player%", session.targetName(),
                        "%type%", msg.raw(session.type().messageKey()))));
        inventory.setItem(SLOT_CLOSE, ItemUtil.icon(Material.BARRIER,
                msg.component("gui-close-name"), msg.lore("gui-close-lore")));
    }

    /** Gambar ulang semua slot data dari inventory asli target. */
    public void renderAll(PlayerInventory targetInventory) {
        for (int guiSlot : DATA_SLOTS) {
            renderSlot(targetInventory, guiSlot);
        }
    }

    /** Gambar ulang satu slot dari inventory asli target dan catat snapshot-nya. */
    public void renderSlot(PlayerInventory targetInventory, int guiSlot) {
        int invSlot = toInventorySlot(guiSlot);
        if (invSlot < 0) {
            return;
        }
        ItemStack real = targetInventory.getItem(invSlot);
        if (ItemUtil.isEmpty(real)) {
            snapshots[guiSlot] = null;
            inventory.setItem(guiSlot, placeholderFor(guiSlot));
        } else {
            snapshots[guiSlot] = real.clone();
            inventory.setItem(guiSlot, real.clone());
        }
    }

    /**
     * Apakah isi slot asli target sudah berbeda dari yang terakhir digambar
     * (target/plugin lain mengubahnya)? Dipakai sebelum setiap aksi klik agar
     * penggeledah tidak pernah bertindak atas gambar yang sudah usang.
     */
    public boolean isStale(PlayerInventory targetInventory, int guiSlot) {
        int invSlot = toInventorySlot(guiSlot);
        if (invSlot < 0) {
            return false;
        }
        return !ItemUtil.same(targetInventory.getItem(invSlot), snapshots[guiSlot]);
    }

    /** Gambar ulang HANYA slot yang berbeda. @return jumlah slot yang diperbarui. */
    public int syncChanged(PlayerInventory targetInventory) {
        int changed = 0;
        for (int guiSlot : DATA_SLOTS) {
            if (isStale(targetInventory, guiSlot)) {
                renderSlot(targetInventory, guiSlot);
                changed++;
            }
        }
        return changed;
    }

    private ItemStack placeholderFor(int guiSlot) {
        String key;
        switch (guiSlot) {
            case SLOT_HELMET:
                key = "gui-empty-helmet";
                break;
            case SLOT_CHESTPLATE:
                key = "gui-empty-chestplate";
                break;
            case SLOT_LEGGINGS:
                key = "gui-empty-leggings";
                break;
            case SLOT_BOOTS:
                key = "gui-empty-boots";
                break;
            case SLOT_OFFHAND:
                key = "gui-empty-offhand";
                break;
            default:
                return null;
        }
        return ItemUtil.icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE, msg.component(key), null);
    }
}
