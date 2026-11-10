package com.footballmanager.dao;

import com.footballmanager.model.*;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ClubDAO extends DAO {
    public List<ClubPlayer> getClubPlayerList(Club club) throws SQLException {
        if (club == null || club.getId() <= 0) {
            throw new IllegalArgumentException("Ngữ cảnh CLB không hợp lệ.");
        }
        String sql = """
            SELECT
                cp.id AS membership_id,
                cp.shirt_number,
                u.id AS player_id,
                u.username,
                u.full_name,
                u.dob,
                u.email,
                u.phone,
                p.nationality,
                p.primary_position,
                p.height_cm,
                p.weight_kg
            FROM club_players cp
            JOIN players p
                ON p.user_id = cp.player_id
            JOIN users u
                ON u.id = p.user_id
            WHERE cp.club_id = ?
            ORDER BY
                cp.shirt_number,
                cp.id
            """;
        List<ClubPlayer> result = new ArrayList<>();
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, club.getId());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(mapMember(rows));
                }
            }
        }
        return result;
    }

    static ClubPlayer mapMember(ResultSet rows) throws SQLException {
        Player player = new Player();
        player.setId(rows.getInt("player_id"));
        player.setUsername(rows.getString("username"));
        player.setFullName(rows.getString("full_name"));
        player.setDob(rows.getObject("dob", LocalDate.class));
        player.setEmail(rows.getString("email"));
        player.setPhone(rows.getString("phone"));
        player.setNationality(rows.getString("nationality"));
        player.setPrimaryPosition(rows.getString("primary_position"));
        player.setHeightCm(rows.getDouble("height_cm"));
        player.setWeightKg(rows.getDouble("weight_kg"));
        ClubPlayer member = new ClubPlayer();
        member.setId(rows.getInt("membership_id"));
        member.setShirtNumber(rows.getInt("shirt_number"));
        member.setPlayer(player);
        return member;
    }
}
