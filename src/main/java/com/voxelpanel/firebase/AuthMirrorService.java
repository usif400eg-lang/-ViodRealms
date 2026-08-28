package com.voxelpanel.firebase;

import com.google.firebase.auth.ExportedUserRecord;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.ListUsersPage;
import com.voxelpanel.VoxelPanel;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Uses the Firebase Admin SDK (available inside the plugin) to list all
 * Authentication users and publish them to Realtime Database so the dashboard
 * can display them. The Web SDK cannot list users, so this bridge is required.
 * Refreshes periodically and on demand via the "refresh_auth" command.
 */
public class AuthMirrorService {
    private final VoxelPanel plugin;
    private final FirebaseManager firebaseManager;
    private BukkitTask task;

    public AuthMirrorService(VoxelPanel plugin, FirebaseManager firebaseManager) {
        this.plugin = plugin;
        this.firebaseManager = firebaseManager;
    }

    public void start() {
        if (!firebaseManager.isConnected()) return;
        // Refresh every 5 minutes; the list rarely changes.
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::publish, 200L, 20L * 300);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    /** Lists all auth users (async) and writes a compact snapshot to RTDB. */
    public void publish() {
        var ref = firebaseManager.getServerRef();
        if (ref == null || firebaseManager.getApp() == null) return;
        try {
            FirebaseAuth auth = FirebaseAuth.getInstance(firebaseManager.getApp());
            List<Map<String, Object>> users = new ArrayList<>();
            ListUsersPage page = auth.listUsers(null);
            while (page != null) {
                for (ExportedUserRecord u : page.getValues()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("uid", u.getUid());
                    m.put("email", u.getEmail() != null ? u.getEmail() : "");
                    m.put("name", u.getDisplayName() != null ? u.getDisplayName() : "");
                    m.put("photo", u.getPhotoUrl() != null ? u.getPhotoUrl() : "");
                    m.put("disabled", u.isDisabled());
                    m.put("provider", u.getProviderId() != null ? u.getProviderId() : "");
                    if (u.getUserMetadata() != null) {
                        m.put("created", u.getUserMetadata().getCreationTimestamp());
                        m.put("lastLogin", u.getUserMetadata().getLastSignInTimestamp());
                    }
                    users.add(m);
                }
                page = page.getNextPage();
            }
            ref.child("authUsers").setValueAsync(users);
            plugin.getLogger().info("[Firebase] Mirrored " + users.size() + " auth user(s) to dashboard.");
        } catch (Exception e) {
            plugin.getLogger().warning("[Firebase] Failed to list auth users: " + e.getMessage());
        }
    }
}
