package com.voxelpanel.firebase;

import com.voxelpanel.VoxelPanel;

import java.util.HashMap;
import java.util.Map;

/**
 * Writes an audit entry to Firebase every time a dashboard command is executed,
 * so admins can see who did what and when. Entries live under
 * servers/{id}/activity and are capped by the dashboard (reads latest N).
 */
public class ActivityLogger {
    private final VoxelPanel plugin;
    private final FirebaseManager firebaseManager;

    public ActivityLogger(VoxelPanel plugin, FirebaseManager firebaseManager) {
        this.plugin = plugin;
        this.firebaseManager = firebaseManager;
    }

    /** Records an executed action. Safe to call from any thread (Firebase write is async). */
    public void log(String action, String target, String issuedBy) {
        if (firebaseManager == null || !firebaseManager.isConnected()) {
            return;
        }
        var ref = firebaseManager.getServerRef();
        if (ref == null) {
            return;
        }
        Map<String, Object> entry = new HashMap<>();
        entry.put("action", action);
        entry.put("target", target != null ? target : "");
        entry.put("by", issuedBy != null ? issuedBy : "dashboard");
        entry.put("timestamp", System.currentTimeMillis());
        ref.child("activity").push().setValueAsync(entry);
    }
}
