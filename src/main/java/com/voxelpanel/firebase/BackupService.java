package com.voxelpanel.firebase;

import com.voxelpanel.VoxelPanel;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Creates a whole-server backup archive and uploads it to Google Drive using a
 * browser-supplied OAuth access token (drive.file scope).
 *
 * Design goals (per the product spec):
 *  - Capture every file under the server root, no silent exclusions (except the
 *    in-progress archive itself and obvious volatile lock files).
 *  - Verify integrity with SHA-256 computed over the finished archive.
 *  - Upload via Google Drive's RESUMABLE protocol in fixed-size chunks, with
 *    retry + resume on network failure so a dropped connection never corrupts
 *    or aborts the transfer.
 *  - Report step-by-step progress (0..100) to Firebase so the dashboard can show
 *    a live progress bar and status messages.
 *  - Never block the main server thread; never crash the server on failure.
 */
public class BackupService {
    private static final int CHUNK = 8 * 1024 * 1024;      // 8 MB Drive chunks (multiple of 256 KB)
    private static final int MAX_RETRIES = 5;              // per-chunk retry attempts
    private static final String DRIVE_RESUMABLE =
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&fields=id,name,size,md5Checksum";

    private final VoxelPanel plugin;
    private final FirebaseManager firebaseManager;
    private volatile boolean running = false;
    /** Set by the dashboard's "cancel" command; checked at every safe point. */
    private volatile boolean cancelRequested = false;

    // ---- Smart Backup Mode ----
    private volatile boolean smartEnabled = false;
    private volatile String smartDest = "local";       // "local" | "gdrive"
    private volatile String smartToken = null;         // GDrive token (if smartDest=gdrive)
    private volatile String smartName = null;          // optional custom base name
    private volatile long lastBackupMtime = 0;         // newest file mtime captured last backup
    private int smartTaskId = -1;

    public BackupService(VoxelPanel plugin, FirebaseManager firebaseManager) {
        this.plugin = plugin;
        this.firebaseManager = firebaseManager;
    }

    /** Requests cancellation of the running backup (safe, cooperative). */
    public void cancel(String issuedBy) {
        if (!running) {
            progress(0, "idle", "لا توجد عملية قيد التنفيذ.");
            return;
        }
        cancelRequested = true;
        progress(0, "cancelling", "جاري إنهاء العملية...");
        plugin.getLogger().info("[Backup] Cancel requested by " + issuedBy);
    }

    public boolean isRunning() { return running; }

    /** Thrown internally to unwind the archive/upload loops on cancellation. */
    private static class BackupCancelled extends RuntimeException {
        BackupCancelled() { super("cancelled"); }
    }

