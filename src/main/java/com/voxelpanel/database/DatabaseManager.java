package com.voxelpanel.database;

import com.voxelpanel.VoxelPanel;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private final VoxelPanel plugin;
    private String connectionUrl;

    public DatabaseManager(VoxelPanel plugin) {
        this.plugin = plugin;
    }

    public boolean initialize() {
        try {
            Class.forName("org.sqlite.JDBC");
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists() && !dataFolder.mkdirs()) {
                plugin.getLogger().warning("Unable to create plugin data directory.");
            }
            File dbFile = new File(dataFolder, "data.db");
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            this.connectionUrl = url;
            try (Connection connection = DriverManager.getConnection(connectionUrl)) {
                createTables(connection);
            }
            return true;
        } catch (ClassNotFoundException | SQLException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to initialize SQLite database.", e);
            return false;
        }
    }

    private void createTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS waypoints (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "player_uuid TEXT NOT NULL, " +
                            "player_name TEXT NOT NULL, " +
                            "waypoint_name TEXT NOT NULL, " +
                            "world_name TEXT NOT NULL, " +
                            "x REAL NOT NULL, " +
                            "y REAL NOT NULL, " +
                            "z REAL NOT NULL, " +
                            "yaw REAL NOT NULL, " +
                            "pitch REAL NOT NULL, " +
                            "icon TEXT DEFAULT 'ENDER_PEARL', " +
                            "category TEXT DEFAULT 'OTHER', " +
                            "is_public INTEGER DEFAULT 0, " +
                            "is_death_waypoint INTEGER DEFAULT 0, " +
                            "death_waypoint_expiry INTEGER DEFAULT 0, " +
                            "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)"
            );
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS waypoint_shares (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "waypoint_id INTEGER NOT NULL, " +
                            "shared_with_uuid TEXT NOT NULL, " +
                            "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                            "FOREIGN KEY (waypoint_id) REFERENCES waypoints(id) ON DELETE CASCADE)"
            );
            // Migrations
            try { statement.executeUpdate("ALTER TABLE waypoints ADD COLUMN icon TEXT DEFAULT 'ENDER_PEARL'"); } catch (SQLException ignored) {}
            try { statement.executeUpdate("ALTER TABLE waypoints ADD COLUMN category TEXT DEFAULT 'OTHER'"); } catch (SQLException ignored) {}
            try { statement.executeUpdate("ALTER TABLE waypoints ADD COLUMN is_public INTEGER DEFAULT 0"); } catch (SQLException ignored) {}
            try { statement.executeUpdate("ALTER TABLE waypoints ADD COLUMN is_death_waypoint INTEGER DEFAULT 0"); } catch (SQLException ignored) {}
            try { statement.executeUpdate("ALTER TABLE waypoints ADD COLUMN death_waypoint_expiry INTEGER DEFAULT 0"); } catch (SQLException ignored) {}
        }
    }

    /**
     * Repository operations use try-with-resources, so every operation must receive its own connection.
     * Returning one shared connection caused the first query to close SQLite for the entire plugin.
     */
    public Connection getConnection() throws SQLException {
        if (connectionUrl == null) {
            throw new SQLException("SQLite database has not been initialized.");
        }
        return DriverManager.getConnection(connectionUrl);
    }

    public void close() {
        // Connections are opened and closed per database operation by WaypointRepository.
    }
}
