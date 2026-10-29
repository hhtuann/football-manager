<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ page import="com.footballmanager.model.*" %>
<%@ page import="com.footballmanager.dao.PlayerDAO" %>
<%@ page import="jakarta.servlet.http.HttpSession" %>
<%@ page import="java.sql.SQLException" %>

<%
    request.setCharacterEncoding("UTF-8");

    if (!"POST".equalsIgnoreCase(request.getMethod())) {
        response.sendError(405);
        return;
    }

    HttpSession currentSession = request.getSession(false);
    Object currentUser = currentSession == null ? null : currentSession.getAttribute("currentUser");

    if (!(currentUser instanceof Staff)) {
        response.sendError(403);
        return;
    }

    int playerId;

    try {
        playerId = Integer.parseInt(request.getParameter("player-id"));
        if (playerId <= 0) throw new NumberFormatException();
    } catch (NumberFormatException e) {
        response.sendError(400, "ID cầu thủ không hợp lệ!");
        return;
    }

    try {
        PlayerDAO playerDAO = new PlayerDAO();
        boolean success = playerDAO.deletePlayer(playerId);

        currentSession.setAttribute("deleteResult", success ? "success" : "not-found");

        if (success) {
            currentSession.removeAttribute("listPlayer");
        }

    } catch (SQLException e) {
        application.log("Lỗi xóa cầu thủ", e);
        currentSession.setAttribute("deleteResult", "error");
    }

    response.sendRedirect(request.getContextPath() + "/index.jsp?page=player-search");
%>
