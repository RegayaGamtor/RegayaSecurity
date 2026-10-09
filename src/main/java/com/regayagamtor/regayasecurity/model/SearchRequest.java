package com.regayagamtor.regayasecurity.model;

import java.util.UUID;

/**
 * Data permintaan penggeledahan (tidak dapat diubah). Identitas selalu UUID;
 * nama hanya disimpan untuk tampilan dan audit.
 *
 * @param reason alasan penggeledahan (boleh null); sudah dibersihkan dari kode warna
 */
public record SearchRequest(
        UUID requesterId,
        String requesterName,
        UUID targetId,
        String targetName,
        String reason,
        long createdAtMillis,
        long expiresAtMillis) {
}
