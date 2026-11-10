<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ page import="com.footballmanager.model.*" %>
<%@ page import="com.footballmanager.dao.MatchDAO" %>
<%@ page import="com.footballmanager.util.LineupSession" %>
<%@ page import="jakarta.servlet.http.HttpSession" %>
<%@ page import="java.sql.SQLException" %>
<%@ page import="java.util.*" %>
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
        try {
            Set<String> permitted = Set.of("mode", "flow", "csrf", "tournamentId", "seasonId", "roundId", "matchId");
            for (String name : request.getParameterMap().keySet()) {
                if (!permitted.contains(name)) {
                    throw new IllegalArgumentException("Biểu mẫu chứa tham số không hợp lệ.");
                }
                LineupSession.single(request, name);
            }
            if ("FINISHED".equals(ctx.match.getStatus())) {
                throw new IllegalArgumentException("Trận đấu đã kết thúc.");
            }
            if (!new MatchDAO().saveMatch(ctx.match)) {
                throw new IllegalArgumentException("Không thể kết thúc trận. Vui lòng kiểm tra đội hình hai đội và trạng thái hiện tại.");
            }
            ctx.changed();
            ctx.eventInputs.clear();
            ctx.eventError = null;
            ctx.flash = "Đã kết thúc trận đấu và chốt kết quả thành công.";
        } catch (IllegalArgumentException ex) {
            ctx.eventError = ex.getMessage();
        } catch (SQLException ex) {
            application.log("Lỗi kết thúc trận đấu", ex);
            ctx.eventError = "Không thể kết thúc trận do lỗi dữ liệu. Vui lòng thử lại.";
        }
        response.sendRedirect(request.getContextPath() + "/index.jsp?page=match-event&" + ctx.query());
    }
%>
