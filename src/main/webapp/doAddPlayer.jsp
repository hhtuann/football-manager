<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ page import="com.footballmanager.model.*" %>
<%@ page import="com.footballmanager.dao.PlayerDAO" %>
<%@ page import="jakarta.servlet.http.HttpSession" %>
<%@ page import="java.time.LocalDate" %>
<%@ page import="java.time.DateTimeException" %>
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

    try {
        String fullName = request.getParameter("full-name");
        String dob = request.getParameter("dob");
        String nationality = request.getParameter("nationality");
        String primaryPosition = request.getParameter("primary-position");
        String email = request.getParameter("email");
        String phone = request.getParameter("phone");
        String heightCm = request.getParameter("height-cm");
        String weightKg = request.getParameter("weight-kg");

        if (fullName == null || fullName.isBlank() || dob == null || dob.isBlank()
                || nationality == null || nationality.isBlank()
                || primaryPosition == null || primaryPosition.isBlank()
                || email == null || email.isBlank()
                || phone == null || phone.isBlank()
                || heightCm == null || heightCm.isBlank()
                || weightKg == null || weightKg.isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập đầy đủ thông tin bắt buộc!");
        }

        Player player = new Player();
        player.setFullName(fullName.trim());
        player.setDob(LocalDate.parse(dob));
        player.setNationality(nationality.trim());
        player.setPrimaryPosition(primaryPosition.trim());
        player.setEmail(email.trim());
        player.setPhone(phone.trim());
        player.setHeightCm(Double.parseDouble(heightCm));
        player.setWeightKg(Double.parseDouble(weightKg));

        if (!Double.isFinite(player.getHeightCm()) || !Double.isFinite(player.getWeightKg())
                || player.getHeightCm() <= 0 || player.getWeightKg() <= 0) {
            throw new IllegalArgumentException("Chiều cao và cân nặng phải lớn hơn 0!");
        }

        PlayerDAO playerDAO = new PlayerDAO();
        boolean success = playerDAO.addPlayer(player);

        if (!success) {
            throw new SQLException("Không thể thêm cầu thủ!");
        }

        currentSession.setAttribute("createdPlayer", player);
        currentSession.removeAttribute("listPlayer");
        response.sendRedirect(request.getContextPath() + "/index.jsp?page=player-add");
        return;

    } catch (IllegalArgumentException | DateTimeException e) {
        request.setAttribute("error", "Dữ liệu không hợp lệ. Vui lòng kiểm tra lại!");
    } catch (SQLException e) {
        application.log("Lỗi thêm cầu thủ", e);
        request.setAttribute("error", "Không thể thêm cầu thủ. Vui lòng thử lại!");
    }

    request.getRequestDispatcher("/WEB-INF/views/gdPlayerForm.jsp").forward(request, response);
%>
