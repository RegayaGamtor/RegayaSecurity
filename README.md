# RegayaSecurity

Plugin penggeledahan inventory untuk server roleplay Minecraft Java **1.21.11** (Paper).
Developer: **RegayaGamtor** · Package: `com.regayagamtor.regayasecurity` · Output: `RegayaSecurity.jar`

---

## 0. STATUS JUJUR (baca dulu)

| Hal | Status |
|---|---|
| Source code lengkap (24 class Java) | Selesai |
| `pom.xml`, `plugin.yml`, `config.yml`, `messages.yml` | Selesai, sintaks YAML sudah divalidasi |
| Pemeriksaan sintaks Java (javac) | **Lolos** untuk semua file: tidak ada error sintaks |
| Build penuh terhadap Paper API 1.21.11 | **BELUM dilakukan** (lingkungan pembuatan tidak punya akses internet/Maven, sehingga `paper-api` tidak bisa diunduh) |
| `RegayaSecurity.jar` | **Tidak ada**, belum bisa dibuild di sini. Jalankan `mvn clean package` di mesin Anda |
| Pengujian di server 1.21.11 (20 skenario) | **BELUM dijalankan.** Daftar uji dan hasil yang diharapkan ada di bagian 7 |

Pemeriksaan javac di sini hanya membuktikan tidak ada kesalahan sintaks dan tidak ada referensi rusak antar class buatan sendiri.
Kecocokan dengan signature API Paper 1.21.11 baru terbukti setelah Anda menjalankan build. Titik yang paling layak dicek ada di bagian 8.
**Jangan anggap plugin ini teruji sebelum 20 skenario di bagian 7 Anda jalankan.**

---

## 1. Build

Syarat: JDK 21, Maven 3.9+, akses internet ke `repo.papermc.io`.

```bash
mvn clean package
# hasil: target/RegayaSecurity.jar
```

## 2. Instalasi

1. Pastikan server memakai **Paper 1.21.11** dan **Java 21**.
2. Salin `RegayaSecurity.jar` ke folder `plugins/`.
3. Start server. File `config.yml`, `messages.yml`, dan folder `logs/` dibuat otomatis di `plugins/RegayaSecurity/`.
4. Atur permission (bagian 4), lalu `/regayasecurity reload` bila mengubah konfigurasi.

## 3. Command

| Command | Fungsi | Izin |
|---|---|---|
| `/geledah <pemain> [alasan]` | Minta izin menggeledah; target menjawab lewat GUI TERIMA/TOLAK | `regayasecurity.use` |
| `/geledahpaksa <pemain>` | Mulai penggeledahan paksa: pukul target, loading, GUI terbuka | `regayasecurity.force` **atau** terdaftar via `/regayasecurity add` |
| `/regayasecurity reload` | Muat ulang config, messages, data | `regayasecurity.reload` |
| `/regayasecurity add <pemain>` | Daftarkan pengguna penggeledahan paksa | `regayasecurity.admin` |
| `/regayasecurity remove <pemain>` | Cabut akses pengguna paksa | `regayasecurity.admin` |
| `/regayasecurity target add <pemain>` | Izinkan pemain menjadi target paksa | `regayasecurity.target.manage` |
| `/regayasecurity target remove <pemain>` | Cabut izin target paksa | `regayasecurity.target.manage` |
| `/regayasecurity info <pemain>` | Status izin, daftar, dan sesi | `regayasecurity.admin` |

Alias admin: `/rsecurity`.

## 4. Permission

| Permission | Default | Keterangan |
|---|---|---|
| `regayasecurity.use` | op | Penggeledahan biasa |
| `regayasecurity.force` | **false** (tidak diberikan ke siapa pun) | Penggeledahan paksa |
| `regayasecurity.admin` | op | Kelola pengguna paksa, `info`. Mewarisi `target.manage` dan `reload` |
| `regayasecurity.target.manage` | op | Kelola daftar target paksa |
| `regayasecurity.reload` | op | Reload konfigurasi |

**Model akses penggeledahan paksa (tiga lapis, semuanya dicek di server):**

