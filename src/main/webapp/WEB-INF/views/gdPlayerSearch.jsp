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

    String deleteResult = (String) currentSession.getAttribute("deleteResult");
    currentSession.removeAttribute("deleteResult");

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
    <script src="${pageContext.request.contextPath}/assets/js/search.js" defer></script>
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
        <div class="error-box" role="alert"><%= HTMLUtil.escape("Không tìm thấy cầu thủ phù hợp.") %>
        </div>
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
                        <a class="edit-btn"
                           href="${pageContext.request.contextPath}/index.jsp?page=player-edit&amp;id=<%= player.getId() %>">Sửa</a>
                        <button class="delete-btn" type="button"
                                data-player-id="<%= player.getId() %>"
                                data-player-name="<%= HTMLUtil.escape(player.getFullName()) %>">
                            Xóa
                        </button>

                    </td>
                </tr>
                <% } %>
                </tbody>
            </table>
        </div>
        <% } %>
    </section>
</main>

<dialog id="delete-player-dialog" class="app-dialog">
    <h3>Xác nhận xóa cầu thủ</h3>

    <p>Bạn có chắc chắn muốn xóa cầu thủ <strong id="delete-player-name"></strong>?</p>

    <p class="dialog-note">Thao tác này sẽ xóa thông tin cầu thủ và tài khoản đăng nhập tương ứng. Không thể hoàn
        tác.</p>

    <div class="dialog-actions">
        <form method="dialog">
            <button type="submit" class="dialog-cancel-btn">Hủy</button>
        </form>

        <form action="${pageContext.request.contextPath}/doDeletePlayer.jsp" method="post">
            <button id="confirm-delete-btn" class="dialog-delete-btn"
                    type="submit" name="player-id">
                Xác nhận xóa
            </button>
        </form>
    </div>
</dialog>

<% if (deleteResult != null) { %>
<dialog id="delete-result-dialog" class="app-dialog">
    <h3><%= "success".equals(deleteResult) ? "Xóa cầu thủ thành công!" : "Xóa cầu thủ thất bại!" %>
    </h3>

    <p><%= switch (deleteResult) {
        case "success" -> "Thông tin cầu thủ và tài khoản đăng nhập đã được xóa.";
        case "not-found" -> "Không tìm thấy cầu thủ cần xóa!";
        default -> "Không thể xóa cầu thủ. Vui lòng thử lại!";
    } %>
    </p>

    <form method="dialog" class="dialog-actions">
        <button type="submit" class="dialog-ok-btn">OK</button>
    </form>
</dialog>
<% } %>

</body>
</html>
