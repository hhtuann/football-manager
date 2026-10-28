package com.footballmanager.dao;

import com.footballmanager.model.*;

import java.sql.*;
import java.time.LocalDate;

public class UserDAO extends DAO {

    public User checkLogin(User input) throws SQLException {

        if (input == null || input.getUsername() == null || input.getPassword() == null) {
            return null;
        }


        String sql = """
                SELECT
                    u.id,
                    u.username,
                    u.full_name,
                    u.dob,
                    u.email,
                    u.phone,
                
                    m.user_id AS manager_id,
                
                    s.user_id AS staff_id,
                    s.position AS staff_position,
                
                    p.user_id AS player_id,
                    p.nationality,
                    p.primary_position,
                    p.height_cm,
                    p.weight_kg
                
                FROM users u
                
                LEFT JOIN managers m
                    ON m.user_id = u.id
                
                LEFT JOIN staff s
                    ON s.user_id = u.id
                
                LEFT JOIN players p
                    ON p.user_id = u.id
                
                WHERE u.username = ?
                  AND u.password_hash = crypt(?, u.password_hash)
                """;


        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, input.getUsername());
            ps.setString(2, input.getPassword());

            try (ResultSet rs = ps.executeQuery()) {

                if (!rs.next()) {
                    return null;
                }

                boolean isManager = rs.getObject("manager_id") != null;
                boolean isStaff = rs.getObject("staff_id") != null;
                boolean isPlayer = rs.getObject("player_id") != null;

                User user;

                if (isManager) {
                    user = new Manager();
                } else if (isStaff) {
                    Staff staff = new Staff();
                    staff.setPosition(rs.getString("staff_position"));
                    user = staff;
                } else if (isPlayer) {
                    Player player = new Player();
                    player.setNationality(rs.getString("nationality"));
                    player.setPrimaryPosition(rs.getString("primary_position"));
                    player.setHeightCm(rs.getDouble("height_cm"));
                    player.setWeightKg(rs.getDouble("weight_kg"));
                    user = player;
                } else {
                    user = new User();
                }

                user.setId(rs.getInt("id"));
                user.setUsername(rs.getString("username"));
                user.setFullName(rs.getString("full_name"));
                user.setDob(rs.getObject("dob", LocalDate.class));
                user.setEmail(rs.getString("email"));
                user.setPhone(rs.getString("phone"));

                return user;
            }
        }
    }
}
