package com.regayagamtor.regayasecurity.model;

/**
 * Alasan berakhirnya permintaan/sesi. Setiap alasan memetakan ke kunci pesan
 * di messages.yml untuk penggeledah dan target (null = tidak ada pesan).
 */
public enum CancelReason {
    DENIED("DITOLAK", "request-denied-searcher", "request-denied"),
    DENIED_BY_CLOSE("DITUTUP_TARGET", "request-denied-searcher", "request-closed"),
    EXPIRED("KEDALUWARSA", "request-expired", "request-expired"),
    DISTANCE("JARAK_TERLALU_JAUH", "session-cancelled-distance", "session-cancelled-distance"),
    WORLD_CHANGE("PINDAH_DUNIA", "session-cancelled-world", "session-cancelled-world"),
    DISCONNECT("TERPUTUS", "session-cancelled-disconnect", "session-cancelled-disconnect"),
    DEATH("PEMAIN_MATI", "session-cancelled-death", "session-cancelled-death"),
    TARGET_UNAVAILABLE("TARGET_TIDAK_TERSEDIA", "session-cancelled-unavailable", "session-cancelled-unavailable"),
    PERMISSION_REVOKED("IZIN_DICABUT", "session-cancelled-permission", "session-cancelled-unavailable"),
    TARGET_NOT_ALLOWED("TARGET_TIDAK_DIIZINKAN", "target-not-allowed", "session-cancelled-unavailable"),
    CONFLICT("KONFLIK_INVENTORY", "inventory-conflict", "inventory-conflict"),
    HIT_TIMEOUT("WAKTU_PUKUL_HABIS", "force-hit-timeout", null),
    CLOSED_BY_SEARCHER("SELESAI_DITUTUP_PENGGELEDAH", "session-finished", "session-finished"),
    GUI_OPEN_FAILED("GAGAL_BUKA_GUI", "gui-open-failed", "session-cancelled-unavailable"),
    SHUTDOWN("PLUGIN_DIMATIKAN", "session-cancelled-shutdown", "session-cancelled-shutdown");

    private final String auditCode;
    private final String searcherKey;
    private final String targetKey;

    CancelReason(String auditCode, String searcherKey, String targetKey) {
        this.auditCode = auditCode;
        this.searcherKey = searcherKey;
        this.targetKey = targetKey;
    }

    public String auditCode() {
        return auditCode;
    }

    public String searcherKey() {
        return searcherKey;
    }

    public String targetKey() {
        return targetKey;
    }
}
