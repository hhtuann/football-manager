package com.footballmanager.dao;

import com.footballmanager.model.ClubPlayer;
import com.footballmanager.model.Match;
import com.footballmanager.model.MatchClub;
import com.footballmanager.model.MatchPlayer;
import com.footballmanager.model.Round;
import com.footballmanager.model.Season;
import com.footballmanager.util.SessionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt-in real PostgreSQL tests. Every invocation owns and drops its own schema.
 */
class Cn2LineupIntegrationTest {
    private Cn2PostgresFixture db;
    private MatchDAO dao;

    @BeforeEach
    void initialize() throws Exception {
        db = new Cn2PostgresFixture();
        db.initialize();
        dao = new MatchDAO() {
            @Override
            protected Connection getConnection() throws SQLException {
                return db.connect();
            }
        };
    }

    @AfterEach
    void cleanup() throws Exception {
        if (db != null) {
            db.close();
        }
    }

    @Test
    void searchesStatusWithoutLeakingAnotherRound() throws Exception {
        int finished = db.createMatch(db.kickoff.plusDays(1));
        db.execute("UPDATE matches SET status='FINISHED' WHERE id=?", finished);
        int otherRound = db.insert("""
            INSERT INTO rounds (number, name, start_time, end_time, status, season_id)
            SELECT 8,
                   'Other round',
                   start_time,
                   end_time,
                   status,
                   season_id
            FROM rounds
            WHERE id = ?
            RETURNING id
            """, db.roundId);
        int outside = db.createMatch(db.kickoff.plusDays(2));
        db.execute("UPDATE matches SET round_id=? WHERE id=?", otherRound, outside);
        Round round = new Round();
        round.setId(db.roundId);
        assertEquals(List.of(db.matchId), dao.searchMatch(round, "scheduled").stream().map(Match::getId).toList());
        assertEquals(List.of(finished), dao.searchMatch(round, "FINISHED").stream().map(Match::getId).toList());
        assertTrue(dao.searchMatch(round, "ONGOING").isEmpty());
    }

    @Test
    void loadsNullKickoffAsLockedAndRefusesEvenWithClientUnlockedFlag() throws Exception {
        db.execute("UPDATE matches SET kickoff_time=NULL WHERE id=?", db.matchId);
        Match baseline = load();
        assertNull(baseline.getKickoffTime());
        assertTrue(baseline.isLineupLocked());
        assertEquals("Chưa xác định", SessionContext.dateTime(baseline.getKickoffTime()));
        baseline.setLineupLocked(false);
        assertTrue(baseline.isLineupLocked());
        MatchClub desired = candidate(baseline, "HOME", 11, 0);
        List<CandidateRow> before = snapshot(desired);
        assertRejected(baseline, desired);
        assertTrue(rows(db.homeSide).isEmpty());
        assertEquals(before, snapshot(desired));
    }

    @Test
    void derivesStableCodesByIdAcrossKeywordFiltersAndKickoffChanges() throws Exception {
        int second = db.createMatch(null);
        int third = db.createMatch(db.kickoff.minusDays(1));
        db.execute("UPDATE matches SET status='ONGOING' WHERE id=?", third);
        Round round = new Round();
        round.setId(db.roundId);
        List<Match> found = dao.searchMatch(round, "");
        assertEquals(List.of(db.matchId, second, third), found.stream().map(Match::getId).toList());
        assertEquals(List.of("R07-T01", "R07-T02", "R07-T03"), found.stream().map(Match::getDisplayCode).toList());
        assertNull(found.get(1).getKickoffTime());
        assertTrue(found.get(1).isLineupLocked());
        List<Match> byCode = dao.searchMatch(round, "r07-t02");
        assertEquals(List.of(second), byCode.stream().map(Match::getId).toList());
        assertEquals("R07-T02", byCode.get(0).getDisplayCode());
        List<Match> byStatus = dao.searchMatch(round, "ONGOING");
        assertEquals(List.of(third), byStatus.stream().map(Match::getId).toList());
        assertEquals("R07-T03", byStatus.get(0).getDisplayCode());
        db.execute("UPDATE matches SET kickoff_time=? WHERE id=?", db.kickoff.minusDays(2), second);
        assertEquals("R07-T02", dao.searchMatch(round, "r07-t02").get(0).getDisplayCode());
        assertTrue(dao.searchMatch(round, "R08-T01").isEmpty());
        assertEquals(List.of(db.matchId, second),
            dao.searchMatch(round, "SCHEDULED").stream().map(Match::getId).toList());
    }

