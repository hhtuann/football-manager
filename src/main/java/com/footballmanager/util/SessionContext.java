package com.footballmanager.util;

import com.footballmanager.model.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Session-only navigation cache. Call while holding the HttpSession monitor.
 */
public final class SessionContext {
    public Tournament tournament;
    public List<Season> seasons = new ArrayList<>();
    public Season season;
    public List<Round> rounds = new ArrayList<>();
    public Round round;
    public List<Match> matches = new ArrayList<>();
    public Match match;
    public MatchClub side;
    public List<ClubPlayer> players = new ArrayList<>();
    private String version = UUID.randomUUID().toString();
    private final String csrf = UUID.randomUUID().toString() + UUID.randomUUID();
    public String flash;
    public String mode = "lineup";
    public List<Event> events = new ArrayList<>();
    public List<MatchPlayer> listPlayerHome = new ArrayList<>();
    public List<MatchPlayer> listPlayerAway = new ArrayList<>();
    public Map<String, String> eventInputs = new LinkedHashMap<>();
    public String eventError;

    public boolean eventMode() {
        return "events".equals(mode);
    }

    public String searchPage(String level) {
        return level + "-search" + (eventMode() ? "-events" : "");
    }

    public void selectMode(boolean eventsMode) {
        String selected = eventsMode ? "events" : "lineup";
        if (!mode.equals(selected)) {
            mode = selected;
            side = null;
            players = new ArrayList<>();
            eventInputs.clear();
            eventError = null;
            changed();
        }
    }

    public static String seasonCode(Season value) {
        return "VLE" + value.getYear();
    }

    public static String matchCode(Match value) {
        return value.getMatchNumber() == null ? "" : String.format("T%02d", value.getMatchNumber());
    }

    private static String code(HttpServletRequest request, String name, String pattern) {
        String value = single(request, name);
        if (value == null || !value.matches(pattern)) {
            throw new IllegalArgumentException("Mã điều hướng không hợp lệ: " + name);
        }
        return value;
    }

    private static <T> T unique(List<T> values, java.util.function.Predicate<T> predicate) {
        T selected = null;
        for (T value : values) {
            if (predicate.test(value)) {
                if (selected != null) {
                    throw new IllegalArgumentException("Mã điều hướng không duy nhất. Vui lòng chọn lại mùa giải.");
                }
                selected = value;
            }
        }
        if (selected == null) {
            throw new IllegalArgumentException("Lựa chọn không thuộc kết quả tìm kiếm hiện tại.");
        }
        return selected;
    }

    /** Resolve only cached search results, never an arbitrary database ID. */
    public void navigate(HttpServletRequest request, int depth, boolean eventsMode) {
        for (String forbidden : new String[]{"flow", "ready", "mode", "tournamentId", "seasonId", "roundId", "matchId", "matchClubId"}) {
            if (request.getParameter(forbidden) != null) {
                throw new IllegalArgumentException("Tham số điều hướng không được hỗ trợ: " + forbidden);
            }
        }
        Season selectedSeason = null;
        Round selectedRound = null;
        Match selectedMatch = null;
        MatchClub selectedSide = null;
        if (depth >= 1) {
            String value = code(request, "season", "VLE[0-9]{4}");
            selectedSeason = unique(seasons, item -> seasonCode(item).equals(value));
        }
        if (depth >= 2) {
            if (selectedSeason != season) {
                throw new IllegalArgumentException("Vòng đấu không thuộc mùa giải hiện tại.");
            }
            String value = code(request, "round", "R[0-9]{2,}");
            selectedRound = unique(rounds, item -> item.getDisplayCode().equals(value));
        }
        if (depth >= 3) {
            if (selectedRound != round) {
                throw new IllegalArgumentException("Trận đấu không thuộc vòng đấu hiện tại.");
            }
            String value = code(request, "match", "T[0-9]{2,}");
            selectedMatch = unique(matches, item -> matchCode(item).equals(value));
        }
        if (depth >= 4) {
            String value = code(request, "side", "HOME|AWAY");
            selectedSide = unique(selectedMatch.getMatchClubs(), item -> value.equals(item.getRole()));
        }
        String[] hierarchy = {"season", "round", "match", "side"};
        for (int index = depth; index < hierarchy.length; index++) {
            if (request.getParameter(hierarchy[index]) != null) {
                throw new IllegalArgumentException("Phân cấp điều hướng không hợp lệ.");
            }
        }
        selectMode(eventsMode);
        if (season != selectedSeason) {
            clearRound();
            season = selectedSeason;
            changed();
        }
        if (round != selectedRound) {
            clearMatch();
            round = selectedRound;
            changed();
        }
        if (match != selectedMatch) {
            clearMatch();
            match = selectedMatch;
            changed();
        }
        if (side != selectedSide) {
            side = selectedSide;
            players = new ArrayList<>();
            changed();
        }
    }

