package com.viodrealms.tpu.firebase;

import com.viodrealms.tpu.ViodRealmsTPU;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exposes the server's installed plugins to the dashboard and can download a new
 * plugin JAR from a direct URL (e.g. a Modrinth CDN link) into the plugins folder.
 *
 * Downloading is OWNER-gated on the dashboard side and additionally validated here:
 * only https URLs ending in .jar are accepted. The downloaded plugin is NOT loaded
 * automatically; a server restart is required, which keeps the operation auditable.
 */
public class PluginManagerBridge {
    private final ViodRealmsTPU plugin;
    private final FirebaseManager firebaseManager;

    public PluginManagerBridge(ViodRealmsTPU plugin, FirebaseManager firebaseManager) {
        this.plugin = plugin;
        this.firebaseManager = firebaseManager;
    }

    /** Publishes the list of installed plugins (name, version, enabled) to Firebase. */
    public void publishPlugins() {
        if (firebaseManager == null || !firebaseManager.isConnected()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Plugin pl : Bukkit.getPluginManager().getPlugins()) {
                Map<String, Object> m = new HashMap<>();
                var desc = pl.getDescription();
                m.put("name", pl.getName());
                m.put("version", desc.getVersion());
                m.put("enabled", pl.isEnabled());
                m.put("authors", String.join(", ", desc.getAuthors()));
                m.put("description", desc.getDescription() != null ? desc.getDescription() : "");
                list.add(m);
            }
            var ref = firebaseManager.getServerRef();
            if (ref != null) ref.child("plugins").setValueAsync(list);
        });
    }

    /**
     * Downloads a plugin JAR from a direct URL into the plugins folder (async).
     * Reports progress/result back to Firebase under servers/{id}/pluginInstall.
     */
    public void downloadPlugin(String url, String fileName, String issuedBy) {
        var ref = firebaseManager.getServerRef();
        if (ref == null) return;

        // Basic validation to reduce abuse: https + path ending in .jar only.
        String lower = url == null ? "" : url.toLowerCase();
        int q = lower.indexOf('?');
        String path = q >= 0 ? lower.substring(0, q) : lower;
        if (!lower.startsWith("https://") || !path.endsWith(".jar")) {
            report(ref, "error", "رابط غير صالح (يجب أن يكون https وينتهي بـ .jar)", issuedBy);
            return;
        }

        String safeName = (fileName == null || fileName.isBlank() ? "downloaded-plugin" : fileName)
                .replaceAll("[^A-Za-z0-9._-]", "_");
        if (!safeName.toLowerCase().endsWith(".jar")) safeName += ".jar";

        final String finalName = safeName;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            report(ref, "downloading", finalName, issuedBy);
            File pluginsDir = plugin.getDataFolder().getParentFile();
            File target = new File(pluginsDir, finalName);
            HttpURLConnection conn = null;
            try {
                URL u = new URL(url);
                conn = (HttpURLConnection) u.openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(60000);
                conn.setRequestProperty("User-Agent", "ViodRealmsTPU-Dashboard");

                int code = conn.getResponseCode();
                if (code != 200) {
                    report(ref, "error", "HTTP " + code, issuedBy);
                    return;
                }

                try (InputStream in = new BufferedInputStream(conn.getInputStream());
                     FileOutputStream out = new FileOutputStream(target)) {
                    byte[] buf = new byte[8192];
                    int n;
                    long total = 0;
                    while ((n = in.read(buf)) != -1) {
                        out.write(buf, 0, n);
                        total += n;
                        // Guard against oversized downloads (100 MB cap).
                        if (total > 100L * 1024 * 1024) {
                            out.close();
                            target.delete();
                            report(ref, "error", "الملف كبير جداً (>100MB)", issuedBy);
                            return;
                        }
                    }
                }

                plugin.getLogger().info("[Dashboard] Downloaded plugin: " + finalName + " by " + issuedBy);
                report(ref, "success", finalName + " — أعد تشغيل السيرفر لتفعيله", issuedBy);
                if (plugin.getActivityLogger() != null) {
                    plugin.getActivityLogger().log("plugin_download", finalName, issuedBy);
                }
                // Refresh the plugin list (the new one shows after restart).
                publishPlugins();
            } catch (Exception e) {
                // Remove any partially written file so a corrupt JAR is not loaded on restart.
                if (target.exists()) {
                    try { target.delete(); } catch (Exception ignored) {}
                }
                report(ref, "error", e.getMessage() != null ? e.getMessage() : "خطأ غير معروف", issuedBy);
            } finally {
                if (conn != null) {
                    try { conn.disconnect(); } catch (Exception ignored) {}
                }
            }
        });
    }

    private void report(com.google.firebase.database.DatabaseReference ref, String status, String message, String issuedBy) {
        Map<String, Object> r = new HashMap<>();
        r.put("status", status);
        r.put("message", message);
        r.put("by", issuedBy != null ? issuedBy : "");
        r.put("t", System.currentTimeMillis());
        ref.child("pluginInstall").setValueAsync(r);
    }
}
