package com.footballmanager.dao;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A private, disposable schema; never falls back to the application's database settings.
 */
final class Cn2PostgresFixture implements AutoCloseable {
    private final String url;
    private final Properties properties = new Properties();
    private final String schema = "cn2_test_" + UUID.randomUUID().toString().replace("-", "");
    private boolean created;
    int matchId;
    int homeSide;
    int awaySide;
    int homeClub;
    int awayClub;
    int roundId;
    int ruleId;
    final int[][] members = new int[2][16];
    final int[][] players = new int[2][16];
    final LocalDate seasonStart = LocalDate.now().withDayOfYear(1);
    final LocalDateTime kickoff = LocalDateTime.now().plusDays(30).withNano(0);

    Cn2PostgresFixture() {
        url = System.getenv("CN2_TEST_JDBC_URL");
        String user = System.getenv("CN2_TEST_DB_USER");
        String password = System.getenv("CN2_TEST_DB_PASSWORD");
        assumeTrue(url != null && !url.isBlank() && user != null && !user.isBlank() && password != null, "Set " +
            "CN2_TEST_JDBC_URL, CN2_TEST_DB_USER and CN2_TEST_DB_PASSWORD for PostgreSQL integration tests");
        properties.setProperty("user", user);
        properties.setProperty("password", password);
        // No public search path: unqualified reads/writes cannot reach existing application tables.
        properties.setProperty("currentSchema", schema);
        properties.setProperty("options", "-c search_path=" + schema);
    }

    void initialize() throws Exception {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.execute("CREATE SCHEMA " + schema);
            created = true;
            s.execute(Files.readString(Path.of("database/schema.sql")));
        }
        int tournament = insert("INSERT INTO tournaments(name) VALUES (?) RETURNING id", "CN2 fixture");
        ruleId = insert("INSERT INTO rules(required_main_players,max_bench_players,max_substitutions," +
            "yellow_cards_to_suspend,age_limit,max_foreign_players) VALUES (11,3,5,2,30,2) RETURNING id");
        int season =
            insert("INSERT INTO seasons(year,name,start_date,end_date,status,tournament_id,rule_id) VALUES " + "(?,?,"
                    + "?,?,?,?,?) RETURNING id", seasonStart.getYear(), "Fixture season", seasonStart,
                kickoff.toLocalDate().plusYears(1), "ACTIVE", tournament, ruleId);
        roundId = insert("""
            INSERT INTO rounds (number, name, start_time, end_time, status, season_id)
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING id
            """, 7, "Vòng 7", seasonStart.atStartOfDay(), kickoff.plusYears(1), "SCHEDULED", season);
        int stadium = insert("INSERT INTO stadiums(name,capacity,address) VALUES ('Fixture ground',1000,'Private " +
            "schema') RETURNING id");
        int[] clubs = new int[2];
        for (int side = 0; side < 2; side++) {
            int manager = user("manager_" + side, seasonStart.minusYears(40));
            execute("INSERT INTO managers(user_id) VALUES (?)", manager);
            clubs[side] = insert("INSERT INTO clubs(full_name,short_name,coach_name,stadium_id,manager_id) VALUES (?,"
                + "?,?,?,?) RETURNING id", "Fixture club " + side, "T" + side, "Coach", stadium, manager);
            for (int p = 0; p < 16; p++) {
                players[side][p] = user("player_" + side + "_" + p, seasonStart.minusYears(22));
                execute("INSERT INTO players(user_id,nationality,primary_position,height_cm,weight_kg) VALUES (?,?,?,"
                    + "?,?)", players[side][p], "Việt Nam", p == 0 ? "Thủ môn" : "Tiền đạo", 180, 75);
                members[side][p] =
                    insert("INSERT INTO club_players(shirt_number,club_id,player_id) VALUES (?,?,?) " + "RETURNING " + "id", p + 1, clubs[side], players[side][p]);
            }
        }
        homeClub = clubs[0];
        awayClub = clubs[1];
        matchId = createMatch(kickoff);
        homeSide = side(matchId, "HOME");
        awaySide = side(matchId, "AWAY");
    }

    Connection connect() throws SQLException {
        return DriverManager.getConnection(url, properties);
    }

    int createMatch(LocalDateTime time) throws SQLException {
        int id = insert("INSERT INTO matches(kickoff_time,status,round_id) VALUES (?,'SCHEDULED',?) RETURNING id",
            time, roundId);
        insert("INSERT INTO match_clubs(role,match_id,club_id) VALUES ('HOME',?,?) RETURNING id", id, homeClub);
        insert("INSERT INTO match_clubs(role,match_id,club_id) VALUES ('AWAY',?,?) RETURNING id", id, awayClub);
        return id;
    }

    int side(int match, String role) throws SQLException {
        return integer("SELECT id FROM match_clubs WHERE match_id=? AND role=?", match, role);
    }

    private int user(String name, LocalDate dob) throws SQLException {
        return insert("INSERT INTO users(username,password_hash,full_name,dob) VALUES (?,'test-only',?,?) RETURNING " + "id", name, name, dob);
    }

    int insert(String sql, Object... values) throws SQLException {
        return integer(sql, values);
    }

    int integer(String sql, Object... values) throws SQLException {
        try (Connection c = connect(); PreparedStatement s = c.prepareStatement(sql)) {
            bind(s, values);
            try (ResultSet rs = s.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Missing isolated fixture row");
                }
                return rs.getInt(1);
            }
        }
    }

    void execute(String sql, Object... values) throws SQLException {
        try (Connection c = connect(); PreparedStatement s = c.prepareStatement(sql)) {
            bind(s, values);
            s.executeUpdate();
        }
    }

    private static void bind(PreparedStatement s, Object[] values) throws SQLException {
        for (int i = 0; i < values.length; i++) {
            s.setObject(i + 1, values[i]);
        }
    }

    @Override
    public void close() throws SQLException {
        if (created) {
            try (Connection c = connect(); Statement s = c.createStatement()) {
                s.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }
}