1. **Siapa yang boleh memakai:** pemain dengan node `regayasecurity.force` (mis. lewat LuckPerms) **atau** pemain yang didaftarkan `/regayasecurity add`. Keduanya adalah "role/izin khusus dari administrator".
   `info` menampilkan kedua sumber ini terpisah. Mencabut lewat `remove` tidak menghapus node LuckPerms; cabut juga di LuckPerms.
2. **Siapa yang boleh digeledah:** hanya pemain di daftar target (`target add`), selama `require-force-target-whitelist: true` (default). Izin force **bukan** akses universal.
3. **Setiap klik GUI** memeriksa ulang izin, jarak, dunia, dan status target. Izin yang dicabut di tengah sesi menghentikan sesi.

Contoh LuckPerms:

```
/lp group polisi permission set regayasecurity.use true
/lp group polisi permission set regayasecurity.force true
/lp group admin  permission set regayasecurity.admin true
/lp group admin  permission set regayasecurity.target.manage true
/lp group admin  permission set regayasecurity.reload true
```

Disarankan memberi node `target.manage` dan `reload` secara eksplisit. Pewarisan child dari `plugin.yml` tergantung pengaturan LuckPerms Anda.

## 5. Konfigurasi (`config.yml`)

Semua nilai divalidasi saat dimuat; nilai tidak valid diganti default dan dicatat di console.

| Kunci | Default | Catatan |
|---|---|---|
| `max-distance` | 3.0 | Blok. Lihat catatan jarak di bagian 6 |
| `request-timeout-seconds` | 30 | Tidak dijawab = ditolak |
| `force-search-duration-seconds` | 5 | Lama loading paksa |
| `force-hit-timeout-seconds` | 15 | Batas waktu memukul target |
| `sync-interval-ticks` | 2 | Interval task pengaman sinkronisasi (bukan penyalinan buta) |
| `session-check-interval-ticks` | 5 | Interval pemeriksaan jarak/dunia/izin/GUI |
| `cancel-on-world-change` / `cancel-on-disconnect` / `cancel-on-death` | true | **Dipaksa true** (false diabaikan + peringatan), karena sesi tidak aman dilanjutkan |
| `require-force-target-whitelist` | true | `false` = semua pemain bisa digeledah paksa; ada peringatan di console |
| `log-search-actions` | true | Audit log |
| `request-cooldown-seconds` | 10 | Anti-spam permintaan (tambahan) |
| `max-conflicts` | 10 | Konflik slot **beruntun** sebelum sesi dihentikan (tambahan) |
| `strict-armor-slots` | true | Slot armor hanya menerima armor yang sesuai (tambahan) |

Seluruh teks (pesan, judul GUI, nama/lore tombol) ada di `messages.yml`. Nilai dari pemain (nama, alasan) otomatis dibersihkan dari kode warna sebelum masuk ke pesan.

## 6. Cara kerja keamanan inventory dan keterbatasan teknis

### Desain

- **Inventory asli target adalah satu-satunya sumber kebenaran.** GUI penggeledah tidak menyimpan item sendiri; ia hanya gambar dari slot asli.
- **Write-through:** setiap klik di GUI dibatalkan lalu diterjemahkan plugin menjadi operasi atomik di main thread: baca slot asli → validasi → tulis slot asli target + kursor/inventory penggeledah. Operasi yang didukung: klik kiri/kanan (ambil, taruh, gabung, tukar, bagi dua), shift-klik dua arah, tombol angka (hotbar swap), tombol offhand (F), slot armor dan offhand.
- **Cek konflik (optimistic):** sebelum bertindak, isi slot asli dibandingkan dengan gambar terakhir. Jika target mengubahnya, klik dibatalkan, GUI digambar ulang, dan penggeledah diberi tahu. Tidak pernah ada aksi atas data usang. `max-conflicts` konflik beruntun menghentikan sesi (`inventory-conflict`).
- **Sinkronisasi target → GUI** berbasis event (`markDirty` dari klik/drag/drop/pickup/swap/konsumsi/durability/pasang blok milik target), digabung menjadi satu penggambaran ulang per tick. Task pengaman setiap `sync-interval-ticks` hanya menggambar ulang slot yang **benar-benar berbeda**, untuk perubahan lewat jalur tanpa event (plugin lain).
- **Aksi berbahaya ditutup:** double-click/collect-to-cursor ditolak selalu; drag yang menyentuh GUI dibatalkan; `InventoryMoveItemEvent` yang menyentuh GUI dibatalkan; drop dari slot target tidak didukung; GUI usang ditutup otomatis.
- **Penutupan sesi tidak mengembalikan apa pun.** Item yang sudah dipindahkan tetap di pemilik barunya. Item di kursor penggeledah saat GUI ditutup ditangani vanilla (masuk inventory/dijatuhkan), karena sudah sah miliknya.
- Identitas memakai **UUID**; nama hanya tampilan. Nama dicari lewat `getPlayerExact` (tanpa lookup web yang memblokir server), pemain yang di-*vanish* atau NPC dianggap tidak ada.
- Semua task (`BukkitTask`) milik sesi dibatalkan saat sesi berakhir; `onDisable` mengakhiri semua sesi. Log ditulis di thread terpisah.

