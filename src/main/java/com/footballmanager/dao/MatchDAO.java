package com.footballmanager.dao;

import com.footballmanager.model.*;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.*;

/**
 * Loads the complete match graph and owns atomic lineup replacement.
 */
public class MatchDAO extends DAO {
    public List<Match> searchMatch(Round round, String key) throws SQLException {
        require(round != null && round.getId() > 0, "Ngữ cảnh vòng đấu không hợp lệ.");
        String sql = """
            WITH numbered_matches AS (
                SELECT m.id,
                       m.kickoff_time,
                       m.status,
                       ROW_NUMBER() OVER (ORDER BY m.id) AS match_ordinal,
                       r.number AS display_round
                FROM matches m
                JOIN rounds r ON r.id = m.round_id
                WHERE m.round_id = ?
            )
            SELECT m.id
            FROM numbered_matches m
            WHERE (('R' || CASE WHEN display_round < 10 THEN '0' ELSE '' END || display_round::TEXT
                        || '-T' || CASE WHEN match_ordinal < 10 THEN '0' ELSE '' END || match_ordinal::TEXT) ILIKE ?
                   OR CAST(m.id AS TEXT) ILIKE ?
                   OR CAST(m.kickoff_time AS TEXT) ILIKE ?
                   OR m.status ILIKE ?
                   OR EXISTS (
                       SELECT 1
                       FROM match_clubs mc
                       JOIN clubs c ON c.id = mc.club_id
                       WHERE mc.match_id = m.id
                         AND (c.full_name ILIKE ?
                              OR c.short_name ILIKE ?)
                   ))
            ORDER BY m.id
            """;
        List<Match> result = new ArrayList<>();
        try (Connection connection = getConnection()) {
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                List<Integer> ids = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setInt(1, round.getId());
                    String pattern = "%" + (key == null ? "" : key.trim()) + "%";
                    for (int i = 2; i <= 7; i++) {
                        statement.setString(i, pattern);
                    }
                    try (ResultSet rows = statement.executeQuery()) {
                        while (rows.next()) {
                            ids.add(rows.getInt(1));
                        }
                    }
                }
                for (int id : ids) {
                    result.add(loadMatch(connection, id));
                }
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                rollback(connection, ex);
                throw ex;
            }
        }
        return result;
    }

    /**
     * Baseline is immutable; only candidate is refreshed, and only after commit.
     */
    public boolean saveLineup(Match baseline, MatchClub candidate) throws SQLException {
        require(baseline != null && baseline.getId() > 0 && candidate != null, "Ngữ cảnh trận đấu không hợp lệ.");
        require(candidate.getClub() != null && candidate.getMatchPlayers() != null, "Đội hình đề xuất không hợp lệ.");
        MatchClub original = side(baseline, candidate.getId());
        require(original != null && original != candidate, "Phải giữ nguyên đội hình gốc khi gửi đề xuất.");
        List<MatchPlayer> saved;
        try (Connection connection = getConnection()) {
            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            connection.setAutoCommit(false);
            try {
                int seasonId;
                String seasonSql = """
                    SELECT r.season_id
                    FROM matches m
                    JOIN rounds r ON r.id = m.round_id
                    WHERE m.id = ?
                    """;
                try (PreparedStatement statement = connection.prepareStatement(seasonSql)) {
                    statement.setInt(1, baseline.getId());
                    try (ResultSet rows = statement.executeQuery()) {
                        require(rows.next(), "Trận đấu không còn tồn tại.");
                        seasonId = rows.getInt(1);
                    }
                }
                Rule rule;
                LocalDate startDate;
                String ruleSql = """
                    SELECT s.start_date,
                           ru.required_main_players,
                           ru.max_bench_players,
                           ru.max_foreign_players,
                           ru.age_limit,
                           ru.yellow_cards_to_suspend
                    FROM seasons s
                    JOIN rules ru ON ru.id = s.rule_id
                    WHERE s.id = ?
                    FOR SHARE OF s, ru
                    """;
                try (PreparedStatement statement = connection.prepareStatement(ruleSql)) {
                    statement.setInt(1, seasonId);
                    try (ResultSet rows = statement.executeQuery()) {
                        require(rows.next(), "Không tìm thấy điều lệ mùa giải.");
                        startDate = rows.getObject("start_date", LocalDate.class);
                        rule = new Rule();
                        rule.setRequiredMainPlayers(rows.getInt("required_main_players"));
                        rule.setMaxBenchPlayers(rows.getInt("max_bench_players"));
                        rule.setMaxForeignPlayers(rows.getInt("max_foreign_players"));
                        rule.setAgeLimit(rows.getInt("age_limit"));
                        rule.setYellowCardsToSuspend(rows.getInt("yellow_cards_to_suspend"));
                    }
                }
                // Only this fixture is exclusive; SERIALIZABLE protects historical suspension reads.
                // UPDATE also conflicts with event inserts taking a match FK key-share lock.
                String matchLockSql = """
                    SELECT m.id
                    FROM matches m
                    JOIN rounds r ON r.id = m.round_id
                    WHERE m.id = ?
                    FOR UPDATE OF m
                    FOR SHARE OF r
                    """;
                try (PreparedStatement statement = connection.prepareStatement(matchLockSql)) {
                    statement.setInt(1, baseline.getId());
                    try (ResultSet rows = statement.executeQuery()) {
                        require(rows.next(), "Trận đấu không còn tồn tại.");
                    }
                }
                String sidesLockSql = """
                    SELECT id
                    FROM match_clubs
                    WHERE match_id = ?
                    ORDER BY id
                    FOR UPDATE
                    """;
                lockRows(connection, sidesLockSql, baseline.getId());
                String playersLockSql = """
                    SELECT mp.id
                    FROM match_players mp
                    JOIN match_clubs mc ON mc.id = mp.match_club_id
                    WHERE mc.match_id = ?
                    ORDER BY mp.id
                    FOR UPDATE OF mp
                    """;
                lockRows(connection, playersLockSql, baseline.getId());
                Match current = loadMatch(connection, baseline.getId());
                validateGraph(current);
                require(!current.isLineupLocked(),
                    "Trận đấu đã khóa đăng ký (trạng thái, giờ thi đấu hoặc đã có sự " + "kiện).");
                validateBaseline(baseline, current);
                MatchClub target = side(current, candidate.getId());
                require(target != null && Objects.equals(target.getRole(), candidate.getRole()) && target.getClub().getId() == candidate.getClub().getId(), "CLB không thuộc đúng bên của trận đấu.");
                Map<Integer, ClubPlayer> members = loadMembers(connection, target.getClub().getId(), true);
                Map<Integer, MatchPlayer> desired = new LinkedHashMap<>();
                Set<Integer> playerIds = new HashSet<>();
                int starting = 0, bench = 0, foreign = 0;
                boolean goalkeeper = false;
                require(rule.getRequiredMainPlayers() == 11 && rule.getYellowCardsToSuspend() > 0 && rule.getAgeLimit() > 0 && rule.getMaxBenchPlayers() >= 0 && rule.getMaxForeignPlayers() >= 0, "Cấu hình điều lệ không hợp lệ: số cầu thủ đá chính phải bằng 11.");
                for (MatchPlayer proposed : candidate.getMatchPlayers()) {
                    require(proposed != null && proposed.getClubPlayer() != null, "Cầu thủ đăng ký không hợp lệ.");
                    ClubPlayer member = members.get(proposed.getClubPlayer().getId());
                    require(member != null, "Cầu thủ không thuộc CLB đang đăng ký.");
                    require(desired.putIfAbsent(member.getId(), proposed) == null && playerIds.add(member.getPlayer().getId()), "Cầu thủ bị trùng trong đội hình.");
                    Player player = member.getPlayer();
                    if ("STARTING".equals(proposed.getRole())) {
                        starting++;
                        if ("Thủ môn".equals(player.getPrimaryPosition())) {
                            goalkeeper = true;
                        }
                    } else if ("SUBSTITUTE".equals(proposed.getRole())) {
                        bench++;
                    } else {
                        throw new IllegalArgumentException("Vai trò cầu thủ không hợp lệ.");
                    }
                    require(player.getDob() != null && !player.getDob().isAfter(startDate), "Ngày sinh cầu thủ thiếu "
                        + "hoặc sau ngày bắt đầu mùa giải.");
                    require(Period.between(player.getDob(), startDate).getYears() <= rule.getAgeLimit(),
                        "Cầu thủ " + "vượt giới hạn tuổi của mùa giải.");
                    String nationality = player.getNationality();
                    require(nationality != null && !nationality.trim().isEmpty(), "Cầu thủ chưa có quốc tịch hợp lệ.");
                    if (!"Việt Nam".equalsIgnoreCase(nationality.trim())) {
                        foreign++;
                    }
                }
                require(starting == 11, "Đội hình phải có đúng 11 cầu thủ đá chính.");
                require(goalkeeper, "Đội hình đá chính phải có ít nhất một Thủ môn.");
                require(bench <= rule.getMaxBenchPlayers(), "Số cầu thủ dự bị vượt điều lệ.");
                require(foreign <= rule.getMaxForeignPlayers(), "Tổng ngoại binh trong đội hình vượt điều lệ.");
                for (MatchClub other : current.getMatchClubs()) {
                    if (other.getId() != target.getId()) {
                        for (MatchPlayer mp : other.getMatchPlayers()) {
                            require(!playerIds.contains(mp.getClubPlayer().getPlayer().getId()),
                                "Một cầu thủ không " + "được đăng ký cho cả hai bên.");
                        }
                    }
                }
                validateSuspensions(connection, seasonId, current.getKickoffTime(), target.getClub().getId(),
                    playerIds, rule.getYellowCardsToSuspend());
                saved = writeDifference(connection, target, desired, members);
                // clock_timestamp, not transaction-start time: a long lock wait may cross kickoff.
                String deadlineSql = """
                    SELECT status = 'SCHEDULED'
                           AND kickoff_time IS NOT NULL
                           AND kickoff_time > CLOCK_TIMESTAMP()::TIMESTAMP
                           AND NOT EXISTS (
                               SELECT 1
                               FROM events
                               WHERE match_id = matches.id
                           )
                    FROM matches
                    WHERE id = ?
                    """;
                try (PreparedStatement statement = connection.prepareStatement(deadlineSql)) {
                    statement.setInt(1, baseline.getId());
                    try (ResultSet rows = statement.executeQuery()) {
                        require(rows.next() && rows.getBoolean(1), "Đã hết thời hạn đăng ký đội hình.");
                    }
                }
                connection.commit();
            } catch (SQLException ex) {
                rollback(connection, ex);
                if ("40001".equals(ex.getSQLState())) {
                    throw new IllegalArgumentException("Dữ liệu đã thay đổi đồng thời. Vui lòng tìm lại trận đấu và " + "thử lại.");
                }
                throw ex;
            } catch (RuntimeException ex) {
                rollback(connection, ex);
                throw ex;
            }
        }
        candidate.setMatchPlayers(saved);
        return true;
    }

    /**
     * Ends a match, including a scoreless match, using current database registrations.
     */
    public boolean saveMatch(Match match) throws SQLException {
        require(match != null && match.getId() > 0, "Ngữ cảnh trận đấu không hợp lệ.");
        Match current;
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                current = lockEventMatch(connection, match.getId());
                EventDAO.validateTimeline(connection, current);
                String finishSql = """
                    UPDATE matches
                    SET status = 'FINISHED'
                    WHERE id = ?
                    """;
                try (PreparedStatement statement = connection.prepareStatement(finishSql)) {
                    statement.setInt(1, match.getId());
                    statement.executeUpdate();
                }
                current.setStatus("FINISHED");
                current.setEvents(EventDAO.loadEvents(connection, current));
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                rollback(connection, ex);
                throw ex;
            }
        }
        applyCommittedMatch(match, current);
        return true;
    }

    /**
     * Shared write boundary: serialize lineup, event and finalization writes on the fixture.
     */
    static Match lockEventMatch(Connection connection, int id) throws SQLException {
        String matchLockSql = """
            SELECT status
            FROM matches
            WHERE id = ?
            FOR UPDATE
            """;
        try (PreparedStatement statement = connection.prepareStatement(matchLockSql)) {
            statement.setInt(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                require(rows.next(), "Trận đấu không còn tồn tại.");
                require("SCHEDULED".equals(rows.getString(1)) || "ONGOING".equals(rows.getString(1)),
                    "Trận đấu đã " + "kết thúc, không thể cập nhật.");
            }
        }
        String sidesLockSql = """
            SELECT id
            FROM match_clubs
            WHERE match_id = ?
            ORDER BY id
            FOR UPDATE
            """;
        lockRows(connection, sidesLockSql, id);
        String playersLockSql = """
            SELECT mp.id
            FROM match_players mp
            JOIN match_clubs mc ON mc.id = mp.match_club_id
            WHERE mc.match_id = ?
            ORDER BY mp.id
            FOR UPDATE OF mp
            """;
        lockRows(connection, playersLockSql, id);
        Match current = loadMatch(connection, id);
        validateGraph(current);
        int required, maxBench, maxForeign;
        String ruleSql = """
            SELECT ru.required_main_players,
                   ru.max_bench_players,
                   ru.max_foreign_players
            FROM matches m
            JOIN rounds r ON r.id = m.round_id
            JOIN seasons s ON s.id = r.season_id
            JOIN rules ru ON ru.id = s.rule_id
            WHERE m.id = ?
            FOR SHARE OF r, s, ru
            """;
        try (PreparedStatement statement = connection.prepareStatement(ruleSql)) {
            statement.setInt(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                require(rows.next(), "Không tìm thấy điều lệ mùa giải.");
                required = rows.getInt(1);
                maxBench = rows.getInt(2);
                maxForeign = rows.getInt(3);
            }
        }
        require(required == 11 && maxBench >= 0 && maxForeign >= 0, "Cấu hình điều lệ không hợp lệ.");
        Set<Integer> playerIds = new HashSet<>();
        for (MatchClub mc : current.getMatchClubs()) {
            int starting = 0, bench = 0, foreign = 0;
            boolean goalkeeper = false;
            for (MatchPlayer mp : mc.getMatchPlayers()) {
                Player player = mp.getClubPlayer().getPlayer();
                require(playerIds.add(player.getId()), "Một cầu thủ không được đăng ký cho cả hai bên.");
                if ("STARTING".equals(mp.getRole())) {
                    starting++;
                    goalkeeper |= "Thủ môn".equals(player.getPrimaryPosition());
                } else {
                    require("SUBSTITUTE".equals(mp.getRole()), "Vai trò đăng ký không hợp lệ.");
                    bench++;
                }
                require(player.getNationality() != null && !player.getNationality().trim().isEmpty(), "Quốc tịch cầu "
                    + "thủ không hợp lệ.");
                if (!"Việt Nam".equalsIgnoreCase(player.getNationality().trim())) {
                    foreign++;
                }
            }
            require(starting == 11 && goalkeeper && bench <= maxBench && foreign <= maxForeign,
                "Cả hai CLB phải có " + "đội hình đầy đủ, hợp lệ trước khi cập nhật kết quả.");
        }
        return current;
    }

    static void refreshScores(Connection connection, Match match) throws SQLException {
        String scoreSql = """
            SELECT home_score,
                   away_score
            FROM v_match_scores
            WHERE match_id = ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(scoreSql)) {
            statement.setInt(1, match.getId());
            try (ResultSet rows = statement.executeQuery()) {
                require(rows.next(), "Không tìm thấy tỷ số trận đấu.");
                for (MatchClub mc : match.getMatchClubs()) {
                    mc.setScore(rows.getInt("HOME".equals(mc.getRole()) ? "home_score" : "away_score"));
                }
            }
        }
    }

    static void applyCommittedMatch(Match target, Match saved) {
        target.setStatus(saved.getStatus());
        target.setKickoffTime(saved.getKickoffTime());
        target.setMatchClubs(saved.getMatchClubs());
        target.setEvents(saved.getEvents());
        target.setLineupLocked(true);
    }

    private static void validateGraph(Match match) {
        require(match != null && match.getMatchClubs().size() == 2, "Trận đấu phải có đúng hai CLB HOME và AWAY.");
        Set<String> roles = new HashSet<>();
        Set<Integer> clubs = new HashSet<>();
        for (MatchClub mc : match.getMatchClubs()) {
            require(mc.getClub() != null && ("HOME".equals(mc.getRole()) || "AWAY".equals(mc.getRole())) && roles.add(mc.getRole()) && clubs.add(mc.getClub().getId()), "Ngữ cảnh HOME/AWAY không hợp lệ.");
            Set<Integer> players = new HashSet<>();
            for (MatchPlayer mp : mc.getMatchPlayers()) {
                require(players.add(mp.getClubPlayer().getPlayer().getId()) && mc.getClub().getClubPlayers().stream().anyMatch(cp -> cp.getId() == mp.getClubPlayer().getId()), "Đội hình hiện tại có quan hệ CLB không hợp lệ.");
            }
        }
    }

    private static void validateBaseline(Match old, Match current) {
        require(Objects.equals(old.getStatus(), current.getStatus()) && Objects.equals(old.getKickoffTime(),
            current.getKickoffTime()) && old.getMatchClubs() != null && old.getMatchClubs().size() == current.getMatchClubs().size(), "Dữ liệu trận đã thay đổi. Vui lòng tìm lại trận.");
        for (MatchClub actual : current.getMatchClubs()) {
            MatchClub expected = side(old, actual.getId());
            require(expected != null && expected.getClub() != null && expected.getClub().getId() == actual.getClub().getId() && Objects.equals(expected.getRole(), actual.getRole()) && expected.getMatchPlayers() != null && expected.getMatchPlayers().size() == actual.getMatchPlayers().size(), "Đội hình đã thay đổi. Vui lòng tìm lại trận.");
            Map<Integer, MatchPlayer> snapshot = new HashMap<>();
            for (MatchPlayer mp : expected.getMatchPlayers()) {
                require(mp != null && snapshot.put(mp.getId(), mp) == null, "Đội hình gốc không hợp lệ.");
            }
            for (MatchPlayer mp : actual.getMatchPlayers()) {
                MatchPlayer before = snapshot.get(mp.getId());
                require(before != null && before.getClubPlayer() != null && before.getClubPlayer().getId() == mp.getClubPlayer().getId() && Objects.equals(before.getRole(), mp.getRole()) && Objects.equals(before.getRegisteredAt(), mp.getRegisteredAt()), "Đội hình đã thay đổi. Vui lòng tìm lại trận.");
            }
        }
    }

    private static List<MatchPlayer> writeDifference(Connection connection, MatchClub target, Map<Integer,
        MatchPlayer> desired, Map<Integer, ClubPlayer> members) throws SQLException {
        Map<Integer, MatchPlayer> previous = new HashMap<>();
        for (MatchPlayer mp : target.getMatchPlayers()) {
            previous.put(mp.getClubPlayer().getId(), mp);
        }
        String deleteSql = """
            DELETE FROM match_players
            WHERE id = ?
              AND match_club_id = ?
            """;
        try (PreparedStatement delete = connection.prepareStatement(deleteSql)) {
            for (MatchPlayer mp : previous.values()) {
                if (!desired.containsKey(mp.getClubPlayer().getId())) {
                    delete.setInt(1, mp.getId());
                    delete.setInt(2, target.getId());
                    delete.executeUpdate();
                }
            }
        }
        List<MatchPlayer> saved = new ArrayList<>();
        String updateSql = """
            UPDATE match_players
            SET role = ?
            WHERE id = ?
            RETURNING id,
                      registered_at
            """;
        String insertSql = """
            INSERT INTO match_players (
                match_club_id,
                club_player_id,
                role
            )
            VALUES (?, ?, ?)
            RETURNING id,
                      registered_at
            """;
        try (PreparedStatement update = connection.prepareStatement(updateSql);
             PreparedStatement insert = connection.prepareStatement(insertSql)) {
            for (Map.Entry<Integer, MatchPlayer> entry : desired.entrySet()) {
                MatchPlayer old = previous.get(entry.getKey());
                PreparedStatement statement;
                if (old == null) {
                    statement = insert;
                    statement.setInt(1, target.getId());
                    statement.setInt(2, entry.getKey());
                    statement.setString(3, entry.getValue().getRole());
                } else {
                    statement = update;
                    statement.setString(1, entry.getValue().getRole());
                    statement.setInt(2, old.getId());
                }
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        throw new SQLException("Không lưu được đăng ký cầu thủ.");
                    }
                    MatchPlayer mp = new MatchPlayer();
                    mp.setId(rows.getInt("id"));
                    mp.setRegisteredAt(rows.getObject("registered_at", LocalDateTime.class));
                    mp.setRole(entry.getValue().getRole());
                    mp.setClubPlayer(members.get(entry.getKey()));
                    saved.add(mp);
                }
            }
        }
        return saved;
    }

    /**
     * Each booking creates debt; only subsequent FINISHED fixtures of the booked club serve it.
     */
    private static void validateSuspensions(Connection connection, int seasonId, LocalDateTime kickoff, int clubId,
                                            Set<Integer> selected, int threshold) throws SQLException {
        String sql = """
            SELECT m.id,
                   m.kickoff_time,
                   m.status,
                   mc.club_id,
                   cp.player_id,
                   COUNT(y.id) AS yellows,
                   COUNT(rc.id) AS reds
            FROM matches m
            JOIN rounds r ON r.id = m.round_id
            JOIN match_clubs mc ON mc.match_id = m.id
            LEFT JOIN match_players mp ON mp.match_club_id = mc.id
            LEFT JOIN club_players cp ON cp.id = mp.club_player_id
            LEFT JOIN events e ON e.match_id = m.id
                             AND e.match_player_id = mp.id
            LEFT JOIN yellow_cards y ON y.id = e.id
            LEFT JOIN red_cards rc ON rc.id = e.id
            WHERE r.season_id = ?
              AND m.kickoff_time < ?
            GROUP BY m.id,
                     m.kickoff_time,
                     m.status,
                     mc.club_id,
                     cp.player_id
            ORDER BY m.kickoff_time,
                     m.id,
                     mc.club_id
            """;
        Map<Integer, Integer> yellowTotals = new HashMap<>();
        Map<Integer, Map<Integer, Integer>> debts = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, seasonId);
            statement.setObject(2, kickoff);
            try (ResultSet rows = statement.executeQuery()) {
                int lastMatch = -1, lastClub = -1;
                while (rows.next()) {
                    int matchId = rows.getInt("id"), bookedClub = rows.getInt("club_id");
                    Map<Integer, Integer> clubDebt = debts.computeIfAbsent(bookedClub, ignored -> new HashMap<>());
                    if (matchId != lastMatch || bookedClub != lastClub) {
                        if ("FINISHED".equals(rows.getString("status"))) {
                            clubDebt.replaceAll((player, debt) -> Math.max(0, debt - 1));
                        }
                        lastMatch = matchId;
                        lastClub = bookedClub;
                    }
                    int player = rows.getInt("player_id");
                    if (rows.wasNull()) {
                        continue;
                    }
                    int before = yellowTotals.getOrDefault(player, 0), after = before + rows.getInt("yellows");
                    yellowTotals.put(player, after);
                    int newBans = after / threshold - before / threshold + rows.getInt("reds");
                    if (newBans > 0) {
                        clubDebt.merge(player, newBans, Integer::sum);
                    }
                }
            }
        }
        Map<Integer, Integer> debt = debts.getOrDefault(clubId, Collections.emptyMap());
        for (int player : selected) {
            require(debt.getOrDefault(player, 0) == 0, "Cầu thủ đang bị treo giò trong mùa giải.");
        }
    }

    private static Match loadMatch(Connection connection, int id) throws SQLException {
        Match match = new Match();
        String matchSql = """
            SELECT m.kickoff_time,
                   m.status,
                   r.number AS round_number,
                   (
                       SELECT COUNT(*)
                       FROM matches sibling
                       WHERE sibling.round_id = m.round_id
                         AND sibling.id <= m.id
                   ) AS match_ordinal,
                   (m.kickoff_time IS NULL
                    OR m.status <> 'SCHEDULED'
                    OR m.kickoff_time <= CLOCK_TIMESTAMP()::TIMESTAMP
                    OR EXISTS (
                        SELECT 1
                        FROM events e
                        WHERE e.match_id = m.id
                    )) AS locked
            FROM matches m
            JOIN rounds r ON r.id = m.round_id
            WHERE m.id = ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(matchSql)) {
            statement.setInt(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                require(rows.next(), "Không tìm thấy trận đấu.");
                match.setId(id);
                match.setKickoffTime(rows.getObject("kickoff_time", LocalDateTime.class));
                match.setStatus(rows.getString("status"));
                match.setLineupLocked(rows.getBoolean("locked"));
                match.setRoundNumber(rows.getInt("round_number"));
                match.setMatchNumber(rows.getInt("match_ordinal"));
            }
        }
        List<Integer> sideIds = new ArrayList<>();
        String sidesSql = """
            SELECT id
            FROM match_clubs
            WHERE match_id = ?
            ORDER BY CASE role WHEN 'HOME' THEN 0 ELSE 1 END,
                     id
            """;
        try (PreparedStatement statement = connection.prepareStatement(sidesSql)) {
            statement.setInt(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    sideIds.add(rows.getInt(1));
                }
            }
        }
        for (int sideId : sideIds) {
            MatchClub mc = new MatchClub();
            mc.setId(sideId);
            int clubId;
            String sideSql = """
                SELECT mc.role,
                       mc.club_id,
                       CASE mc.role WHEN 'HOME' THEN v.home_score ELSE v.away_score END AS score
                FROM match_clubs mc
                JOIN v_match_scores v ON v.match_id = mc.match_id
                WHERE mc.id = ?
                """;
            try (PreparedStatement statement = connection.prepareStatement(sideSql)) {
                statement.setInt(1, sideId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        throw new SQLException("Thiếu CLB trong trận.");
                    }
                    mc.setRole(rows.getString("role"));
                    mc.setScore(rows.getInt("score"));
                    clubId = rows.getInt("club_id");
                }
            }
            Club club = loadClub(connection, clubId);
            mc.setClub(club);
            Map<Integer, ClubPlayer> members = loadMembers(connection, clubId, false);
            club.setClubPlayers(new ArrayList<>(members.values()));
            String playersSql = """
                SELECT id,
                       role,
                       registered_at,
                       club_player_id
                FROM match_players
                WHERE match_club_id = ?
                ORDER BY id
                """;
            try (PreparedStatement statement = connection.prepareStatement(playersSql)) {
                statement.setInt(1, sideId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        MatchPlayer mp = new MatchPlayer();
                        mp.setId(rows.getInt("id"));
                        mp.setRole(rows.getString("role"));
                        mp.setRegisteredAt(rows.getObject("registered_at", LocalDateTime.class));
                        mp.setClubPlayer(members.get(rows.getInt("club_player_id")));
                        require(mp.getClubPlayer() != null, "Đăng ký cầu thủ sai CLB.");
                        mc.getMatchPlayers().add(mp);
                    }
                }
            }
            match.getMatchClubs().add(mc);
        }
        return match;
    }

    private static Club loadClub(Connection connection, int id) throws SQLException {
        String sql = """
            SELECT c.full_name,
                   c.short_name,
                   c.founding_year,
                   c.coach_name,
                   c.stadium_id,
                   c.manager_id,
                   s.name AS stadium_name,
                   s.capacity,
                   s.address,
                   u.username,
                   u.full_name AS manager_name,
                   u.dob,
                   u.email,
                   u.phone
            FROM clubs c
            JOIN stadiums s ON s.id = c.stadium_id
            JOIN managers ma ON ma.user_id = c.manager_id
            JOIN users u ON u.id = ma.user_id
            WHERE c.id = ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                require(rows.next(), "Thiếu dữ liệu CLB, sân hoặc người quản lý.");
                Club club = new Club();
                club.setId(id);
                club.setFullName(rows.getString("full_name"));
                club.setShortName(rows.getString("short_name"));
                club.setFoundingYear(rows.getInt("founding_year"));
                club.setCoachName(rows.getString("coach_name"));
                Stadium stadium = new Stadium();
                stadium.setId(rows.getInt("stadium_id"));
                stadium.setName(rows.getString("stadium_name"));
                stadium.setCapacity(rows.getInt("capacity"));
                stadium.setAddress(rows.getString("address"));
                club.setHomeStadium(stadium);
                Manager manager = new Manager();
                manager.setId(rows.getInt("manager_id"));
                manager.setUsername(rows.getString("username"));
                manager.setFullName(rows.getString("manager_name"));
                manager.setDob(rows.getObject("dob", LocalDate.class));
                manager.setEmail(rows.getString("email"));
                manager.setPhone(rows.getString("phone"));
                club.setManager(manager);
                return club;
            }
        }
    }

    private static Map<Integer, ClubPlayer> loadMembers(Connection connection, int clubId, boolean lock) throws SQLException {
        String sql = """
            SELECT cp.id AS membership_id,
                   cp.shirt_number,
                   p.user_id AS player_id,
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
            JOIN players p ON p.user_id = cp.player_id
            JOIN users u ON u.id = p.user_id
            WHERE cp.club_id = ?
            ORDER BY cp.id
            """ + (lock ? "FOR UPDATE OF cp, p, u" : "");
        Map<Integer, ClubPlayer> result = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, clubId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    ClubPlayer member = ClubDAO.mapMember(rows);
                    result.put(member.getId(), member);
                }
            }
        }
        return result;
    }

    private static MatchClub side(Match match, int id) {
        if (match.getMatchClubs() != null) {
            for (MatchClub mc : match.getMatchClubs()) {
                if (mc != null && mc.getId() == id) {
                    return mc;
                }
            }
        }
        return null;
    }

    private static void lockRows(Connection connection, String sql, int id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) { /* acquire locks */ }
            }
        }
    }

    private static void require(boolean valid, String message) {
        if (!valid) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void rollback(Connection connection, Exception cause) {
        try {
            connection.rollback();
        } catch (SQLException failure) {
            cause.addSuppressed(failure);
        }
    }
}
