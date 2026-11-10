package com.footballmanager.dao;

import com.footballmanager.model.Round;
import com.footballmanager.model.Season;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class RoundDAO extends DAO {
    public List<Round> searchRound(Season season, String key) throws SQLException {
        if (season == null || season.getId() <= 0) {
            throw new IllegalArgumentException("Ngữ cảnh mùa giải không hợp lệ.");
        }
        List<Round> result = new ArrayList<>();
        String sql = """
            SELECT id,
                   number,
                   name,
                   start_time,
                   end_time,
                   status
            FROM rounds
            WHERE season_id = ?
              AND (name ILIKE ?
                   OR ('R' || CASE WHEN number < 10 THEN '0' ELSE '' END || number::TEXT) ILIKE ?)
            ORDER BY number,
                     id
            """;
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, season.getId());
            String pattern = "%" + (key == null ? "" : key.trim()) + "%";
            statement.setString(2, pattern);
            statement.setString(3, pattern);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    Round round = new Round();
                    round.setId(rows.getInt("id"));
                    round.setNumber(rows.getInt("number"));
                    round.setName(rows.getString("name"));
                    round.setStartTime(rows.getObject("start_time", LocalDateTime.class));
                    round.setEndTime(rows.getObject("end_time", LocalDateTime.class));
                    round.setStatus(rows.getString("status"));
                    result.add(round);
                }
            }
        }
        return result;
    }
}