### Keterbatasan yang diketahui

1. **GUI bukan container vanilla langsung.** Paper API tidak menyediakan view yang memuat slot armor/offhand milik pemain lain. Solusinya write-through di atas, bukan salinan statis. Konsekuensinya: tampilan bisa tertinggal maksimal satu tick dari kenyataan, dan itu aman karena dicek konflik sebelum tiap aksi.
2. **Tidak tercakup:** item di kursor target, grid crafting 2×2 target, dan ender chest.
3. **Jarak vs jangkauan pukul.** `max-distance` diukur antar posisi kaki. Jangkauan serangan vanilla (~3 blok dari mata ke hitbox) bisa menghasilkan jarak kaki lebih dari 3,0. Dengan default 3,0, pukulan pemicu di batas jangkauan bisa gagal validasi (sesi dibatalkan dengan pesan jarak, pukulan tetap diredam). Server roleplay biasanya perlu `max-distance: 4.0` atau lebih.
4. **Validasi slot armor berbasis nama material** (`*_HELMET`, `*_CHESTPLATE`, `ELYTRA`, `CARVED_PUMPKIN`, `*_HEAD/_SKULL`, dst.). Item kustom berbasis komponen *equippable* dengan material lain bisa ditolak. Atur `strict-armor-slots: false` jika perlu. Curse of Binding **tidak** ditegakkan.
5. **Pukulan pemicu** diredam lewat `PrePlayerAttackEntityEvent` (Paper). Animasi ayunan tangan di sisi klien tetap terlihat, tetapi tidak ada damage, knockback, sapuan, ataupun pengurangan durability. Pukulan tetap dikenali sebagai pemicu walau plugin lain (mis. zona anti-PvP) membatalkannya.
6. **Menutup jendela konfirmasi = menolak** (persetujuan harus eksplisit).
7. **Plugin lain yang menulis inventory target pada tick yang sama** ditangani oleh cek konflik, tetapi tidak ada kunci lintas plugin.
8. Hanya Paper (bukan Folia). Audit log satu file tanpa rotasi otomatis.

## 7. Laporan pengujian

**Status keseluruhan: BELUM DIJALANKAN.** Tabel berikut adalah daftar uji wajib (butuh 2 akun/pemain, satu server Paper 1.21.11) beserta hasil yang diharapkan. Kolom hasil diisi saat Anda menguji. Setelah tiap sesi, hitung ulang item kedua pemain untuk memeriksa duplikasi/kehilangan (skenario 20).

