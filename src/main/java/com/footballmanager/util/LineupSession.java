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
public final class LineupSession {
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

    public void selectMode(HttpServletRequest request) {
        String selected = single(request, "mode");
        if (selected == null) {
            selected = "lineup";
        }
        if (!"lineup".equals(selected) && !"events".equals(selected)) {
            throw new IllegalArgumentException("Chức năng điều hướng không hợp lệ.");
        }
        if (request.getParameter("flow") != null) {
            if (!mode.equals(selected)) {
                throw new IllegalArgumentException("Chức năng đã thay đổi. Vui lòng chọn lại mùa giải.");
            }
        } else if (!mode.equals(selected)) {
            clearSeason();
            seasons = new ArrayList<>();
            mode = selected;
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

    public static LineupSession get(HttpSession session) {
        Object value = session.getAttribute("cn2Context");
        if (value instanceof LineupSession) {
            return (LineupSession) value;
        }
        LineupSession context = new LineupSession();
        session.setAttribute("cn2Context", context);
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

    public void requireOptionalSide(HttpServletRequest request) {
        if (request.getParameter("matchClubId") == null) {
            return;
        }
        requireMatch(request);
        int selectedId = id(request, "matchClubId");
        for (MatchClub club : match.getMatchClubs()) {
            if (club.getId() == selectedId) {
                return;
            }
        }
        throw new IllegalArgumentException("CLB không thuộc trận đấu hiện tại.");
    }

    public String query() {
        return "mode=" + mode + "&flow=" + version + (tournament == null ? "" : "&tournamentId=" + tournament.getId()) + (season == null ? "" : "&seasonId=" + season.getId()) + (round == null ? "" : "&roundId=" + round.getId()) + (match == null ? "" : "&matchId=" + match.getId());
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
