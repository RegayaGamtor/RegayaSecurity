package com.regayagamtor.regayasecurity.manager;

import com.regayagamtor.regayasecurity.RegayaSecurity;
import com.regayagamtor.regayasecurity.config.ConfigManager;
import com.regayagamtor.regayasecurity.config.MessageManager;
import com.regayagamtor.regayasecurity.gui.SearchInventoryGUI;
import com.regayagamtor.regayasecurity.gui.SearchRequestGUI;
import com.regayagamtor.regayasecurity.model.CancelReason;
import com.regayagamtor.regayasecurity.model.SearchRequest;
import com.regayagamtor.regayasecurity.model.SearchSession;
import com.regayagamtor.regayasecurity.model.SearchSession.State;
import com.regayagamtor.regayasecurity.model.SearchType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Mengelola seluruh siklus hidup sesi: permintaan, persetujuan, penggeledahan paksa
 * (menunggu pukulan, loading), sesi aktif, dan pembersihan.
 *
 * Semua metode dipanggil dari main thread. Satu pemain hanya boleh berada di satu sesi
 * (sebagai penggeledah ATAU target). Identitas selalu UUID.
 */
public final class SearchSessionManager {

    /** Hasil pengecekan pukulan penggeledah. */
    public enum HitResult {
        NOT_APPLICABLE,
        TRIGGERED,
        TRIGGERED_DUPLICATE,
        WRONG_TARGET
    }

    private static final int PROGRESS_INTERVAL_TICKS = 2;
    private static final int PROGRESS_BAR_LENGTH = 10;
    private static final long WRONG_HIT_MESSAGE_COOLDOWN_MILLIS = 1500L;

    private final RegayaSecurity plugin;
    private final Map<UUID, SearchSession> byId = new HashMap<>();
    private final Map<UUID, SearchSession> byPlayer = new HashMap<>();
    private final Map<UUID, Long> lastRequestMillis = new HashMap<>();
    private BukkitTask checkTask;

    public SearchSessionManager(RegayaSecurity plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ lifecycle

    public void start() {
        restartCheckTask();
    }

    public void restartCheckTask() {
        if (checkTask != null) {
            checkTask.cancel();
        }
        long interval = plugin.config().sessionCheckIntervalTicks();
        checkTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkAll, interval, interval);
    }

    /** Hentikan semua sesi dengan aman (plugin dimatikan). */
    public void shutdown() {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
        for (SearchSession session : new ArrayList<>(byId.values())) {
            end(session, CancelReason.SHUTDOWN);
        }
        byId.clear();
        byPlayer.clear();
    }

    // ------------------------------------------------------------------ queries

    public SearchSession getSession(UUID player) {
        return byPlayer.get(player);
    }

    public SearchSession getById(UUID sessionId) {
        return byId.get(sessionId);
    }

    public boolean isBusy(UUID player) {
        return byPlayer.containsKey(player);
    }

    public List<SearchSession> activeSessions() {
        List<SearchSession> list = new ArrayList<>();
        for (SearchSession session : byId.values()) {
            if (session.state() == State.ACTIVE && session.gui() != null) {
                list.add(session);
            }
        }
        return list;
    }

    // ------------------------------------------------------------------ prerequisites

    /**
     * Pemeriksaan sebelum permintaan/penggeledahan dimulai (izin diperiksa oleh pemanggil).
     *
     * @return null jika lolos, atau kunci pesan error di messages.yml
     */
    public String checkPrerequisites(Player searcher, Player target, SearchType type) {
        if (searcher.getUniqueId().equals(target.getUniqueId())) {
            return "cannot-self";
        }
        if (type == SearchType.FORCE && plugin.config().requireForceTargetWhitelist()
                && !plugin.forceTargets().isTarget(target.getUniqueId())) {
            return "target-not-allowed";
        }
        if (searcher.isDead()) {
            return "searcher-dead";
        }
        if (target.isDead()) {
            return "target-unavailable";
        }
        if (!searcher.getWorld().getUID().equals(target.getWorld().getUID())) {
            return "different-world";
        }
        if (distanceSquared(searcher.getLocation(), target.getLocation())
                > plugin.config().maxDistanceSquared()) {
            return "player-too-far";
        }
        if (isBusy(searcher.getUniqueId())) {
            return "searcher-busy";
        }
        if (isBusy(target.getUniqueId())) {
            return "target-busy";
        }
        return null;
    }

