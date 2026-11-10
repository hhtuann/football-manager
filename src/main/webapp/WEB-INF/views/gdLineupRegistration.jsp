<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ page import="com.footballmanager.model.*" %>
<%@ page import="com.footballmanager.dao.*" %>
<%@ page import="com.footballmanager.util.*" %>
<%@ page import="jakarta.servlet.http.HttpSession" %>
<%@ page import="java.util.*" %>
<%@ page import="java.sql.SQLException" %>

<%
    response.setHeader("Cache-Control", "no-store");
    HttpSession currentSession = request.getSession(false);
    Object currentUser = currentSession == null ? null : currentSession.getAttribute("currentUser");

    if (!(currentUser instanceof Staff)) {
        response.sendError(403);
        return;
    }
    synchronized (currentSession) {
        LineupSession ctx = LineupSession.get(currentSession);
        String error = null;
        String key = request.getParameter("key") == null ? "" : request.getParameter("key");
        Map<Integer, String> choices = new LinkedHashMap<>();
        try {
            if (request.getAttribute("lineupContextError") != null) {
                throw new IllegalArgumentException((String) request.getAttribute("lineupContextError"));
            }
            ctx.requireMatch(request);
            int selectedId = LineupSession.id(request, "matchClubId");
            MatchClub selected = null;
            for (MatchClub item : ctx.match.getMatchClubs()) {
                if (item.getId() == selectedId) {
                    selected = item;
                }
            }
            if (selected == null) {
                throw new IllegalArgumentException("CLB không thuộc trận đấu hiện tại.");
            }
            if (request.getAttribute("lineupChoices") == null) {
                if (ctx.side != selected || !ctx.version().equals(LineupSession.single(request, "ready"))) {
                    ctx.side = null;
                    ctx.players = new ArrayList<>();
                    List<ClubPlayer> loaded = new ClubDAO().getClubPlayerList(selected.getClub());
                    ctx.side = selected;
                    ctx.players = loaded;
                    ctx.changed();
                    response.sendRedirect(request.getContextPath() + "/index.jsp?page=lineup-registration&" + ctx.query() + "&matchClubId=" + selected.getId() + "&ready=" + ctx.version());
                    return;
                }
                for (MatchPlayer player : selected.getMatchPlayers()) {
                    choices.put(player.getClubPlayer().getId(), player.getRole());
                }
            } else {
                ctx.requireSide(request);
                choices = (Map<Integer, String>) request.getAttribute("lineupChoices");
            }
        } catch (SQLException ex) {
            application.log("Lỗi tải cầu thủ CLB", ex);
            error = "Không thể tải danh sách cầu thủ của CLB. Vui lòng thử lại.";
        } catch (IllegalArgumentException ex) {
            error = ex.getMessage();
        }
        String saveError = (String) request.getAttribute("lineupError");
        String title = "Đăng ký cầu thủ thi đấu";
        request.setAttribute("breadcrumb", title);
        if (error == null) {
            request.setAttribute("breadcrumbParents", new String[][]{{"Tìm mùa giải", request.getContextPath() + "/index.jsp?page=season-search&" + ctx.query()}, {"Tìm vòng đấu", request.getContextPath() + "/index.jsp?page=round-search&" + ctx.query()}, {"Tìm trận đấu", request.getContextPath() + "/index.jsp?page=match-search&" + ctx.query()}, {"Thông tin trận đấu", request.getContextPath() + "/index.jsp?page=match-info&" + ctx.query()}});
        }
%>

<!DOCTYPE html><html lang="vi"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
<title><%= HTMLUtil.escape(title) %> - Football Manager</title>
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/common.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/search.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/lineup.css?v=20261105-2">
<script src="${pageContext.request.contextPath}/assets/js/lineup.js?v=20261105-2" defer></script>
</head><body class="search-page">
<%@ include file="fragments/topbar.jspf" %>
<main class="search-content lineup-content">
    <% if (error != null) { %>
    <div class="error-box" role="alert"><%= HTMLUtil.escape(error) %>
    </div>
    <a class="back-link" href="${pageContext.request.contextPath}/index.jsp?page=season-search">« Quay lại</a>
    <h2><%= HTMLUtil.escape(title) %>
    </h2>
    <% } else { %>
    <a class="back-link"
       href="${pageContext.request.contextPath}/index.jsp?page=match-info&amp;<%= HTMLUtil.escape(ctx.query()) %>">«
        Quay lại</a>
    <h2><%= HTMLUtil.escape(title) %>
    </h2>
    <h3><%= HTMLUtil.escape(ctx.side.getClub().getFullName()) %>
        · <%= "HOME".equals(ctx.side.getRole()) ? "Đội nhà" : "Đội khách" %>
    </h3>
    <% if (saveError != null) { %>
    <div class="error-box" role="alert"><%= HTMLUtil.escape(saveError) %>
    </div>
    <% } %>
    <% Rule rule = ctx.season.getRule();
        boolean locked = ctx.locked(); %>
    <div class="notice">Đủ 11 đá chính, có ít nhất một Thủ môn đá chính; tối đa <%= rule.getMaxBenchPlayers() %> dự bị
        và <%= rule.getMaxForeignPlayers() %> ngoại binh trên toàn bộ đội hình. Tuổi tối đa <%= rule.getAgeLimit() %>tại
        ngày <%= LineupSession.date(ctx.season.getStartDate()) %>. Cầu thủ bị treo giò không được đăng ký. Lưu sẽ thay
        thế đội hình bên này; bên còn lại được giữ nguyên.
    </div>
    <% if (rule.getRequiredMainPlayers() != 11) { %>
    <div class="error-box" role="alert">Cấu hình điều lệ không hợp lệ: số đá chính bắt buộc phải là 11.</div>
    <% } %>
    <% if (locked) { %>
    <div class="notice">Đội hình đã khóa theo trạng thái, giờ bắt đầu chưa xác định hoặc đã đến, hoặc sự kiện trận đấu.
        Không thể lưu thay đổi.
    </div>
    <% } %>
    <form id="lineup-form" action="${pageContext.request.contextPath}/doSaveLineup.jsp" method="post">
        <input type="hidden" name="mode" value="<%= ctx.mode %>">
        <input type="hidden" name="flow" value="<%= ctx.version() %>">
        <input type="hidden" name="csrf" value="<%= ctx.csrf() %>">
        <input type="hidden" name="tournamentId" value="<%= ctx.tournament.getId() %>">
        <input type="hidden" name="seasonId" value="<%= ctx.season.getId() %>">
        <input type="hidden" name="roundId" value="<%= ctx.round.getId() %>">
        <input type="hidden" name="matchId" value="<%= ctx.match.getId() %>">
        <input type="hidden" name="matchClubId" value="<%= ctx.side.getId() %>">
        <div id="lineup-counts" class="lineup-counts" role="status" aria-live="polite"></div>
        <div class="search-results">
            <div class="table-scroll">
                <table>
                    <thead>
                    <tr>
                        <th>Số áo</th>
                        <th>Cầu thủ</th>
                        <th>Ngày sinh</th>
                        <th>Vị trí</th>
                        <th>Quốc tịch</th>
                        <th>Vai trò thi đấu</th>
                    </tr>
                    </thead>
                    <tbody>
                    <% for (ClubPlayer member : ctx.players) {
                        Player player = member.getPlayer();
                        String selected = choices.getOrDefault(member.getId(), ""); %>
                    <tr>
                        <td><%= member.getShirtNumber() %>
                        </td>
                        <td><%= HTMLUtil.escape(player.getFullName()) %>
                        </td>
                        <td><%= player.getDob() == null ? "Thiếu ngày sinh" : LineupSession.date(player.getDob()) %>
                        </td>
                        <td><%= HTMLUtil.escape(player.getPrimaryPosition()) %>
                        </td>
                        <td><%= HTMLUtil.escape(player.getNationality()) %>
                        </td>
                        <td>
                            <label class="sr-only" for="role-<%= member.getId() %>">Vai trò
                                của <%= HTMLUtil.escape(player.getFullName()) %>
                            </label>
                            <select id="role-<%= member.getId() %>" name="role_<%= member.getId() %>" data-lineup-role
                                    data-foreign="<%= player.getNationality() != null && !"Việt Nam".equalsIgnoreCase(player.getNationality().trim()) %>" <%= locked ? "disabled" : "" %>>
                                <option value="" <%= selected.isEmpty() ? "selected" : "" %>>Không đăng ký</option>
                                <option value="STARTING" <%= "STARTING".equals(selected) ? "selected" : "" %>>Đá chính
                                </option>
                                <option value="SUBSTITUTE" <%= "SUBSTITUTE".equals(selected) ? "selected" : "" %>>Dự
                                    bị
                                </option>
                            </select></td>
                    </tr>
                    <% } %>
                    </tbody>
                </table>
            </div>
        </div>
        <% if (ctx.players.isEmpty()) { %><p class="empty-state">CLB chưa có cầu thủ. Cần bổ sung thành viên CLB trước
        khi đăng ký.</p><% } %>
        <div class="lineup-actions">
            <button
                type="submit" <%= locked || ctx.players.isEmpty() || rule.getRequiredMainPlayers() != 11 ? "disabled" : "" %>>
                Lưu đội hình
            </button>
            <a href="${pageContext.request.contextPath}/index.jsp?page=match-info&amp;<%= HTMLUtil.escape(ctx.query()) %>">Hủy
                / quay lại</a></div>
        <p class="muted">Bộ đếm chỉ hỗ trợ lựa chọn. Hệ thống kiểm tra lại điều lệ, thành viên CLB, treo giò và tình
            trạng trận khi lưu.</p>
    </form>
    <% } %></main>
</body></html><% } %>