    @Test
    void loadsAllRoundsInNumericOrderWithUnknownDatesAndNonchronologicalIds() throws Exception {
        db.execute("UPDATE rounds SET start_time=NULL,end_time=NULL WHERE id=?", db.roundId);
        for (int number = 26; number >= 1; number--) {
            if (number == 7) {
                continue;
            }
            db.insert("""
                INSERT INTO rounds (number, name, start_time, end_time, status, season_id)
                SELECT ?,
                       ?,
                       NULL,
                       NULL,
                       'SCHEDULED',
                       season_id
                FROM rounds
                WHERE id = ?
                RETURNING id
                """, number, "Vòng " + number, db.roundId);
        }
        Season season = new Season();
        season.setId(db.integer("SELECT season_id FROM rounds WHERE id=?", db.roundId));
        RoundDAO rounds = new RoundDAO() {
            @Override
            protected Connection getConnection() throws SQLException {
                return db.connect();
            }
        };
        List<Round> found = rounds.searchRound(season, "");
        assertEquals(26, found.size());
        for (int index = 0; index < found.size(); index++) {
            Round round = found.get(index);
            assertEquals(Integer.valueOf(index + 1), round.getRoundNumber());
            assertEquals(String.format("R%02d", index + 1), round.getDisplayCode());
            assertEquals("Vòng " + (index + 1), round.getDisplayName());
            assertNull(round.getStartTime());
            assertNull(round.getEndTime());
        }
        assertEquals("Chưa xác định", SessionContext.date(null));
        assertEquals(List.of(db.roundId), rounds.searchRound(season, "r07").stream().map(Round::getId).toList());
        assertEquals(List.of(Integer.valueOf(26)),
            rounds.searchRound(season, "Vòng 26").stream().map(Round::getRoundNumber).toList());
    }

