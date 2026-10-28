<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ page import="jakarta.servlet.http.HttpSession" %>

<%
    if (!"POST".equalsIgnoreCase(request.getMethod())) {
        response.setHeader("Allow", "POST");
        response.sendError(405);
    } else {
        HttpSession currentSession = request.getSession(false);
        if (currentSession != null) currentSession.invalidate();

        response.setHeader("Cache-Control", "no-store");
        response.sendRedirect(request.getContextPath() + "/index.jsp");
    }
%>
