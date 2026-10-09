package com.regayagamtor.regayasecurity.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Memuat messages.yml (dengan cadangan default dari dalam jar bila ada kunci yang hilang)
 * dan mengganti placeholder dengan aman: nilai dari pemain dibersihkan dari kode warna
 * sehingga tidak dapat menyuntikkan format.
 */
public final class MessageManager {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private static final Pattern COLOR_CODES = Pattern.compile("(?i)(&#[0-9a-f]{6}|[&\u00A7][0-9a-fk-orx])");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}]");
    private static final int MAX_REASON_LENGTH = 64;

    /** Placeholder yang nilainya dibuat oleh plugin sendiri (bukan input pemain). */
    private static final Set<String> TRUSTED_PLACEHOLDERS = Set.of("%bar%");

    private final JavaPlugin plugin;
    private FileConfiguration messages = new YamlConfiguration();
    private FileConfiguration defaults = new YamlConfiguration();

    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(file);

        YamlConfiguration loadedDefaults = new YamlConfiguration();
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    loadedDefaults = YamlConfiguration.loadConfiguration(reader);
                }
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Gagal membaca messages.yml bawaan: " + ex.getMessage());
        }
        defaults = loadedDefaults;
    }

    /** Hapus kode warna dan karakter kontrol dari teks buatan pemain. */
    public static String sanitize(String input) {
        if (input == null) {
            return "";
        }
        String cleaned = COLOR_CODES.matcher(input).replaceAll("");
        cleaned = cleaned.replace('\u00A7', ' ');
        return CONTROL_CHARS.matcher(cleaned).replaceAll(" ");
    }

    /** Bersihkan alasan penggeledahan: tanpa kode warna, spasi dirapikan, maksimal 64 karakter. */
    public static String cleanReason(String input) {
        String cleaned = sanitize(input).replaceAll("\\s+", " ").trim();
        if (cleaned.length() > MAX_REASON_LENGTH) {
            cleaned = cleaned.substring(0, MAX_REASON_LENGTH).trim();
        }
        return cleaned;
    }

    public String raw(String key) {
        String value = messages.getString(key);
        if (value == null) {
            value = defaults.getString(key);
        }
        return value != null ? value : "&c[pesan '" + key + "' tidak ditemukan di messages.yml]";
    }

    public List<String> rawList(String key) {
        List<String> list = messages.getStringList(key);
        if (list.isEmpty()) {
            list = defaults.getStringList(key);
        }
        return list;
    }

    /** Teks (kode &amp; belum diterjemahkan) dengan placeholder terisi. kv = pasangan placeholder, nilai. */
    public String format(String key, String... kv) {
        return apply(raw(key), kv);
    }

    private String apply(String text, String... kv) {
        String result = text;
        if (kv != null) {
            for (int i = 0; i + 1 < kv.length; i += 2) {
                String placeholder = kv[i];
                String value = kv[i + 1] == null ? "" : kv[i + 1];
                result = result.replace(placeholder,
                        TRUSTED_PLACEHOLDERS.contains(placeholder) ? value : sanitize(value));
            }
        }
        return result;
    }

    /** Ubah teks berkode '&amp;' menjadi Component (italic default dimatikan, cocok untuk item GUI). */
    public Component text(String legacy) {
        return LEGACY.deserialize(legacy).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Component tanpa prefix (untuk GUI, action bar, judul). */
    public Component component(String key, String... kv) {
        return text(format(key, kv));
    }

    /** Daftar baris lore/pesan sebagai Component. */
    public List<Component> lore(String key, String... kv) {
        List<Component> lines = new ArrayList<>();
        for (String line : rawList(key)) {
            lines.add(text(apply(line, kv)));
        }
        return lines;
    }

    public String prefix() {
        return raw("prefix");
    }

    /** Kirim pesan dengan prefix. */
    public void send(CommandSender to, String key, String... kv) {
        if (to == null) {
            return;
        }
        to.sendMessage(text(prefix() + format(key, kv)));
    }

    /** Kirim pesan tanpa prefix. */
    public void sendPlain(CommandSender to, String key, String... kv) {
        if (to == null) {
            return;
        }
        to.sendMessage(text(format(key, kv)));
    }

    /** Kirim semua baris sebuah daftar pesan tanpa prefix. */
    public void sendLines(CommandSender to, String key, String... kv) {
        if (to == null) {
            return;
        }
        for (String line : rawList(key)) {
            to.sendMessage(text(apply(line, kv)));
        }
    }
}
