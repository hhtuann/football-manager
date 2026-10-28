<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ page import="com.footballmanager.model.*" %>
<%@ page import="jakarta.servlet.http.HttpSession" %>

<%
    response.setHeader("Cache-Control", "no-store");

    String pageName = request.getParameter("page");
    String contextPath = request.getContextPath();

    HttpSession currentSession = request.getSession(false);
    Object currentUser = currentSession == null ? null : currentSession.getAttribute("currentUser");

    if (!(currentUser instanceof User)) {
        if (!"login".equals(pageName)) {
            response.sendRedirect(contextPath + "/index.jsp?page=login");
            return;
        }
        request.getRequestDispatcher("/WEB-INF/views/gdLogin.jsp").forward(request, response);
        return;
    }

    if (pageName == null || pageName.isBlank() || "login".equals(pageName)) {
        response.sendRedirect(contextPath + "/index.jsp?page=home");
        return;
    }

    String view = switch (pageName) {
        case "home" -> "/WEB-INF/views/gdHome.jsp";
        case "player-search" -> "/WEB-INF/views/gdPlayerSearch.jsp";
        case "player-add" -> "/WEB-INF/views/gdPlayerForm.jsp";
        default -> null;
    };

    if (view == null) {
        response.sendError(404);
        return;
    }

    boolean allowed = switch (pageName) {
        case "home" -> true;
        case "player-search" -> currentUser instanceof Staff;
        case "player-add" -> currentUser instanceof Staff;
        default -> false;
    };

    if (!allowed) {
        response.sendError(403);
        return;
    }

    request.getRequestDispatcher(view).forward(request, response);
%>