    @Test
    void renamedRoundsRetainNumbersCodesAndNumericOrder() throws Exception {
        db.execute("UPDATE rounds SET name='Final' WHERE id=?", db.roundId);
        int tenth = db.insert("""
            INSERT INTO rounds (number, name, status, season_id)
            SELECT 10,
                   'Vòng 1',
                   'SCHEDULED',
                   season_id
            FROM rounds
            WHERE id = ?
            RETURNING id
            """, db.roundId);
        int second = db.insert("""
            INSERT INTO rounds (number, name, status, season_id)
            SELECT 2,
                   'Vòng 999999999999999999999999',
                   'SCHEDULED',
                   season_id
            FROM rounds
            WHERE id = ?
            RETURNING id
            """, db.roundId);
        Season season = new Season();
        season.setId(db.integer("SELECT season_id FROM rounds WHERE id=?", db.roundId));
        RoundDAO rounds = new RoundDAO() {
            @Override
            protected Connection getConnection() throws SQLException {
                return db.connect();
            }
        };
        List<Round> found = rounds.searchRound(season, "");
        assertEquals(List.of(second, db.roundId, tenth), found.stream().map(Round::getId).toList());
        assertEquals(List.of(2, 7, 10), found.stream().map(Round::getRoundNumber).toList());
        assertEquals(List.of("R02", "R07", "R10"), found.stream().map(Round::getDisplayCode).toList());
        assertEquals("Final", found.get(1).getDisplayName());
        assertEquals(List.of(db.roundId), rounds.searchRound(season, "Final").stream().map(Round::getId).toList());
        assertEquals(List.of(db.roundId), rounds.searchRound(season, "r07").stream().map(Round::getId).toList());
        assertTrue(rounds.searchRound(season, "R01").isEmpty());
        Round round = new Round();
        round.setId(db.roundId);
        assertEquals("R07-T01", dao.searchMatch(round, "r07-t01").get(0).getDisplayCode());
        db.execute("UPDATE rounds SET name='Vòng 99' WHERE id=?", db.roundId);
        assertEquals("R07-T01", dao.searchMatch(round, "r07-t01").get(0).getDisplayCode());
        assertTrue(dao.searchMatch(round, "R99-T01").isEmpty());
        Round model = found.get(1);
        model.setName("Renamed again");
        assertEquals(Integer.valueOf(7), model.getRoundNumber());
        assertEquals("R07", model.getDisplayCode());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonpositiveRoundNumbers(int number) {
        SQLException failure = assertThrows(SQLException.class,
            () -> db.execute("UPDATE rounds SET number=? WHERE id=?", number, db.roundId));
        assertEquals("23514", failure.getSQLState());
    }

    @Test
    void requiresRoundNumbersAndRejectsDuplicatesWithinASeason() throws Exception {
        SQLException missing = assertThrows(SQLException.class,
            () -> db.execute("UPDATE rounds SET number=NULL WHERE id=?", db.roundId));
        assertEquals("23502", missing.getSQLState());
        String duplicateSql = """
            INSERT INTO rounds (number, name, status, season_id)
            SELECT number,
                   'Another display name',
                   status,
                   season_id
            FROM rounds
            WHERE id = ?
            RETURNING id
            """;
        SQLException duplicate = assertThrows(SQLException.class, () -> db.insert(duplicateSql, db.roundId));
        assertEquals("23505", duplicate.getSQLState());
        int otherSeason = db.insert("""
            INSERT INTO seasons (year, name, start_date, end_date, status, tournament_id, rule_id)
            SELECT s.year,
                   'Another season',
                   s.start_date,
                   s.end_date,
                   s.status,
                   s.tournament_id,
                   s.rule_id
            FROM seasons s
            JOIN rounds r ON r.season_id = s.id
            WHERE r.id = ?
            RETURNING id
            """, db.roundId);
        int otherRound = db.insert("""
            INSERT INTO rounds (number, name, status, season_id)
            VALUES (7, 'Another season round', 'SCHEDULED', ?)
            RETURNING id
            """, otherSeason);
        assertEquals(7, db.integer("SELECT number FROM rounds WHERE id=?", otherRound));
    }

    @Test
    void savesBothSidesIndependentlyAndRetainsRegistrationIdentityAndTime() throws Exception {
        Match homeBaseline = load();
        MatchClub home = candidate(homeBaseline, "HOME", 11, 2);
        assertTrue(dao.saveLineup(homeBaseline, home));
        List<Row> firstHome = rows(db.homeSide);
        assertEquals(13, firstHome.size());
        assertTrue(rows(db.awaySide).isEmpty());

        Match awayBaseline = load();
        MatchClub away = candidate(awayBaseline, "AWAY", 11, 1);
        assertTrue(dao.saveLineup(awayBaseline, away));
        List<Row> firstAway = rows(db.awaySide);
        assertEquals(12, firstAway.size());
        assertEquals(firstHome, rows(db.homeSide));

        Match editBaseline = load();
        MatchClub edit = candidate(editBaseline, "HOME", 11, 1);
        // Exchange starter and bench roles without replacing their existing registrations.
        edit.getMatchPlayers().get(10).setRole("SUBSTITUTE");
        edit.getMatchPlayers().get(11).setRole("STARTING");
        assertTrue(dao.saveLineup(editBaseline, edit));
        List<Row> edited = rows(db.homeSide);
        assertEquals(12, edited.size());
        for (Row retained : edited) {
            Row original = firstHome.stream().filter(r -> r.member == retained.member).findFirst().orElseThrow();
            assertEquals(original.id, retained.id);
            assertEquals(original.time, retained.time);
        }
        assertEquals(firstAway, rows(db.awaySide));
        Match reloaded = load();
        assertEquals(12, side(reloaded, "HOME").getMatchPlayers().size());
        assertEquals(12, side(reloaded, "AWAY").getMatchPlayers().size());
    }

    @ParameterizedTest(name = "Rejects {0}")
    @ValueSource(strings = {"ten starters", "no goalkeeper", "bench goalkeeper only", "too much bench", "foreign " +
        "bench", "over age", "wrong membership", "duplicate", "invalid role"})
    void rejectsInvalidLineupsWithoutChangingDatabaseOrCandidate(String invalid) throws Exception {
        seedHome();
        if (invalid.equals("no goalkeeper")) {
            db.execute("UPDATE players SET primary_position='Tiền đạo' WHERE user_id=?", db.players[0][0]);
        } else if (invalid.equals("bench goalkeeper only")) {
            db.execute("UPDATE players SET primary_position='Tiền đạo' WHERE user_id=?", db.players[0][0]);
            db.execute("UPDATE players SET primary_position='Thủ môn' WHERE user_id=?", db.players[0][12]);
        } else if (invalid.equals("foreign bench")) {
            for (int p : new int[]{10, 11, 12}) {
                db.execute("UPDATE players SET nationality='Brazil' WHERE user_id=?", db.players[0][p]);
            }
        } else if (invalid.equals("over age")) {
            db.execute("UPDATE users SET dob=? WHERE id=?", db.seasonStart.minusYears(31), db.players[0][12]);
        }
        Match baseline = load();
        MatchClub desired = candidate(baseline, "HOME", invalid.equals("ten starters") ? 10 : 11, invalid.equals("too"
            + " much bench") ? 4 : 2);
        if (invalid.equals("wrong membership")) {
            desired.getMatchPlayers().get(12).getClubPlayer().setId(db.members[1][12]);
        }
        if (invalid.equals("duplicate")) {
            desired.getMatchPlayers().get(12).getClubPlayer().setId(db.members[0][1]);
        }
        if (invalid.equals("invalid role")) {
            desired.getMatchPlayers().get(12).setRole("CAPTAIN");
        }
        List<Row> before = rows(db.homeSide);
        List<CandidateRow> untouched = snapshot(desired);
        List<MatchPlayer> cachedBaseline = side(baseline, "HOME").getMatchPlayers();
        List<CandidateRow> cacheBefore = snapshot(side(baseline, "HOME"));
        assertRejected(baseline, desired);
        assertEquals(before, rows(db.homeSide));
        assertEquals(untouched, snapshot(desired));
        assertSame(cachedBaseline, side(baseline, "HOME").getMatchPlayers());
        assertEquals(cacheBefore, snapshot(side(baseline, "HOME")));
    }

    @Test
    void rejectsStaleBaselineAfterAnotherSave() throws Exception {
        seedHome();
        Match stale = load();
        MatchClub staleDesired = candidate(stale, "HOME", 11, 2);
        Match fresh = load();
        assertTrue(dao.saveLineup(fresh, candidate(fresh, "HOME", 11, 1)));
        List<Row> committed = rows(db.homeSide);
        List<CandidateRow> untouched = snapshot(staleDesired);
        assertRejected(stale, staleDesired);
        assertEquals(committed, rows(db.homeSide));
        assertEquals(untouched, snapshot(staleDesired));
    }

    @Test
    void simultaneousSavesFromSameBaselineCommitExactlyOneCandidate() throws Exception {
        seedHome();
        Match first = load();
        Match second = load();
        MatchClub firstDesired = candidate(first, "HOME", 11, 0);
        MatchClub secondDesired = candidate(second, "HOME", 11, 1);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> a = workers.submit(() -> competingSave(first, firstDesired, ready, start));
            Future<Boolean> b = workers.submit(() -> competingSave(second, secondDesired, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            boolean savedA = a.get(20, TimeUnit.SECONDS);
            boolean savedB = b.get(20, TimeUnit.SECONDS);
            assertNotEquals(savedA, savedB, "Exactly one snapshot may win the row lock and optimistic check");
            assertEquals(savedA ? 11 : 12, rows(db.homeSide).size());
        } finally {
            start.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(20, TimeUnit.SECONDS));
        }
    }

    private boolean competingSave(Match baseline, MatchClub desired, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Fixture barrier timed out");
        }
        try {
            return dao.saveLineup(baseline, desired);
        } catch (IllegalArgumentException stale) {
            return false;
        } catch (SQLException conflict) {
            if (!"40001".equals(conflict.getSQLState())) {
                throw conflict;
            }
            return false;
        }
    }

