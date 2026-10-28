package com.footballmanager.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DAO {

    protected Connection getConnection() throws SQLException {

        String url = System.getenv("JDBC_URL");
        String username = System.getenv("DB_USER");
        String password = System.getenv("DB_PASSWORD");

        if (url == null || url.isBlank()) {
            throw new SQLException("Chưa cấu hình JDBC_URL");
        }

        if (username == null || username.isBlank()) {
            throw new SQLException("Chưa cấu hình DB_USER");
        }

        if (password == null || password.isBlank()) {
            throw new SQLException("Chưa cấu hình DB_PASSWORD");
        }

        // Tomcat's initial JDBC scan does not see drivers in WEB-INF/lib.
        // Load with the web application's class loader before using DriverManager.
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("Không tìm thấy PostgreSQL JDBC driver trong ứng dụng", e);
        }

        return DriverManager.getConnection(url, username, password);
    }
}
