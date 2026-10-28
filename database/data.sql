-- ==========================================
-- FOOTBALL MANAGER - SAMPLE DATA
-- ==========================================

-- Hỗ trợ mã hóa mật khẩu bằng BCrypt
CREATE EXTENSION IF NOT EXISTS pgcrypto;

BEGIN;

-- ==========================================
-- 1. USERS
-- ==========================================

INSERT INTO users (username,
                   password_hash,
                   full_name,
                   dob,
                   email,
                   phone)
VALUES ('QL001',
        crypt('manager@123', gen_salt('bf', 12)),
        'Chu Đình Nghiêm',
        '1972-09-21',
        'nghiemcd@football.example',
        '0900000001'),
       ('NV001',
        crypt('staff@123', gen_salt('bf', 12)),
        'Lê Văn An',
        '1996-03-15',
        'anlv@football.example',
        '0900000002'),
       ('CT001',
        crypt('player@123', gen_salt('bf', 12)),
        'Trần Văn Công',
        '1999-01-20',
        'congtv@football.example',
        '0900000003'),
       ('khangia',
        crypt('spectator@123', gen_salt('bf', 12)),
        'Khán giả',
        '2003-05-18',
        'khangia@football.example',
        '0900000004')
ON CONFLICT (username) DO NOTHING;

-- ==========================================
-- 2. MANAGERS
-- ==========================================

INSERT INTO managers (user_id)
SELECT id
FROM users
WHERE username = 'nghiemcd'
ON CONFLICT DO NOTHING;

-- ==========================================
-- 3. STAFF
-- ==========================================

INSERT INTO staff (user_id, position)
SELECT id,
       'Nhân viên Ban tổ chức'
FROM users
WHERE username = 'anlv'
ON CONFLICT DO NOTHING;

-- ==========================================
-- 4. PLAYERS
-- ==========================================

INSERT INTO players (user_id,
                     nationality,
                     primary_position,
                     height_cm,
                     weight_kg)
SELECT id,
       'Việt Nam',
       'Thủ môn',
       180.0,
       75.0
FROM users
WHERE username = 'CT001'
ON CONFLICT DO NOTHING;

COMMIT;