    @Test
    void configuredStarterCountCannotOverrideTheElevenPlayerRule() throws Exception {
        db.execute("UPDATE rules SET required_main_players=10 WHERE id=?", db.ruleId);
        Match baseline = load();
        assertRejected(baseline, candidate(baseline, "HOME", 10, 0));
        assertTrue(rows(db.homeSide).isEmpty());
    }

    @ParameterizedTest(name = "Locks lineup for {0}")
    @ValueSource(strings = {"ONGOING", "FINISHED", "kickoff", "unknown kickoff", "event"})
    void locksLineupUsingFreshDatabaseState(String reason) throws Exception {
        seedHome();
        Match baseline = load();
        MatchClub desired = candidate(baseline, "HOME", 11, 2);
        switch (reason) {
            case "ONGOING", "FINISHED" -> db.execute("UPDATE matches SET status=? WHERE id=?", reason, db.matchId);
            case "kickoff" ->
                db.execute("UPDATE matches SET kickoff_time=LOCALTIMESTAMP - INTERVAL '1 second' WHERE id=?",
                    db.matchId);
            case "unknown kickoff" -> db.execute("UPDATE matches SET kickoff_time=NULL WHERE id=?", db.matchId);
            case "event" -> db.insert("INSERT INTO events(minute,match_id,match_player_id) VALUES (1,?,?) RETURNING id",
                db.matchId, rows(db.homeSide).get(0).id);
            default -> fail("Unexpected fixture");
        }
        List<Row> before = rows(db.homeSide);
        List<CandidateRow> untouched = snapshot(desired);
        assertRejected(baseline, desired);
        assertEquals(before, rows(db.homeSide));
        assertEquals(untouched, snapshot(desired));
    }

