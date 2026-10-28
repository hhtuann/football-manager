
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>

<%
    String error = (String) request.getAttribute("error");
%>

<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Đăng nhập - Football Manager</title>

    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/common.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/login.css">
</head>

<body>

<main class="login-content">
    <h2>Hệ thống quản lý giải bóng đá <br> vô địch quốc gia</h2>
    <p class="subtitle">Vui lòng đăng nhập để sử dụng hệ thống.</p>

    <% if (error != null && !error.isBlank()) { %>
    <div class="error-box" role="alert"><%= error %></div>
    <% } %>

    <form action="${pageContext.request.contextPath}/doLogin.jsp" method="post">
        <label for="username">Tên người dùng</label>
        <input id="username" name="username" type="text" autocomplete="username" required>

        <label for="password">Mật khẩu</label>
        <input id="password" name="password" type="password" autocomplete="current-password" required>

        <button class="login-button" type="submit">Đăng nhập</button>
    </form>
</main>

</body>
</html>
