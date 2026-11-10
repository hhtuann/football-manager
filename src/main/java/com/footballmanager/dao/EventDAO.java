package com.footballmanager.dao;

import com.footballmanager.model.*;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Persists typed events and validates the complete chronological on-field history.
 */
public class EventDAO extends DAO {
    private static final String EVENT_QUERY = """
        SELECT
            e.*,
            g.id AS goal_id,
            g.goal_type,
            g.assister_id,
            f.id AS foul_id,
            f.victim_id,
            y.id AS yellow_id,
            r.id AS red_id,
            s.id AS substitution_id,
            s.player_in_id
        FROM events e
        LEFT JOIN goals g
            ON g.id = e.id
        LEFT JOIN fouls f
            ON f.id = e.id
        LEFT JOIN yellow_cards y
            ON y.id = e.id
        LEFT JOIN red_cards r
            ON r.id = e.id
        LEFT JOIN substitutions s
            ON s.id = e.id
        WHERE e.match_id = ?
        ORDER BY
            e.minute,
            e.extra_minute,
            e.id
        """;

    /**
     * Refreshes only status and derived scores; the session remains the owner of its cached lineup.
     */
    public void refreshMatch(Match match) throws SQLException {
        require(match != null && match.getId() > 0 && match.getMatchClubs() != null, "Ngữ cảnh trận đấu không hợp lệ.");
        String status;
        int homeScore, awayScore;
        String sql = """
            SELECT
                m.status,
                v.home_score,
                v.away_score
            FROM matches m
            JOIN v_match_scores v
                ON v.match_id = m.id
            WHERE m.id = ?
            """;
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, match.getId());
            try (ResultSet rows = statement.executeQuery()) {
                require(rows.next(), "Trận đấu không còn tồn tại.");
                status = rows.getString("status");
                homeScore = rows.getInt("home_score");
                awayScore = rows.getInt("away_score");
            }
        }
        match.setStatus(status);
        if (!"SCHEDULED".equals(status)) {
            match.setLineupLocked(true);
        }
        for (MatchClub side : match.getMatchClubs()) {
            side.setScore("HOME".equals(side.getRole()) ? homeScore : awayScore);
        }
    }

    public List<Event> getEventList(int matchId) throws SQLException {
        require(matchId > 0, "Mã trận đấu không hợp lệ.");
        try (Connection connection = getConnection()) {
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            // Only event-referenced players are mapped; views keep using their cached lineup.
            Map<Integer, MatchPlayer> players = new HashMap<>();
            String sql = """
                SELECT
                    mp.id AS registration_id,
                    mp.role,
                    mp.registered_at,
                    cp.id AS membership_id,
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
                FROM match_players mp
                JOIN club_players cp
                    ON cp.id = mp.club_player_id
                JOIN players p
                    ON p.user_id = cp.player_id
                JOIN users u
                    ON u.id = p.user_id
                WHERE mp.id IN (
                    SELECT
                        match_player_id
                    FROM events
                    WHERE match_id = ?
                    UNION
                    SELECT
                        g.assister_id
                    FROM goals g
                    JOIN events e
                        ON e.id = g.id
                    WHERE e.match_id = ?
                    UNION
                    SELECT
                        f.victim_id
                    FROM fouls f
                    JOIN events e
                        ON e.id = f.id
                    WHERE e.match_id = ?
                    UNION
                    SELECT
                        s.player_in_id
                    FROM substitutions s
                    JOIN events e
                        ON e.id = s.id
                    WHERE e.match_id = ?
                )
                """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 1; i <= 4; i++) {
                    statement.setInt(i, matchId);
                }
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        MatchPlayer player = new MatchPlayer();
                        player.setId(rows.getInt("registration_id"));
                        player.setRole(rows.getString("role"));
                        player.setRegisteredAt(rows.getObject("registered_at", LocalDateTime.class));
                        player.setClubPlayer(ClubDAO.mapMember(rows));
                        players.put(player.getId(), player);
                    }
                }
            }
            List<Event> events = loadEvents(connection, matchId, players);
            connection.commit();
            return events;
        }
    }

    public boolean addEvent(Match match, Event event) throws SQLException {
        require(match != null && match.getId() > 0 && event != null, "Ngữ cảnh sự kiện không hợp lệ.");
        require(event.getId() == 0, "Chỉ được thêm sự kiện mới.");
        validateShape(event);
        Match current;
        int savedId;
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                current = MatchDAO.lockEventMatch(connection, match.getId());
                Map<Integer, MatchPlayer> registered = players(current);
                require(registered.containsKey(event.getPrimaryPlayer().getId()), "Cầu thủ không thuộc đội hình trận "
                    + "đấu.");
                MatchPlayer related = relatedPlayer(event);
                require(related == null || registered.containsKey(related.getId()),
                    "Cầu thủ liên quan không thuộc " + "đội hình trận đấu.");
                // Validate before insertion as well as after: no trigger/unique error substitutes for business
                // validation.
                List<Event> timeline = loadEvents(connection, current);
                timeline.add(event);
                timeline.sort(Comparator.comparingInt(Event::getMinute).thenComparingInt(Event::getExtraMinute).thenComparingLong(e -> e == event ? Long.MAX_VALUE : e.getId()));
                validateTimeline(connection, current, timeline);
                String insertEventSql = """
                    INSERT INTO events (
                        minute,
                        extra_minute,
                        note,
                        match_id,
                        match_player_id
                    )
                    VALUES (
                        ?,
                        ?,
                        ?,
                        ?,
                        ?
                    )
                    RETURNING id
                    """;
                try (PreparedStatement statement = connection.prepareStatement(insertEventSql)) {
                    statement.setInt(1, event.getMinute());
                    statement.setInt(2, event.getExtraMinute());
                    statement.setString(3, event.getNote());
                    statement.setInt(4, match.getId());
                    statement.setInt(5, event.getPrimaryPlayer().getId());
                    try (ResultSet rows = statement.executeQuery()) {
                        if (!rows.next()) {
                            throw new SQLException("Không nhận được mã sự kiện đã lưu.");
                        }
                        savedId = rows.getInt(1);
                    }
                }
                insertSubtype(connection, savedId, event);
                String updateMatchSql = """
                    UPDATE matches
                    SET status = 'ONGOING'
                    WHERE id = ?
                    """;
                try (PreparedStatement statement = connection.prepareStatement(updateMatchSql)) {
                    statement.setInt(1, match.getId());
                    statement.executeUpdate();
                }
                current.setStatus("ONGOING");
                current.setEvents(loadEvents(connection, current));
                MatchDAO.refreshScores(connection, current);
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                try {
                    connection.rollback();
                } catch (SQLException failure) {
                    ex.addSuppressed(failure);
                }
                throw ex;
            }
        }
        event.setId(savedId);
        MatchDAO.applyCommittedMatch(match, current);
        return true;
    }

    static List<Event> loadEvents(Connection connection, Match match) throws SQLException {
        return loadEvents(connection, match.getId(), players(match));
    }

    private static List<Event> loadEvents(Connection connection, int matchId, Map<Integer, MatchPlayer> players) throws SQLException {
        List<Event> events = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(EVENT_QUERY)) {
            statement.setInt(1, matchId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    int types = 0;
                    for (String column : List.of("goal_id", "foul_id", "yellow_id", "red_id", "substitution_id")) {
                        if (rows.getObject(column) != null) {
                            types++;
                        }
                    }
                    if (types != 1) {
                        throw new SQLException("Sự kiện phải có đúng một loại chi tiết.");
                    }
                    Event event;
                    if (rows.getObject("goal_id") != null) {
                        Goal goal = new Goal();
                        goal.setType(rows.getString("goal_type"));
                        if (rows.getObject("assister_id") != null) {
                            goal.setAssister(mappedPlayer(players, rows.getInt("assister_id")));
                        }
                        event = goal;
                    } else if (rows.getObject("foul_id") != null) {
                        Foul foul = new Foul();
                        foul.setVictim(mappedPlayer(players, rows.getInt("victim_id")));
                        event = foul;
                    } else if (rows.getObject("yellow_id") != null) {
                        event = new YellowCard();
                    } else if (rows.getObject("red_id") != null) {
                        event = new RedCard();
                    } else {
                        Substitution substitution = new Substitution();
                        substitution.setPlayerIn(mappedPlayer(players, rows.getInt("player_in_id")));
                        event = substitution;
                    }
                    event.setId(rows.getInt("id"));
                    event.setMinute(rows.getInt("minute"));
                    event.setExtraMinute(rows.getInt("extra_minute"));
                    event.setNote(rows.getString("note"));
                    event.setPrimaryPlayer(mappedPlayer(players, rows.getInt("match_player_id")));
                    events.add(event);
                }
            }
        }
        return events;
    }

    static void validateTimeline(Connection connection, Match match) throws SQLException {
        validateTimeline(connection, match, loadEvents(connection, match));
    }

    private static void validateTimeline(Connection connection, Match match, List<Event> timeline) throws SQLException {
        int maximum;
        String sql = """
            SELECT
                ru.max_substitutions
            FROM matches m
            JOIN rounds r
                ON r.id = m.round_id
            JOIN seasons s
                ON s.id = r.season_id
            JOIN rules ru
                ON ru.id = s.rule_id
            WHERE m.id = ?
            FOR SHARE OF ru
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, match.getId());
            try (ResultSet rows = statement.executeQuery()) {
                require(rows.next(), "Không tìm thấy điều lệ mùa giải.");
                maximum = rows.getInt(1);
            }
        }
        require(maximum >= 0, "Giới hạn thay người không hợp lệ.");
        Map<Integer, Integer> sides = new HashMap<>();
        Map<Integer, MatchPlayer> registered = players(match);
        Map<Integer, Integer> substitutions = new HashMap<>();
        Set<Integer> onField = new HashSet<>(), used = new HashSet<>();
        for (MatchClub side : match.getMatchClubs()) {
            for (MatchPlayer player : side.getMatchPlayers()) {
                sides.put(player.getId(), side.getId());
                if ("STARTING".equals(player.getRole())) {
                    onField.add(player.getId());
                    used.add(player.getId());
                }
            }
        }
        for (Event event : timeline) {
            validateShape(event);
            int actor = event.getPrimaryPlayer().getId();
            require(onField.contains(actor), "Cầu thủ chính phải đang trên sân tại thời điểm sự kiện.");
            MatchPlayer related = relatedPlayer(event);
            if (related != null) {
                int secondary = related.getId();
                require(registered.containsKey(secondary) && secondary != actor, "Cầu thủ liên quan không hợp lệ.");
                boolean sameSide = Objects.equals(sides.get(actor), sides.get(secondary));
                if (event instanceof Substitution) {
                    require(sameSide && "SUBSTITUTE".equals(registered.get(secondary).getRole()) && !used.contains(secondary), "Người vào sân phải là dự bị cùng CLB và chưa từng vào sân.");
                    int count = substitutions.merge(sides.get(actor), 1, Integer::sum);
                    require(count <= maximum, "CLB đã vượt số lần thay người theo điều lệ.");
                    onField.remove(actor);
                    onField.add(secondary);
                    used.add(secondary);
                } else {
                    require(onField.contains(secondary), "Cầu thủ liên quan phải đang trên sân tại thời điểm sự kiện.");
                    require((event instanceof Foul) != sameSide, event instanceof Foul ? "Nạn nhân phải " +
                        "thuộc CLB đối phương." : "Người kiến tạo phải cùng CLB với cầu thủ ghi bàn.");
                }
            }
            if (event instanceof RedCard) {
                onField.remove(actor);
            }
        }
    }

    private static void validateShape(Event event) {
        require(event.getClass() == Goal.class || event.getClass() == Foul.class || event.getClass() == YellowCard.class || event.getClass() == RedCard.class || event.getClass() == Substitution.class, "Loại sự kiện không hợp lệ.");
        require(event.getMinute() >= 0 && event.getExtraMinute() >= 0, "Phút thi đấu và bù giờ phải không âm.");
        require(event.getNote() == null || event.getNote().codePointCount(0, event.getNote().length()) <= 255,
            "Ghi " + "chú không được quá 255 ký tự.");
        require(event.getPrimaryPlayer() != null && event.getPrimaryPlayer().getId() > 0, "Phải chọn cầu thủ chính.");
        if (event instanceof Goal goal) {
            require("NORMAL".equals(goal.getType()) || "PENALTY".equals(goal.getType()) || "OWN".equals(goal.getType()), "Loại bàn thắng không hợp lệ.");
            require(goal.getAssister() == null || "NORMAL".equals(goal.getType()), "Chỉ bàn thắng thường được có " +
                "người kiến tạo.");
        }
        if (event instanceof Foul foul) {
            require(foul.getVictim() != null, "Phải chọn cầu thủ bị phạm lỗi.");
        }
        if (event instanceof Substitution substitution) {
            require(substitution.getPlayerIn() != null, "Phải chọn cầu thủ vào sân.");
        }
    }

    private static MatchPlayer relatedPlayer(Event event) {
        if (event instanceof Goal goal) {
            return goal.getAssister();
        }
        if (event instanceof Foul foul) {
            return foul.getVictim();
        }
        if (event instanceof Substitution substitution) {
            return substitution.getPlayerIn();
        }
        return null;
    }

    private static void insertSubtype(Connection connection, int id, Event event) throws SQLException {
        String sql;
        if (event instanceof Goal) {
            sql = """
                INSERT INTO goals (
                    id,
                    goal_type,
                    assister_id
                )
                VALUES (
                    ?,
                    ?,
                    ?
                )
                """;
        } else if (event instanceof Foul) {
            sql = """
                INSERT INTO fouls (
                    id,
                    victim_id
                )
                VALUES (
                    ?,
                    ?
                )
                """;
        } else if (event instanceof Substitution) {
            sql = """
                INSERT INTO substitutions (
                    id,
                    player_in_id
                )
                VALUES (
                    ?,
                    ?
                )
                """;
        } else if (event instanceof YellowCard) {
            sql = """
                INSERT INTO yellow_cards (
                    id
                )
                VALUES (
                    ?
                )
                """;
        } else {
            sql = """
                INSERT INTO red_cards (
                    id
                )
                VALUES (
                    ?
                )
                """;
        }
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            if (event instanceof Goal goal) {
                statement.setString(2, goal.getType());
                if (goal.getAssister() == null) {
                    statement.setNull(3, Types.INTEGER);
                } else {
                    statement.setInt(3, goal.getAssister().getId());
                }
            } else if (relatedPlayer(event) != null) {
                statement.setInt(2, relatedPlayer(event).getId());
            }
            statement.executeUpdate();
        }
    }

    private static Map<Integer, MatchPlayer> players(Match match) {
        Map<Integer, MatchPlayer> result = new HashMap<>();
        for (MatchClub side : match.getMatchClubs()) {
            for (MatchPlayer player : side.getMatchPlayers()) {
                result.put(player.getId(), player);
            }
        }
        return result;
    }

    private static MatchPlayer mappedPlayer(Map<Integer, MatchPlayer> players, int id) throws SQLException {
        MatchPlayer player = players.get(id);
        if (player == null) {
            throw new SQLException("Sự kiện tham chiếu đăng ký cầu thủ không hợp lệ.");
        }
        return player;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
