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
            ctx.navigate(request, 0, SessionContext.single(request, "page").endsWith("-events"));
            key = SessionContext.single(request, "key");
            if (key == null) {
                key = "";
            } else if (key.isEmpty()) {
                response.sendRedirect(request.getContextPath() + "/index.jsp?page=" + ctx.searchPage("season") + "");
                return;
            }
            ctx.clearSeason();
            ctx.seasons = new ArrayList<>();
            ctx.tournament = new TournamentDAO().getTournament();
            ctx.seasons = new SeasonDAO().searchSeason(ctx.tournament, key);
            ctx.changed();
        } catch (SQLException ex) {
            application.log("Lỗi tìm mùa giải", ex);
            error = "Không thể tải mùa giải. Vui lòng thử lại.";
        } catch (IllegalArgumentException ex) {
            error = ex.getMessage();
        }
        String title = "Tìm mùa giải";
        request.setAttribute("breadcrumb", title);
        if (error == null) {
            request.setAttribute("breadcrumbParents", new String[][]{});
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
<main class="search-content cn2-search">
    <% if (error != null) { %>
    <div class="error-box" role="alert"><%= HTMLUtil.escape(error) %>
    </div>
    <a class="back-link"
       href="${pageContext.request.contextPath}/index.jsp?page=<%= ctx.searchPage("season") %>">« Quay lại</a>
    <h2><%= HTMLUtil.escape(title) %>
    </h2>
    <% } else { %>
    <a class="back-link" href="${pageContext.request.contextPath}/index.jsp?page=home">« Quay lại</a>
    <h2><%= HTMLUtil.escape(title) %>
    </h2>
    <div class="search-toolbar">
        <form action="${pageContext.request.contextPath}/index.jsp" method="get">
            <input type="hidden" name="page" value="<%= ctx.searchPage("season") %>">
            <label class="sr-only" for="key">Tìm kiếm mùa giải</label><input type="text" id="key" name="key"
                                                                             value="<%= HTMLUtil.escape(key) %>"
                                                                             placeholder="Nhập tên, năm hoặc trạng thái mùa giải...">
            <button type="submit">Tìm</button>
        </form>
    </div>
    <section class="search-results">
        <div class="table-scroll">
            <table>
                <thead>
                <tr>
                    <th>Mã mùa giải</th>
                    <th>Tên mùa giải</th>
                    <th>Trạng thái</th>
                </tr>
                </thead>
                <tbody>
                <% for (Season item : ctx.seasons) { %>
                <tr data-row-link="${pageContext.request.contextPath}/index.jsp?page=<%= ctx.searchPage("round") %>&amp;season=VLE<%= item.getYear() %>"
                    tabindex="0" role="link">
                    <td><strong>VLE<%= item.getYear() %>
                    </strong></td>
                    <td><%= HTMLUtil.escape(item.getName()) %>
                    </td>
                    <td><%= HTMLUtil.escape(item.getStatus()) %>
                    </td>
                </tr>
                <% } %>
                </tbody>
            </table>
        </div>
        <% if (ctx.seasons.isEmpty()) { %><p class="empty-state">Không tìm thấy mùa giải phù hợp.</p><% } %>
    </section>
    <% } %></main>
</body></html><% } %>
