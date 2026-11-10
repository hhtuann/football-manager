<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ page import="com.footballmanager.model.*" %>
<%@ page import="com.footballmanager.dao.EventDAO" %>
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
        Map<String, String> inputs = new LinkedHashMap<>(ctx.eventInputs);
        ctx.eventInputs.clear();
        String validation = ctx.eventError;
        ctx.eventError = null;
        try {
            ctx.requireEvents(request);
            ctx.requireOptionalSide(request);
            ctx.cacheEventPlayers(currentSession);
            new EventDAO().refreshMatch(ctx.match);
            for (String name : new String[]{"matchClubId", "eventType", "minute", "extraMinute", "primaryPlayerId", "secondaryPlayerId", "note"}) {
                String value = LineupSession.single(request, name);
                if (value != null) {
                    inputs.put(name, value);
                }
            }
        } catch (SQLException ex) {
            application.log("Lỗi tải trạng thái trận đấu", ex);
            error = "Không thể tải trạng thái trận đấu. Vui lòng thử lại.";
        } catch (IllegalArgumentException ex) {
            error = ex.getMessage();
        }
        String title = "Thêm sự kiện trận đấu";
        request.setAttribute("breadcrumb", title);
        if (error == null) {
            request.setAttribute("breadcrumbParents", new String[][]{{"Sự kiện trận đấu", request.getContextPath() + "/index.jsp?page=match-event&" + ctx.query()}});
        }