| # | Skenario | Hasil yang diharapkan | Hasil |
|---|---|---|---|
| 1 | `/geledah B`, B klik TERIMA | GUI terbuka untuk A, inventory B tampil | Belum diuji |
| 2 | B klik TOLAK | Sesi batal, kedua pemain diberi pesan, tidak ada item berubah | Belum diuji |
| 3 | B diam 30 detik | Kedaluwarsa otomatis = ditolak, GUI B tertutup | Belum diuji |
| 4 | A > `max-distance` dari B | Pesan jarak, tidak ada permintaan | Belum diuji |
| 5 | Sesi aktif, A mendekat lalu menjauh | Sesi dibatalkan saat melewati batas, GUI A tertutup | Belum diuji |
| 6 | B berpindah dunia saat sesi | Sesi dibatalkan | Belum diuji |
| 7 | B disconnect / mati saat sesi | Sesi dibatalkan, tidak ada error di console | Belum diuji |
| 8 | Pemain tanpa izin menjalankan `/geledahpaksa` | `force-not-authorized` | Belum diuji |
| 9 | Pengguna paksa menarget pemain tidak terdaftar | `target-not-allowed` | Belum diuji |
| 10 | Pukulan pemicu pada target terdaftar | Tanpa damage, knockback, dan durability turun; PvP lain normal | Belum diuji |
| 11 | Setelah pukulan | Action bar 0% sampai 100%, lalu GUI terbuka; target diberi pesan | Belum diuji |
| 12 | Pindah item A↔B lewat klik kiri/kanan | Inventory asli B berubah sesuai, tidak ada duplikasi | Belum diuji |
| 13 | Shift-klik dua arah | Item pindah, sisa yang tak muat tetap di pemilik asal | Belum diuji |
| 14 | Tombol angka dan tombol F pada slot GUI | Pertukaran benar dengan hotbar/offhand A | Belum diuji |
| 15 | Pasang/lepas armor B, termasuk item tidak sesuai | Armor aktif di B; item salah ditolak dengan pesan | Belum diuji |
| 16 | B mengubah inventory saat GUI terbuka | GUI A ikut berubah dalam ≤ 1 tick | Belum diuji |
| 17 | A dan B klik slot yang sama hampir bersamaan | Satu menang, yang lain mendapat `slot-changed`, tanpa dupe | Belum diuji |
| 18 | A menutup GUI (Esc) / klik tombol AKHIRI | Sesi bersih, `/geledah` bisa dipakai lagi, GUI usang tidak bisa dipakai | Belum diuji |
| 19 | Restart server, ubah config, `/regayasecurity reload` | Daftar pengguna/target tetap; nilai tidak valid memakai default + peringatan | Belum diuji |
| 20 | Hitung item A+B sebelum dan sesudah tiap sesi | Total identik, tidak ada item hilang/ganda | Belum diuji |

Uji tambahan yang disarankan: double-click di GUI, drag item melintasi GUI, `/regayasecurity info`, pemain lain memukul target saat A menunggu pukulan (tidak boleh memicu), dan A memukul pemain yang salah (pesan `force-wrong-target`).

## 8. Titik API yang perlu diperhatikan saat build pertama

Kode ditulis untuk Paper 1.21.11. Bila `mvn package` melaporkan error, kemungkinan besar di sini:

- `Inventory#getHolder(boolean)` (Paper), dipakai agar tidak membuat snapshot tile entity
- `io.papermc.paper.event.player.PrePlayerAttackEntityEvent`
- `Bukkit#getOfflinePlayerIfCached(String)` dan `Bukkit#getCurrentTick()` (Paper)
- `JavaPlugin#getPluginMeta()` dan `FileConfigurationOptions#setHeader(List)`
- `PlayerMoveEvent#hasChangedPosition()`
- `Player#sendActionBar(Component)` dan `ItemMeta#displayName/lore(Component)` (Adventure)

## 9. Struktur proyek

```
RegayaSecurity/
├─ pom.xml
├─ README.md
└─ src/main/
   ├─ resources/ plugin.yml, config.yml, messages.yml
   └─ java/com/regayagamtor/regayasecurity/
      ├─ RegayaSecurity.java            (main class, merangkai komponen)
      ├─ command/   SearchCommand, ForceSearchCommand, RegayaSecurityCommand
      ├─ config/    ConfigManager, MessageManager
      ├─ gui/       SearchRequestGUI, SearchInventoryGUI
      ├─ listener/  InventoryListener, CombatListener, PlayerConnectionListener, PlayerMovementListener
      ├─ manager/   SearchSessionManager, InventorySyncManager, PermissionManager, ForceTargetManager
      ├─ model/     SearchSession, SearchRequest, SearchType, CancelReason
      ├─ storage/   AuditLogger, ForceTargetStorage
      └─ util/      ItemUtil, PlayerUtil
```

Audit log: `plugins/RegayaSecurity/logs/audit.log`. Isinya UUID/nama kedua pemain, jenis, waktu permintaan, status (diterima/ditolak/kedaluwarsa), waktu mulai, alasan berakhir, dan penggunaan paksa. **Isi inventory tidak pernah dicatat.**
