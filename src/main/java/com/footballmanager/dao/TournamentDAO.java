package com.footballmanager.dao;

import com.footballmanager.model.Tournament;

import java.sql.*;

public class TournamentDAO extends DAO {
    public Tournament getTournament() throws SQLException {
        String sql = """
            SELECT
                id,
                name
            FROM tournaments
            ORDER BY id
            LIMIT 2
            """;
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            if (!rows.next()) {
                throw new IllegalArgumentException("Lỗi cấu hình: chưa có giải bóng đá vô địch quốc gia. Vui lòng " + "liên hệ quản trị dữ liệu.");
            }
            Tournament tournament = new Tournament();
            tournament.setId(rows.getInt("id"));
            tournament.setName(rows.getString("name"));
            if (rows.next()) {
                throw new IllegalArgumentException("Lỗi cấu hình: hệ thống yêu cầu đúng một giải đấu, nhưng đang có " + "nhiều bản ghi.");
            }
            return tournament;
        }
    }
}