    public void requireEvents(HttpServletRequest request) {
        requireMatch(request);
        if (!eventMode()) {
            throw new IllegalArgumentException("Vui lòng mở chức năng cập nhật kết quả từ trang chủ.");
        }
    }

    public void cacheEventPlayers(HttpSession session) {
        listPlayerHome = new ArrayList<>();
        listPlayerAway = new ArrayList<>();
        if (match != null) {
            for (MatchClub club : match.getMatchClubs()) {
                if ("HOME".equals(club.getRole())) {
                    listPlayerHome = club.getMatchPlayers();
                }
                if ("AWAY".equals(club.getRole())) {
                    listPlayerAway = club.getMatchPlayers();
                }
            }
        }
        session.setAttribute("listPlayerHome", listPlayerHome);
        session.setAttribute("listPlayerAway", listPlayerAway);
    }

    public MatchClub eventSide(int id) {
        if (match != null) {
            for (MatchClub club : match.getMatchClubs()) {
                if (club.getId() == id) {
                    return club;
                }
            }
        }
        throw new IllegalArgumentException("CLB không thuộc trận đấu hiện tại.");
    }

    public List<MatchPlayer> eventPlayers(MatchClub club) {
        return "HOME".equals(club.getRole()) ? listPlayerHome : listPlayerAway;
    }

    public static MatchPlayer eventPlayer(List<MatchPlayer> players, int id) {
        for (MatchPlayer player : players) {
            if (player.getId() == id) {
                return player;
            }
        }
        throw new IllegalArgumentException("Cầu thủ không thuộc đội hình đã chọn.");
    }

    public static String playerName(MatchPlayer player) {
        return player == null ? "—" : player.getClubPlayer().getPlayer().getFullName();
    }

    public static String eventLabel(Event event) {
        if (event instanceof Goal) {
            return "OWN".equals(((Goal) event).getType()) ? "Phản lưới nhà" :
                "PENALTY".equals(((Goal) event).getType()) ? "Bàn phạt đền" : "Bàn thắng";
        }
        if (event instanceof Foul) {
            return "Phạm lỗi";
        }
        if (event instanceof YellowCard) {
            return "Thẻ vàng";
        }
        if (event instanceof RedCard) {
            return "Thẻ đỏ";
        }
        return "Thay người";
    }

    public static MatchPlayer relatedPlayer(Event event) {
        if (event instanceof Goal) {
            return ((Goal) event).getAssister();
        }
        if (event instanceof Foul) {
            return ((Foul) event).getVictim();
        }
        if (event instanceof Substitution) {
            return ((Substitution) event).getPlayerIn();
        }
        return null;
    }

    public static SessionContext get(HttpSession session) {
        Object value = session.getAttribute("sessionContext");
        if (value instanceof SessionContext) {
            return (SessionContext) value;
        }
        SessionContext context = new SessionContext();
        session.setAttribute("sessionContext", context);
        return context;
    }

    public String version() {
        return version;
    }

    public String csrf() {
        return csrf;
    }

    public void changed() {
        version = UUID.randomUUID().toString();
    }

