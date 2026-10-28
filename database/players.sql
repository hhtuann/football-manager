-- Football Manager - 50 cầu thủ mẫu (CT002 - CT051)
-- Dùng để kiểm tra PlayerDAO.searchPlayer(q) với PostgreSQL.
-- Chạy sau database/schema.sql và database/data.sql.
-- An toàn khi chạy lại: không ghi đè các tài khoản/cầu thủ đã tồn tại.
-- Mật khẩu mẫu của 50 tài khoản: player@123 (CHỈ dùng ở môi trường phát triển).

CREATE EXTENSION IF NOT EXISTS pgcrypto;

BEGIN;

CREATE TEMP TABLE seed_players ON COMMIT DROP AS
SELECT *
FROM (VALUES ('CT002', 'Nguyễn Đức Anh', '2002-03-23'::date, 'ct002@football.example', '0901000002', 'Việt Nam',
              'Thủ môn', 185.0, 82.0),
             ('CT003', 'Trần Minh Quân', '2005-10-07'::date, 'ct003@football.example', '0901000003', 'Việt Nam',
              'Trung vệ', 169.0, 67.0),
             ('CT004', 'Lê Hoàng Nam', '1997-05-18'::date, 'ct004@football.example', '0901000004', 'Việt Nam',
              'Hậu vệ trái', 176.0, 75.0),
             ('CT005', 'Phạm Nhật Huy', '2000-12-02'::date, 'ct005@football.example', '0901000005', 'Việt Nam',
              'Hậu vệ phải', 183.0, 83.0),
             ('CT006', 'Vũ Tuấn Kiệt', '2003-07-13'::date, 'ct006@football.example', '0901000006', 'Việt Nam',
              'Tiền vệ', 170.0, 71.0),
             ('CT007', 'Đỗ Thành Long', '2006-02-24'::date, 'ct007@football.example', '0901000007', 'Việt Nam',
              'Tiền vệ công', 177.0, 79.0),
             ('CT008', 'Hoàng Quốc Việt', '1998-09-08'::date, 'ct008@football.example', '0901000008', 'Việt Nam',
              'Tiền đạo', 184.0, 79.0),
             ('CT009', 'Bùi Trung Hiếu', '2001-04-19'::date, 'ct009@football.example', '0901000009', 'Việt Nam',
              'Thủ môn', 192.0, 88.0),
             ('CT010', 'Nguyễn Văn Hải', '2004-11-03'::date, 'ct010@football.example', '0901000010', 'Việt Nam',
              'Trung vệ', 178.0, 75.0),
             ('CT011', 'Trần Quang Hưng', '1996-06-14'::date, 'ct011@football.example', '0901000011', 'Việt Nam',
              'Hậu vệ trái', 185.0, 83.0),
             ('CT012', 'Lê Đức Minh', '1999-01-25'::date, 'ct012@football.example', '0901000012', 'Việt Nam',
              'Hậu vệ phải', 172.0, 71.0),
             ('CT013', 'Phạm Hồng Phúc', '2002-08-09'::date, 'ct013@football.example', '0901000013', 'Việt Nam',
              'Tiền vệ', 179.0, 79.0),
             ('CT014', 'Đặng Văn Thành', '2005-03-20'::date, 'ct014@football.example', '0901000014', 'Việt Nam',
              'Tiền vệ công', 186.0, 87.0),
             ('CT015', 'Ngô Anh Tuấn', '1997-10-04'::date, 'ct015@football.example', '0901000015', 'Việt Nam',
              'Tiền đạo', 173.0, 75.0),
             ('CT016', 'Phan Thanh Bình', '2000-05-15'::date, 'ct016@football.example', '0901000016', 'Việt Nam',
              'Thủ môn', 187.0, 82.0),
             ('CT017', 'Đinh Gia Bảo', '2003-12-26'::date, 'ct017@football.example', '0901000017', 'Việt Nam',
              'Trung vệ', 187.0, 83.0),
             ('CT018', 'Nguyễn Hải Đăng', '2006-07-10'::date, 'ct018@football.example', '0901000018', 'Việt Nam',
              'Hậu vệ trái', 174.0, 71.0),
             ('CT019', 'Trần Đức Thắng', '1998-02-21'::date, 'ct019@football.example', '0901000019', 'Việt Nam',
              'Hậu vệ phải', 181.0, 79.0),
             ('CT020', 'Lê Tuấn Anh', '2001-09-05'::date, 'ct020@football.example', '0901000020', 'Việt Nam', 'Tiền vệ',
              168.0, 67.0),
             ('CT021', 'Phạm Quốc Bảo', '2004-04-16'::date, 'ct021@football.example', '0901000021', 'Việt Nam',
              'Tiền vệ công', 175.0, 75.0),
             ('CT022', 'Đỗ Quang Khải', '1996-11-27'::date, 'ct022@football.example', '0901000022', 'Việt Nam',
              'Tiền đạo', 182.0, 83.0),
             ('CT023', 'Võ Minh Trí', '1999-06-11'::date, 'ct023@football.example', '0901000023', 'Việt Nam', 'Thủ môn',
              194.0, 96.0),
             ('CT024', 'Hoàng Đức Mạnh', '2002-01-22'::date, 'ct024@football.example', '0901000024', 'Việt Nam',
              'Trung vệ', 176.0, 71.0),
             ('CT025', 'Bùi Công Danh', '2005-08-06'::date, 'ct025@football.example', '0901000025', 'Việt Nam',
              'Hậu vệ trái', 183.0, 79.0),
             ('CT026', 'Nguyễn Xuân Trường', '1997-03-17'::date, 'ct026@football.example', '0901000026', 'Việt Nam',
              'Hậu vệ phải', 170.0, 67.0),
             ('CT027', 'Trần Văn Phong', '2000-10-01'::date, 'ct027@football.example', '0901000027', 'Việt Nam',
              'Tiền vệ', 177.0, 75.0),
             ('CT028', 'Lê Hữu Nghĩa', '2003-05-12'::date, 'ct028@football.example', '0901000028', 'Việt Nam',
              'Tiền vệ công', 184.0, 83.0),
             ('CT029', 'Phạm Minh Khoa', '2006-12-23'::date, 'ct029@football.example', '0901000029', 'Việt Nam',
              'Tiền đạo', 171.0, 71.0),
             ('CT030', 'Vũ Hoàng Sơn', '1998-07-07'::date, 'ct030@football.example', '0901000030', 'Việt Nam',
              'Thủ môn', 189.0, 90.0),
             ('CT031', 'Đặng Thanh Tùng', '2001-02-18'::date, 'ct031@football.example', '0901000031', 'Việt Nam',
              'Trung vệ', 185.0, 87.0),
             ('CT032', 'Nguyễn Văn Lâm', '2004-09-02'::date, 'ct032@football.example', '0901000032', 'Việt Nam',
              'Hậu vệ trái', 172.0, 67.0),
             ('CT033', 'Trần Quốc Khánh', '1996-04-13'::date, 'ct033@football.example', '0901000033', 'Việt Nam',
              'Hậu vệ phải', 179.0, 75.0),
             ('CT034', 'Lê Đức Phát', '1999-11-24'::date, 'ct034@football.example', '0901000034', 'Việt Nam', 'Tiền vệ',
              186.0, 83.0),
             ('CT035', 'Phạm Văn Toàn', '2002-06-08'::date, 'ct035@football.example', '0901000035', 'Việt Nam',
              'Tiền vệ công', 173.0, 71.0),
             ('CT036', 'Ngô Minh Nhật', '2005-01-19'::date, 'ct036@football.example', '0901000036', 'Việt Nam',
              'Tiền đạo', 180.0, 79.0),
             ('CT037', 'Đỗ Anh Khoa', '1997-08-03'::date, 'ct037@football.example', '0901000037', 'Việt Nam', 'Thủ môn',
              184.0, 84.0),
             ('CT038', 'Võ Quốc Cường', '2000-03-14'::date, 'ct038@football.example', '0901000038', 'Việt Nam',
              'Trung vệ', 174.0, 75.0),
             ('CT039', 'Bùi Thanh Sơn', '2003-10-25'::date, 'ct039@football.example', '0901000039', 'Việt Nam',
              'Hậu vệ trái', 181.0, 83.0),
             ('CT040', 'Nguyễn Ngọc Hưng', '2006-05-09'::date, 'ct040@football.example', '0901000040', 'Việt Nam',
              'Hậu vệ phải', 168.0, 63.0),
             ('CT041', 'Trần Thành Đạt', '1998-12-20'::date, 'ct041@football.example', '0901000041', 'Việt Nam',
              'Tiền vệ', 175.0, 71.0),
             ('CT042', 'Lê Văn Khánh', '2001-07-04'::date, 'ct042@football.example', '0901000042', 'Việt Nam',
              'Tiền vệ công', 182.0, 79.0),
             ('CT043', 'Phạm Anh Duy', '2004-02-15'::date, 'ct043@football.example', '0901000043', 'Việt Nam',
              'Tiền đạo', 169.0, 67.0),
             ('CT044', 'Nguyễn Minh Triết', '1996-09-26'::date, 'ct044@football.example', '0901000044', 'Việt Nam',
              'Thủ môn', 191.0, 90.0),
             ('CT045', 'Trần Tuấn Vũ', '1999-04-10'::date, 'ct045@football.example', '0901000045', 'Việt Nam',
              'Trung vệ', 183.0, 83.0),
             ('CT046', 'Kim Min-jun', '2002-11-21'::date, 'ct046@football.example', '0901000046', 'Hàn Quốc',
              'Hậu vệ trái', 170.0, 71.0),
             ('CT047', 'Sato Haruki', '2005-06-05'::date, 'ct047@football.example', '0901000047', 'Nhật Bản',
              'Hậu vệ phải', 177.0, 79.0),
             ('CT048', 'Lucas Oliveira', '1997-01-16'::date, 'ct048@football.example', '0901000048', 'Brazil',
              'Tiền vệ', 184.0, 79.0),
             ('CT049', 'Jean Dupont', '2000-08-27'::date, 'ct049@football.example', '0901000049', 'Pháp',
              'Tiền vệ công', 171.0, 67.0),
             ('CT050', 'Somchai Prasert', '2003-03-11'::date, 'ct050@football.example', '0901000050', 'Thái Lan',
              'Tiền đạo', 178.0, 75.0),
             ('CT051', 'Chinedu Okafor', '2006-10-22'::date, 'ct051@football.example', '0901000051', 'Nigeria',
              'Thủ môn', 186.0, 84.0)) AS s (username, full_name, dob, email, phone, nationality, primary_position,
                                             height_cm, weight_kg);

-- Bảng players tham chiếu users.id nên phải thêm users trước.
INSERT INTO users (username, password_hash, full_name, dob, email, phone)
SELECT s.username, crypt('player@123', gen_salt('bf', 10)), s.full_name, s.dob, s.email, s.phone
FROM seed_players s
WHERE true
ON CONFLICT (username) DO NOTHING;

INSERT INTO players (user_id, nationality, primary_position, height_cm, weight_kg)
SELECT u.id, s.nationality, s.primary_position, s.height_cm, s.weight_kg
FROM seed_players s
         JOIN users u ON u.username = s.username
WHERE true
ON CONFLICT (user_id) DO NOTHING;

COMMIT;

-- Kiểm tra: dự kiến có 50 cầu thủ CT002-CT051 (nếu chưa có dữ liệu trùng).
SELECT COUNT(*) AS sample_player_count
FROM players p
         JOIN users u ON u.id = p.user_id
WHERE u.username ~ '^CT(00[2-9]|0[1-4][0-9]|05[01])$';
