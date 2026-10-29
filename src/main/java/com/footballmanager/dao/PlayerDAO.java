package com.footballmanager.dao;

import com.footballmanager.model.Player;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;

public class PlayerDAO extends UserDAO {

    public ArrayList<Player> searchPlayer(String searchKey) throws SQLException {
        ArrayList<Player> players = new ArrayList<>();
        String keyword = "%" + (searchKey == null ? "" : searchKey.trim()) + "%";

        String sql = """
                SELECT u.id, u.username, u.full_name, u.dob,
                       u.email, u.phone, p.nationality,
                       p.primary_position, p.height_cm, p.weight_kg
                FROM players p
                JOIN users u ON p.user_id = u.id
                WHERE u.full_name ILIKE ? OR u.username ILIKE ?
                ORDER BY u.id
                """;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, keyword);
            ps.setString(2, keyword);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Player player = new Player();

                    player.setId(rs.getInt("id"));
                    player.setUsername(rs.getString("username"));
                    player.setFullName(rs.getString("full_name"));
                    player.setDob(rs.getObject("dob", LocalDate.class));
                    player.setEmail(rs.getString("email"));
                    player.setPhone(rs.getString("phone"));
                    player.setNationality(rs.getString("nationality"));
                    player.setPrimaryPosition(rs.getString("primary_position"));
                    player.setHeightCm(rs.getDouble("height_cm"));
                    player.setWeightKg(rs.getDouble("weight_kg"));

                    players.add(player);
                }
            }
        }

        return players;
    }

    public boolean addPlayer(Player player) throws SQLException {
        String sqlUser = """
                INSERT INTO users (username, password_hash, full_name, dob, email, phone)
                VALUES (?, crypt(?, gen_salt('bf', 12)), ?, ?, ?, ?)
                RETURNING id
                """;

        String sqlPlayer = """
                INSERT INTO players (user_id, nationality, primary_position, height_cm, weight_kg)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try {
                long code;

                try (PreparedStatement ps = conn.prepareStatement("SELECT nextval('player_code_seq')");
                     ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new SQLException("Không thể cấp mã cầu thủ!");
                    code = rs.getLong(1);
                }

                String username = String.format("CT%03d", code);
                int userId;

                try (PreparedStatement ps = conn.prepareStatement(sqlUser)) {
                    ps.setString(1, username);
                    ps.setString(2, "player@123");
                    ps.setString(3, player.getFullName());
                    ps.setObject(4, player.getDob());
                    ps.setString(5, player.getEmail());
                    ps.setString(6, player.getPhone());

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Không thể tạo tài khoản cầu thủ!");
                        userId = rs.getInt("id");
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(sqlPlayer)) {
                    ps.setInt(1, userId);
                    ps.setString(2, player.getNationality());
                    ps.setString(3, player.getPrimaryPosition());
                    ps.setDouble(4, player.getHeightCm());
                    ps.setDouble(5, player.getWeightKg());
                    ps.executeUpdate();
                }

                conn.commit();

                player.setId(userId);
                player.setUsername(username);
                return true;

            } catch (SQLException | RuntimeException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            }
        }
    }

    public boolean updatePlayer(Player player) throws SQLException {
        String sqlUser = """
                UPDATE users
                SET full_name = ?, dob = ?, email = ?, phone = ?
                WHERE id = ?
                """;

        String sqlPlayer = """
                UPDATE players
                SET nationality = ?, primary_position = ?, height_cm = ?, weight_kg = ?
                WHERE user_id = ?
                """;

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try {
                int updatedUser, updatedPlayer;

                try (PreparedStatement ps = conn.prepareStatement(sqlUser)) {
                    ps.setString(1, player.getFullName());
                    ps.setObject(2, player.getDob());
                    ps.setString(3, player.getEmail());
                    ps.setString(4, player.getPhone());
                    ps.setInt(5, player.getId());

                    updatedUser = ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(sqlPlayer)) {
                    ps.setString(1, player.getNationality());
                    ps.setString(2, player.getPrimaryPosition());
                    ps.setDouble(3, player.getHeightCm());
                    ps.setDouble(4, player.getWeightKg());
                    ps.setInt(5, player.getId());

                    updatedPlayer = ps.executeUpdate();
                }

                if (updatedUser != 1 || updatedPlayer != 1) {
                    conn.rollback();
                    return false;
                }

                conn.commit();
                return true;

            } catch (SQLException | RuntimeException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            }
        }
    }
}
