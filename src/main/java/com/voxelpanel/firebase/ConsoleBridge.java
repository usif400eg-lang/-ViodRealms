package com.voxelpanel.firebase;

import com.voxelpanel.VoxelPanel;
import org.bukkit.Bukkit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * Captures the server console output (via the root logger) and streams the most
 * recent lines to Firebase so the dashboard shows a live console. Lines are
 * batched and flushed on a timer to avoid hammering Firebase.
 */
public class ConsoleBridge {
    private final VoxelPanel plugin;
    private final FirebaseManager firebaseManager;
    private final Deque<String> buffer = new ArrayDeque<>();
    private Handler handler;
    private int flushTask = -1;
    private volatile boolean dirty = false;

    public ConsoleBridge(VoxelPanel plugin, FirebaseManager firebaseManager) {
        this.plugin = plugin;
        this.firebaseManager = firebaseManager;
    }

    public void start() {
        if (!firebaseManager.isConnected()) return;
        handler = new Handler() {
            @Override public void publish(LogRecord record) {
                if (record == null || record.getMessage() == null) return;
                String msg = record.getMessage();
                try {
                    if (record.getParameters() != null && record.getParameters().length > 0) {
                        msg = java.text.MessageFormat.format(msg, record.getParameters());
                    }
                } catch (Exception ignored) {}
                String line = "[" + record.getLevel() + "] " + msg;
                synchronized (buffer) {
                    buffer.addLast(line);
                    while (buffer.size() > 200) buffer.removeFirst();
                    dirty = true;
                }
            }
            @Override public void flush() {}
            @Override public void close() {}
        };
        // Attach to the root logger to capture everything the server prints.
        java.util.logging.Logger.getLogger("").addHandler(handler);

        // Flush batched lines to Firebase every 2 seconds.
        flushTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flush, 40L, 40L).getTaskId();
        plugin.getLogger().info("[Firebase] Console bridge started.");
    }

    public void stop() {
        if (handler != null) {
            try { java.util.logging.Logger.getLogger("").removeHandler(handler); } catch (Exception ignored) {}
        }
        if (flushTask != -1) Bukkit.getScheduler().cancelTask(flushTask);
    }

    private void flush() {
        if (!dirty) return;
        List<String> snapshot;
        synchronized (buffer) {
            snapshot = new ArrayList<>(buffer);
            dirty = false;
        }
        var ref = firebaseManager.getServerRef();
        if (ref == null) return;
        // Store as an indexed list of the recent lines.
        Map<String, Object> data = new HashMap<>();
        for (int i = 0; i < snapshot.size(); i++) {
            data.put(String.valueOf(i), snapshot.get(i));
        }
        ref.child("consoleLog").setValueAsync(data);
    }
}
