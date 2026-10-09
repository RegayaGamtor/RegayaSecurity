package com.regayagamtor.regayasecurity.manager;

import com.regayagamtor.regayasecurity.RegayaSecurity;
import com.regayagamtor.regayasecurity.gui.SearchInventoryGUI;
import com.regayagamtor.regayasecurity.model.CancelReason;
import com.regayagamtor.regayasecurity.model.SearchSession;
import com.regayagamtor.regayasecurity.model.SearchSession.State;
import com.regayagamtor.regayasecurity.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;

/**
 * Sinkronisasi inventory dua arah antara GUI penggeledah dan inventory ASLI target.
 *
 * Prinsip:
 * 1. Inventory asli target adalah satu-satunya sumber kebenaran. GUI hanya gambar.
 * 2. Semua klik pada GUI dibatalkan; plugin menerjemahkan klik menjadi operasi atomik
 *    (baca slot asli -> validasi -> tulis slot asli + kursor/inventory penggeledah) di main thread.
 * 3. Sebelum bertindak, isi slot asli dibandingkan dengan gambar terakhir (optimistic check).
 *    Jika berbeda (target mengubahnya), klik dibatalkan dan GUI digambar ulang -> tidak ada
 *    aksi atas data usang, sehingga tidak ada duplikasi/kehilangan item.
 * 4. Perubahan sisi target ditangani lewat event (markDirty) dan task pengaman yang hanya
 *    menggambar ulang slot yang benar-benar berbeda.
 */
public final class InventorySyncManager {

    private final RegayaSecurity plugin;
    private BukkitTask syncTask;

