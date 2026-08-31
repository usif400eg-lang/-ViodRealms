package com.voxelpanel.firebase;

import com.sun.net.httpserver.HttpServer;
import com.voxelpanel.VoxelPanel;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * A tiny, single-purpose HTTP server that streams finished backup archives to
 * the browser for the "Direct Local Download" option.
 *
 * Design notes:
 *  - Only serves files that were explicitly registered here (by an opaque random
 *    token), so it can never be used to read arbitrary paths on the host.
 *  - Streams in 64 KB chunks; never loads the whole archive into memory.
 *  - Registrations expire after 24 hours, matching the temp-file purge policy.
 *  - The port is configurable (backup.download-port, default 8765). The host must
 *    allow inbound access to it for the download link to work externally.
 */
public class BackupHttpServer {
    private static BackupHttpServer instance;

    private final VoxelPanel plugin;
    private HttpServer server;
    private int port;
    private String publicHost;
    // token -> [absolutePath, downloadName, expiryEpochMs]
    private final Map<String, Entry> files = new ConcurrentHashMap<>();

    private record Entry(Path path, String name, long expiry) {}

    private BackupHttpServer(VoxelPanel plugin) {
        this.plugin = plugin;
    }

    /** Lazily starts (once) and returns the shared instance. */
    public static synchronized BackupHttpServer get(VoxelPanel plugin) {
        if (instance == null) {
            instance = new BackupHttpServer(plugin);
            instance.start();
        }
        return instance;
    }

    private void start() {
        port = plugin.getConfig().getInt("backup.download-port", 8765);
        publicHost = plugin.getConfig().getString("backup.public-host", "");
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(Executors.newFixedThreadPool(2));
            server.createContext("/dl/", this::handle);
            server.start();
            plugin.getLogger().info("[Backup] Download server listening on port " + port);
        } catch (Exception e) {
            plugin.getLogger().warning("[Backup] Could not start download server on port " + port + ": " + e.getMessage());
            server = null;
        }
    }

    /** Registers a file for download; returns the opaque token used in the URL. */
    public String register(Path archive, String downloadName) {
        String token = UUID.randomUUID().toString().replace("-", "");
        files.put(token, new Entry(archive.toAbsolutePath().normalize(), downloadName,
                System.currentTimeMillis() + 24L * 60 * 60 * 1000));
        return token;
    }

    /** Builds the browser-facing URL for a token. */
    public String publicUrl(String token) {
        String host = (publicHost != null && !publicHost.isBlank())
                ? publicHost
                : guessHost();
        return "http://" + host + ":" + port + "/dl/" + token;
    }

    private String guessHost() {
        try {
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "localhost";
        }
    }

    private void handle(com.sun.net.httpserver.HttpExchange ex) {
        try {
            // Purge expired registrations lazily on each request.
            long now = System.currentTimeMillis();
            files.entrySet().removeIf(e -> e.getValue().expiry() < now);

            String pathPart = ex.getRequestURI().getPath();
            String token = pathPart.substring(pathPart.lastIndexOf('/') + 1);
            Entry entry = files.get(token);
            if (entry == null || !Files.isReadable(entry.path())) {
                byte[] msg = "Not found or expired.".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                ex.sendResponseHeaders(404, msg.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(msg); }
                return;
            }
            long len = Files.size(entry.path());
            ex.getResponseHeaders().add("Content-Type", "application/zip");
            ex.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"" + entry.name() + "\"");
            ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            ex.sendResponseHeaders(200, len);
            try (OutputStream os = ex.getResponseBody();
                 var in = Files.newInputStream(entry.path())) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            }
        } catch (Exception e) {
            plugin.getLogger().fine("[Backup] Download error: " + e.getMessage());
            try { ex.close(); } catch (Exception ignored) {}
        }
    }

    public void stop() {
        if (server != null) {
            try { server.stop(0); } catch (Exception ignored) {}
            server = null;
        }
    }
}
