package com.footballmanager.util;

import com.footballmanager.model.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SessionContextTest {
    private static HttpServletRequest request(String method, String... pairs) {
        Map<String, String[]> parameters = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            parameters.put(pairs[i], new String[]{pairs[i + 1]});
        }
        return (HttpServletRequest) Proxy.newProxyInstance(SessionContextTest.class.getClassLoader(),
            new Class<?>[]{HttpServletRequest.class}, (proxy, call, args) -> switch (call.getName()) {
                case "getMethod" -> method;
                case "getParameter" -> parameters.containsKey(args[0]) ? parameters.get(args[0])[0] : null;
                case "getParameterValues" -> parameters.get(args[0]);
                case "getParameterMap" -> parameters;
                default -> throw new UnsupportedOperationException(call.getName());
            });
    }

    private static SessionContext context() {
        SessionContext ctx = new SessionContext();
        Tournament tournament = new Tournament();
        tournament.setId(10);
        ctx.tournament = tournament;
        Season season = new Season();
        season.setId(20);
        season.setYear(2026);
        ctx.seasons = List.of(season);
        ctx.season = season;
        Round round = new Round();
        round.setId(30);
        round.setNumber(7);
        ctx.rounds = List.of(round);
        ctx.round = round;
        Match match = new Match();
        match.setId(40);
        match.setRoundNumber(7);
        match.setMatchNumber(1);
        MatchClub side = new MatchClub();
        side.setId(50);
        side.setRole("HOME");
        match.setMatchClubs(List.of(side));
        ctx.matches = List.of(match);
        return ctx;
    }

    private static HttpServletRequest matchGet() {
        return request("GET", "season", "VLE2026", "round", "R07", "match", "T01");
    }

    @Test
    void cleanCodesSelectCachedGraphAndIdenticalReloadKeepsVersion() {
        SessionContext ctx = context();
        Match cached = ctx.matches.get(0);
        ctx.navigate(matchGet(), 3, false);
        String version = ctx.version();
        ctx.navigate(matchGet(), 3, false);
        assertSame(cached, ctx.match);
        assertEquals(version, ctx.version());
        assertEquals("season=VLE2026&round=R07&match=T01", ctx.query());
    }

    @Test
    void sameMatchModeChangeInvalidatesOldPostAndKeepsGraph() {
        SessionContext ctx = context();
        ctx.navigate(matchGet(), 3, false);
        String oldVersion = ctx.version();
        Match cached = ctx.match;
        ctx.navigate(matchGet(), 3, true);
        assertSame(cached, ctx.match);
        assertNotEquals(oldVersion, ctx.version());
        assertEquals("match-search-events", ctx.searchPage("match"));
        assertThrows(IllegalArgumentException.class, () -> ctx.requireMatch(request("POST",
            "mode", "lineup", "flow", oldVersion, "tournamentId", "10", "seasonId", "20", "roundId", "30", "matchId", "40")));
    }

    @Test
    void duplicateSeasonYearCannotChooseFirstCachedRow() {
        SessionContext ctx = context();
        Season duplicate = new Season();
        duplicate.setId(21);
        duplicate.setYear(2026);
        ctx.seasons = List.of(ctx.season, duplicate);
        assertThrows(IllegalArgumentException.class, () -> ctx.navigate(matchGet(), 3, true));
        assertFalse(ctx.eventMode());
        assertNull(ctx.match);
    }

    @Test
    void rejectsUnknownMismatchedAndInternalNavigationWithoutChangingVersion() {
        SessionContext ctx = context();
        String version = ctx.version();
        assertThrows(IllegalArgumentException.class, () -> ctx.navigate(request("GET",
            "season", "VLE2025", "round", "R07", "match", "T01"), 3, false));
        assertThrows(IllegalArgumentException.class, () -> ctx.navigate(request("GET",
            "season", "VLE2026", "round", "R08", "match", "T01"), 3, false));
        assertThrows(IllegalArgumentException.class, () -> ctx.navigate(request("GET",
            "season", "VLE2026", "round", "R07", "match", "R07-T01"), 3, false));
        assertThrows(IllegalArgumentException.class, () -> ctx.navigate(request("GET", "flow", version), 0, false));
        assertEquals(version, ctx.version());
    }

    @Test
    void roleSelectionAndBackNavigationInvalidateOldLineupPost() {
        SessionContext ctx = context();
        ctx.navigate(request("GET", "season", "VLE2026", "round", "R07", "match", "T01", "side", "HOME"), 4, false);
        String oldVersion = ctx.version();
        assertEquals("HOME", ctx.side.getRole());
        ctx.navigate(matchGet(), 3, false);
        assertNull(ctx.side);
        assertThrows(IllegalArgumentException.class, () -> ctx.requireSide(request("POST",
            "mode", "lineup", "flow", oldVersion, "tournamentId", "10", "seasonId", "20", "roundId", "30", "matchId", "40", "matchClubId", "50")));
    }

    @Test
    void csrfAndSessionAttributeRemainServerOnly() {
        SessionContext ctx = context();
        assertThrows(SecurityException.class, () -> ctx.requireCsrf(request("POST", "csrf", "invalid")));
        assertDoesNotThrow(() -> ctx.requireCsrf(request("POST", "csrf", ctx.csrf())));
        Map<String, Object> attributes = new HashMap<>();
        HttpSession session = (HttpSession) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{HttpSession.class}, (proxy, call, args) -> {
                if ("getAttribute".equals(call.getName())) return attributes.get(args[0]);
                if ("setAttribute".equals(call.getName())) {
                    attributes.put((String) args[0], args[1]);
                    return null;
                }
                throw new UnsupportedOperationException(call.getName());
            });
        assertSame(SessionContext.get(session), attributes.get("sessionContext"));
        assertEquals(Set.of("sessionContext"), attributes.keySet());
    }
}
