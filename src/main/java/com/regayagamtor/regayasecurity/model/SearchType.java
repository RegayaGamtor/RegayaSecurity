package com.regayagamtor.regayasecurity.model;

/** Jenis penggeledahan. */
public enum SearchType {
    /** Penggeledahan biasa: wajib persetujuan target. */
    NORMAL("type-normal", "BIASA"),
    /** Penggeledahan paksa: tanpa persetujuan, hanya untuk target terdaftar. */
    FORCE("type-force", "PAKSA");

    private final String messageKey;
    private final String auditName;

    SearchType(String messageKey, String auditName) {
        this.messageKey = messageKey;
        this.auditName = auditName;
    }

    public String messageKey() {
        return messageKey;
    }

    public String auditName() {
        return auditName;
    }
}
