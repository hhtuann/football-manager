<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ page import="com.footballmanager.model.*" %>
<%@ page import="com.footballmanager.dao.MatchDAO" %>
<%@ page import="com.footballmanager.util.SessionContext" %>
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
        SessionContext ctx = SessionContext.get(currentSession);
        Map<Integer, String> choices = new LinkedHashMap<>();
        try {
            ctx.requireCsrf(request);
            ctx.requireSide(request);
        } catch (SecurityException ex) {
            response.sendError(403);
            return;
        } catch (IllegalArgumentException ex) {
            request.setAttribute("lineupContextError", ex.getMessage());
            request.getRequestDispatcher("/WEB-INF/views/gdLineupRegistration.jsp").forward(request, response);
            return;
        }
        String error = null;
        try {
            Set<String> permitted = new HashSet<>(Arrays.asList("mode", "flow", "csrf", "tournamentId", "seasonId", "roundId", "matchId", "matchClubId"));
            Map<Integer, ClubPlayer> members = new LinkedHashMap<>();
            for (ClubPlayer member : ctx.players) {
                members.put(member.getId(), member);
                permitted.add("role_" + member.getId());
            }
            // Collect known choices before validating so business errors preserve the form.
            for (ClubPlayer member : ctx.players) {
                String[] submitted = request.getParameterValues("role_" + member.getId());
                if (submitted != null && submitted.length == 1) {
                    choices.put(member.getId(), submitted[0]);
                }
            }
            for (String name : request.getParameterMap().keySet()) {
                if (!permitted.contains(name)) {
                    throw new IllegalArgumentException("Biểu mẫu chứa cầu thủ hoặc tham số không thuộc danh sách hiện tại.");
                }
                SessionContext.single(request, name);
            }
            MatchClub candidate = new MatchClub();
            candidate.setId(ctx.side.getId());
            candidate.setRole(ctx.side.getRole());
            candidate.setClub(ctx.side.getClub());
            candidate.setScore(ctx.side.getScore());
            List<MatchPlayer> selected = new ArrayList<>();
            for (ClubPlayer member : ctx.players) {
                String role = SessionContext.single(request, "role_" + member.getId());
                if (role == null) {
                    throw new IllegalArgumentException("Biểu mẫu thiếu lựa chọn cầu thủ. Vui lòng tải lại trang đăng ký.");
                }
                if (role.isEmpty()) {
                    continue;
                }
                if (!"STARTING".equals(role) && !"SUBSTITUTE".equals(role)) {
                    throw new IllegalArgumentException("Vai trò thi đấu không hợp lệ.");
                }
                MatchPlayer player = new MatchPlayer();
                player.setClubPlayer(member);
                player.setRole(role);
                selected.add(player);
            }
            candidate.setMatchPlayers(selected);
            if (!new MatchDAO().saveLineup(ctx.match, candidate)) {
                throw new IllegalArgumentException("Không thể lưu đội hình. Vui lòng thử lại.");
            }
            // DAO has committed and populated persistent IDs / timestamps on the candidate.
            ctx.side.setMatchPlayers(candidate.getMatchPlayers());
            ctx.changed();
            ctx.flash = "Đã lưu đội hình " + ctx.side.getClub().getFullName() + " thành công.";
            response.sendRedirect(request.getContextPath() + "/index.jsp?page=match-info&" + ctx.query());
            return;
        } catch (IllegalArgumentException ex) {
            error = ex.getMessage();
        } catch (SQLException ex) {
            application.log("Lỗi lưu đội hình", ex);
            error = "Không thể lưu đội hình do lỗi dữ liệu. Vui lòng thử lại; đội hình đã lưu trước đó không bị thay đổi.";
        }
        request.setAttribute("lineupChoices", choices);
        request.setAttribute("lineupError", error);
        request.getRequestDispatcher("/WEB-INF/views/gdLineupRegistration.jsp").forward(request, response);
    }
%>
