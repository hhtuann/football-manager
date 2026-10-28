
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ page import="com.footballmanager.model.*" %>
<%@ page import="jakarta.servlet.http.HttpSession" %>

<%!
    private String escapeHomeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
%>

<%
    response.setHeader("Cache-Control", "no-store");

    HttpSession currentSession = request.getSession(false);
    Object currentUser = currentSession == null ? null : currentSession.getAttribute("currentUser");

    if (!(currentUser instanceof User)) {
        response.sendRedirect(request.getContextPath() + "/index.jsp");
    } else {
        User user = (User) currentUser;
%>

<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Trang chủ - Football Manager</title>

    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/common.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/home.css">
</head>

<body>
<%@ include file="fragments/topbar.jspf" %>

<main class="home-content">
    <section class="welcome">
        <h2>Xin chào, <%= escapeHomeHtml(user.getFullName()) %>!</h2>
        <p>Chào mừng bạn đến với hệ thống quản lý giải bóng đá vô địch quốc gia.</p>
    </section>

    <section class="functions">
        <h3>Chức năng của bạn</h3>

        <div class="function-grid">
            <% if (user instanceof Staff) { %>
            <button class="function-btn" disabled>Quản lý thông tin cầu thủ</button>
            <button class="function-btn" disabled>Đăng ký cầu thủ thi đấu cho trận đấu</button>
            <button class="function-btn" disabled>Cập nhật kết quả thi đấu</button>
            <% } else if (user instanceof Player) { %>
            <button class="function-btn" disabled>Quản lý thông tin cầu thủ</button>
            <% } else if (user instanceof Manager) { %>
            <button class="function-btn" disabled>Đăng ký cầu thủ thi đấu cho trận đấu</button>
            <% } else { %>
            <p>Hiện chưa có chức năng dành cho tài khoản của bạn.</p>
            <% } %>
        </div>
    </section>
</main>

</body>
</html>

<% } %>