    @Test
    void transactionFailureRollsBackDeletesAndRoleEditsWithoutMutatingCandidateOrCache() throws Exception {
        seedHome();
        // Force a real SQL failure after the DAO has begun applying a legitimate diff.
        db.execute("CREATE SEQUENCE fixture_failure_reached");
        db.execute("CREATE FUNCTION reject_fixture_insert() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN PERFORM " +
            "nextval('fixture_failure_reached'); RAISE EXCEPTION 'fixture insert rejection'; END; $$");
        db.execute("CREATE TRIGGER reject_fixture_insert BEFORE INSERT ON match_players FOR EACH ROW EXECUTE " +
            "FUNCTION" + " reject_fixture_insert()");
        Match baseline = load();
        MatchClub desired = candidate(baseline, "HOME", 11, 1);
        desired.getMatchPlayers().get(10).setRole("SUBSTITUTE");
        desired.getMatchPlayers().get(11).setRole("STARTING");
        desired.getMatchPlayers().get(11).getClubPlayer().setId(db.members[0][14]);
        desired.getMatchPlayers().get(11).setId(0);
        desired.getMatchPlayers().get(11).setRegisteredAt(null);
        List<Row> before = rows(db.homeSide);
        List<CandidateRow> untouched = snapshot(desired);
        List<MatchPlayer> cached = side(baseline, "HOME").getMatchPlayers();
        List<CandidateRow> cacheBefore = snapshot(side(baseline, "HOME"));
        assertThrows(SQLException.class, () -> dao.saveLineup(baseline, desired));
        assertEquals(1, db.integer("SELECT CASE WHEN is_called THEN 1 ELSE 0 END FROM fixture_failure_reached"));
        assertEquals(before, rows(db.homeSide));
        assertEquals(untouched, snapshot(desired));
        assertSame(cached, side(baseline, "HOME").getMatchPlayers());
        assertEquals(cacheBefore, snapshot(side(baseline, "HOME")));
    }

