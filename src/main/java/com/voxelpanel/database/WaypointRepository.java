package com.voxelpanel.database;

import com.voxelpanel.models.Waypoint;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class WaypointRepository {
    private final DatabaseManager databaseManager;

    public WaypointRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public boolean insertWaypoint(Waypoint waypoint) {
        String sql = "INSERT INTO waypoints (player_uuid, player_name, waypoint_name, world_name, x, y, z, yaw, pitch, icon, category, is_public, is_death_waypoint, death_waypoint_expiry, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, waypoint.getPlayerUuid().toString());
            statement.setString(2, waypoint.getPlayerName());
            statement.setString(3, waypoint.getWaypointName());
            statement.setString(4, waypoint.getWorldName());
            statement.setDouble(5, waypoint.getX());
            statement.setDouble(6, waypoint.getY());
            statement.setDouble(7, waypoint.getZ());
            statement.setDouble(8, waypoint.getYaw());
            statement.setDouble(9, waypoint.getPitch());
            statement.setString(10, waypoint.getIcon());
            statement.setString(11, waypoint.getCategory());
            statement.setInt(12, waypoint.isPublic() ? 1 : 0);
            statement.setInt(13, waypoint.isDeathWaypoint() ? 1 : 0);
            Long expiry = waypoint.getDeathWaypointExpiry();
            statement.setLong(14, expiry != null ? expiry : 0L);
            statement.setString(15, waypoint.getCreatedAt().toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean renameWaypoint(int waypointId, String newName) {
        String sql = "UPDATE waypoints SET waypoint_name = ? WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, newName);
            statement.setInt(2, waypointId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean deleteWaypoint(int waypointId) {
        String sql = "DELETE FROM waypoints WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, waypointId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public Optional<Waypoint> getWaypointById(int waypointId) {
        String sql = "SELECT * FROM waypoints WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, waypointId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(Waypoint.fromResultSet(resultSet));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public Optional<Waypoint> getWaypointForPlayerByName(UUID playerUuid, String waypointName) {
        String sql = "SELECT * FROM waypoints WHERE player_uuid = ? AND LOWER(waypoint_name) = LOWER(?) LIMIT 1";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, waypointName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) return Optional.of(Waypoint.fromResultSet(resultSet));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public List<Waypoint> getWaypointsForPlayer(UUID playerUuid) {
        String sql = "SELECT * FROM waypoints WHERE player_uuid = ? ORDER BY created_at DESC";
        List<Waypoint> waypoints = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    waypoints.add(Waypoint.fromResultSet(resultSet));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return waypoints;
    }

    public List<Waypoint> getPublicWaypoints() {
        String sql = "SELECT * FROM waypoints WHERE is_public = 1 ORDER BY player_name ASC, waypoint_name ASC";
        List<Waypoint> waypoints = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                waypoints.add(Waypoint.fromResultSet(resultSet));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return waypoints;
    }

    public List<Waypoint> getSharedWaypointsForPlayer(UUID playerUuid) {
        String sql = "SELECT w.* FROM waypoints w JOIN waypoint_shares s ON w.id = s.waypoint_id WHERE s.shared_with_uuid = ? ORDER BY w.player_name ASC, w.waypoint_name ASC";
        List<Waypoint> waypoints = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    waypoints.add(Waypoint.fromResultSet(resultSet));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return waypoints;
    }

    public List<Waypoint> getWaypointsForPlayerByCategory(UUID playerUuid, String category) {
        String sql = "SELECT * FROM waypoints WHERE player_uuid = ? AND category = ? ORDER BY created_at DESC";
        List<Waypoint> waypoints = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, category);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    waypoints.add(Waypoint.fromResultSet(resultSet));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return waypoints;
    }

    public List<Waypoint> searchWaypoints(UUID playerUuid, String query) {
        String sql = "SELECT * FROM waypoints WHERE player_uuid = ? AND LOWER(waypoint_name) LIKE LOWER(?) ORDER BY waypoint_name ASC";
        List<Waypoint> results = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, '%' + query + '%');
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(Waypoint.fromResultSet(resultSet));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return results;
    }

    public boolean playerHasWaypoint(UUID playerUuid, String waypointName) {
        String sql = "SELECT 1 FROM waypoints WHERE player_uuid = ? AND LOWER(waypoint_name) = LOWER(?) LIMIT 1";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, waypointName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return true;
        }
    }

    public int countWaypoints(UUID playerUuid) {
        String sql = "SELECT COUNT(*) FROM waypoints WHERE player_uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int countWaypointsByCategory(UUID playerUuid, String category) {
        String sql = "SELECT COUNT(*) FROM waypoints WHERE player_uuid = ? AND category = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, category);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public boolean shareWaypoint(int waypointId, UUID sharedWithUuid) {
        String sql = "INSERT INTO waypoint_shares (waypoint_id, shared_with_uuid) VALUES (?, ?)";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, waypointId);
            statement.setString(2, sharedWithUuid.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean unshareWaypoint(int waypointId, UUID sharedWithUuid) {
        String sql = "DELETE FROM waypoint_shares WHERE waypoint_id = ? AND shared_with_uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, waypointId);
            statement.setString(2, sharedWithUuid.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<UUID> getSharedPlayers(int waypointId) {
        String sql = "SELECT shared_with_uuid FROM waypoint_shares WHERE waypoint_id = ?";
        List<UUID> uuids = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, waypointId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    uuids.add(UUID.fromString(resultSet.getString("shared_with_uuid")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return uuids;
    }

    public boolean updateWaypointIcon(int waypointId, String icon) {
        String sql = "UPDATE waypoints SET icon = ? WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, icon);
            statement.setInt(2, waypointId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean updateWaypointCoords(int waypointId, double x, double y, double z) {
        String sql = "UPDATE waypoints SET x = ?, y = ?, z = ? WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDouble(1, x);
            statement.setDouble(2, y);
            statement.setDouble(3, z);
            statement.setInt(4, waypointId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean updateWaypointCategory(int waypointId, String category) {
        String sql = "UPDATE waypoints SET category = ? WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, category);
            statement.setInt(2, waypointId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean updateWaypointPublic(int waypointId, boolean isPublic) {
        String sql = "UPDATE waypoints SET is_public = ? WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, isPublic ? 1 : 0);
            statement.setInt(2, waypointId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean updateDeathWaypoint(int waypointId, boolean isDeathWaypoint, Long expiry) {
        String sql = "UPDATE waypoints SET is_death_waypoint = ?, death_waypoint_expiry = ? WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, isDeathWaypoint ? 1 : 0);
            statement.setLong(2, expiry != null ? expiry : 0L);
            statement.setInt(3, waypointId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<Waypoint> getExpiredDeathWaypoints() {
        String sql = "SELECT * FROM waypoints WHERE is_death_waypoint = 1 AND death_waypoint_expiry > 0 AND death_waypoint_expiry < ?";
        List<Waypoint> waypoints = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, System.currentTimeMillis());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    waypoints.add(Waypoint.fromResultSet(resultSet));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return waypoints;
    }

    public List<PlayerRecord> getKnownPlayers() {
        String sql = "SELECT DISTINCT player_uuid, player_name FROM waypoints ORDER BY player_name ASC";
        List<PlayerRecord> players = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                players.add(new PlayerRecord(UUID.fromString(resultSet.getString("player_uuid")), resultSet.getString("player_name")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return players;
    }

    public int getTotalWaypointCount() {
        String sql = "SELECT COUNT(*) FROM waypoints";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                return resultSet.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int getPublicWaypointCount() {
        String sql = "SELECT COUNT(*) FROM waypoints WHERE is_public = 1";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                return resultSet.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static class PlayerRecord {
        private final UUID uuid;
        private final String playerName;

        public PlayerRecord(UUID uuid, String playerName) {
            this.uuid = uuid;
            this.playerName = playerName;
        }

        public UUID getUuid() {
            return uuid;
        }

        public String getPlayerName() {
            return playerName;
        }
    }
}
