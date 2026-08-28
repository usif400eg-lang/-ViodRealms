package com.viodrealms.tpu.firebase;

import com.viodrealms.tpu.ViodRealmsTPU;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lets the dashboard browse the server directory, read/edit small text files,
 * and delete files (e.g. plugins). All paths are constrained to the server root
 * so the dashboard can never escape above it. Heavy/binary files are not sent.
 */
public class FileManagerBridge {
    private final ViodRealmsTPU plugin;
    private final FirebaseManager firebaseManager;
    private final Path root;
    private final long maxEditBytes;
    private final List<String> editableExt;

    public FileManagerBridge(ViodRealmsTPU plugin, FirebaseManager firebaseManager) {
        this.plugin = plugin;
        this.firebaseManager = firebaseManager;
        // Server root = the folder that contains the plugins directory.
        // Resolve defensively: getParentFile() can be null depending on how the
        // host launches the server, so fall back to the current working directory.
        this.root = resolveServerRoot(plugin);
        this.maxEditBytes = plugin.getConfig().getLong("files.max-edit-kb", 512) * 1024L;
        this.editableExt = plugin.getConfig().getStringList("files.editable-extensions");
    }

    private static Path resolveServerRoot(ViodRealmsTPU plugin) {
        // The server always runs from its own root directory, so the current
        // working directory is the most reliable server root across all hosts.
        try {
            Path cwd = new File("").getAbsoluteFile().toPath().normalize();
            if (cwd != null && java.nio.file.Files.isDirectory(cwd)) return cwd;
        } catch (Exception ignored) {
        }
        // Fallback: derive from the data folder (plugins/ViodRealmsTPU -> server root).
        try {
            File dataFolder = plugin.getDataFolder();
            File pluginsDir = dataFolder != null ? dataFolder.getParentFile() : null;
            File serverDir = pluginsDir != null ? pluginsDir.getParentFile() : null;
            if (serverDir != null) return serverDir.toPath().toAbsolutePath().normalize();
            if (pluginsDir != null) return pluginsDir.toPath().toAbsolutePath().normalize();
        } catch (Exception ignored) {
        }
        return new File(".").getAbsoluteFile().toPath().normalize();
    }

    /** Resolves a relative path safely inside the server root; returns null if it escapes. */
    private Path resolve(String rel) {
        if (rel == null) rel = "";
        rel = rel.replace("\\", "/").replaceAll("^/+", "");
        Path p = root.resolve(rel).normalize().toAbsolutePath();
        if (!p.startsWith(root)) return null;
        return p;
    }

    private String relOf(Path p) {
        String r = root.relativize(p).toString().replace("\\", "/");
        return r;
    }

    private boolean isEditable(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        return editableExt.contains(name.substring(dot + 1).toLowerCase());
    }

    /** Lists a directory into Firebase under files/list. */
    public void listDir(String rel) {
        if (!plugin.getConfig().getBoolean("files.enabled", true)) return;
        var ref = firebaseManager.getServerRef();
        if (ref == null) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Path dir = resolve(rel);
            Map<String, Object> out = new HashMap<>();
            out.put("path", dir == null ? "" : relOf(dir));
            List<Map<String, Object>> entries = new ArrayList<>();
            if (dir != null && Files.isDirectory(dir)) {
                try (var stream = Files.list(dir)) {
                    stream.sorted(Comparator.comparing((Path p) -> !Files.isDirectory(p)).thenComparing(p -> p.getFileName().toString().toLowerCase()))
                          .forEach(p -> {
                              Map<String, Object> e = new HashMap<>();
                              String name = p.getFileName().toString();
                              boolean isDir = Files.isDirectory(p);
                              e.put("name", name);
                              e.put("dir", isDir);
                              e.put("path", relOf(p));
                              long size = 0;
                              try { size = isDir ? 0 : Files.size(p); } catch (IOException ignored) {}
                              e.put("size", size);
                              e.put("editable", !isDir && isEditable(name) && size <= maxEditBytes);
                              entries.add(e);
                          });
                } catch (IOException e) {
                    out.put("error", e.getMessage());
                }
            } else {
                out.put("error", "not a directory");
            }
            out.put("entries", entries);
            out.put("t", System.currentTimeMillis());
            ref.child("files").child("list").setValueAsync(out);
        });
    }

    /** Reads a text file into Firebase under files/read. */
    public void readFile(String rel) {
        var ref = firebaseManager.getServerRef();
        if (ref == null) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Path p = resolve(rel);
            Map<String, Object> out = new HashMap<>();
            out.put("path", rel);
            try {
                if (p == null || !Files.isRegularFile(p)) { out.put("error", "file not found"); }
                else if (!isEditable(p.getFileName().toString())) { out.put("error", "not an editable text file"); }
                else if (Files.size(p) > maxEditBytes) { out.put("error", "file too large"); }
                else { out.put("content", Files.readString(p, StandardCharsets.UTF_8)); }
            } catch (IOException e) {
                out.put("error", e.getMessage());
            }
            out.put("t", System.currentTimeMillis());
            ref.child("files").child("read").setValueAsync(out);
        });
    }

    /** Writes text content to a file. */
    public void writeFile(String rel, String content, String issuedBy) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Path p = resolve(rel);
            String status, message;
            if (p == null || !isEditable(p.getFileName().toString())) {
                status = "error"; message = "غير مسموح بتعديل هذا الملف";
            } else {
                try {
                    Files.writeString(p, content != null ? content : "", StandardCharsets.UTF_8);
                    status = "success"; message = "تم حفظ " + relOf(p);
                    plugin.getLogger().info("[Dashboard] File edited: " + relOf(p) + " by " + issuedBy);
                    if (plugin.getActivityLogger() != null) plugin.getActivityLogger().log("file_edit", relOf(p), issuedBy);
                } catch (IOException e) {
                    status = "error"; message = e.getMessage();
                }
            }
            reportFileOp(status, message);
        });
    }

    /** Deletes a file (e.g. a plugin jar). Directories are refused for safety. */
    public void deleteFile(String rel, String issuedBy) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Path p = resolve(rel);
            String status, message;
            if (p == null || !Files.exists(p)) {
                status = "error"; message = "الملف غير موجود";
            } else if (Files.isDirectory(p)) {
                status = "error"; message = "لا يمكن حذف مجلد من هنا";
            } else {
                try {
                    Files.delete(p);
                    status = "success"; message = "تم حذف " + relOf(p);
                    plugin.getLogger().info("[Dashboard] File deleted: " + relOf(p) + " by " + issuedBy);
                    if (plugin.getActivityLogger() != null) plugin.getActivityLogger().log("file_delete", relOf(p), issuedBy);
                } catch (IOException e) {
                    status = "error"; message = e.getMessage();
                }
            }
            reportFileOp(status, message);
        });
    }

    private void reportFileOp(String status, String message) {
        var ref = firebaseManager.getServerRef();
        if (ref == null) return;
        Map<String, Object> r = new HashMap<>();
        r.put("status", status);
        r.put("message", message);
        r.put("t", System.currentTimeMillis());
        ref.child("files").child("op").setValueAsync(r);
    }
}
