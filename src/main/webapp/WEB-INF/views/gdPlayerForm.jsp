<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ page import="com.footballmanager.model.*" %>
<%@ page import="com.footballmanager.util.HTMLUtil" %>
<%@ page import="jakarta.servlet.http.HttpSession" %>
<%@ page import="java.util.ArrayList" %>

<%
    HttpSession currentSession = request.getSession(false);
    Object currentUser = currentSession == null ? null : currentSession.getAttribute("currentUser");

    if (!(currentUser instanceof Staff)) {
        response.sendError(403);
        return;
    }

    boolean isEditMode = "player-edit".equals(request.getParameter("page"));
    Player selectedPlayer = null, createdPlayer = null, updatedPlayer = null;

    if (isEditMode) {
        int playerId;

        try {
            playerId = Integer.parseInt(request.getParameter("id"));
            if (playerId <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            response.sendError(400, "ID cầu thủ không hợp lệ!");
            return;
        }

        ArrayList<?> listPlayer = (ArrayList<?>) currentSession.getAttribute("listPlayer");
        if (listPlayer != null) {
            for (Object item : listPlayer) {
                if (item instanceof Player player && player.getId() == playerId) {
                    selectedPlayer = player;
                    currentSession.setAttribute("selectedPlayer", selectedPlayer);
                    break;
                }
            }
        }

        if (selectedPlayer == null) {
            response.sendError(404, "Không tìm thấy cầu thủ!");
            return;
        }

        updatedPlayer = (Player) currentSession.getAttribute("updatedPlayer");
        currentSession.removeAttribute("updatedPlayer");
    } else {
        createdPlayer = (Player) currentSession.getAttribute("createdPlayer");
        currentSession.removeAttribute("createdPlayer");
    }

    String formFullName = selectedPlayer == null ? "" : selectedPlayer.getFullName();
    String formDob = selectedPlayer == null || selectedPlayer.getDob() == null ? "" : selectedPlayer.getDob().toString();
    String formNationality = selectedPlayer == null ? "" : selectedPlayer.getNationality();
    String formPosition = selectedPlayer == null ? "" : selectedPlayer.getPrimaryPosition();
    String formEmail = selectedPlayer == null ? "" : selectedPlayer.getEmail();
    String formPhone = selectedPlayer == null ? "" : selectedPlayer.getPhone();
    String formHeight = selectedPlayer == null ? "" : String.valueOf(selectedPlayer.getHeightCm());
    String formWeight = selectedPlayer == null ? "" : String.valueOf(selectedPlayer.getWeightKg());

    String error = (String) request.getAttribute("error");

    if (error == null) {
        error = (String) currentSession.getAttribute("error");
    }

    currentSession.removeAttribute("error");

    request.setAttribute("breadcrumbParents", new String[][]{
            {"Tìm cầu thủ", request.getContextPath() + "/index.jsp?page=player-search"}
    });
    request.setAttribute("breadcrumb", isEditMode ? "Sửa cầu thủ" : "Thêm cầu thủ");
%>

<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title><%= isEditMode ? "Sửa cầu thủ" : "Thêm cầu thủ" %> - Football Manager</title>

    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/common.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/form.css">
    <script src="${pageContext.request.contextPath}/assets/js/form.js" defer></script>
</head>

<body class="form-page">

<%@ include file="fragments/topbar.jspf" %>

<main class="form-content">
    <a class="back-link" href="${pageContext.request.contextPath}/index.jsp?page=player-search">« Quay lại</a>

    <h2><%= isEditMode ? "Sửa thông tin cầu thủ" : "Thêm cầu thủ mới" %>
    </h2>

    <% if (error != null && !error.isBlank()) { %>
    <div class="error-box" role="alert"><%= HTMLUtil.escape(error) %>
    </div>
    <% } %>

    <form id="player-form"
          action="${pageContext.request.contextPath}/<%= isEditMode ? "doEditPlayer.jsp" : "doAddPlayer.jsp" %>"
          method="post">
        <div class="form-grid">
            <div class="form-field">
                <label for="full-name">Họ và tên <span>*</span></label>
                <input type="text" id="full-name" name="full-name" value="<%= HTMLUtil.escape(formFullName) %>"
                       maxlength="150" required>
            </div>

            <div class="form-field">
                <label for="dob">Ngày sinh <span>*</span></label>
                <input type="date" id="dob" name="dob" value="<%= HTMLUtil.escape(formDob) %>" required>
            </div>

            <div class="form-field">
                <label for="nationality">Quốc tịch <span>*</span></label>
                <input type="text" id="nationality" name="nationality" value="<%= HTMLUtil.escape(formNationality) %>"
                       maxlength="100" required>
            </div>

            <div class="form-field">
                <label for="primary-position">Vị trí <span>*</span></label>
                <select id="primary-position" name="primary-position" required>
                    <option value="">-- Chọn vị trí --</option>
                    <option value="Thủ môn" <%= "Thủ môn".equals(formPosition) ? "selected" : "" %>>Thủ môn</option>
                    <option value="Trung vệ" <%= "Trung vệ".equals(formPosition) ? "selected" : "" %>>Trung vệ</option>
                    <option value="Hậu vệ trái" <%= "Hậu vệ trái".equals(formPosition) ? "selected" : "" %>>Hậu vệ
                        trái
                    </option>
                    <option value="Hậu vệ phải" <%= "Hậu vệ phải".equals(formPosition) ? "selected" : "" %>>Hậu vệ
                        phải
                    </option>
                    <option value="Tiền vệ" <%= "Tiền vệ".equals(formPosition) ? "selected" : "" %>>Tiền vệ</option>
                    <option value="Tiền vệ công" <%= "Tiền vệ công".equals(formPosition) ? "selected" : "" %>>Tiền vệ
                        công
                    </option>
                    <option value="Tiền đạo" <%= "Tiền đạo".equals(formPosition) ? "selected" : "" %>>Tiền đạo</option>
                </select>
            </div>

            <div class="form-field">
                <label for="email">Email <span>*</span></label>
                <input type="email" id="email" name="email" value="<%= HTMLUtil.escape(formEmail) %>" maxlength="254"
                       required>
            </div>

            <div class="form-field">
                <label for="phone">Số điện thoại <span>*</span></label>
                <input type="tel" id="phone" name="phone" value="<%= HTMLUtil.escape(formPhone) %>" maxlength="20"
                       required>
            </div>

            <div class="form-field">
                <label for="height-cm">Chiều cao (cm) <span>*</span></label>
                <input type="number" id="height-cm" name="height-cm" value="<%= formHeight %>" min="0.1" step="0.1"
                       required>
            </div>

            <div class="form-field">
                <label for="weight-kg">Cân nặng (kg) <span>*</span></label>
                <input type="number" id="weight-kg" name="weight-kg" value="<%= formWeight %>" min="0.1" step="0.1"
                       required>
            </div>
        </div>

        <div class="form-actions">
            <button class="save-btn" type="submit"><%= isEditMode ? "Lưu thay đổi" : "Lưu cầu thủ" %>
            </button>
        </div>
    </form>
</main>

<% if (createdPlayer != null) { %>
<dialog id="result-dialog" class="result-dialog" data-clear-on-close="true">
    <h3>Thêm cầu thủ thành công!</h3>

    <p>Họ và tên: <strong><%= HTMLUtil.escape(createdPlayer.getFullName()) %>
    </strong></p>
    <p>Tài khoản: <strong><%= HTMLUtil.escape(createdPlayer.getUsername()) %>
    </strong></p>
    <p>Mật khẩu mặc định: <strong>player@123</strong></p>

    <p class="result-dialog-note">Vui lòng bàn giao thông tin đăng nhập cho cầu thủ.</p>

    <form method="dialog" class="result-dialog-actions">
        <button type="submit" class="save-btn">OK</button>
    </form>
</dialog>
<% } %>


<% if (updatedPlayer != null) { %>
<dialog id="result-dialog" class="result-dialog">
    <h3>Cập nhật cầu thủ thành công!</h3>

    <p>Họ và tên: <strong><%= HTMLUtil.escape(updatedPlayer.getFullName()) %>
    </strong></p>
    <p>Tài khoản: <strong><%= HTMLUtil.escape(updatedPlayer.getUsername()) %>
    </strong></p>

    <p class="result-dialog-note">Thông tin cầu thủ đã được cập nhật thành công.</p>

    <form method="dialog" class="result-dialog-actions">
        <button type="submit" class="save-btn">OK</button>
    </form>
</dialog>
<% } %>

</body>
</html>
