package com.footballmanager.dao;

import com.footballmanager.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real PostgreSQL, disposable schema; no application data is modified.
 */
class Cn3EventIntegrationTest {
    private Cn2PostgresFixture db;
    private MatchDAO matches;
    private EventDAO events;
    private Match match;

    @BeforeEach
    void initialize() throws Exception {
        db = new Cn2PostgresFixture();
        db.initialize();
        matches = new MatchDAO() {
            @Override
            protected Connection getConnection() throws SQLException {
                return db.connect();
            }
        };
        events = new EventDAO() {
            @Override
            protected Connection getConnection() throws SQLException {
                return db.connect();
            }
        };
        for (int team = 0; team < 2; team++) {
            for (int i = 0; i < 14; i++) {
                db.execute("INSERT INTO match_players(match_club_id,club_player_id,role) VALUES (?,?,?)", team == 0 ?
                    db.homeSide : db.awaySide, db.members[team][i], i < 11 ? "STARTING" : "SUBSTITUTE");
            }
        }
        match = load();
    }

    @AfterEach
    void cleanup() throws Exception {
        if (db != null) {
            db.close();
        }
    }

    @Test
    void persistsSubtypesAndDerivesOwnGoalForOppositionAcrossReload() throws Exception {
        Goal normal = goal("NORMAL", player(0, 1), 10);
        normal.setAssister(player(0, 2));
        normal.setNote("Bàn thắng <đẹp> & chính xác");
        assertTrue(events.addEvent(match, normal));
        Goal penalty = goal("PENALTY", player(1, 1), 20);
        events.addEvent(match, penalty);
        Goal own = goal("OWN", player(1, 2), 30);
        events.addEvent(match, own);
        Goal unassisted = goal("NORMAL", player(0, 3), 35);
        events.addEvent(match, unassisted);
        Foul foul = at(new Foul(), player(0, 3), 40);
        foul.setVictim(player(1, 3));
        events.addEvent(match, foul);
        events.addEvent(match, at(new YellowCard(), player(0, 4), 41));
        events.addEvent(match, at(new RedCard(), player(1, 4), 42));
        Substitution sub = at(new Substitution(), player(0, 5), 50);
        sub.setPlayerIn(player(0, 11));
        events.addEvent(match, sub);
        List<Event> saved = events.getEventList(db.matchId);
        assertEquals(List.of(Goal.class, Goal.class, Goal.class, Goal.class, Foul.class, YellowCard.class,
            RedCard.class, Substitution.class), saved.stream().map(Object::getClass).toList());
        Goal read = (Goal) saved.get(0);
        assertEquals(normal.getId(), read.getId());
        assertTrue(read.getId() > 0);
        assertEquals(player(0, 2).getId(), read.getAssister().getId());
        assertEquals("player_0_1", read.getPrimaryPlayer().getClubPlayer().getPlayer().getFullName());
        assertEquals(normal.getNote(), read.getNote());
        assertNull(((Goal) saved.get(3)).getAssister());
        assertEquals(player(1, 3).getId(), ((Foul) saved.get(4)).getVictim().getId());
        assertEquals(player(0, 11).getId(), ((Substitution) saved.get(7)).getPlayerIn().getId());
        Match reloaded = load();
        assertEquals(3, side(reloaded, "HOME").getScore());
        assertEquals(1, side(reloaded, "AWAY").getScore());
        assertEquals(3, side(match, "HOME").getScore());
        assertEquals("ONGOING", reloaded.getStatus());
        assertTrue(matches.saveMatch(match));
        assertEquals("FINISHED", load().getStatus());
        assertThrows(IllegalArgumentException.class, () -> events.addEvent(match, goal("NORMAL", player(0, 1), 60)));
        assertEquals(8, events.getEventList(db.matchId).size());
    }

    @Test
    void refusesWrongRelationsWithoutPartialRowsOrCacheMutation() throws Exception {
        Goal badAssist = goal("NORMAL", player(0, 1), 10);
        badAssist.setAssister(player(1, 1));
        reject(badAssist);
        Goal self = goal("NORMAL", player(0, 1), 10);
        self.setAssister(player(0, 1));
        reject(self);
        Goal penalty = goal("PENALTY", player(0, 1), 10);
        penalty.setAssister(player(0, 2));
        reject(penalty);
        Foul foul = at(new Foul(), player(0, 1), 10);
        foul.setVictim(player(0, 2));
        reject(foul);
        Substitution opponent = at(new Substitution(), player(0, 1), 10);
        opponent.setPlayerIn(player(1, 11));
        reject(opponent);
        assertEquals("SCHEDULED", match.getStatus());
        assertEquals(0, side(match, "HOME").getScore());
        assertEquals(0, db.integer("SELECT count(*) FROM events"));
    }

