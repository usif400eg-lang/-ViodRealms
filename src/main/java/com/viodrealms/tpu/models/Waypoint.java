package com.viodrealms.tpu.models;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

public class Waypoint {
    private final int id;
    private final UUID playerUuid;
    private final String playerName;
    private String waypointName;
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final Instant createdAt;
    private final String category;
    private final String icon;
    private final boolean isPublic;
    private final boolean isDeathWaypoint;
    private final Long deathWaypointExpiry;

    public Waypoint(int id, UUID playerUuid, String playerName, String waypointName, String worldName,
                    double x, double y, double z, float yaw, float pitch, Instant createdAt,
                    String category, String icon, boolean isPublic, boolean isDeathWaypoint, Long deathWaypointExpiry) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.waypointName = waypointName;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.createdAt = createdAt;
        this.category = category;
        this.icon = icon;
        this.isPublic = isPublic;
        this.isDeathWaypoint = isDeathWaypoint;
        this.deathWaypointExpiry = deathWaypointExpiry;
    }

    public static Waypoint fromResultSet(ResultSet resultSet) throws SQLException {
        return new Waypoint(
                resultSet.getInt("id"),
                UUID.fromString(resultSet.getString("player_uuid")),
                resultSet.getString("player_name"),
                resultSet.getString("waypoint_name"),
                resultSet.getString("world_name"),
                resultSet.getDouble("x"),
                resultSet.getDouble("y"),
                resultSet.getDouble("z"),
                resultSet.getFloat("yaw"),
                resultSet.getFloat("pitch"),
                Instant.parse(resultSet.getString("created_at")),
                resultSet.getString("category"),
                resultSet.getString("icon"),
                resultSet.getInt("is_public") > 0,
                resultSet.getInt("is_death_waypoint") > 0,
                resultSet.getLong("death_waypoint_expiry")
        );
    }

    public int getId() {
        return id;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getWaypointName() {
        return waypointName;
    }

    public void setWaypointName(String waypointName) {
        this.waypointName = waypointName;
    }

    public String getWorldName() {
        return worldName;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCategory() {
        return category;
    }

    public String getIcon() {
        return icon;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public boolean isDeathWaypoint() {
        return isDeathWaypoint;
    }

    public Long getDeathWaypointExpiry() {
        return deathWaypointExpiry;
    }
}
