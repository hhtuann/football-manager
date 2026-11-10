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
        try {
            ctx.requireRound(request);
            if (!ctx.eventMode()) {
                throw new IllegalArgumentException("Vui lòng mở chức năng cập nhật kết quả từ trang chủ.");
            }
            int selectedId = LineupSession.id(request, "matchId");
            Match selected = null;
            for (Match item : ctx.matches) {
                if (item.getId() == selectedId) {
                    selected = item;
                }
            }
            if (selected == null) {
                throw new IllegalArgumentException("Trận đấu không thuộc kết quả tìm kiếm hiện tại.");
            }
            ctx.requireOptionalSide(request);
            if (ctx.match != selected) {
                ctx.clearMatch();
                ctx.match = selected;
                ctx.changed();
                ctx.cacheEventPlayers(currentSession);
                response.sendRedirect(request.getContextPath() + "/index.jsp?page=match-event&" + ctx.query());
                return;
            }
            ctx.requireEvents(request);
            ctx.cacheEventPlayers(currentSession);
            ctx.events = new EventDAO().getEventList(ctx.match.getId());
            ctx.match.setEvents(ctx.events);
            new EventDAO().refreshMatch(ctx.match);
        } catch (SQLException ex) {
            application.log("Lỗi tải sự kiện trận đấu", ex);
            error = "Không thể tải diễn biến trận đấu. Vui lòng thử lại.";
        } catch (IllegalArgumentException ex) {
            error = ex.getMessage();
        }
        String title = "Sự kiện trận đấu";
        request.setAttribute("breadcrumb", title);
        if (error == null) {
            request.setAttribute("breadcrumbParents", new String[][]{{"Tìm mùa giải", request.getContextPath() + "/index.jsp?page=season-search&" + ctx.query()}, {"Tìm vòng đấu", request.getContextPath() + "/index.jsp?page=round-search&" + ctx.query()}, {"Tìm trận đấu", request.getContextPath() + "/index.jsp?page=match-search&" + ctx.query()}});
        }
%>
<!DOCTYPE html><html lang="vi"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
<title><%= title %> - Football Manager</title>
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/common.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/search.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/lineup.css?v=20261108">
</head><body class="search-page">
<%@ include file="fragments/topbar.jspf" %>
<main class="search-content lineup-content">
    <% if (error != null) { %>
    <div class="error-box" role="alert"><%= HTMLUtil.escape(error) %>
    </div>
    <a class="back-link" href="${pageContext.request.contextPath}/index.jsp?page=season-search&amp;mode=events">« Tìm
        lại mùa giải</a>
    <% } else { %>
    <a class="back-link"
       href="${pageContext.request.contextPath}/index.jsp?page=match-search&amp;<%= HTMLUtil.escape(ctx.query()) %>">«
        Quay lại danh sách trận</a>
    <h2><%= title %> — <%= HTMLUtil.escape(ctx.match.getDisplayCode()) %>
    </h2>
    <div class="match-summary"><p><strong>Thời gian:</strong> <%= LineupSession.dateTime(ctx.match.getKickoffTime()) %>
    </p>
        <p><strong>Trạng thái:</strong> <%= HTMLUtil.escape(LineupSession.status(ctx.match.getStatus())) %>
        </p></div>
    <div class="event-score" aria-label="Tỷ số trận đấu">
        <% for (String role : new String[]{"HOME", "AWAY"}) { %>
        <% for (MatchClub club : ctx.match.getMatchClubs()) { %>
        <% if (role.equals(club.getRole())) { %>
        <section>
            <h3><%= HTMLUtil.escape(club.getClub().getFullName()) %>
            </h3>
            <p><%= "HOME".equals(role) ? "Đội nhà" : "Đội khách" %>
            </p>
            <strong><%= club.getScore() %>
            </strong>
        </section>
        <% } %>
        <% } %>
        <% } %>
    </div>
    <% if (ctx.flash != null) { %>
    <div class="success-box" role="status"><%= HTMLUtil.escape(ctx.flash) %>
    </div>
    <% ctx.flash = null;
    } %>
    <% if (ctx.eventError != null) { %>
    <div class="error-box" role="alert"><%= HTMLUtil.escape(ctx.eventError) %>
    </div>
    <% ctx.eventError = null;
    } %>
    <% if ("FINISHED".equals(ctx.match.getStatus())) { %>
    <p class="notice" role="status">Trận đấu đã kết thúc. Kết quả cuối cùng được hiển thị ở trên; không thể thêm sự
        kiện.</p>
    <% } else { %>
    <div class="lineup-actions">
        <a class="lineup-button"
           href="${pageContext.request.contextPath}/index.jsp?page=event-form&amp;<%= HTMLUtil.escape(ctx.query()) %>">Thêm
            sự kiện</a>
        <form action="${pageContext.request.contextPath}/doEndMatch.jsp" method="post">
            <%@ include file="fragments/eventContext.jspf" %>
            <button type="submit">Kết thúc trận đấu</button>
        </form>
    </div>
    <p class="muted">Chỉ ghi sự kiện và kết thúc khi cả hai đội có đội hình hợp lệ. Tỷ số được tính từ các bàn thắng;
        không nhập tổng thủ công.</p>
    <% } %>
    <section class="search-results">
        <div class="table-scroll event-table">
            <table>
                <thead>
                <tr>
                    <th>Phút</th>
                    <th>CLB</th>
                    <th>Sự kiện</th>
                    <th>Cầu thủ chính</th>
                    <th>Cầu thủ liên quan</th>
                    <th>Ghi chú</th>
                </tr>
                </thead>
                <tbody>
                <% for (Event event : ctx.events) {
                    String clubName = "—";
                    for (MatchClub club : ctx.match.getMatchClubs()) {
                        for (MatchPlayer player : club.getMatchPlayers()) {
                            if (player.getId() == event.getPrimaryPlayer().getId()) {
                                clubName = club.getClub().getShortName();
                            }
                        }
                    }
                %>
                <tr>
                    <td><%= event.getMinute() %><%= event.getExtraMinute() > 0 ? "+" + event.getExtraMinute() : "" %>′
                    </td>
                    <td><%= HTMLUtil.escape(clubName) %>
                    </td>
                    <td><%= HTMLUtil.escape(LineupSession.eventLabel(event)) %>
                    </td>
                    <td><%= HTMLUtil.escape(LineupSession.playerName(event.getPrimaryPlayer())) %>
                    </td>
                    <td><%= HTMLUtil.escape(LineupSession.playerName(LineupSession.relatedPlayer(event))) %>
                    </td>
                    <td><%= HTMLUtil.escape(event.getNote()) %>
                    </td>
                </tr>
                <% } %>
                </tbody>
            </table>
        </div>
        <% if (ctx.events.isEmpty()) { %><p class="empty-state">Chưa có sự kiện. Tỷ số hiện tại là 0–0.</p><% } %>
    </section>
    <% } %></main>
</body></html><% } %>