    /** Sisa detik cooldown permintaan baru untuk penggeledah ini (0 = boleh). */
    public int cooldownRemainingSeconds(UUID searcher) {
        int cooldown = plugin.config().requestCooldownSeconds();
        if (cooldown <= 0) {
            return 0;
        }
        Long last = lastRequestMillis.get(searcher);
        if (last == null) {
            return 0;
        }
        long remaining = last + cooldown * 1000L - System.currentTimeMillis();
        return remaining <= 0 ? 0 : (int) Math.ceil(remaining / 1000.0);
    }

    public String formatDistance() {
        double distance = plugin.config().maxDistance();
        if (distance == Math.rint(distance)) {
            return String.valueOf((int) distance);
        }
        return String.format(Locale.ROOT, "%.1f", distance);
    }

    // ------------------------------------------------------------------ normal search

    /** Buat permintaan penggeledahan biasa dan buka GUI konfirmasi pada target. */
    public void createNormalRequest(Player searcher, Player target, String reason) {
        MessageManager msg = plugin.messages();
        ConfigManager cfg = plugin.config();
        long now = System.currentTimeMillis();
        int timeout = cfg.requestTimeoutSeconds();

        String cleanReason = (reason == null || reason.isBlank()) ? null : reason;
        SearchRequest request = new SearchRequest(
                searcher.getUniqueId(), searcher.getName(),
                target.getUniqueId(), target.getName(),
                cleanReason, now, now + timeout * 1000L);
        SearchSession session = new SearchSession(SearchType.NORMAL, request);
        session.setRemainingSeconds(timeout);
        register(session);

        if (lastRequestMillis.size() > 256) {
            lastRequestMillis.values().removeIf(time -> now - time > 600_000L);
        }
        lastRequestMillis.put(searcher.getUniqueId(), now);

        plugin.audit().log("REQUEST_DIBUAT", session, null);

        Inventory requestInventory = SearchRequestGUI.create(msg, session);
        session.setRequestInventory(requestInventory);

        msg.send(searcher, "request-sent", "%player%", target.getName());
        msg.send(target, "request-received", "%player%", searcher.getName());
        if (cleanReason != null) {
            msg.send(target, "request-reason", "%reason%", cleanReason);
        }

        InventoryView view = target.openInventory(requestInventory);
        if (view == null) {
            end(session, CancelReason.GUI_OPEN_FAILED);
            return;
        }
        session.addTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> tickRequest(session), 20L, 20L));
    }

    private void tickRequest(SearchSession session) {
        if (session.state() != State.REQUESTED) {
            return;
        }
        session.setRemainingSeconds(session.remainingSeconds() - 1);
        if (session.remainingSeconds() <= 0) {
            end(session, CancelReason.EXPIRED);
            return;
        }
        Inventory inventory = session.requestInventory();
        if (inventory != null) {
            SearchRequestGUI.updateInfo(plugin.messages(), inventory, session);
        }
    }

    /** Target menerima permintaan. Semua kondisi diperiksa ulang sebelum inventory dibuka. */
    public void accept(SearchSession session, Player target) {
        if (session.state() != State.REQUESTED || !session.targetId().equals(target.getUniqueId())) {
            return;
        }
        CancelReason problem = validate(session, true);
        if (problem != null) {
            end(session, problem);
            return;
        }
        Player searcher = Bukkit.getPlayer(session.searcherId());
        if (searcher == null) {
            end(session, CancelReason.DISCONNECT);
            return;
        }
        session.cancelTasks();
        session.setState(State.ACTIVE); // sebelum menutup GUI agar handler close tidak menganggap penolakan
        target.closeInventory();
        plugin.messages().send(target, "request-accepted");
        plugin.messages().send(searcher, "request-accepted-searcher", "%player%", target.getName());
        plugin.audit().log("REQUEST_DITERIMA", session, null);
        activate(session);
    }

    /** Target menolak. Tidak berpengaruh jika sesi sudah tidak berstatus menunggu. */
    public void deny(SearchSession session) {
        if (session.state() == State.REQUESTED) {
            end(session, CancelReason.DENIED);
        }
    }

    // ------------------------------------------------------------------ force search

    /** Mulai mode menunggu pukulan untuk penggeledahan paksa (pemeriksaan sudah dilakukan pemanggil). */
    public void startForce(Player searcher, Player target) {
        MessageManager msg = plugin.messages();
        long now = System.currentTimeMillis();
        int hitTimeout = plugin.config().forceHitTimeoutSeconds();

        SearchRequest request = new SearchRequest(
                searcher.getUniqueId(), searcher.getName(),
                target.getUniqueId(), target.getName(),
                null, now, now + hitTimeout * 1000L);
        SearchSession session = new SearchSession(SearchType.FORCE, request);
        session.setRemainingSeconds(hitTimeout);
        register(session);

        plugin.audit().log("GELEDAH_PAKSA_MENUNGGU_PUKULAN", session, null);

        msg.send(searcher, "force-hit-instruction");
        msg.send(searcher, "force-hit-target",
                "%player%", target.getName(), "%seconds%", String.valueOf(hitTimeout));
        searcher.sendActionBar(msg.component("force-hit-actionbar",
                "%player%", target.getName(), "%seconds%", String.valueOf(hitTimeout)));

        session.addTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> tickHitWait(session), 20L, 20L));
    }

    private void tickHitWait(SearchSession session) {
        if (session.state() != State.AWAITING_HIT) {
            return;
        }
        session.setRemainingSeconds(session.remainingSeconds() - 1);
        if (session.remainingSeconds() <= 0) {
            end(session, CancelReason.HIT_TIMEOUT);
            return;
        }
        Player searcher = Bukkit.getPlayer(session.searcherId());
        if (searcher != null) {
            searcher.sendActionBar(plugin.messages().component("force-hit-actionbar",
                    "%player%", session.targetName(),
                    "%seconds%", String.valueOf(session.remainingSeconds())));
        }
    }

    /**
     * Dipanggil dari listener serangan. Hanya pukulan PENGGELEDAH kepada TARGET YANG BENAR
     * pada sesi yang sedang menunggu pukulan yang menghasilkan TRIGGERED.
     */
    public HitResult handleTriggerHit(Player attacker, Entity victim) {
        SearchSession session = byPlayer.get(attacker.getUniqueId());
        if (session == null || session.type() != SearchType.FORCE
                || !session.searcherId().equals(attacker.getUniqueId())) {
            return HitResult.NOT_APPLICABLE;
        }
        boolean isTarget = victim.getUniqueId().equals(session.targetId());

        if (session.state() == State.AWAITING_HIT) {
            if (!isTarget) {
                if (victim instanceof Player) {
                    notifyWrongTarget(session, attacker);
                    return HitResult.WRONG_TARGET;
                }
                return HitResult.NOT_APPLICABLE;
            }
            CancelReason problem = validate(session, true);
            if (problem != null) {
                // Pukulan ini sudah ditujukan untuk memicu penggeledahan: sesi dibatalkan dan
                // pukulannya tetap diredam agar tidak berubah menjadi serangan sungguhan.
                end(session, problem);
                return HitResult.TRIGGERED_DUPLICATE;
            }
            session.setLastTriggerTick(Bukkit.getCurrentTick());
            startProgress(session);
            return HitResult.TRIGGERED;
        }

        if (session.state() == State.PROGRESS && isTarget
                && session.lastTriggerTick() == Bukkit.getCurrentTick()) {
            return HitResult.TRIGGERED_DUPLICATE;
        }
        return HitResult.NOT_APPLICABLE;
    }

    private void notifyWrongTarget(SearchSession session, Player attacker) {
        long now = System.currentTimeMillis();
        if (now - session.lastWrongHitMillis() < WRONG_HIT_MESSAGE_COOLDOWN_MILLIS) {
            return;
        }
        session.setLastWrongHitMillis(now);
        plugin.messages().send(attacker, "force-wrong-target", "%player%", session.targetName());
    }

    private void startProgress(SearchSession session) {
        session.cancelTasks(); // hentikan hitung mundur menunggu pukulan
        session.setState(State.PROGRESS);
        session.setProgressTicks(0);
        plugin.audit().log("GELEDAH_PAKSA_DIPICU", session, null);

        int totalTicks = plugin.config().forceSearchDurationSeconds() * 20;
        session.addTask(Bukkit.getScheduler().runTaskTimer(plugin,
                () -> tickProgress(session, totalTicks), 0L, PROGRESS_INTERVAL_TICKS));
    }

    private void tickProgress(SearchSession session, int totalTicks) {
        if (session.state() != State.PROGRESS) {
            return;
        }
        Player searcher = Bukkit.getPlayer(session.searcherId());
        Player target = Bukkit.getPlayer(session.targetId());
        if (searcher == null || target == null) {
            end(session, CancelReason.DISCONNECT);
            return;
        }
        int percent = (int) Math.min(100L, (session.progressTicks() * 100L) / totalTicks);
        searcher.sendActionBar(plugin.messages().component("force-progress",
                "%progress%", String.valueOf(percent), "%bar%", buildBar(percent)));

        if (session.progressTicks() >= totalTicks) {
            finishProgress(session, searcher, target);
            return;
        }
        session.setProgressTicks(session.progressTicks() + PROGRESS_INTERVAL_TICKS);
    }

    private void finishProgress(SearchSession session, Player searcher, Player target) {
        CancelReason problem = validate(session, true);
        if (problem != null) {
            end(session, problem);
            return;
        }
        session.cancelTasks();
        session.setState(State.ACTIVE);
        plugin.messages().send(searcher, "force-completed-searcher", "%player%", target.getName());
        plugin.messages().send(target, "force-started-target", "%player%", searcher.getName());
        plugin.audit().log("GELEDAH_PAKSA_LOADING_SELESAI", session, null);
        activate(session);
    }

    private static String buildBar(int percent) {
        int filled = Math.max(0, Math.min(PROGRESS_BAR_LENGTH, percent * PROGRESS_BAR_LENGTH / 100));
        StringBuilder bar = new StringBuilder("&8[&a");
        for (int i = 0; i < filled; i++) {
            bar.append('\u2588');
        }
        bar.append("&7");
        for (int i = filled; i < PROGRESS_BAR_LENGTH; i++) {
            bar.append('\u2591');
        }
        bar.append("&8]");
        return bar.toString();
    }

    // ------------------------------------------------------------------ activation

    private void activate(SearchSession session) {
        Player searcher = Bukkit.getPlayer(session.searcherId());
        Player target = Bukkit.getPlayer(session.targetId());
        if (searcher == null || target == null) {
            end(session, CancelReason.DISCONNECT);
            return;
        }
        SearchInventoryGUI gui = new SearchInventoryGUI(plugin.messages(), session);
        gui.renderAll(target.getInventory());
        session.setGui(gui);
        session.setStartedAtMillis(System.currentTimeMillis());

        InventoryView view = searcher.openInventory(gui.getInventory());
        if (view == null) {
            end(session, CancelReason.GUI_OPEN_FAILED);
            return;
        }
        plugin.audit().log("SESI_DIMULAI", session, null);
    }

    // ------------------------------------------------------------------ validation

    /** Validasi penuh saat ini (dipakai sebelum aksi klik GUI). @return null jika sesi masih sah. */
    public CancelReason validateNow(SearchSession session) {
        return validate(session, session.state() != State.REQUESTED);
    }

    /** Validasi dan akhiri sesi bila tidak sah lagi. */
    public void validateAndEnd(SearchSession session) {
        if (session.state() == State.ENDED || session.isPendingEnd()) {
            return;
        }
        CancelReason problem = validateNow(session);
        if (problem != null) {
            end(session, problem);
        }
    }

    /**
     * @param geometry true = periksa juga dunia, jarak, izin, dan GUI terbuka
     */
    private CancelReason validate(SearchSession session, boolean geometry) {
        Player searcher = Bukkit.getPlayer(session.searcherId());
        Player target = Bukkit.getPlayer(session.targetId());
        if (searcher == null || !searcher.isOnline() || target == null || !target.isOnline()) {
            return CancelReason.DISCONNECT;
        }
        if (searcher.isDead() || target.isDead()) {
            return CancelReason.DEATH;
        }
        if (!geometry) {
            return null;
        }
        if (!searcher.getWorld().getUID().equals(target.getWorld().getUID())) {
            return CancelReason.WORLD_CHANGE;
        }
        if (distanceSquared(searcher.getLocation(), target.getLocation())
                > plugin.config().maxDistanceSquared()) {
            return CancelReason.DISTANCE;
        }
        if (session.type() == SearchType.NORMAL) {
            if (!plugin.permissions().canUseSearch(searcher)) {
                return CancelReason.PERMISSION_REVOKED;
            }
        } else {
            if (!plugin.permissions().canUseForce(searcher)) {
                return CancelReason.PERMISSION_REVOKED;
            }
            if (plugin.config().requireForceTargetWhitelist()
                    && !plugin.forceTargets().isTarget(target.getUniqueId())) {
                return CancelReason.TARGET_NOT_ALLOWED;
            }
        }
        if (session.state() == State.ACTIVE && session.gui() != null && !isGuiOpen(searcher, session)) {
            return CancelReason.CLOSED_BY_SEARCHER;
        }
        return null;
    }

    private boolean isGuiOpen(Player searcher, SearchSession session) {
        InventoryHolder holder = searcher.getOpenInventory().getTopInventory().getHolder(false);
        return holder instanceof SearchInventoryGUI.Holder guiHolder
                && guiHolder.sessionId().equals(session.id());
    }

    /** Pemeriksaan cepat dari PlayerMoveEvent: jarak/dunia terhadap pemain lawan. */
    public void checkMove(SearchSession session, Player moved, Location to) {
        Player other = Bukkit.getPlayer(session.otherOf(moved.getUniqueId()));
        if (other == null) {
            end(session, CancelReason.DISCONNECT);
            return;
        }
        if (to.getWorld() == null || !to.getWorld().getUID().equals(other.getWorld().getUID())) {
            end(session, CancelReason.WORLD_CHANGE);
            return;
        }
        if (distanceSquared(to, other.getLocation()) > plugin.config().maxDistanceSquared()) {
            end(session, CancelReason.DISTANCE);
        }
    }

    private void checkAll() {
        for (SearchSession session : new ArrayList<>(byId.values())) {
            if (session.state() == State.ENDED || session.isPendingEnd()) {
                continue;
            }
            CancelReason problem = validate(session, session.state() != State.REQUESTED);
            if (problem != null) {
                end(session, problem);
            }
        }
    }

    private static double distanceSquared(Location a, Location b) {
        return a.distanceSquared(b);
    }

    // ------------------------------------------------------------------ ending

    /** Akhiri sesi sekarang juga (tutup GUI, hentikan task, hapus status, kirim alasan). */
    public void end(SearchSession session, CancelReason reason) {
        end(session, reason, true);
    }

    /** Akhiri sesi satu tick kemudian; aman dipanggil dari dalam event klik inventory. */
    public void endLater(SearchSession session, CancelReason reason) {
        if (session.state() == State.ENDED || session.isPendingEnd()) {
            return;
        }
        session.setPendingEnd(true);
        Bukkit.getScheduler().runTask(plugin, () -> end(session, reason, true));
    }

    public void endAllFor(UUID player, CancelReason reason) {
        SearchSession session = byPlayer.get(player);
        if (session != null) {
            end(session, reason);
        }
    }

    /**
     * @param closeViews false bila dipanggil dari dalam InventoryCloseEvent (GUI sudah menutup sendiri)
     */
    public void end(SearchSession session, CancelReason reason, boolean closeViews) {
        if (session.state() == State.ENDED) {
            return;
        }
        State previous = session.state();
        session.setState(State.ENDED);
        session.setPendingEnd(true);
        session.cancelTasks();

        byId.remove(session.id());
        byPlayer.remove(session.searcherId(), session);
        byPlayer.remove(session.targetId(), session);

        Player searcher = Bukkit.getPlayer(session.searcherId());
        Player target = Bukkit.getPlayer(session.targetId());

        if (closeViews) {
            closeViewIfOurs(searcher, session);
            closeViewIfOurs(target, session);
        }

        MessageManager msg = plugin.messages();
        boolean targetKnows = session.type() == SearchType.NORMAL || previous == State.ACTIVE;
        if (searcher != null && reason.searcherKey() != null) {
            msg.send(searcher, reason.searcherKey(),
                    "%player%", session.targetName(), "%distance%", formatDistance());
        }
        if (target != null && targetKnows && reason.targetKey() != null) {
            msg.send(target, reason.targetKey(),
                    "%player%", session.searcherName(), "%distance%", formatDistance());
        }

        String event;
        String detail = "alasan=" + reason.auditCode();
        switch (previous) {
            case REQUESTED:
                event = "REQUEST_" + reason.auditCode();
                break;
            case AWAITING_HIT:
            case PROGRESS:
                event = "GELEDAH_PAKSA_DIBATALKAN";
                break;
            default:
                event = "SESI_BERAKHIR";
                if (session.startedAtMillis() > 0) {
                    long seconds = (System.currentTimeMillis() - session.startedAtMillis()) / 1000L;
                    detail += " durasi=" + seconds + "dtk";
                }
                break;
        }
        plugin.audit().log(event, session, detail);

        session.setGui(null);
        session.setRequestInventory(null);
    }

    private void closeViewIfOurs(Player player, SearchSession session) {
        if (player == null) {
            return;
        }
        InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder(false);
        boolean ours = (holder instanceof SearchInventoryGUI.Holder guiHolder
                && guiHolder.sessionId().equals(session.id()))
                || (holder instanceof SearchRequestGUI.Holder requestHolder
                && requestHolder.sessionId().equals(session.id()));
        if (ours) {
            player.closeInventory();
        }
    }

    private void register(SearchSession session) {
        byId.put(session.id(), session);
        byPlayer.put(session.searcherId(), session);
        byPlayer.put(session.targetId(), session);
    }
}
