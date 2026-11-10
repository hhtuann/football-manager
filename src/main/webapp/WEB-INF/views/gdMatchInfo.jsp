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
        SessionContext ctx = SessionContext.get(currentSession);
        String error = null;
        String key = request.getParameter("key") == null ? "" : request.getParameter("key");
        try {
            ctx.navigate(request, 3, false);
        } catch (IllegalArgumentException ex) {
            error = ex.getMessage();
        }
        String title = "Thông tin trận đấu";
        request.setAttribute("breadcrumb", title);
        if (error == null) {
            request.setAttribute("breadcrumbParents", new String[][]{{"Tìm mùa giải", request.getContextPath() + "/index.jsp?page=" + ctx.searchPage("season") }, {"Tìm vòng đấu", request.getContextPath() + "/index.jsp?page=" + ctx.searchPage("round") + "&" + ctx.query(1)}, {"Tìm trận đấu", request.getContextPath() + "/index.jsp?page=" + ctx.searchPage("match") + "&" + ctx.query(2)}});
        }
%>
<!DOCTYPE html><html lang="vi"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
<title><%= HTMLUtil.escape(title) %> - Football Manager</title>
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/common.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/search.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/lineup.css?v=20261105-2">
<script src="${pageContext.request.contextPath}/assets/js/lineup.js?v=20261110-flash" defer></script>
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
       href="${pageContext.request.contextPath}/index.jsp?page=<%= ctx.searchPage("match") %>&amp;<%= HTMLUtil.escape(ctx.query(2)) %>">«
        Quay lại</a>
    <h2><%= HTMLUtil.escape(title) %>
    </h2>
    <div class="match-summary"><p><strong>Thời gian:</strong> <%= SessionContext.dateTime(ctx.match.getKickoffTime()) %>
    </p>
        <p><strong>Mã trận:</strong> <%= HTMLUtil.escape(ctx.match.getDisplayCode()) %>
        </p>
        <p><strong>Trạng thái:</strong> <%= HTMLUtil.escape(SessionContext.status(ctx.match.getStatus())) %>
        </p>
        <% for (MatchClub home : ctx.match.getMatchClubs()) {
            if ("HOME".equals(home.getRole())) { %><p>
            <strong>Sân:</strong> <%= HTMLUtil.escape(home.getClub().getHomeStadium() == null ? "Chưa có thông tin sân" : home.getClub().getHomeStadium().getName()) %>
        </p><% }
        } %></div>
    <% if (ctx.flash != null) { %>
    <div class="success-box" role="status" data-lineup-success><%= HTMLUtil.escape(ctx.flash) %>
    </div>
    <% ctx.flash = null;
    } %>
    <% if (ctx.locked()) { %><p class="notice" role="status">Đội hình đã khóa: chỉ được lưu khi trận chưa diễn ra, có
    giờ bắt đầu xác định trong tương lai và chưa có sự kiện.</p><% } %>
    <div class="lineup-sides">
        <% for (MatchClub side : ctx.match.getMatchClubs()) { %>
        <section class="lineup-panel"><h3><%= HTMLUtil.escape(side.getClub().getFullName()) %>
            — <%= "HOME".equals(side.getRole()) ? "đội nhà" : "đội khách" %>
        </h3>
            <p>Đã đăng kí: <%= side.getMatchPlayers().size() %> cầu thủ</p>
            <div class="search-results">
                <div class="table-scroll">
                    <table>
                        <thead>
                        <tr>
                            <th>Mã cầu thủ</th>
                            <th>Họ tên</th>
                            <th>Số áo</th>
                            <th>Vị trí</th>
                            <th>Vai trò</th>
                        </tr>
                        </thead>
                        <tbody>
                        <% for (MatchPlayer player : side.getMatchPlayers()) { %>
                        <tr>
                            <td><%= HTMLUtil.escape(player.getClubPlayer().getPlayer().getUsername()) %>
                            </td>
                            <td><%= HTMLUtil.escape(player.getClubPlayer().getPlayer().getFullName()) %>
                            </td>
                            <td><%= player.getClubPlayer().getShirtNumber() %>
                            </td>
                            <td><%= HTMLUtil.escape(player.getClubPlayer().getPlayer().getPrimaryPosition()) %>
                            </td>
                            <td><%= HTMLUtil.escape(SessionContext.role(player.getRole())) %>
                            </td>
                        </tr>
                        <% } %>
                        </tbody>
                    </table>
                </div>
            </div>
            <a class="lineup-button secondary"
               href="${pageContext.request.contextPath}/index.jsp?page=lineup-registration&amp;<%= HTMLUtil.escape(ctx.query()) %>&amp;side=<%= side.getRole() %>"><%= ctx.locked() ? "Xem danh sách cầu thủ" : "Đăng ký / thay thế đội hình" %>
            </a>
        </section>
        <% } %></div>
    <% } %></main>
</body></html><% } %>