    @Test
    void acceptsExactAgeForeignAndBenchLimitsUsingApprovedVocabulary() throws Exception {
        db.execute("UPDATE users SET dob=? WHERE id=?", db.seasonStart.minusYears(30), db.players[0][13]);
        db.execute("UPDATE players SET nationality='Brazil' WHERE user_id IN (?,?)", db.players[0][12],
            db.players[0][13]);
        Match baseline = load();
        assertTrue(dao.saveLineup(baseline, candidate(baseline, "HOME", 11, 3)));
        assertEquals(14, rows(db.homeSide).size());
    }

    @Test
    void maximumAgeIsMeasuredAtSeasonStartNotAtKickoff() throws Exception {
        db.execute("UPDATE users SET dob=? WHERE id=?", db.seasonStart.minusYears(31).plusDays(1), db.players[0][12]);
        Match baseline = load();
        assertTrue(dao.saveLineup(baseline, candidate(baseline, "HOME", 11, 2)));
    }

    @ParameterizedTest(name = "Suspension from {0}")
    @ValueSource(strings = {"red", "yellow"})
    void suspensionIsTriggeredAndServedByNextFinishedClubMatch(String card) throws Exception {
        int history = db.createMatch(LocalDateTime.now().minusDays(10));
        int actor =
            db.insert("INSERT INTO match_players(role,match_club_id,club_player_id) VALUES ('STARTING',?,?) " +
                "RETURNING id", db.side(history, "HOME"), db.members[0][1]);
        int count = card.equals("yellow") ? 2 : 1;
        for (int i = 0; i < count; i++) {
            int event = db.insert("INSERT INTO events(minute,match_id,match_player_id) VALUES (?,?,?) RETURNING id",
                10 + i, history, actor);
            db.execute("INSERT INTO " + (card.equals("yellow") ? "yellow_cards" : "red_cards") + "(id) VALUES (?)",
                event);
        }
        db.execute("UPDATE matches SET status='FINISHED' WHERE id=?", history);
        Match blocked = load();
        assertRejected(blocked, candidate(blocked, "HOME", 11, 0));
        assertTrue(rows(db.homeSide).isEmpty());
        int served = db.createMatch(LocalDateTime.now().minusDays(5));
        // Merely scheduled/ongoing fixtures do not consume a suspension.
        Match stillBlocked = load();
        assertRejected(stillBlocked, candidate(stillBlocked, "HOME", 11, 0));
        db.execute("UPDATE matches SET status='ONGOING' WHERE id=?", served);
        stillBlocked = load();
        assertRejected(stillBlocked, candidate(stillBlocked, "HOME", 11, 0));
        db.execute("UPDATE matches SET status='FINISHED' WHERE id=?", served);
        Match eligible = load();
        assertTrue(dao.saveLineup(eligible, candidate(eligible, "HOME", 11, 0)));
    }

