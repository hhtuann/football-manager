<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ page import="com.footballmanager.model.User" %>
<%@ page import="jakarta.servlet.http.HttpSession" %>

<%
    HttpSession currentSession = request.getSession(false);
    Object currentUser = currentSession == null ? null : currentSession.getAttribute("currentUser");

    if (currentUser instanceof User) {
        request.getRequestDispatcher("/WEB-INF/views/gdHome.jsp").forward(request, response);
    } else {
        request.getRequestDispatcher("/WEB-INF/views/gdLogin.jsp").forward(request, response);
    }
%>
