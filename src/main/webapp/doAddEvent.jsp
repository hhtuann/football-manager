<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ page import="com.footballmanager.model.*" %>
<%@ page import="com.footballmanager.dao.EventDAO" %>
<%@ page import="com.footballmanager.util.LineupSession" %>
<%@ page import="jakarta.servlet.http.HttpSession" %>
<%@ page import="java.util.*" %>
<%@ page import="java.sql.SQLException" %>
<%
    request.setCharacterEncoding("UTF-8");
    response.setHeader("Cache-Control", "no-store");
    if (!"POST".equals(request.getMethod())) {
        response.setHeader("Allow", "POST");
        response.sendError(405);
        return;
    }
    HttpSession currentSession = request.getSession(false);
    Object currentUser = currentSession == null ? null : currentSession.getAttribute("currentUser");

    if (!(currentUser instanceof Staff)) {
        response.sendError(403);
        return;
    }
    synchronized (currentSession) {
        LineupSession ctx = LineupSession.get(currentSession);
        try {
            ctx.requireCsrf(request);
            ctx.requireEvents(request);
        } catch (SecurityException ex) {
            response.sendError(403);
            return;
        } catch (IllegalArgumentException ex) {
            response.sendError(400, ex.getMessage());
            return;
        }
        ctx.eventInputs = new LinkedHashMap<>();
        String[] fields = {"matchClubId", "eventType", "minute", "extraMinute", "primaryPlayerId", "secondaryPlayerId", "note"};
        for (String name : fields) {
            String[] values = request.getParameterValues(name);
            if (values != null && values.length == 1) {
                ctx.eventInputs.put(name, values[0]);
            }
        }
        try {
            Set<String> permitted = new HashSet<>(Arrays.asList("page", "preview", "mode", "flow", "csrf", "tournamentId", "seasonId", "roundId", "matchId"));
            permitted.addAll(Arrays.asList(fields));
            for (String name : request.getParameterMap().keySet()) {
                if (!permitted.contains(name)) {
                    throw new IllegalArgumentException("Biểu mẫu chứa tham số không hợp lệ.");
                }
                LineupSession.single(request, name);
            }
            if ("true".equals(LineupSession.single(request, "preview"))) {
                ctx.eventError = null;
                response.sendRedirect(request.getContextPath() + "/index.jsp?page=event-form&" + ctx.query());
                return;
            }
            if ("FINISHED".equals(ctx.match.getStatus())) {
                throw new IllegalArgumentException("Trận đấu đã kết thúc; không thể thêm sự kiện.");
            }
            ctx.cacheEventPlayers(currentSession);
            MatchClub side = ctx.eventSide(LineupSession.id(request, "matchClubId"));
            MatchPlayer actor = LineupSession.eventPlayer(ctx.eventPlayers(side), LineupSession.id(request, "primaryPlayerId"));
            String type = LineupSession.single(request, "eventType");
            String relatedId = LineupSession.single(request, "secondaryPlayerId");
            MatchPlayer related = null;
            if (relatedId != null && !relatedId.isEmpty()) {
                List<MatchPlayer> choices = new ArrayList<>();
                for (MatchClub club : ctx.match.getMatchClubs()) {
                    if ("FOUL".equals(type) ? club != side : club == side) {
                        choices.addAll(ctx.eventPlayers(club));
                    }
                }
                related = LineupSession.eventPlayer(choices, LineupSession.id(request, "secondaryPlayerId"));
                if (related.getId() == actor.getId()) {
                    throw new IllegalArgumentException("Cầu thủ liên quan phải khác cầu thủ chính.");
                }
            }
            Event event;
            if ("NORMAL".equals(type) || "PENALTY".equals(type) || "OWN".equals(type)) {
                if (!"NORMAL".equals(type) && related != null) {
                    throw new IllegalArgumentException("Chỉ bàn thắng thường được có cầu thủ kiến tạo.");
                }
                Goal goal = new Goal();
                goal.setType(type);
                goal.setAssister(related);
                event = goal;
            } else if ("FOUL".equals(type)) {
                if (related == null) {
                    throw new IllegalArgumentException("Vui lòng chọn cầu thủ đối phương bị phạm lỗi.");
                }
                Foul foul = new Foul();
                foul.setVictim(related);
                event = foul;
            } else if ("SUBSTITUTION".equals(type)) {
                if (related == null || !"SUBSTITUTE".equals(related.getRole())) {
                    throw new IllegalArgumentException("Vui lòng chọn cầu thủ dự bị cùng đội vào sân.");
                }
                Substitution substitution = new Substitution();
                substitution.setPlayerIn(related);
                event = substitution;
            } else if ("YELLOW_CARD".equals(type) || "RED_CARD".equals(type)) {
                if (related != null) {
                    throw new IllegalArgumentException("Sự kiện thẻ không có cầu thủ liên quan.");
                }
                event = "YELLOW_CARD".equals(type) ? new YellowCard() : new RedCard();
            } else {
                throw new IllegalArgumentException("Loại sự kiện không hợp lệ.");
            }
            int minute, extra;
            try {
                minute = Integer.parseInt(LineupSession.single(request, "minute"));
                extra = Integer.parseInt(LineupSession.single(request, "extraMinute"));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Phút thi đấu và phút bù giờ phải là số nguyên không âm.");
            }
            if (minute < 0 || extra < 0) {
                throw new IllegalArgumentException("Phút thi đấu và phút bù giờ phải là số nguyên không âm.");
            }
            event.setPrimaryPlayer(actor);
            event.setMinute(minute);
            event.setExtraMinute(extra);
            event.setNote(LineupSession.single(request, "note"));
            if (!new EventDAO().addEvent(ctx.match, event)) {
                throw new IllegalArgumentException("Không thể lưu sự kiện. Vui lòng kiểm tra trạng thái trận và đội hình hiện tại.");
            }
            ctx.eventInputs.clear();
            ctx.eventError = null;
            ctx.changed();
            ctx.flash = "Đã thêm sự kiện thành công.";
            response.sendRedirect(request.getContextPath() + "/index.jsp?page=match-event&" + ctx.query());
            return;
        } catch (IllegalArgumentException ex) {
            ctx.eventError = ex.getMessage();
        } catch (SQLException ex) {
            application.log("Lỗi thêm sự kiện trận đấu", ex);
            ctx.eventError = "Không thể lưu sự kiện do lỗi dữ liệu. Vui lòng thử lại; sự kiện chưa được lưu.";
        }
        response.sendRedirect(request.getContextPath() + "/index.jsp?page=event-form&" + ctx.query());
    }
%>