    @Test
    void redAndYellowPenaltiesAccumulateAndRequireTwoFinishedClubMatches() throws Exception {
        int history = db.createMatch(LocalDateTime.now().minusDays(12));
        int actor =
            db.insert("INSERT INTO match_players(role,match_club_id,club_player_id) VALUES ('STARTING',?,?) " +
                "RETURNING id", db.side(history, "HOME"), db.members[0][1]);
        for (String type : new String[]{"yellow_cards", "yellow_cards", "red_cards"}) {
            int event = db.insert("INSERT INTO events(minute,match_id,match_player_id) VALUES (10,?,?) RETURNING id",
                history, actor);
            db.execute("INSERT INTO " + type + "(id) VALUES (?)", event);
        }
        db.execute("UPDATE matches SET status='FINISHED' WHERE id=?", history);
        int firstServed = db.createMatch(LocalDateTime.now().minusDays(8));
        db.execute("UPDATE matches SET status='FINISHED' WHERE id=?", firstServed);
        Match baseline = load();
        assertRejected(baseline, candidate(baseline, "HOME", 11, 0));
        int secondServed = db.createMatch(LocalDateTime.now().minusDays(4));
        db.execute("UPDATE matches SET status='FINISHED' WHERE id=?", secondServed);
        baseline = load();
        assertTrue(dao.saveLineup(baseline, candidate(baseline, "HOME", 11, 0)));
    }

    private void assertRejected(Match baseline, MatchClub desired) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> dao.saveLineup(baseline,
            desired));
        assertNotNull(error.getMessage());
        assertFalse(error.getMessage().isBlank());
    }

    private void seedHome() throws Exception {
        Match baseline = load();
        assertTrue(dao.saveLineup(baseline, candidate(baseline, "HOME", 11, 2)));
    }

    private Match load() throws SQLException {
        Round round = new Round();
        round.setId(db.roundId);
        return dao.searchMatch(round, "").stream().filter(m -> m.getId() == db.matchId).findFirst().orElseThrow();
    }

    private static MatchClub side(Match match, String role) {
        return match.getMatchClubs().stream().filter(s -> role.equals(s.getRole())).findFirst().orElseThrow();
    }

    private MatchClub candidate(Match baseline, String role, int starters, int bench) {
        MatchClub original = side(baseline, role);
        MatchClub result = new MatchClub();
        result.setId(original.getId());
        result.setRole(original.getRole());
        result.setClub(original.getClub());
        List<MatchPlayer> entries = new ArrayList<>();
        int team = role.equals("HOME") ? 0 : 1;
        for (int i = 0; i < starters + bench; i++) {
            ClubPlayer member = new ClubPlayer();
            member.setId(db.members[team][i]);
            MatchPlayer entry = new MatchPlayer();
            entry.setClubPlayer(member);
            entry.setRole(i < starters ? "STARTING" : "SUBSTITUTE");
            MatchPlayer retained =
                original.getMatchPlayers().stream().filter(p -> p.getClubPlayer().getId() == member.getId()).findFirst().orElse(null);
            if (retained != null) {
                entry.setId(retained.getId());
                entry.setRegisteredAt(retained.getRegisteredAt());
            }
            entries.add(entry);
        }
        result.setMatchPlayers(entries);
        return result;
    }

    private List<Row> rows(int side) throws SQLException {
        List<Row> result = new ArrayList<>();
        try (Connection c = db.connect(); PreparedStatement s =
            c.prepareStatement("SELECT id,club_player_id,role," + "registered_at FROM match_players WHERE " +
                "match_club_id=? ORDER BY club_player_id")) {
            s.setInt(1, side);
            try (ResultSet rs = s.executeQuery()) {
                while (rs.next()) {
                    result.add(new Row(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getObject(4,
                        LocalDateTime.class)));
                }
            }
        }
        return result;
    }

    private static List<CandidateRow> snapshot(MatchClub candidate) {
        return candidate.getMatchPlayers().stream().map(p -> new CandidateRow(p.getId(), p.getClubPlayer().getId(),
            p.getRole(), p.getRegisteredAt())).toList();
    }

    private record Row(int id, int member, String role, LocalDateTime time) {
    }

    private record CandidateRow(int id, int member, String role, LocalDateTime time) {
    }
}