%>
<!DOCTYPE html><html lang="vi"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
<title><%= title %> - Football Manager</title>
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/common.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/search.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/lineup.css?v=20261108">
<script src="${pageContext.request.contextPath}/assets/js/events.js" defer></script>
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
       href="${pageContext.request.contextPath}/index.jsp?page=match-event&amp;<%= HTMLUtil.escape(ctx.query()) %>">«
        Quay lại sự kiện trận</a>
    <h2><%= title %> — <%= HTMLUtil.escape(ctx.match.getDisplayCode()) %>
    </h2>
    <% if (validation != null) { %>
    <div class="error-box" role="alert"><%= HTMLUtil.escape(validation) %>
    </div>
    <% } %>
    <% if ("FINISHED".equals(ctx.match.getStatus())) { %><p class="notice" role="status">Trận đấu đã kết thúc; không thể
    thêm sự kiện.</p>
    <% } else if (ctx.listPlayerHome.isEmpty() || ctx.listPlayerAway.isEmpty()) { %><p class="notice" role="status">Chưa
    đủ đội hình hai đội. Vui lòng đăng ký đội hình trong chức năng đăng ký cầu thủ trước khi thêm sự kiện.</p>
    <% } else {
        String selectedSide = inputs.getOrDefault("matchClubId", String.valueOf(ctx.match.getMatchClubs().get(0).getId()));
        String selectedType = inputs.getOrDefault("eventType", "NORMAL");
        String primaryId = inputs.getOrDefault("primaryPlayerId", "");
        boolean hasRelated = Arrays.asList("NORMAL", "FOUL", "SUBSTITUTION").contains(selectedType);
    %>
    <form class="event-form" action="${pageContext.request.contextPath}/doAddEvent.jsp" method="post" id="event-form">
        <%@ include file="fragments/eventContext.jspf" %>
        <input type="hidden" name="page" value="event-form">
        <div class="event-fields">
            <label for="matchClubId">CLB</label><select id="matchClubId" name="matchClubId" required>
            <% for (MatchClub club : ctx.match.getMatchClubs()) { %>
            <option
                value="<%= club.getId() %>" <%= selectedSide.equals(String.valueOf(club.getId())) ? "selected" : "" %>><%= HTMLUtil.escape(club.getClub().getFullName()) %>
            </option>
            <% } %></select>
            <label for="eventType">Loại sự kiện</label><select id="eventType" name="eventType" required>
            <%
                String[][] types = {{"NORMAL", "Bàn thắng thường"}, {"PENALTY", "Bàn phạt đền"}, {"OWN", "Phản lưới nhà"}, {"FOUL", "Phạm lỗi"}, {"YELLOW_CARD", "Thẻ vàng"}, {"RED_CARD", "Thẻ đỏ"}, {"SUBSTITUTION", "Thay người"}};
                for (String[] type : types) { %>
            <option value="<%= type[0] %>" <%= type[0].equals(selectedType) ? "selected" : "" %>><%= type[1] %>
            </option>
            <% } %></select>
            <label for="minute">Phút thi đấu</label><input id="minute" type="number" name="minute" min="0" step="1"
                                                           required
                                                           value="<%= HTMLUtil.escape(inputs.getOrDefault("minute", "0")) %>">
            <label for="extraMinute">Phút bù giờ</label><input id="extraMinute" type="number" name="extraMinute" min="0"
                                                               step="1" required
                                                               value="<%= HTMLUtil.escape(inputs.getOrDefault("extraMinute", "0")) %>">
            <label for="primaryPlayerId">Cầu thủ chính / người ra sân</label><select id="primaryPlayerId"
                                                                                     name="primaryPlayerId" required>
            <option value="">Chọn cầu thủ</option>
            <% for (MatchClub club : ctx.match.getMatchClubs()) { %>
            <% for (MatchPlayer player : ctx.eventPlayers(club)) { %>
            <option data-side="<%= club.getId() %>" data-role="<%= HTMLUtil.escape(player.getRole()) %>"
                    value="<%= player.getId() %>"
                <%= primaryId.equals(String.valueOf(player.getId())) ? "selected" : "" %>
                <%= selectedSide.equals(String.valueOf(club.getId())) ? "" : "hidden disabled" %>>
                <%= HTMLUtil.escape(LineupSession.playerName(player)) %>
                — <%= HTMLUtil.escape(LineupSession.role(player.getRole())) %>
            </option>
            <% } %>
            <% } %>
        </select>
            <label for="secondaryPlayerId"
                   id="related-label"><%= "FOUL".equals(selectedType) ? "Cầu thủ bị phạm lỗi" : "SUBSTITUTION".equals(selectedType) ? "Cầu thủ vào sân" : "Cầu thủ kiến tạo (không bắt buộc)" %>
            </label>
            <select id="secondaryPlayerId" name="secondaryPlayerId" <%= hasRelated ? "" : "disabled" %>>
                <option value="">Không chọn</option>
                <% for (MatchClub club : ctx.match.getMatchClubs()) { %>
                <%
                    for (MatchPlayer player : ctx.eventPlayers(club)) {
                        boolean sameSide = selectedSide.equals(String.valueOf(club.getId()));
                        boolean available = hasRelated && !primaryId.equals(String.valueOf(player.getId())) && ("FOUL".equals(selectedType) ? !sameSide : sameSide) && (!"SUBSTITUTION".equals(selectedType) || "SUBSTITUTE".equals(player.getRole()));
                %>
                <option data-side="<%= club.getId() %>" data-role="<%= HTMLUtil.escape(player.getRole()) %>"
                        value="<%= player.getId() %>"
                    <%= inputs.getOrDefault("secondaryPlayerId", "").equals(String.valueOf(player.getId())) ? "selected" : "" %>
                    <%= available ? "" : "hidden disabled" %>>
                    <%= HTMLUtil.escape(LineupSession.playerName(player)) %>
                    — <%= HTMLUtil.escape(LineupSession.role(player.getRole())) %>
                </option>
                <% } %>
                <% } %>
            </select>
            <label for="note">Ghi chú</label><textarea id="note" name="note"
                                                       rows="3"><%= HTMLUtil.escape(inputs.getOrDefault("note", "")) %></textarea>
        </div>
        <p class="muted">Cầu thủ chính phải đang trên sân tại thời điểm sự kiện. Kiến tạo cùng đội và khác người ghi
            bàn; nạn nhân thuộc đối phương; người vào sân là dự bị cùng đội, chưa từng vào sân. Máy chủ kiểm tra toàn bộ
            diễn biến theo phút và phút bù giờ.</p>
        <div class="lineup-actions">
            <button type="submit">Lưu sự kiện</button>
            <button type="submit" name="preview" value="true" formnovalidate class="secondary">Cập nhật lựa chọn CLB /
                loại
            </button>
        </div>
    </form>
    <% }
    } %></main>
</body></html><% } %>