    @Test
    void validatesTimeNoteAndGoalTypeWithoutInventingNinetyMinuteCap() throws Exception {
        reject(goal("INVALID", player(0, 1), 10));
        reject(goal("NORMAL", player(0, 1), -1));
        Goal extra = goal("NORMAL", player(0, 1), 45);
        extra.setExtraMinute(-1);
        reject(extra);
        Goal longNote = goal("NORMAL", player(0, 1), 10);
        longNote.setNote("x".repeat(256));
        reject(longNote);
        Goal extended = goal("NORMAL", player(0, 1), 120);
        extended.setExtraMinute(2);
        extended.setNote("x".repeat(255));
        events.addEvent(match, extended);
        Goal read = (Goal) events.getEventList(db.matchId).get(0);
        assertEquals(120, read.getMinute());
        assertEquals(2, read.getExtraMinute());
        assertEquals(extended.getNote(), read.getNote());
    }

    @Test
    void timelinePreventsBenchGoalsReentryAndBackdatedInvalidation() throws Exception {
        reject(goal("NORMAL", player(0, 11), 10));
        Substitution sub = at(new Substitution(), player(0, 1), 30);
        sub.setPlayerIn(player(0, 11));
        events.addEvent(match, sub);
        events.addEvent(match, goal("NORMAL", player(0, 11), 31));
        int before = db.integer("SELECT count(*) FROM events");
        rejectWithoutChangingCount(goal("NORMAL", player(0, 1), 32), before);
        rejectWithoutChangingCount(at(new RedCard(), player(0, 11), 29), before);
        Substitution repeated = at(new Substitution(), player(0, 2), 40);
        repeated.setPlayerIn(player(0, 11));
        rejectWithoutChangingCount(repeated, before);
        events.addEvent(match, at(new RedCard(), player(1, 1), 35));
        rejectWithoutChangingCount(goal("NORMAL", player(1, 1), 36), before + 1);
        // A valid earlier event remains legal; entry order is not forced to chronology.
        events.addEvent(match, goal("NORMAL", player(0, 1), 20));
        assertEquals(List.of(20, 30, 31, 35), events.getEventList(db.matchId).stream().map(Event::getMinute).toList());
    }

    @Test
    void backdatedSubstitutionCannotInvalidateLaterScorer() throws Exception {
        events.addEvent(match, goal("NORMAL", player(0, 1), 60));
        Substitution sub = at(new Substitution(), player(0, 1), 30);
        sub.setPlayerIn(player(0, 11));
        rejectWithoutChangingCount(sub, 1);
        assertEquals(1,
            load().getMatchClubs().stream().filter(s -> "HOME".equals(s.getRole())).findFirst().orElseThrow().getScore());
    }

    @Test
    void enforcesSubstitutionLimitUnderCurrentRule() throws Exception {
        db.execute("UPDATE rules SET max_substitutions=1 WHERE id=?", db.ruleId);
        Substitution first = at(new Substitution(), player(0, 1), 20);
        first.setPlayerIn(player(0, 11));
        events.addEvent(match, first);
        Substitution second = at(new Substitution(), player(0, 2), 30);
        second.setPlayerIn(player(0, 12));
        rejectWithoutChangingCount(second, 1);
        // The other club has an independent quota.
        Substitution away = at(new Substitution(), player(1, 1), 30);
        away.setPlayerIn(player(1, 11));
        events.addEvent(match, away);
        assertEquals(2, db.integer("SELECT count(*) FROM substitutions"));
    }

    @Test
    void finishesZeroZeroAndBlocksStaleObjectWritingAfterFinish() throws Exception {
        Match stale = load();
        assertTrue(matches.saveMatch(match));
        assertEquals("FINISHED", match.getStatus());
        assertTrue(match.isLineupLocked());
        assertEquals(0, side(load(), "HOME").getScore());
        assertEquals(0, side(load(), "AWAY").getScore());
        assertThrows(IllegalArgumentException.class, () -> events.addEvent(stale, goal("NORMAL", player(0, 1), 1)));
        assertThrows(IllegalArgumentException.class, () -> matches.saveMatch(stale));
        assertEquals(0, db.integer("SELECT count(*) FROM events"));
        List<MatchPlayer> cachedHome = side(stale, "HOME").getMatchPlayers();
        events.refreshMatch(stale);
        assertEquals("FINISHED", stale.getStatus());
        assertTrue(stale.isLineupLocked());
        assertSame(cachedHome, side(stale, "HOME").getMatchPlayers());
    }

