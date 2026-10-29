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

    Player selectedPlayer = (Player) currentSession.getAttribute("selectedPlayer");

    if (selectedPlayer == null) {
        response.sendError(409, "Không tìm thấy cầu thủ đang được chỉnh sửa!");
        return;
    }

    int playerId = selectedPlayer.getId();

    try {
        String fullName = request.getParameter("full-name");
        String dob = request.getParameter("dob");
        String nationality = request.getParameter("nationality");
        String primaryPosition = request.getParameter("primary-position");
        String email = request.getParameter("email");
        String phone = request.getParameter("phone");
        String heightCm = request.getParameter("height-cm");
        String weightKg = request.getParameter("weight-kg");

        if (fullName == null || fullName.isBlank()
                || dob == null || dob.isBlank()
                || nationality == null || nationality.isBlank()
                || primaryPosition == null || primaryPosition.isBlank()
                || email == null || email.isBlank()
                || phone == null || phone.isBlank()
                || heightCm == null || heightCm.isBlank()
                || weightKg == null || weightKg.isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập đầy đủ thông tin bắt buộc!");
        }

        Player updatedPlayer = new Player();
        updatedPlayer.setId(playerId);
        updatedPlayer.setUsername(selectedPlayer.getUsername());
        updatedPlayer.setFullName(fullName.trim());
        updatedPlayer.setDob(LocalDate.parse(dob));
        updatedPlayer.setNationality(nationality.trim());
        updatedPlayer.setPrimaryPosition(primaryPosition.trim());
        updatedPlayer.setEmail(email.trim());
        updatedPlayer.setPhone(phone.trim());
        updatedPlayer.setHeightCm(Double.parseDouble(heightCm));
        updatedPlayer.setWeightKg(Double.parseDouble(weightKg));

        if (!Double.isFinite(updatedPlayer.getHeightCm()) || !Double.isFinite(updatedPlayer.getWeightKg())
                || updatedPlayer.getHeightCm() <= 0 || updatedPlayer.getWeightKg() <= 0) {
            throw new IllegalArgumentException("Chiều cao và cân nặng phải lớn hơn 0!");
        }

        PlayerDAO playerDAO = new PlayerDAO();
        boolean success = playerDAO.updatePlayer(updatedPlayer);

        if (!success) {
            throw new SQLException("Không tìm thấy bản ghi cầu thủ để cập nhật!");
        }

        selectedPlayer.setFullName(updatedPlayer.getFullName());
        selectedPlayer.setDob(updatedPlayer.getDob());
        selectedPlayer.setNationality(updatedPlayer.getNationality());
        selectedPlayer.setPrimaryPosition(updatedPlayer.getPrimaryPosition());
        selectedPlayer.setEmail(updatedPlayer.getEmail());
        selectedPlayer.setPhone(updatedPlayer.getPhone());
        selectedPlayer.setHeightCm(updatedPlayer.getHeightCm());
        selectedPlayer.setWeightKg(updatedPlayer.getWeightKg());

        currentSession.setAttribute("updatedPlayer", selectedPlayer);
        currentSession.removeAttribute("selectedPlayer");

        response.sendRedirect(request.getContextPath() + "/index.jsp?page=player-edit&id=" + playerId);
        return;

    } catch (IllegalArgumentException | DateTimeException e) {
        currentSession.setAttribute("error", "Dữ liệu không hợp lệ. Vui lòng kiểm tra lại!");
    } catch (SQLException e) {
        application.log("Lỗi cập nhật cầu thủ", e);
        request.setAttribute("error", "Không thể cập nhật cầu thủ. Vui lòng thử lại!");
    }

    request.getRequestDispatcher("/index.jsp?page=player-edit&id=" + playerId).forward(request, response);
%>