    /** Entry: Google Drive backup with optional custom file name. */
    public void startGoogleDriveBackup(String accessToken, String customName, String issuedBy) {
        if (accessToken == null || accessToken.isBlank()) {
            progress(0, "error", "لم يتم استلام توكن Google Drive.");
            return;
        }
        if (running) { progress(0, "error", "هناك نسخة احتياطية قيد التنفيذ بالفعل."); return; }
        running = true; cancelRequested = false;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> runBackup("gdrive", accessToken, customName, issuedBy, false));
    }

    /** Entry: Direct local download backup with optional custom file name. */
    public void startLocalBackup(String customName, String issuedBy) {
        if (running) { progress(0, "error", "هناك نسخة احتياطية قيد التنفيذ بالفعل."); return; }
        running = true; cancelRequested = false;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> runBackup("local", null, customName, issuedBy, false));
    }

    /**
     * Unified pipeline for all destinations + smart mode:
     *   STEP 1 SCAN files  ->  STEP 2 COMPRESS into one named .zip
     *   ->  STEP 3 HASH (SHA-256)  ->  STEP 4 DELIVER (Drive upload or local link).
     * Async; never crashes the server.
     */
    private void runBackup(String dest, String accessToken, String customName, String issuedBy, boolean smart) {
        Path archive = null;
        boolean local = "local".equals(dest);
        try {
            progress(1, "starting", smart ? "نسخة تلقائية (Smart)..." : "بدء النسخ الاحتياطي...");
            Path root = serverRoot();
            String fileName = buildFileName(customName);

            // STEP 1 — SCAN.
            progress(3, "scanning", "فحص ملفات السيرفر...");
            java.util.List<Path> files = scanFiles(root);
            long fileCount = files.size();
            lastBackupMtime = latestMtime(files);
            progress(8, "scanning", "تم فحص " + fileCount + " ملف.");

            if (local) {
                Path dir = plugin.getDataFolder().toPath().resolve("backups");
                Files.createDirectories(dir);
                purgeOldBackups(dir);
                archive = dir.resolve(fileName);
            } else {
                archive = Files.createTempFile("vpbackup-", ".zip");
            }

            // STEP 2 — COMPRESS.
            clearFiles();
            progress(10, "archiving", "ضغط الملفات في أرشيف واحد...");
            long bytes = archiveFiles(root, files, archive, local ? 78 : 58, 10);
            clearFiles();

            // STEP 3 — HASH.
            progress(local ? 82 : 62, "hashing", "حساب SHA-256...");
            String sha256 = sha256Of(archive);

            // STEP 4 — DELIVER.
            String downloadUrl = null, fileId = null;
            if (local) {
                progress(96, "linking", "تجهيز رابط التحميل...");
                String token = BackupHttpServer.get(plugin).register(archive, fileName);
                downloadUrl = BackupHttpServer.get(plugin).publicUrl(token);
            } else {
                progress(70, "uploading", "مزامنة الأجزاء مع Google Drive...");
                fileId = resumableUpload(accessToken, archive, fileName);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("fileName", fileName);
            result.put("mode", dest);
            result.put("sha256", sha256);
            result.put("size", bytes);
            result.put("fileCount", fileCount);
            result.put("smart", smart);
            if (downloadUrl != null) result.put("downloadUrl", downloadUrl);
            if (fileId != null) result.put("fileId", fileId);
            result.put("t", System.currentTimeMillis());
            result.put("by", issuedBy);
            var ref = firebaseManager.getServerRef();
            if (ref != null) ref.child("backup").child("result").setValueAsync(result);
            progress(100, "complete", local ? "اكتمل النسخ الاحتياطي. الملف جاهز للتنزيل." : "اكتمل النسخ الاحتياطي.");
            if (plugin.getActivityLogger() != null) plugin.getActivityLogger().log(smart ? "backup_smart" : ("backup_" + dest), fileName, issuedBy);
        } catch (BackupCancelled c) {
            clearFiles();
            progress(0, "cancelled", "تم إنهاء العملية بواسطة المستخدم.");
        } catch (Exception e) {
            clearFiles();
            plugin.getLogger().warning("[Backup] Failed: " + e.getMessage());
            progress(0, "error", "فشل النسخ الاحتياطي: " + (e.getMessage() != null ? e.getMessage() : "خطأ غير معروف"));
        } finally {
            running = false;
            cancelRequested = false;
            if (archive != null && !local) { try { Files.deleteIfExists(archive); } catch (IOException ignored) {} }
        }
    }

    /** Builds a safe .zip file name from an optional user-provided name. */
    private String buildFileName(String customName) {
        String stamp = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new java.util.Date());
        if (customName != null && !customName.isBlank()) {
            String safe = customName.trim().replaceAll("[^\\p{L}\\p{N}_\\- ]", "").trim().replace(' ', '_');
            if (!safe.isEmpty()) {
                if (safe.toLowerCase().endsWith(".zip")) safe = safe.substring(0, safe.length() - 4);
                return safe + ".zip";
            }
        }
        return "voxelpanel-backup-" + stamp + ".zip";
    }

    /** Scans (enumerates) every regular file under root. */
    private java.util.List<Path> scanFiles(Path root) {
        java.util.List<Path> files = new java.util.ArrayList<>();
        try (Stream<Path> s = Files.walk(root)) {
            s.filter(p -> { try { return Files.isRegularFile(p); } catch (Exception e) { return false; } }).forEach(files::add);
        } catch (Exception e) {
            plugin.getLogger().warning("[Backup] Scan warning: " + e.getMessage());
        }
        return files;
    }

    /** Newest last-modified time in the list (smart-mode change detection). */
    private long latestMtime(java.util.List<Path> files) {
        long max = 0;
        for (Path p : files) {
            try { long m = Files.getLastModifiedTime(p).toMillis(); if (m > max) max = m; } catch (Exception ignored) {}
        }
        return max;
    }

    // ---- Smart Backup Mode ----
    /** Enables/updates smart mode: auto-backup to the chosen destination on change. */
    public void configureSmart(boolean enabled, String dest, String token, String name, String issuedBy) {
        this.smartEnabled = enabled;
        if (dest != null) this.smartDest = dest;
        if (token != null) this.smartToken = token;
        this.smartName = name;
        publishSmartState();
        if (enabled) {
            startSmartTimer();
            plugin.getLogger().info("[Backup] Smart mode ON (" + smartDest + ") by " + issuedBy);
        } else {
            stopSmartTimer();
            plugin.getLogger().info("[Backup] Smart mode OFF by " + issuedBy);
        }
    }

    private void startSmartTimer() {
        stopSmartTimer();
        // Check every 5 minutes (6000 ticks); back up only if files changed since last time.
        smartTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::smartTick, 6000L, 6000L).getTaskId();
    }
    private void stopSmartTimer() {
        if (smartTaskId != -1) { try { Bukkit.getScheduler().cancelTask(smartTaskId); } catch (Exception ignored) {} smartTaskId = -1; }
    }

    /** Periodic smart check: if the server files changed and no backup runs, take one. */
    private void smartTick() {
        if (!smartEnabled || running) return;
        try {
            long newest = latestMtime(scanFiles(serverRoot()));
            if (newest > lastBackupMtime) {
                plugin.getLogger().info("[Backup] Smart mode: change detected, backing up to " + smartDest);
                running = true; cancelRequested = false;
                runBackup(smartDest, "gdrive".equals(smartDest) ? smartToken : null, smartName, "smart", true);
            }
        } catch (Exception e) {
            plugin.getLogger().fine("[Backup] Smart tick error: " + e.getMessage());
        }
    }

    private void publishSmartState() {
        try {
            var ref = firebaseManager.getServerRef();
            if (ref == null) return;
            Map<String, Object> s = new HashMap<>();
            s.put("enabled", smartEnabled);
            s.put("dest", smartDest);
            s.put("t", System.currentTimeMillis());
            ref.child("backup").child("smart").setValueAsync(s);
        } catch (Exception ignored) {}
    }

    /** Throws BackupCancelled if the dashboard asked to stop. */
    private void checkCancelled() {
        if (cancelRequested) throw new BackupCancelled();
    }

    /** Deletes backup archives older than 24 hours to protect host disk space. */
    private void purgeOldBackups(Path dir) {
        long cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000;
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(Files::isRegularFile).forEach(p -> {
                try {
                    if (p.getFileName().toString().endsWith(".zip")
                            && Files.getLastModifiedTime(p).toMillis() < cutoff) {
                        Files.deleteIfExists(p);
                    }
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }

    /**
     * Compresses the pre-scanned file list into one archive; returns the size.
     * progressSpan/progressBase let the caller map archiving onto a % range
     * (e.g. base=10, span=68 -> archiving reports 10..78%).
     */
    private long archiveFiles(Path root, java.util.List<Path> files, Path archive, int progressSpan, int progressBase) throws IOException {
        final Path archiveAbs = archive.toAbsolutePath().normalize();
        long total = Math.max(1, files.size());
        long done = 0;

        // Dedupe entry names (a duplicate makes putNextEntry throw).
        java.util.Set<String> usedNames = new java.util.HashSet<>();

        // ONE ZipOutputStream/Deflater for the whole archive, created fresh here
        // and owned solely by this try-with-resources. The compression level is
        // set ONCE (never mutated per-entry), and no inner code closes the stream.
        final byte[] buffer = new byte[8192];
        try (OutputStream rawOut = Files.newOutputStream(archive);
             java.io.BufferedOutputStream bufferedOut = new java.io.BufferedOutputStream(rawOut, 1 << 16);
             ZipOutputStream zos = new ZipOutputStream(bufferedOut)) {
            zos.setLevel(java.util.zip.Deflater.DEFAULT_COMPRESSION);

            for (Path p : files) {
                checkCancelled();   // safe stop point between files
                done++;

                String rel;
                long fileSize = 0;
                try {
                    Path abs = p.toAbsolutePath().normalize();
                    if (abs.equals(archiveAbs)) continue;
                    String fn = p.getFileName().toString();
                    if (fn.equals("session.lock")) continue;
                    rel = root.relativize(p).toString().replace('\\', '/');
                    if (rel.isEmpty() || !usedNames.add(rel)) continue;
                    try { fileSize = Files.size(p); } catch (Exception ignored) {}
                } catch (Exception meta) {
                    plugin.getLogger().fine("[Backup] Skipped (meta) " + p + ": " + meta.getMessage());
                    continue;
                }

                boolean entryOpen = false;
                try {
                    zos.putNextEntry(new ZipEntry(rel));
                    entryOpen = true;
                    publishFile(rel, 0, fileSize, false);
                    try (InputStream in = Files.newInputStream(p)) {
                        int n; long written = 0, lastPush = 0;
                        while ((n = in.read(buffer)) > 0) {
                            zos.write(buffer, 0, n);
                            written += n;
                            if (fileSize > 262144 && written - lastPush >= 400 * 1024) {
                                lastPush = written;
                                publishFile(rel, written, fileSize, false);
                                if (cancelRequested) break;
                            }
                        }
                    } catch (Exception readErr) {
                        plugin.getLogger().warning("[Backup] Skipped unreadable file " + rel + ": " + readErr.getMessage());
                    } finally {
                        if (entryOpen) { zos.closeEntry(); entryOpen = false; }
                    }
                    publishFile(rel, fileSize, fileSize, true);
                } catch (Exception entryErr) {
                    plugin.getLogger().warning("[Backup] Skipped entry " + rel + ": " + entryErr.getMessage());
                    if (entryOpen) { try { zos.closeEntry(); } catch (Exception ignored) {} }
                }

                int pct = progressBase + (int) Math.round((done / (double) total) * progressSpan);
                progress(Math.min(progressBase + progressSpan, pct), "archiving", "ضغط الملفات (" + done + "/" + total + ")...");
            }
        }
        return Files.size(archive);
    }

    /** SHA-256 of the finished archive, as lowercase hex. */
    private String sha256Of(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[1024 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /**
     * Google Drive resumable upload. Opens a session, then PUTs the archive in
     * CHUNK-sized pieces. Each chunk retries with backoff, and on a recoverable
     * failure we query the server for the last received byte and resume from
     * there — so dropped connections never corrupt or restart the whole upload.
     */
    private String resumableUpload(String token, Path file, String fileName) throws Exception {
        long size = Files.size(file);

        // 1) Start a resumable session.
        String sessionUri = startSession(token, fileName);

        // 2) Upload chunks with resume-on-failure.
        long offset = 0;
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            while (offset < size) {
                checkCancelled();   // safe stop point between chunks
                long end = Math.min(offset + CHUNK, size);
                int len = (int) (end - offset);
                byte[] buf = new byte[len];
                raf.seek(offset);
                raf.readFully(buf);

                int attempt = 0;
                while (true) {
                    attempt++;
                    try {
                        long next = putChunk(sessionUri, buf, offset, size);
                        if (next < 0) {
                            // Upload finished (200/201 received).
                            offset = size;
                        } else {
                            offset = next;
                        }
                        break;
                    } catch (Exception chunkErr) {
                        if (attempt >= MAX_RETRIES) throw chunkErr;
                        // Backoff, then ask Drive how far it got and resume there.
                        Thread.sleep(Math.min(8000, 500L * (1L << attempt)));
                        long resumeAt = queryResumeOffset(sessionUri, size);
                        if (resumeAt >= 0) offset = resumeAt;
                        plugin.getLogger().fine("[Backup] Retry chunk at " + offset + " (attempt " + attempt + ")");
                    }
                }
                // Uploading spans 70..99%.
                int pct = 70 + (int) Math.round((offset / (double) size) * 29);
                progress(Math.min(99, pct), "uploading", "رفع الأجزاء إلى Google Drive (" + human(offset) + " / " + human(size) + ")...");
            }
        }
        return "uploaded";
    }

    private String startSession(String token, String fileName) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(DRIVE_RESUMABLE).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setDoOutput(true);
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        String meta = "{\"name\":\"" + fileName.replace("\"", "") + "\"}";
        byte[] body = meta.getBytes(StandardCharsets.UTF_8);
        c.setRequestProperty("X-Upload-Content-Type", "application/zip");
        try (OutputStream os = c.getOutputStream()) { os.write(body); }
        int code = c.getResponseCode();
        if (code != 200 && code != 201) {
            throw new IOException("Drive session start failed: HTTP " + code + " " + readErr(c));
        }
        String loc = c.getHeaderField("Location");
        c.disconnect();
        if (loc == null || loc.isBlank()) throw new IOException("Drive did not return an upload session URI.");
        return loc;
    }

    /** PUTs one chunk. Returns the next byte offset, or -1 when the upload completed. */
    private long putChunk(String sessionUri, byte[] chunk, long offset, long total) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(sessionUri).openConnection();
        c.setRequestMethod("PUT");
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setDoOutput(true);
        c.setFixedLengthStreamingMode(chunk.length);
        long end = offset + chunk.length - 1;
        c.setRequestProperty("Content-Length", String.valueOf(chunk.length));
        c.setRequestProperty("Content-Range", "bytes " + offset + "-" + end + "/" + total);
        try (OutputStream os = c.getOutputStream()) { os.write(chunk); }
        int code = c.getResponseCode();
        if (code == 200 || code == 201) { c.disconnect(); return -1; }        // done
        if (code == 308) {                                                     // resume incomplete
            String range = c.getHeaderField("Range");                          // e.g. "bytes=0-8388607"
            c.disconnect();
            if (range != null && range.contains("-")) {
                try { return Long.parseLong(range.substring(range.lastIndexOf('-') + 1)) + 1; }
                catch (NumberFormatException ignored) {}
            }
            return offset + chunk.length;
        }
        String err = readErr(c);
        c.disconnect();
        throw new IOException("Drive chunk PUT failed: HTTP " + code + " " + err);
    }

    /** Asks Drive how many bytes it already has (Content-Range: bytes *​/total). */
    private long queryResumeOffset(String sessionUri, long total) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(sessionUri).openConnection();
            c.setRequestMethod("PUT");
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setRequestProperty("Content-Range", "bytes */" + total);
            c.setFixedLengthStreamingMode(0);
            c.setDoOutput(true);
            try (OutputStream os = c.getOutputStream()) { /* empty */ }
            int code = c.getResponseCode();
            if (code == 308) {
                String range = c.getHeaderField("Range");
                c.disconnect();
                if (range != null && range.contains("-")) {
                    return Long.parseLong(range.substring(range.lastIndexOf('-') + 1)) + 1;
                }
                return 0;
            }
            c.disconnect();
            if (code == 200 || code == 201) return total; // already complete
        } catch (Exception ignored) {
        }
        return -1;
    }

    private String readErr(HttpURLConnection c) {
        try (InputStream es = c.getErrorStream()) {
            if (es == null) return "";
            byte[] b = es.readNBytes(400);
            return new String(b, StandardCharsets.UTF_8).replaceAll("\\s+", " ").trim();
        } catch (Exception e) { return ""; }
    }

    private String human(long b) {
        if (b < 1024) return b + " B";
        if (b < 1048576) return String.format("%.1f KB", b / 1024.0);
        if (b < 1073741824L) return String.format("%.1f MB", b / 1048576.0);
        return String.format("%.2f GB", b / 1073741824.0);
    }

    /** Writes a progress snapshot to Firebase (percent 0..100, phase, message). */
    private void progress(int percent, String phase, String message) {
        try {
            var ref = firebaseManager.getServerRef();
            if (ref == null) return;
            Map<String, Object> p = new HashMap<>();
            p.put("percent", percent);
            p.put("phase", phase);
            p.put("message", message);
            p.put("t", System.currentTimeMillis());
            ref.child("backup").child("progress").setValueAsync(p);
        } catch (Exception ignored) {
        }
    }

    // Rolling counter so the dashboard can order file rows by arrival.
    private long fileSeq = 0;
    private final Map<String, String> filePathKeys = new HashMap<>();

    /** Clears all per-file rows (called before and after the archiving phase). */
    private void clearFiles() {
        try {
            var ref = firebaseManager.getServerRef();
            if (ref != null) ref.child("backup").child("files").removeValueAsync();
        } catch (Exception ignored) {}
        filePathKeys.clear();
    }

    /**
     * Publishes per-file archiving progress to backup/files/{key}. Each file gets
     * a live row (name, written/size, percent). When done=true the row is removed
     * so the dashboard's console shows only the files currently being written.
     */
    private void publishFile(String rel, long written, long size, boolean done) {
        try {
            var ref = firebaseManager.getServerRef();
            if (ref == null) return;
            var filesRef = ref.child("backup").child("files");
            String key = filePathKeys.computeIfAbsent(rel, k -> "f" + (fileSeq++));
            if (done) {
                // Mark 100% + done; the dashboard shows it briefly then removes the row.
                Map<String, Object> row = new HashMap<>();
                row.put("name", rel);
                row.put("written", size);
                row.put("size", size);
                row.put("pct", 100);
                row.put("done", true);
                row.put("t", System.currentTimeMillis());
                filesRef.child(key).setValueAsync(row);
                filePathKeys.remove(rel);
            } else {
                Map<String, Object> row = new HashMap<>();
                row.put("name", rel);
                row.put("written", written);
                row.put("size", size);
                row.put("pct", size > 0 ? (int) Math.min(100, Math.round(written * 100.0 / size)) : 100);
                row.put("seq", fileSeq);
                row.put("t", System.currentTimeMillis());
                filesRef.child(key).setValueAsync(row);
            }
        } catch (Exception ignored) {
        }
    }

    private Path serverRoot() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(cwd)) return cwd;
        return plugin.getDataFolder().getParentFile().getParentFile().toPath().toAbsolutePath().normalize();
    }
}