    public InventorySyncManager(RegayaSecurity plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ sinkronisasi sisi target

    public void start() {
        stop();
        long interval = plugin.config().syncIntervalTicks();
        syncTask = Bukkit.getScheduler().runTaskTimer(plugin, this::syncAll, interval, interval);
    }

    public void stop() {
        if (syncTask != null) {
            syncTask.cancel();
            syncTask = null;
        }
    }

    private void syncAll() {
        for (SearchSession session : plugin.sessions().activeSessions()) {
            syncSession(session);
        }
    }

    private void syncSession(SearchSession session) {
        SearchInventoryGUI gui = session.gui();
        if (gui == null || session.state() != State.ACTIVE) {
            return;
        }
        Player target = Bukkit.getPlayer(session.targetId());
        if (target == null) {
            return;
        }
        gui.syncChanged(target.getInventory());
    }

    /**
     * Dipanggil dari listener saat inventory seorang pemain mungkin berubah. Jika pemain itu
     * adalah target sesi aktif, GUI digambar ulang SATU kali pada tick berikutnya (setelah
     * perubahan benar-benar diterapkan server), dengan beberapa event digabung.
     */
    public void markDirty(UUID playerId) {
        SearchSession session = plugin.sessions().getSession(playerId);
        if (session == null || session.state() != State.ACTIVE || session.gui() == null
                || !session.targetId().equals(playerId) || session.isRenderScheduled()) {
            return;
        }
        session.setRenderScheduled(true);
        Bukkit.getScheduler().runTask(plugin, () -> {
            session.setRenderScheduled(false);
            syncSession(session);
        });
    }

    // ------------------------------------------------------------------ klik pada GUI penggeledahan

    public void handleSearchClick(InventoryClickEvent event, SearchInventoryGUI.Holder holder) {
        event.setCancelled(true); // aman secara default; hanya dibuka kembali untuk klik di inventory sendiri
        if (!(event.getWhoClicked() instanceof Player viewer)) {
            return;
        }
        SearchSession session = plugin.sessions().getById(holder.sessionId());
        if (session == null || session.state() != State.ACTIVE || session.gui() == null
                || !session.searcherId().equals(viewer.getUniqueId())) {
            // GUI usang (sesi sudah berakhir) atau bukan milik pemain ini: tutup.
            Bukkit.getScheduler().runTask(plugin, viewer::closeInventory);
            return;
        }
        if (session.isPendingEnd()) {
            return;
        }
        CancelReason problem = plugin.sessions().validateNow(session);
        if (problem != null) {
            plugin.sessions().endLater(session, problem);
            return;
        }
        Player target = Bukkit.getPlayer(session.targetId());
        if (target == null) {
            plugin.sessions().endLater(session, CancelReason.DISCONNECT);
            return;
        }

        SearchInventoryGUI gui = session.gui();
        ClickType click = event.getClick();
        int raw = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();

        // Klik di luar jendela: biarkan vanilla menjatuhkan item di kursor penggeledah
        // (item itu sudah sah milik penggeledah karena sudah dilepas dari target).
        if (raw < 0) {
            if (click == ClickType.LEFT || click == ClickType.RIGHT) {
                event.setCancelled(false);
            }
            return;
        }

        // Klik double / kumpulkan-ke-kursor bisa menarik item dari GUI: selalu ditolak.
        if (click == ClickType.DOUBLE_CLICK) {
            return;
        }

        // ---- Klik di inventory penggeledah sendiri (bagian bawah) ----
        if (raw >= topSize) {
            if (click.isShiftClick()) {
                shiftFromViewer(event, session, viewer, target, gui);
                return;
            }
            switch (click) {
                case LEFT, RIGHT, NUMBER_KEY, SWAP_OFFHAND, DROP, CONTROL_DROP -> event.setCancelled(false);
                default -> {
                    // tetap dibatalkan
                }
            }
            return;
        }

        // ---- Klik di GUI (bagian atas) ----
        if (raw == SearchInventoryGUI.SLOT_CLOSE) {
            if (click == ClickType.LEFT || click == ClickType.RIGHT) {
                plugin.sessions().endLater(session, CancelReason.CLOSED_BY_SEARCHER);
            }
            return;
        }
        int invSlot = SearchInventoryGUI.toInventorySlot(raw);
        if (invSlot < 0) {
            return; // hiasan
        }

        PlayerInventory targetInventory = target.getInventory();
        if (gui.isStale(targetInventory, raw)) {
            handleConflict(session, gui, targetInventory, viewer);
            return;
        }

        boolean changed;
        switch (click) {
            case LEFT -> changed = leftClick(viewer, targetInventory, invSlot);
            case RIGHT -> changed = rightClick(viewer, targetInventory, invSlot);
            case SHIFT_LEFT, SHIFT_RIGHT -> changed = shiftToViewer(viewer, targetInventory, invSlot);
            case NUMBER_KEY -> changed = swapWithViewerSlot(viewer, targetInventory, invSlot, event.getHotbarButton());
            case SWAP_OFFHAND -> changed = swapWithViewerSlot(viewer, targetInventory, invSlot, ItemUtil.SLOT_OFFHAND);
            default -> changed = false; // drop, middle, creative, dll. tidak didukung
        }
        if (changed) {
            session.resetConflicts();
        }
        gui.renderSlot(targetInventory, raw);
    }

    /** Seret (drag) yang menyentuh slot GUI dibatalkan; seret di inventory sendiri dibiarkan. */
    public void handleDrag(InventoryDragEvent event) {
        int topSize = event.getView().getTopInventory().getSize();
        for (int raw : event.getRawSlots()) {
            if (raw < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private void handleConflict(SearchSession session, SearchInventoryGUI gui,
                                PlayerInventory targetInventory, Player viewer) {
        int conflicts = session.incrementConflicts();
        gui.renderAll(targetInventory);
        plugin.messages().send(viewer, "slot-changed");
        if (conflicts >= plugin.config().maxConflicts()) {
            plugin.sessions().endLater(session, CancelReason.CONFLICT);
        }
    }

    // ------------------------------------------------------------------ operasi slot (atomik, main thread)

    /** Klik kiri pada slot target: ambil semua / letakkan semua / gabung / tukar dengan kursor. */
    private boolean leftClick(Player viewer, PlayerInventory targetInventory, int invSlot) {
        ItemStack cursor = ItemUtil.copy(viewer.getItemOnCursor());
        ItemStack slot = ItemUtil.copy(targetInventory.getItem(invSlot));

        if (cursor == null) {
            if (slot == null) {
                return false;
            }
            viewer.setItemOnCursor(slot);
            targetInventory.setItem(invSlot, null);
            return true;
        }
        if (!canPlace(viewer, cursor, invSlot)) {
            return false;
        }
        int limit = ItemUtil.slotLimit(invSlot, cursor);

        if (slot == null) {
            int put = Math.min(cursor.getAmount(), limit);
            ItemStack placed = cursor.clone();
            placed.setAmount(put);
            targetInventory.setItem(invSlot, placed);
            cursor.setAmount(cursor.getAmount() - put);
            viewer.setItemOnCursor(cursor.getAmount() > 0 ? cursor : null);
            return true;
        }
        if (slot.isSimilar(cursor)) {
            int space = limit - slot.getAmount();
            if (space <= 0) {
                return false;
            }
            int move = Math.min(space, cursor.getAmount());
            slot.setAmount(slot.getAmount() + move);
            targetInventory.setItem(invSlot, slot);
            cursor.setAmount(cursor.getAmount() - move);
            viewer.setItemOnCursor(cursor.getAmount() > 0 ? cursor : null);
            return true;
        }
        if (cursor.getAmount() > limit) {
            return false; // tidak muat untuk ditukar (mis. tumpukan >1 ke slot armor)
        }
        targetInventory.setItem(invSlot, cursor);
        viewer.setItemOnCursor(slot);
        return true;
    }

    /** Klik kanan pada slot target: ambil setengah / letakkan satu / tambah satu / tukar. */
    private boolean rightClick(Player viewer, PlayerInventory targetInventory, int invSlot) {
        ItemStack cursor = ItemUtil.copy(viewer.getItemOnCursor());
        ItemStack slot = ItemUtil.copy(targetInventory.getItem(invSlot));

        if (cursor == null) {
            if (slot == null) {
                return false;
            }
            int take = (slot.getAmount() + 1) / 2;
            ItemStack taken = slot.clone();
            taken.setAmount(take);
            viewer.setItemOnCursor(taken);
            int left = slot.getAmount() - take;
            if (left <= 0) {
                targetInventory.setItem(invSlot, null);
            } else {
                slot.setAmount(left);
                targetInventory.setItem(invSlot, slot);
            }
            return true;
        }
        if (!canPlace(viewer, cursor, invSlot)) {
            return false;
        }
        int limit = ItemUtil.slotLimit(invSlot, cursor);

        if (slot == null) {
            ItemStack one = cursor.clone();
            one.setAmount(1);
            targetInventory.setItem(invSlot, one);
            decrementCursor(viewer, cursor);
            return true;
        }
        if (slot.isSimilar(cursor)) {
            if (slot.getAmount() >= limit) {
                return false;
            }
            slot.setAmount(slot.getAmount() + 1);
            targetInventory.setItem(invSlot, slot);
            decrementCursor(viewer, cursor);
            return true;
        }
        if (cursor.getAmount() > limit) {
            return false;
        }
        targetInventory.setItem(invSlot, cursor);
        viewer.setItemOnCursor(slot);
        return true;
    }

    private void decrementCursor(Player viewer, ItemStack cursor) {
        cursor.setAmount(cursor.getAmount() - 1);
        viewer.setItemOnCursor(cursor.getAmount() > 0 ? cursor : null);
    }

    /** Shift-klik slot target: pindahkan ke inventory penggeledah; sisa yang tidak muat tetap di target. */
    private boolean shiftToViewer(Player viewer, PlayerInventory targetInventory, int invSlot) {
        ItemStack slot = ItemUtil.copy(targetInventory.getItem(invSlot));
        if (slot == null) {
            return false;
        }
        Map<Integer, ItemStack> leftover = viewer.getInventory().addItem(slot.clone());
        if (leftover.isEmpty()) {
            targetInventory.setItem(invSlot, null);
            return true;
        }
        ItemStack rest = leftover.values().iterator().next();
        if (rest.getAmount() >= slot.getAmount()) {
            return false; // tidak ada yang berpindah
        }
        targetInventory.setItem(invSlot, rest);
        return true;
    }

    /**
     * Tombol angka / tombol offhand: tukar isi slot target dengan slot hotbar (0-8)
     * atau offhand (40) milik penggeledah. Kedua sisi dibaca dan divalidasi dulu, baru ditulis.
     */
    private boolean swapWithViewerSlot(Player viewer, PlayerInventory targetInventory, int invSlot, int viewerSlot) {
        boolean hotbar = viewerSlot >= 0 && viewerSlot <= 8;
        if (!hotbar && viewerSlot != ItemUtil.SLOT_OFFHAND) {
            return false;
        }
        PlayerInventory viewerInventory = viewer.getInventory();
        ItemStack theirs = ItemUtil.copy(viewerInventory.getItem(viewerSlot));
        ItemStack slot = ItemUtil.copy(targetInventory.getItem(invSlot));
        if (theirs == null && slot == null) {
            return false;
        }
        if (theirs != null) {
            if (!canPlace(viewer, theirs, invSlot)) {
                return false;
            }
            if (theirs.getAmount() > ItemUtil.slotLimit(invSlot, theirs)) {
                return false;
            }
        }
        targetInventory.setItem(invSlot, theirs);
        viewerInventory.setItem(viewerSlot, slot);
        return true;
    }

    /** Shift-klik item di inventory penggeledah: masukkan ke inventory target; sisa tetap di penggeledah. */
    private void shiftFromViewer(InventoryClickEvent event, SearchSession session, Player viewer,
                                 Player target, SearchInventoryGUI gui) {
        int viewerSlot = event.getSlot();
        if (viewerSlot < 0 || viewerSlot > 35) {
            return;
        }
        PlayerInventory viewerInventory = viewer.getInventory();
        ItemStack item = ItemUtil.copy(viewerInventory.getItem(viewerSlot));
        if (item == null || !ItemUtil.same(item, event.getCurrentItem())) {
            return;
        }
        PlayerInventory targetInventory = target.getInventory();
        Map<Integer, ItemStack> leftover = targetInventory.addItem(item.clone());
        if (leftover.isEmpty()) {
            viewerInventory.setItem(viewerSlot, null);
        } else {
            ItemStack rest = leftover.values().iterator().next();
            if (rest.getAmount() >= item.getAmount()) {
                return; // tidak ada yang berpindah
            }
            viewerInventory.setItem(viewerSlot, rest);
        }
        session.resetConflicts();
        gui.renderAll(targetInventory);
    }

    private boolean canPlace(Player viewer, ItemStack item, int invSlot) {
        if (ItemUtil.canPlace(item, invSlot, plugin.config().strictArmorSlots())) {
            return true;
        }
        plugin.messages().send(viewer, "armor-invalid");
        return false;
    }

    /** Dipakai listener untuk memastikan inventory ini milik GUI penggeledahan. */
    public static boolean isSearchInventory(Inventory inventory) {
        return inventory != null && inventory.getHolder(false) instanceof SearchInventoryGUI.Holder;
    }
}
