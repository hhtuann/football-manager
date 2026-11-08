<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ page
        import="com.footballmanager.model.*,com.footballmanager.dao.*,com.footballmanager.util.*,jakarta.servlet.http.HttpSession,java.util.*,java.sql.SQLException" %>
<%
    response.setHeader("Cache-Control", "no-store");
    HttpSession currentSession = request.getSession(false);
    if (currentSession == null || !(currentSession.getAttribute("currentUser") instanceof Staff)) {
        response.sendError(403);
        return;
    }
    synchronized (currentSession) {
        LineupSession ctx = LineupSession.get(currentSession);
        String error = null;
        String key = request.getParameter("key") == null ? "" : request.getParameter("key");
        try {
            ctx.requireSeason(request);
            if (request.getParameter("matchId") != null) ctx.requireMatch(request);
            ctx.requireOptionalSide(request);
            int selectedId = LineupSession.id(request, "roundId");
            Round selected = null;
            for (Round item : ctx.rounds) if (item.getId() == selectedId) selected = item;
            if (selected == null) throw new IllegalArgumentException("Vòng đấu không thuộc kết quả tìm kiếm hiện tại.");
            if (ctx.round != selected || !ctx.version().equals(LineupSession.single(request, "ready"))) {
                ctx.clearMatch();
                ctx.round = selected;
                ctx.matches = new ArrayList<>();
                ctx.changed();
                ctx.matches = new MatchDAO().searchMatch(ctx.round, key);
                response.sendRedirect(request.getContextPath() + "/index.jsp?page=match-search&" + ctx.query() + "&ready=" + ctx.version() + "&key=" + java.net.URLEncoder.encode(key, java.nio.charset.StandardCharsets.UTF_8));
                return;
            }
        } catch (SQLException ex) {
            application.log("Lỗi tìm trận đấu", ex);
            error = "Không thể tải trận đấu. Vui lòng thử lại.";
        } catch (IllegalArgumentException ex) {
            error = ex.getMessage();
        }
        String title = "Tìm trận đấu";
        request.setAttribute("breadcrumb", title);
        if (error == null) request.setAttribute("breadcrumbParents", new String[][]{
                {"Tìm mùa giải", request.getContextPath() + "/index.jsp?page=season-search&" + ctx.query()},
                {"Tìm vòng đấu", request.getContextPath() + "/index.jsp?page=round-search&" + ctx.query()}
        });
%>
<!DOCTYPE html><html lang="vi"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
<title><%= HTMLUtil.escape(title) %> - Football Manager</title>
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/common.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/search.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/lineup.css?v=20261105-2">
<script src="${pageContext.request.contextPath}/assets/js/lineup.js?v=20261105-2" defer></script>
</head><body class="search-page">
<%@ include file="fragments/topbar.jspf" %>
<main class="search-content cn2-search">
    <% if (error != null) { %>
    <div class="error-box" role="alert"><%= HTMLUtil.escape(error) %>
    </div>
    <a class="back-link" href="${pageContext.request.contextPath}/index.jsp?page=season-search">« Quay lại</a>
    <h2><%= HTMLUtil.escape(title) %>
    </h2>
    <% } else { %>
    <a class="back-link"
       href="${pageContext.request.contextPath}/index.jsp?page=round-search&amp;<%= HTMLUtil.escape(ctx.query()) %>">«
        Quay lại</a>
    <h2><%= HTMLUtil.escape(title) %>
    </h2>
    <div class="search-toolbar">
        <form action="${pageContext.request.contextPath}/index.jsp" method="get">
            <input type="hidden" name="page" value="match-search">
            <input type="hidden" name="flow" value="<%= ctx.version() %>">
            <input type="hidden" name="tournamentId" value="<%= ctx.tournament.getId() %>"><input type="hidden"
                                                                                                  name="seasonId"
                                                                                                  value="<%= ctx.season.getId() %>"><input
                type="hidden" name="roundId" value="<%= ctx.round.getId() %>">
            <label class="sr-only" for="key">Tìm kiếm trận đấu</label><input type="text" id="key" name="key"
                                                                             value="<%= HTMLUtil.escape(key) %>"
                                                                             placeholder="Nhập mã trận, tên CLB hoặc trạng thái...">
            <button type="submit">Tìm</button>
        </form>
    </div>
    <section class="search-results">
        <div class="table-scroll">
            <table>
                <thead>
                <tr>
                    <th>Mã trận đấu</th>
                    <th>Đội nhà</th>
                    <th>Đội khách</th>
                    <th>Ngày thi đấu</th>
                    <th>Giờ thi đấu</th>
                    <th>Trạng thái đăng kí</th>
                </tr>
                </thead>
                <tbody>
                <% for (Match item : ctx.matches) { %>
                <tr data-row-link="${pageContext.request.contextPath}/index.jsp?page=match-info&amp;<%= HTMLUtil.escape(ctx.query()) %>&amp;matchId=<%= item.getId() %>"
                    tabindex="0" role="link">
                    <td><strong><%= HTMLUtil.escape(item.getDisplayCode()) %>
                    </strong></td>
                    <% for (String sideRole : new String[]{"HOME", "AWAY"}) { %>
                    <td><% for (MatchClub club : item.getMatchClubs()) {
                        if (sideRole.equals(club.getRole())) { %><%= HTMLUtil.escape(club.getClub().getShortName()) %><% }
                    } %></td>
                    <% } %>
                    <td><%= LineupSession.date(item.getKickoffTime() == null ? null : item.getKickoffTime().toLocalDate()) %>
                    </td>
                    <td><%= item.getKickoffTime() == null ? "Chưa xác định" : item.getKickoffTime().format(java.time.format.DateTimeFormatter.ofPattern("HH'h'mm")) %>
                    </td>
                    <td><%= item.getMatchClubs().size() == 2 && item.getMatchClubs().stream().allMatch(c -> c.getMatchPlayers().stream().filter(p -> "STARTING".equals(p.getRole())).count() == 11) ? "Đã đủ đội hình" : "Chưa đủ đội hình" %>
                    </td>
                </tr>
                <% } %>
                </tbody>
            </table>
        </div>
        <% if (ctx.matches.isEmpty()) { %><p class="empty-state">Không tìm thấy trận đấu phù hợp.</p><% } %>
    </section>
    <% } %></main>
</body></html><% } %>