    @Test
    void refusesIncompleteLineupAndForeignMatchRegistration() throws Exception {
        int another = db.createMatch(db.kickoff.plusDays(1));
        int outsider = db.insert("INSERT INTO match_players(match_club_id,club_player_id,role) VALUES (?,?," +
            "'STARTING') RETURNING id", db.side(another, "HOME"), db.members[0][1]);
        MatchPlayer fake = new MatchPlayer();
        fake.setId(outsider);
        reject(goal("NORMAL", fake, 1));
        db.execute("DELETE FROM match_players WHERE match_club_id=? AND club_player_id=?", db.awaySide,
            db.members[1][10]);
        assertThrows(IllegalArgumentException.class, () -> matches.saveMatch(match));
        assertEquals("SCHEDULED", load().getStatus());
        assertEquals(0, db.integer("SELECT count(*) FROM events"));
    }

    @Test
    void rejectsOffFieldRelatedPlayersAndDismissedSubstitutionActor() throws Exception {
        Goal benchAssist = goal("NORMAL", player(0, 1), 10);
        benchAssist.setAssister(player(0, 11));
        reject(benchAssist);
        Foul benchVictim = at(new Foul(), player(0, 1), 10);
        benchVictim.setVictim(player(1, 11));
        reject(benchVictim);
        events.addEvent(match, at(new RedCard(), player(0, 2), 15));
        Substitution dismissed = at(new Substitution(), player(0, 2), 20);
        dismissed.setPlayerIn(player(0, 11));
        rejectWithoutChangingCount(dismissed, 1);
        Goal dismissedAssist = goal("NORMAL", player(0, 1), 20);
        dismissedAssist.setAssister(player(0, 2));
        rejectWithoutChangingCount(dismissedAssist, 1);
    }

    @Test
    void rejectsEventWhenCurrentDatabaseLineupIsIncomplete() throws Exception {
        db.execute("DELETE FROM match_players WHERE match_club_id=? AND club_player_id=?", db.awaySide,
            db.members[1][10]);
        reject(goal("NORMAL", player(0, 1), 10));
        assertEquals("SCHEDULED", load().getStatus());
    }

    @Test
    void addAndFinishSerializeWithoutEventAfterFinalization() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Match eventMatch = load();
        Match finalMatch = load();
        try {
            Future<Boolean> added = pool.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    return events.addEvent(eventMatch, goal("NORMAL", player(0, 1), 10));
                } catch (IllegalArgumentException ex) {
                    return false;
                }
            });
            Future<Boolean> ended = pool.submit(() -> {
                ready.countDown();
                start.await();
                return matches.saveMatch(finalMatch);
            });
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            boolean didAdd = added.get(20, TimeUnit.SECONDS);
            assertTrue(ended.get(20, TimeUnit.SECONDS));
            assertEquals("FINISHED", load().getStatus());
            assertEquals(didAdd ? 1 : 0, db.integer("SELECT count(*) FROM events WHERE match_id=?", db.matchId));
            assertThrows(IllegalArgumentException.class, () -> events.addEvent(eventMatch, goal("NORMAL", player(0,
                2), 11)));
        } finally {
            pool.shutdownNow();
        }
    }

    private void reject(Event event) throws Exception {
        rejectWithoutChangingCount(event, 0);
    }

    private void rejectWithoutChangingCount(Event event, int count) throws Exception {
        assertThrows(IllegalArgumentException.class, () -> events.addEvent(match, event));
        assertEquals(0, event.getId());
        assertEquals(count, db.integer("SELECT count(*) FROM events WHERE match_id=?", db.matchId));
    }

    private Match load() throws SQLException {
        Round round = new Round();
        round.setId(db.roundId);
        return matches.searchMatch(round, "").stream().filter(m -> m.getId() == db.matchId).findFirst().orElseThrow();
    }

    private static MatchClub side(Match match, String role) {
        return match.getMatchClubs().stream().filter(s -> role.equals(s.getRole())).findFirst().orElseThrow();
    }

    private MatchPlayer player(int team, int index) {
        return side(match, team == 0 ? "HOME" : "AWAY").getMatchPlayers().stream().filter(p -> p.getClubPlayer().getId() == db.members[team][index]).findFirst().orElseThrow();
    }

    private static <T extends Event> T at(T event, MatchPlayer actor, int minute) {
        event.setPrimaryPlayer(actor);
        event.setMinute(minute);
        return event;
    }

    private static Goal goal(String type, MatchPlayer actor, int minute) {
        Goal goal = at(new Goal(), actor, minute);
        goal.setType(type);
        return goal;
    }
}
