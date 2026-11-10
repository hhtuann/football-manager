package com.footballmanager.dao;

import com.footballmanager.model.*;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class SeasonDAO extends DAO {
    public List<Season> searchSeason(Tournament tournament, String key) throws SQLException {
        if (tournament == null || tournament.getId() <= 0) {
            throw new IllegalArgumentException("Ngữ cảnh giải đấu không hợp lệ.");
        }
        String sql = """
            SELECT
                s.*,
                r.required_main_players,
                r.max_bench_players,
                r.max_substitutions,
                r.yellow_cards_to_suspend,
                r.age_limit,
                r.max_foreign_players
            FROM seasons s
            JOIN rules r
                ON r.id = s.rule_id
            WHERE s.tournament_id = ?
                AND (
                    s.name ILIKE ?
                    OR CAST(s.year AS TEXT) ILIKE ?
                    OR s.status ILIKE ?
                )
            ORDER BY
                s.year DESC,
                s.id
            """;
        List<Season> result = new ArrayList<>();
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            String keyword = "%" + (key == null ? "" : key.trim()) + "%";
            statement.setInt(1, tournament.getId());
            statement.setString(2, keyword);
            statement.setString(3, keyword);
            statement.setString(4, keyword);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    Season season = new Season();
                    season.setId(rows.getInt("id"));
                    season.setYear(rows.getInt("year"));
                    season.setName(rows.getString("name"));
                    season.setStartDate(rows.getObject("start_date", LocalDate.class));
                    season.setEndDate(rows.getObject("end_date", LocalDate.class));
                    season.setStatus(rows.getString("status"));
                    Rule rule = new Rule();
                    rule.setId(rows.getInt("rule_id"));
                    rule.setRequiredMainPlayers(rows.getInt("required_main_players"));
                    rule.setMaxBenchPlayers(rows.getInt("max_bench_players"));
                    rule.setMaxSubstitutions(rows.getInt("max_substitutions"));
                    rule.setYellowCardsToSuspend(rows.getInt("yellow_cards_to_suspend"));
                    rule.setAgeLimit(rows.getInt("age_limit"));
                    rule.setMaxForeignPlayers(rows.getInt("max_foreign_players"));
                    season.setRule(rule);
                    result.add(season);
                }
            }
        }
        return result;
    }
}
