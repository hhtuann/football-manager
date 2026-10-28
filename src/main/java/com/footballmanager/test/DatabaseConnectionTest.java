package com.footballmanager.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DatabaseConnectionTest {

    public static void main(String[] args) {

        String url = "jdbc:postgresql://localhost:5433/football_db";
        String username = "football_app";
        String password = System.getenv("DB_PASSWORD");

        if (password == null || password.isBlank()) {
            System.err.println("Chưa cấu hình DB_PASSWORD!");
            return;
        }

        String sql = """
                SELECT
                    current_database() AS db_name,
                    current_user AS db_user
                """;

        try (Connection conn = DriverManager.getConnection(
                url, username, password);
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            System.out.println("Kết nối PostgreSQL thành công!");

            if (rs.next()) {
                System.out.println("Database: " +
                        rs.getString("db_name"));

                System.out.println("User: " +
                        rs.getString("db_user"));
            }

        } catch (SQLException e) {
            System.err.println("Kết nối thất bại!");
            e.printStackTrace();
        }
    }
}
