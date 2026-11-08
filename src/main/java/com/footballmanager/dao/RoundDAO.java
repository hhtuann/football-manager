package com.footballmanager.dao;

import com.footballmanager.model.Round;
import com.footballmanager.model.Season;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class RoundDAO extends DAO {
    public List<Round> searchRound(Season season, String key) throws SQLException {
        if (season == null || season.getId() <= 0)
            throw new IllegalArgumentException("Ngữ cảnh mùa giải không hợp lệ.");
        List<Round> result = new ArrayList<>();
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(
                """
                WITH numbered_rounds AS (
                    SELECT *,CASE WHEN name ~ '^Vòng [1-9][0-9]{0,8}$'
                                  THEN substring(name FROM 6)::integer END AS display_number
                    FROM rounds WHERE season_id=?
                )
                SELECT id,name,start_time,end_time,status FROM numbered_rounds
                WHERE name ILIKE ? OR ('R' || CASE WHEN display_number<10 THEN '0' ELSE '' END || display_number::text) ILIKE ?
                ORDER BY display_number NULLS LAST,id
                """)) {
            statement.setInt(1, season.getId());
            String pattern = "%" + (key == null ? "" : key.trim()) + "%";
            statement.setString(2, pattern);
            statement.setString(3, pattern);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    Round round = new Round();
                    round.setId(rows.getInt("id"));
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
