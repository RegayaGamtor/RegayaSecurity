package com.regayagamtor.regayasecurity.model;

import com.regayagamtor.regayasecurity.gui.SearchInventoryGUI;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Satu sesi penggeledahan (dari permintaan sampai berakhir).
 * Hanya diakses dari main thread server.
 */
public final class SearchSession {

    public enum State {
        /** Penggeledahan biasa: menunggu jawaban target. */
        REQUESTED,
        /** Penggeledahan paksa: menunggu penggeledah memukul target. */
        AWAITING_HIT,
        /** Penggeledahan paksa: animasi loading berjalan. */
        PROGRESS,
        /** GUI penggeledahan terbuka. */
        ACTIVE,
        /** Sudah berakhir; tidak boleh dipakai lagi. */
        ENDED
    }

    private final UUID id = UUID.randomUUID();
    private final SearchType type;
    private final SearchRequest request;
    private final List<BukkitTask> tasks = new ArrayList<>();

    private State state;
    private SearchInventoryGUI gui;
    private Inventory requestInventory;
    private long startedAtMillis;
    private int conflicts;
    private boolean pendingEnd;
    private boolean renderScheduled;
    private int remainingSeconds;
    private int progressTicks;
    private int lastTriggerTick = -1;
    private long lastWrongHitMillis;

    public SearchSession(SearchType type, SearchRequest request) {
        this.type = type;
        this.request = request;
        this.state = type == SearchType.NORMAL ? State.REQUESTED : State.AWAITING_HIT;
    }

    public UUID id() {
        return id;
    }

    public SearchType type() {
        return type;
    }

    public SearchRequest request() {
        return request;
    }

    public UUID searcherId() {
        return request.requesterId();
    }

    public UUID targetId() {
        return request.targetId();
    }

    public String searcherName() {
        return request.requesterName();
    }

    public String targetName() {
        return request.targetName();
    }

    public boolean involves(UUID player) {
        return player.equals(searcherId()) || player.equals(targetId());
    }

    public UUID otherOf(UUID player) {
        return player.equals(searcherId()) ? targetId() : searcherId();
    }

    public State state() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public SearchInventoryGUI gui() {
        return gui;
    }

    public void setGui(SearchInventoryGUI gui) {
        this.gui = gui;
    }

    public Inventory requestInventory() {
        return requestInventory;
    }

    public void setRequestInventory(Inventory requestInventory) {
        this.requestInventory = requestInventory;
    }

    public long startedAtMillis() {
        return startedAtMillis;
    }

    public void setStartedAtMillis(long startedAtMillis) {
        this.startedAtMillis = startedAtMillis;
    }

    public int conflicts() {
        return conflicts;
    }

    public int incrementConflicts() {
        return ++conflicts;
    }

    public void resetConflicts() {
        conflicts = 0;
    }

    public boolean isPendingEnd() {
        return pendingEnd;
    }

    public void setPendingEnd(boolean pendingEnd) {
        this.pendingEnd = pendingEnd;
    }

    public boolean isRenderScheduled() {
        return renderScheduled;
    }

    public void setRenderScheduled(boolean renderScheduled) {
        this.renderScheduled = renderScheduled;
    }

    public int remainingSeconds() {
        return remainingSeconds;
    }

    public void setRemainingSeconds(int remainingSeconds) {
        this.remainingSeconds = remainingSeconds;
    }

    public int progressTicks() {
        return progressTicks;
    }

    public void setProgressTicks(int progressTicks) {
        this.progressTicks = progressTicks;
    }

    public int lastTriggerTick() {
        return lastTriggerTick;
    }

    public void setLastTriggerTick(int lastTriggerTick) {
        this.lastTriggerTick = lastTriggerTick;
    }

    public long lastWrongHitMillis() {
        return lastWrongHitMillis;
    }

    public void setLastWrongHitMillis(long lastWrongHitMillis) {
        this.lastWrongHitMillis = lastWrongHitMillis;
    }

    public void addTask(BukkitTask task) {
        tasks.add(task);
    }

    public void cancelTasks() {
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
    }
}
