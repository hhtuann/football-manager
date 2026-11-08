<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ page import ="com.footballmanager.model.*" %>
<%@ page import ="com.footballmanager.dao.*" %>
<%@ page import ="com.footballmanager.util.*" %>
<%@ page import ="jakarta.servlet.http.HttpSession" %>
<%@ page import ="java.util.*,java.sql.SQLException" %>

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
            ctx.requireVersion(request);
            if (ctx.tournament == null || LineupSession.id(request, "tournamentId") != ctx.tournament.getId())
                throw new IllegalArgumentException("Giải đấu không hợp lệ.");
            if (request.getParameter("roundId") != null) ctx.requireRound(request);
            if (request.getParameter("matchId") != null) ctx.requireMatch(request);
            ctx.requireOptionalSide(request);
            int selectedId = LineupSession.id(request, "seasonId");
            Season selected = null;
            for (Season item : ctx.seasons) if (item.getId() == selectedId) selected = item;
            if (selected == null) throw new IllegalArgumentException("Mùa giải không thuộc kết quả tìm kiếm hiện tại.");
            if (ctx.season != selected || !ctx.version().equals(LineupSession.single(request, "ready"))) {
                ctx.clearRound();
                ctx.season = selected;
                ctx.rounds = new ArrayList<>();
                ctx.changed();
                ctx.rounds = new RoundDAO().searchRound(ctx.season, key);
                response.sendRedirect(request.getContextPath() + "/index.jsp?page=round-search&" + ctx.query() + "&ready=" + ctx.version() + "&key=" + java.net.URLEncoder.encode(key, java.nio.charset.StandardCharsets.UTF_8));
                return;
            }
        } catch (SQLException ex) {
            application.log("Lỗi tìm vòng đấu", ex);
            error = "Không thể tải vòng đấu. Vui lòng thử lại.";
        } catch (IllegalArgumentException ex) {
            error = ex.getMessage();
        }
        String title = "Tìm vòng đấu";
        request.setAttribute("breadcrumb", title);
        if (error == null) request.setAttribute("breadcrumbParents", new String[][]{
                {"Tìm mùa giải", request.getContextPath() + "/index.jsp?page=season-search&" + ctx.query()}
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
    <a class="back-link" href="${pageContext.request.contextPath}/index.jsp?page=season-search">« Quay lại</a>
    <h2><%= HTMLUtil.escape(title) %>
    </h2>
    <div class="search-toolbar">
        <form action="${pageContext.request.contextPath}/index.jsp" method="get">
            <input type="hidden" name="page" value="round-search">
            <input type="hidden" name="flow" value="<%= ctx.version() %>">
            <input type="hidden" name="tournamentId" value="<%= ctx.tournament.getId() %>"><input type="hidden"
                                                                                                  name="seasonId"
                                                                                                  value="<%= ctx.season.getId() %>">
            <label class="sr-only" for="key">Tìm kiếm vòng đấu</label><input type="text" id="key" name="key"
                                                                             value="<%= HTMLUtil.escape(key) %>"
                                                                             placeholder="Nhập mã hoặc tên vòng đấu...">
            <button type="submit">Tìm</button>
        </form>
    </div>
    <section class="search-results">
        <div class="table-scroll">
            <table>
                <thead>
                <tr>
                    <th>Mã vòng đấu</th>
                    <th>Tên vòng đấu</th>
                    <th>Thời gian bắt đầu</th>
                    <th>Thời gian kết thúc</th>
                    <th>Trạng thái</th>
                </tr>
                </thead>
                <tbody>
                <% for (Round item : ctx.rounds) { %>
                <tr data-row-link="${pageContext.request.contextPath}/index.jsp?page=match-search&amp;<%= HTMLUtil.escape(ctx.query()) %>&amp;roundId=<%= item.getId() %>"
                    tabindex="0" role="link">
                    <td><strong><%= HTMLUtil.escape(item.getDisplayCode()) %>
                    </strong></td>
                    <td><%= HTMLUtil.escape(item.getDisplayName()) %>
                    </td>
                    <td><%= LineupSession.date(item.getStartTime() == null ? null : item.getStartTime().toLocalDate()) %>
                    </td>
                    <td><%= LineupSession.date(item.getEndTime() == null ? null : item.getEndTime().toLocalDate()) %>
                    </td>
                    <td><%= HTMLUtil.escape(item.getStatus()) %>
                    </td>
                </tr>
                <% } %>
                </tbody>
            </table>
        </div>
        <% if (ctx.rounds.isEmpty()) { %><p class="empty-state">Không tìm thấy vòng đấu phù hợp.</p><% } %>
    </section>
    <% } %></main>
</body></html><% } %>
