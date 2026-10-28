package com.footballmanager.test;

import com.footballmanager.dao.UserDAO;
import com.footballmanager.model.*;

import java.sql.SQLException;

public class LoginDAOTest {

    public static void main(String[] args) {

        UserDAO dao = new UserDAO();

        // 1. Quản lý CLB
        test(dao, "nghiemcd", "manager@123", Manager.class);

        // 2. Nhân viên BTC
        test(dao, "anlv", "anlv@123", Staff.class);

        // 3. Cầu thủ
        test(dao, "CT001", "player@123", Player.class);

        // 4. Khán giả
        test(dao, "khangia", "spectator@123", User.class);

        // 5. Sai mật khẩu
        test(dao, "anlv", "wrongpassword", null);

        // 6. Tài khoản không tồn tại
        test(dao, "unknown", "password123", null);
    }

    private static void test(UserDAO dao, String username, String password, Class<? extends User> expectedClass) {
        try {
            User input = new User(username, password);
            User result = dao.checkLogin(input);

            boolean passed;

            if (expectedClass == null) {
                passed = (result == null);
            } else {
                passed = result != null && result.getClass() == expectedClass;
            }

            String expected = expectedClass == null ? "null" : expectedClass.getSimpleName();

            String actual = result == null ? "null" : result.getClass().getSimpleName();

            System.out.println("------------------------");
            System.out.println("Username: " + username);
            System.out.println("Expected: " + expected);
            System.out.println("Actual: " + actual);
            System.out.println(passed ? "PASS" : "FAIL");

            if (result != null) {
                System.out.println("Full name: " + result.getFullName());
            }

        } catch (SQLException e) {
            System.out.println("DATABASE ERROR!");
            e.printStackTrace();
        }
    }
}
