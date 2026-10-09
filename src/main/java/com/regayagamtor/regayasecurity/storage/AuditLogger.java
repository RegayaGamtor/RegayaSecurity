package com.regayagamtor.regayasecurity.storage;

import com.regayagamtor.regayasecurity.RegayaSecurity;
import com.regayagamtor.regayasecurity.model.SearchRequest;
import com.regayagamtor.regayasecurity.model.SearchSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Catatan audit penggeledahan ke plugins/RegayaSecurity/logs/audit.log.
 * Baris disusun di main thread (dari data tidak berubah) lalu ditulis di thread terpisah,
 * sehingga operasi file tidak membebani server. Isi inventory TIDAK pernah dicatat.
 */
public final class AuditLogger {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final RegayaSecurity plugin;
    private final Path logFile;
    private final ExecutorService executor;

    public AuditLogger(RegayaSecurity plugin) {
        this.plugin = plugin;
        this.logFile = plugin.getDataFolder().toPath().resolve("logs").resolve("audit.log");
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "RegayaSecurity-Audit");
            thread.setDaemon(true);
            return thread;
        });
        try {
            Files.createDirectories(logFile.getParent());
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Tidak dapat membuat folder log audit", ex);
        }
    }

    /** Catat satu kejadian untuk sebuah sesi. Tidak melakukan apa pun jika log-search-actions=false. */
    public void log(String event, SearchSession session, String detail) {
        if (!plugin.config().logSearchActions()) {
            return;
        }
        SearchRequest request = session.request();
        String started = session.startedAtMillis() > 0 ? format(session.startedAtMillis()) : "-";
        StringBuilder line = new StringBuilder(256);
        line.append(format(System.currentTimeMillis()))
                .append(" | ").append(event)
                .append(" | sesi=").append(session.id().toString(), 0, 8)
                .append(" | jenis=").append(session.type().auditName())
                .append(" | penggeledah=").append(clean(request.requesterName()))
                .append(" (").append(request.requesterId()).append(')')
                .append(" | target=").append(clean(request.targetName()))
                .append(" (").append(request.targetId()).append(')')
                .append(" | diminta=").append(format(request.createdAtMillis()))
                .append(" | mulai=").append(started);
        if (request.reason() != null && !request.reason().isBlank()) {
            line.append(" | alasan_geledah=").append(clean(request.reason()));
        }
        if (detail != null && !detail.isBlank()) {
            line.append(" | detail=").append(clean(detail));
        }
        line.append(System.lineSeparator());
        enqueue(line.toString());
    }

    private void enqueue(String line) {
        try {
            executor.execute(() -> write(line));
        } catch (RejectedExecutionException ignored) {
            // Plugin sedang dimatikan; baris diabaikan.
        }
    }

    private void write(String line) {
        try {
            Files.createDirectories(logFile.getParent());
            Files.writeString(logFile, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Gagal menulis log audit", ex);
        }
    }

    private static String format(long millis) {
        return TIME_FORMAT.format(Instant.ofEpochMilli(millis));
    }

    /** Cegah injeksi baris/kolom palsu dari teks buatan pemain. */
    private static String clean(String text) {
        if (text == null) {
            return "-";
        }
        String cleaned = text.replaceAll("[\\p{Cntrl}|]", " ").trim();
        return cleaned.length() > 120 ? cleaned.substring(0, 120) : cleaned;
    }

    /** Selesaikan penulisan yang masih antre lalu hentikan thread. */
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
