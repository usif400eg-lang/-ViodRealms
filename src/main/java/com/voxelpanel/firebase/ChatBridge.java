package com.voxelpanel.firebase;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.voxelpanel.VoxelPanel;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Mirrors in-game chat (and join/leave events) to Firebase so the dashboard can
 * display a live chat feed, and lets admins send messages from the dashboard
 * that appear in-game. Dashboard messages are written under chatOut and are
 * broadcast to the server, then echoed back into the feed.
 */
public class ChatBridge implements Listener {
    private final VoxelPanel plugin;
    private final FirebaseManager firebaseManager;
    private DatabaseReference outRef;
    private ChildEventListener outListener;

    public ChatBridge(VoxelPanel plugin, FirebaseManager firebaseManager) {
        this.plugin = plugin;
        this.firebaseManager = firebaseManager;
    }

    public void start() {
        if (!firebaseManager.isConnected()) return;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Listen for messages sent FROM the dashboard.
        DatabaseReference serverRef = firebaseManager.getServerRef();
        if (serverRef == null) return;
        outRef = serverRef.child("chatOut");
        outListener = new ChildEventListener() {
            @Override public void onChildAdded(DataSnapshot snap, String prev) {
                String sender = snap.child("sender").getValue(String.class);
                String message = snap.child("message").getValue(String.class);
                String key = snap.getKey();
                if (message != null) {
                    final String s = sender != null ? sender : "Admin";
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Bukkit.broadcastMessage("§d[§5" + s + "§d] §f" + message);
                        // Echo into the live feed so the dashboard shows it too.
                        pushChat("§d" + s, message, "admin");
                    });
                }
                if (key != null) outRef.child(key).removeValueAsync();
            }
            @Override public void onChildChanged(DataSnapshot s, String p) {}
            @Override public void onChildRemoved(DataSnapshot s) {}
            @Override public void onChildMoved(DataSnapshot s, String p) {}
            @Override public void onCancelled(DatabaseError e) {}
        };
        outRef.addChildEventListener(outListener);
        plugin.getLogger().info("[Firebase] Chat bridge started.");
    }

    public void stop() {
        if (outRef != null && outListener != null) outRef.removeEventListener(outListener);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        pushChat(e.getPlayer().getName(), e.getMessage(), "player");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        pushChat(e.getPlayer().getName(), "انضم إلى السيرفر", "join");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pushChat(e.getPlayer().getName(), "غادر السيرفر", "leave");
    }

    /** Appends one entry to the chat feed (kept trimmed by the dashboard reading limitToLast). */
    private void pushChat(String sender, String message, String kind) {
        if (!firebaseManager.isConnected()) return;
        DatabaseReference serverRef = firebaseManager.getServerRef();
        if (serverRef == null) return;
        Map<String, Object> m = new HashMap<>();
        m.put("sender", sender != null ? sender : "?");
        m.put("message", message != null ? message : "");
        m.put("kind", kind);
        m.put("t", System.currentTimeMillis());
        serverRef.child("chat").push().setValueAsync(m);
    }
}
