<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ page import="com.footballmanager.dao.UserDAO" %>
<%@ page import="com.footballmanager.model.User" %>
<%@ page import="jakarta.servlet.http.HttpSession" %>
<%@ page import="java.sql.SQLException" %>

<%
    request.setCharacterEncoding("UTF-8");

    if (!"POST".equalsIgnoreCase(request.getMethod())) {
        response.sendError(405);
        return;
    }

    String username = request.getParameter("username");
    String password = request.getParameter("password");

    if (username == null || username.isBlank() || password == null || password.isBlank()) {
        request.setAttribute("error", "Vui lòng nhập đầy đủ thông tin!");
        request.getRequestDispatcher("/WEB-INF/views/gdLogin.jsp").forward(request, response);
        return;
    }

    try {
        User user = new UserDAO().checkLogin(new User(username.trim(), password));

        if (user == null) {
            request.setAttribute("error", "Sai tên người dùng hoặc mật khẩu!");
            request.getRequestDispatcher("/WEB-INF/views/gdLogin.jsp").forward(request, response);
        } else {
            HttpSession oldSession = request.getSession(false);
            if (oldSession != null) oldSession.invalidate();

            HttpSession newSession = request.getSession(true);
            newSession.setAttribute("currentUser", user);
            newSession.setMaxInactiveInterval(30 * 60);
            response.sendRedirect(response.encodeRedirectURL(request.getContextPath() + "/index.jsp"));
        }

    } catch (SQLException e) {
        application.log("Lỗi database khi đăng nhập", e);
        request.setAttribute("error", "Hệ thống đang gặp lỗi, vui lòng thử lại!");
        request.getRequestDispatcher("/WEB-INF/views/gdLogin.jsp").forward(request, response);
    }
%>
