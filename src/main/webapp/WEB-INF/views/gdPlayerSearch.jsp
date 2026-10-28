<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ page import="com.footballmanager.dao.PlayerDAO" %>
<%@ page import="com.footballmanager.model.*" %>
<%@ page import="jakarta.servlet.http.HttpSession" %>
<%@ page import="java.util.*" %>
<%@ page import="java.sql.SQLException" %>
<%@ page import="com.footballmanager.util.HTMLUtil" %>

<%
    response.setHeader("Cache-Control", "no-store");

    HttpSession currentSession = request.getSession(false);
    Object currentUser = currentSession == null ? null : currentSession.getAttribute("currentUser");

    if (!(currentUser instanceof Staff)) {
        response.sendError(403);
        return;
    }

    String key = request.getParameter("key");
    if (key == null) key = "";

    ArrayList<Player> listPlayer = new ArrayList<>();
    String error = null;

    try {
        PlayerDAO playerDAO = new PlayerDAO();
        listPlayer = playerDAO.searchPlayer(key);

        currentSession.setAttribute("listPlayer", listPlayer);

    } catch (SQLException e) {
        application.log("Lỗi tìm kiếm cầu thủ", e);
        currentSession.removeAttribute("listPlayer");
        error = "Không thể tải danh sách cầu thủ. Vui lòng thử lại!";
    }

    request.setAttribute("breadcrumb", "Tìm cầu thủ");
%>

<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Tìm cầu thủ - Football Manager</title>

    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/common.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/search.css">
</head>

<body class="search-page">

<%@ include file="fragments/topbar.jspf" %>

<main class="search-content">
    <a class="back-link" href="${pageContext.request.contextPath}/index.jsp?page=home">« Quay lại</a>

    <h2>Tìm cầu thủ</h2>

    <div class="search-toolbar">
        <form action="${pageContext.request.contextPath}/index.jsp" method="get">
            <input type="hidden" name="page" value="player-search">
            <input type="text" id="key" name="key" placeholder="Nhập tên hoặc mã cầu thủ..."
                   value="<%= HTMLUtil.escape(key) %>">
            <button type="submit" id="btnSearch">Tìm</button>
        </form>

        <a class="add-btn" href="${pageContext.request.contextPath}/index.jsp?page=player-add">Thêm cầu thủ mới</a>
    </div>

    <section class="search-results">
        <% if (error != null && !error.isBlank()) { %>
        <div class="error-box" role="alert"><%= HTMLUtil.escape(error) %>
        </div>
        <% } else if (listPlayer.isEmpty()) { %>
        <div class="error-box" role="alert">HTMLUtil.escape(Không tìm thấy cầu thủ phù hợp.)</div>
        <% } else { %>
        <div class="table-scroll">
            <table>
                <thead>
                <tr>
                    <th>Mã CT</th>
                    <th>Họ và tên</th>
                    <th>Ngày sinh</th>
                    <th>Quốc tịch</th>
                    <th>SĐT</th>
                    <th>Thao tác</th>
                </tr>
                </thead>
                <tbody>
                <% for (Player player : listPlayer) { %>
                <tr>
                    <td><strong><%= HTMLUtil.escape(player.getUsername()) %>
                    </strong></td>
                    <td><%= HTMLUtil.escape(player.getFullName()) %>
                    </td>
                    <td><%= player.getDob() == null ? "" : player.getDob().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) %>
                    </td>
                    <td><%= HTMLUtil.escape(player.getNationality()) %>
                    </td>
                    <td><%= HTMLUtil.escape(player.getPhone()) %>
                    </td>
                    <td>
                        <button class="edit-btn" type="button" disabled>Sửa</button>
                        <button class="delete-btn" type="button" disabled>Xoá</button>
                    </td>
                </tr>
                <% } %>
                </tbody>
            </table>
        </div>
        <% } %>
    </section>
</main>

</body>
</html>