    public void requireVersion(HttpServletRequest request) {
        String selectedMode = single(request, "mode");
        if (!mode.equals(selectedMode == null ? "lineup" : selectedMode)) {
            throw new IllegalArgumentException("Chức năng đã thay đổi. Vui lòng chọn lại mùa giải.");
        }
        if (!version.equals(single(request, "flow"))) {
            throw new IllegalArgumentException("Trang đã cũ hoặc ngữ cảnh đã thay đổi. Vui lòng chọn lại từ tìm mùa " + "giải.");
        }
    }

    public void requireCsrf(HttpServletRequest request) {
        String submitted = single(request, "csrf");
        if (submitted == null || !MessageDigest.isEqual(csrf.getBytes(StandardCharsets.UTF_8),
            submitted.getBytes(StandardCharsets.UTF_8))) {
            throw new SecurityException("Phiên xác nhận không hợp lệ.");
        }
    }

    public static String single(HttpServletRequest request, String name) {
        String[] values = request.getParameterValues(name);
        if (values == null) {
            return null;
        }
        if (values.length != 1) {
            throw new IllegalArgumentException("Tham số bị trùng: " + name);
        }
        return values[0];
    }

    public static int id(HttpServletRequest request, String name) {
        String value = single(request, name);
        try {
            int id = Integer.parseInt(value);
            if (id > 0) {
                return id;
            }
        } catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException("Mã ngữ cảnh không hợp lệ. Vui lòng chọn lại từ tìm mùa giải.");
    }

    public void requireSeason(HttpServletRequest request) {
        requireVersion(request);
        if (tournament == null || season == null || id(request, "tournamentId") != tournament.getId() || id(request,
            "seasonId") != season.getId()) {
            throw new IllegalArgumentException("Mùa giải không còn trong ngữ cảnh hiện tại.");
        }
    }

    public void requireRound(HttpServletRequest request) {
        requireSeason(request);
        if (round == null || id(request, "roundId") != round.getId()) {
            throw new IllegalArgumentException("Vòng đấu không còn trong ngữ cảnh hiện tại.");
        }
    }

    public void requireMatch(HttpServletRequest request) {
        requireRound(request);
        if (match == null || id(request, "matchId") != match.getId()) {
            throw new IllegalArgumentException("Trận đấu không còn trong ngữ cảnh hiện tại.");
        }
    }

    public void requireSide(HttpServletRequest request) {
        requireMatch(request);
        if (side == null || id(request, "matchClubId") != side.getId()) {
            throw new IllegalArgumentException("Đội bóng không còn trong ngữ cảnh hiện tại.");
        }
    }

    public String query() {
        return query(3);
    }

    public String query(int depth) {
        return (depth >= 1 && season != null ? "season=" + seasonCode(season) : "")
            + (depth >= 2 && round != null ? "&round=" + round.getDisplayCode() : "")
            + (depth >= 3 && match != null ? "&match=" + matchCode(match) : "");
    }

    public void clearSeason() {
        season = null;
        rounds = new ArrayList<>();
        clearRound();
    }

    public void clearRound() {
        round = null;
        matches = new ArrayList<>();
        clearMatch();
    }

    public void clearMatch() {
        match = null;
        side = null;
        players = new ArrayList<>();
        flash = null;
        events = new ArrayList<>();
        listPlayerHome = new ArrayList<>();
        listPlayerAway = new ArrayList<>();
        eventInputs = new LinkedHashMap<>();
        eventError = null;
    }

    public boolean locked() {
        return match == null || match.isLineupLocked();
    }

    public static String role(String role) {
        return "STARTING".equals(role) ? "Đá chính" : "SUBSTITUTE".equals(role) ? "Dự bị" : role;
    }

    public static String date(java.time.LocalDate value) {
        return value == null ? "Chưa xác định" : value.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM" +
            "/yyyy"));
    }

    public static String dateTime(java.time.LocalDateTime value) {
        return value == null ? "Chưa xác định" : value.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM" +
            "/yyyy HH:mm"));
    }

    public static String status(String status) {
        return "SCHEDULED".equals(status) ? "Chưa diễn ra" : "ONGOING".equals(status) ? "Đang diễn ra" :
            "FINISHED".equals(status) ? "Đã kết thúc" : status;
    }
}
