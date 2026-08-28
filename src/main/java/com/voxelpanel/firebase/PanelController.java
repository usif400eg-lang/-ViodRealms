package com.voxelpanel.firebase;

import com.voxelpanel.VoxelPanel;
import org.bukkit.Bukkit;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Sends power signals (start / stop / restart / kill) to a Pterodactyl-style
 * hosting panel API. This is the only reliable way to START a server, because
 * the plugin itself dies when the server stops and cannot restart itself.
 *
 * Configure panel.base-url, panel.api-key and panel.server-identifier in config.yml.
 */
public class PanelController {
    private final VoxelPanel plugin;
    // Panel config can come from config.yml OR be pushed by the dashboard into
    // servers/{id}/panelConfig. The Firebase value takes priority when present.
    private volatile String fbUrl = "", fbKey = "", fbId = "";

    public PanelController(VoxelPanel plugin) {
        this.plugin = plugin;
        watchFirebaseConfig();
    }

    /** Watches panelConfig in Firebase so owners can set panel creds from the dashboard. */
    private void watchFirebaseConfig() {
        var db = plugin.getFirebaseManager();
        if (db == null || !db.isConnected()) return;
        var ref = db.getServerRef();
        if (ref == null) return;
        ref.child("panelConfig").addValueEventListener(new com.google.firebase.database.ValueEventListener() {
            @Override public void onDataChange(com.google.firebase.database.DataSnapshot snap) {
                var v = snap.getValue() != null ? snap : null;
                fbUrl = snap.child("url").getValue(String.class) != null ? snap.child("url").getValue(String.class) : "";
                fbKey = snap.child("key").getValue(String.class) != null ? snap.child("key").getValue(String.class) : "";
                fbId = snap.child("id").getValue(String.class) != null ? snap.child("id").getValue(String.class) : "";
            }
            @Override public void onCancelled(com.google.firebase.database.DatabaseError e) {}
        });
    }

    private String url() {
        if (fbUrl != null && !fbUrl.isBlank()) return fbUrl;
        return plugin.getConfig().getString("panel.base-url", "");
    }
    private String key() {
        if (fbKey != null && !fbKey.isBlank()) return fbKey;
        return plugin.getConfig().getString("panel.api-key", "");
    }
    private String identifier() {
        if (fbId != null && !fbId.isBlank()) return fbId;
        return plugin.getConfig().getString("panel.server-identifier", "");
    }

    public boolean isConfigured() {
        return !url().isBlank() && !key().isBlank() && !identifier().isBlank();
    }

    /** signal: start | stop | restart | kill */
    public void sendPower(String signal, String issuedBy) {
        if (!isConfigured()) {
            report("error", "لوحة التحكم غير مهيّأة في config.yml");
            return;
        }
        String sig = switch (signal.toLowerCase()) {
            case "start", "stop", "restart", "kill" -> signal.toLowerCase();
            default -> null;
        };
        if (sig == null) { report("error", "إشارة غير صالحة"); return; }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            HttpURLConnection conn = null;
            try {
                String base = url().replaceAll("/+$", "");
                String id = identifier();
                String key = key();
                URL apiUrl = new URL(base + "/api/client/servers/" + id + "/power");
                conn = (HttpURLConnection) apiUrl.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(12000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Authorization", "Bearer " + key);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                byte[] body = ("{\"signal\":\"" + sig + "\"}").getBytes(StandardCharsets.UTF_8);
                conn.getOutputStream().write(body);
                int code = conn.getResponseCode();
                if (code == 204 || code == 200) {
                    report("success", "تم إرسال إشارة " + sig + " للسيرفر");
                    plugin.getLogger().info("[Panel] Power signal '" + sig + "' by " + issuedBy);
                    if (plugin.getActivityLogger() != null) plugin.getActivityLogger().log("power_" + sig, "", issuedBy);
                } else {
                    report("error", "استجابة اللوحة: HTTP " + code);
                }
            } catch (Exception e) {
                report("error", e.getMessage() != null ? e.getMessage() : "فشل الاتصال باللوحة");
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void report(String status, String message) {
        var db = plugin.getFirebaseManager();
        if (db == null || !db.isConnected()) return;
        var ref = db.getServerRef();
        if (ref == null) return;
        java.util.Map<String, Object> r = new java.util.HashMap<>();
        r.put("status", status);
        r.put("message", message);
        r.put("t", System.currentTimeMillis());
        ref.child("power").child("result").setValueAsync(r);
    }
}
