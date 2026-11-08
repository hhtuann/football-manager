---
title: Làm mới database demo theo ERD
status: completed
created: 2026-11-08
source: docs/brainstorm-2026-11-08-database-demo-reset.md
---

# Phạm vi đã được chốt

Được phê duyệt thực thi --auto. Giữ Java 17/JSP–DAO/PostgreSQL, các bảng hiện tại, FK và model một chiều. Giữ seasons.name theo quyết định riêng của người dùng; bỏ users.source_key, rounds.round_number, matches.match_number, match_players.is_simulated, events.is_simulated. Không thêm cột/bảng bền vững khác. provenance đã bỏ.

Bộ mới: 1 Tournament, 5 Season (mùa chính 2026/27 + bốn mùa cũ chỉ có thông tin mùa), 14 CLB, 26 vòng/182 trận mùa chính. Tên/đội hình CLB và cầu thủ lấy từ VPF; bổ sung tình huống nghiệp vụ để demo. Dịch lịch quanh ngày chạy seed. Không cảnh báo mô phỏng trên UI. Không triển khai CN3.

## Điều kiện quyết định

- Backup trước khi reset. Chuẩn bị và chứng minh bộ mới trong schema/database cô lập trước cutover.
- Reset toàn bộ dữ liệu ứng dụng, kể cả tài khoản hiện tại. Không reset Docker volume; không xóa bảng ngoài phạm vi ứng dụng.
- Giữ mật khẩu băm pgcrypto, currentUser, phân quyền Staff và CSRF; tài khoản demo rõ trong README.
- Lấy lại nguồn VPF khi thực thi; file cũ không chứng minh web hiện tại. Ghi nguồn/khác biệt trong file repo, không trong cột/bảng metadata.
- Không khôi phục cả báo cáo về Git HEAD: bản đó có các quyết định cũ khác đã được thay thế.

# 1. Kiểm kê và đóng contract schema

## Đọc và quan sát

- Đọc AGENTS và bốn báo cáo docs; đối chiếu bản Git HEAD chỉ để xác định thuộc tính ban đầu, không dùng kiến trúc/quan hệ cũ để ghi đè quyết định sau.
- Kiểm tra git status, bảo toàn thay đổi người dùng.
- Query information_schema/pg_constraint/pg_trigger/pg_views và sequence thực tế. Lập danh sách chính xác 20 bảng ứng dụng: users/managers/staff/players, tournaments/seasons/rules/rounds/matches, stadiums/clubs, match_clubs/club_players/match_players, events/goals/fouls/yellow_cards/red_cards/substitutions.
- Đối chiếu toàn bộ cột ngoài mô hình. Năm cột bị bỏ nêu trên là danh sách đã chốt; seasons.name là ngoại lệ duy nhất đã thảo luận. FK vật lý, password_hash và các quy tắc lưu vật lý không phải đối tượng ngược Java.

## Kết quả

- Một đường khởi tạo schema rõ ràng dựa trên schema.sql + football_manager_migration.sql; giữ view v_match_scores và trigger invariant đang dùng.
- Migration cutover chỉ DROP năm cột đã chốt, không CASCADE để che dependency; nếu có dependency ngoài dự kiến phải xử lý caller cụ thể trước.
- Không đổi nullability ngày sang NOT NULL chỉ vì reset. NULL vẫn hợp lệ cho dữ liệu chưa xác định; seed demo mới bố trí kickoff rõ cho ca cần thao tác.

# 2. Đồng bộ mã nguồn không phụ thuộc cột bị bỏ

## File đã xác định

- src/main/java/com/footballmanager/dao/RoundDAO.java: bỏ đọc/tìm/sắp xếp round_number.
- src/main/java/com/footballmanager/dao/MatchDAO.java: bỏ SQL match_number/round_number, cờ simulated và điều kiện NOT e.is_simulated khi tính treo giò.
- src/main/java/com/footballmanager/model/Round.java và Match.java: bỏ metadata simulated; số/mã hiển thị chỉ derived từ dữ liệu hiện có nếu cần, không tạo cột thay thế.
- gdMatchSearch.jsp, gdMatchInfo.jsp, gdLineupRegistration.jsp: bỏ toàn bộ nhãn/cảnh báo mô phỏng và lời giải thích thẻ được miễn.
- src/test/java/com/footballmanager/dao/Cn2PostgresFixture.java, Cn2LineupIntegrationTest.java: SQL fixture theo schema mới; bỏ test về miễn thẻ mô phỏng/cờ cảnh báo. Giữ invariant treo giò, quota, khóa, concurrency, rollback, hai bên độc lập.
- SeasonDAO và Season.name vẫn giữ; không phá tìm mùa theo tên.

## Contract mã vòng/trận

- Seed lưu Round.name chuẩn `Vòng 1` ... `Vòng 26`, không prefix kỹ thuật VL2627_.
- Số vòng derived từ phần số của tên chuẩn; tìm `R07` và `Vòng 7` vẫn trỏ cùng vòng. Trường hợp tên không có số phải có fallback ổn định, không lỗi parse.
- Thứ tự trận derived theo ID trong từng vòng, không theo kickoff để tránh mã đổi khi sửa lịch. SQL window row_number() phải tính trên toàn bộ vòng TRƯỚC lọc từ khóa; không tính lại số thứ tự trên tập kết quả tìm kiếm.
- UI vẫn có thể dùng R07-T01; URL/session/FK dùng ID. Seed tạo vòng tăng dần để vòng chính có ID1–26, trận theo thứ tự nguồn. Không giả định ID luôn là số vòng khi bổ sung mùa khác.
- Không thay đổi baseline/cache-before-commit, transaction/locking hay kiến trúc DAO để tiện đổi dữ liệu.

# 3. Thu thập nguồn và thiết kế bộ demo

## Đầu vào

- https://vpf.vn/season/v-league-2027/?action=calendar&pagejs=1 và trang CLB/cầu thủ liên kết.
- Các JSON/CSV database/vleague_2026_2027_* đang có: dùng đối chiếu, kiểm tra lại các trường cần thiết.
- Xác minh đủ 14 CLB/26 vòng/182 cặp đấu. Kiểm tra lại lỗi nguồn BN–ND vòng15/16; không tự sửa thành cặp khác nếu không có bằng chứng. Nếu nguồn vẫn mâu thuẫn, giữ lịch nguồn và ghi trong tài liệu ngoài database.
- Hồ sơ thiếu trường bắt buộc: ưu tiên cầu thủ có dữ liệu đủ; không đoán quốc tịch từ tên hoặc biến năm sinh thành ngày sinh chính xác. Nếu cần dữ liệu phục vụ ca demo bổ sung, tách rõ cách tạo trong file nguồn/báo cáo, không khẳng định là thông tin đã xác minh.

## Identity và tài khoản

- Reset ID và sequence có chủ đích, tạo NV001 cho Staff, QL001... cho 14 Manager, CT001... cho tập cầu thủ mới. Không mang theo 52 CT demo cũ.
- Manager kỹ thuật chỉ đại diện quan hệ CLB, không tự tuyên bố là danh tính người thật.
- Mỗi vai trò có ít nhất một tài khoản đăng nhập dùng kiểm tra quyền; khán giả User có tài khoản riêng. Tài khoản demo dùng mật khẩu băm và ghi trong README, không đọc/in secret môi trường.
- Mapping VPF → ID chỉ dùng biến/bảng TEMP trong transaction hoặc dữ liệu ngoài database; không source_key, không bảng ánh xạ bền vững. Không dùng tên cầu thủ để ghép mù quáng.

## Quy tắc lịch và trạng thái

- Seed nhận ngày mốc một lần, mặc định current_date ở múi giờ nhất quán với ứng dụng. Ghi cú pháp truyền mốc cố định để tái lập.
- Vòng1–6 đặt trước mốc, FINISHED; vòng7 đặt sau mốc ít nhất 7 ngày; vòng8–26 tiếp tục theo tuần. Khoảng thời gian khai mạc mùa điều chỉnh theo lịch demo để validation tuổi dùng đúng mùa này; các mùa cũ không chồng lên mùa chính.
- Trong mỗi vòng bố trí giờ cụ thể và đúng thứ tự; không tạo trận giả trùng để đủ ca. Season.year/name là nhãn mùa nguồn, ngày là lịch demo đã dịch; ghi rõ ngoài UI.
- Ca ONGOING dành một trận còn lại trong bối cảnh demo nếu cần, phải nhất quán với kickoff và trạng thái vòng. Không mở thêm action bắt đầu trận.

## Đội hình và sự kiện

- Mỗi CLB đủ 11 STARTING, >=1 thủ môn đá chính, tuổi và toàn-danh-sách ngoại binh trong Rule; có dự bị để demo thay thế/quota.
- Chọn Rule nghiệp vụ đã chốt của đồ án, không tuyên bố là điều lệ VPF đầy đủ.
- Không ép đủ mọi thẻ/sự kiện lịch sử chưa kiểm chứng. Cặp đấu và hồ sơ dựa nguồn; phần diễn biến phục vụ demo được thiết kế nhất quán.
- Xây ca treo giò trước, rồi sinh đội hình theo trạng thái án tại từng kickoff: đủ vàng, đỏ rõ ràng, án cộng dồn, chấp hành ở trận FINISHED tiếp theo CLB. Mọi thẻ có cùng ý nghĩa sau khi bỏ is_simulated.
- Có một cầu thủ đang bị cấm ở vòng tương lai, một cầu thủ đã chấp hành xong; phần còn lại đủ nhân sự hợp lệ để Staff lưu cả hai bên.
- Có NORMAL với/không kiến tạo, PENALTY, OWN, Foul victim khác đội, Yellow/Red và Substitution người vào chỉ một lần; actor phải đăng ký cùng trận và có diễn biến ra/vào sân hợp lý.
- Có trận FINISHED0–0; tỷ số chỉ derived từ goals. Không thêm stored score/result.
- Để ít nhất một trận tương lai HOME/AWAY chưa đăng ký cho demo lưu; một bên đã lưu ở trận khác để demo thay thế và bảo toàn ID/registered_at.

# 4. Tạo đường reset/seed chuẩn và bỏ đường cũ

## Artifact dự kiến

- database/demo_reset.sql: chỉ chạy tường minh, không mount tự động lúc app khởi động. Yêu cầu biến xác nhận reset, sai/thiếu biến thì dừng trước mutation. Danh sách bảng explicit, không TRUNCATE CASCADE vượt phạm vi.
- database/demo_seed.sql: seed schema sạch với ngày mốc, identity và mapping tạm. Chạy lại trên database không rỗng phải fail rõ, không silently reset/nhân đôi.
- database/demo_verify.sql: audit chỉ đọc và assertions cho schema/cardinality/quan hệ/ca nghiệp vụ; không phụ thuộc metadata đã bỏ.
- Script/nguồn chuẩn bị dữ liệu chỉ bổ sung khi thực sự cần tái lập việc lấy nguồn; không thêm công cụ frontend/build mới.

## Reset atomic

- Stop web. Backup dạng custom, kiểm tra pg_restore -l và phục hồi thử vào môi trường cô lập.
- Một transaction khóa đúng phạm vi, kiểm tra bảng/FK, TRUNCATE danh sách 20 bảng RESTART IDENTITY không CASCADE; bỏ năm cột, tạo seed và audit trước commit nếu cùng kết nối. Lỗi rollback toàn bộ; lưu ý sequence không transactional cho nextval/setval và chỉ cấp lại sau rollback bằng reset có kiểm soát.
- Sync player_code_seq sau seed; tránh SQL lpad cắt số >999: độ dài ít nhất 3, không giới hạn tối đa3.
- Không tắt FK/trigger và không session_replication_role=replica để vượt dữ liệu sai. Nạp trạng thái SCHEDULED, hai MatchClub, đội hình/sự kiện hợp lệ rồi chốt FINISHED khi dữ liệu đầy đủ.

## Script lỗi thời cần clean cutover

Đánh giá và loại khỏi đường chạy/README, xóa phần obsolete đã bị thay thế: full_season_migration.sql, official_vleague_cutover.sql, username_convention_migration.sql, round_id_order_migration.sql, drop_provenance_migration.sql, vleague_2026_2027.sql, vleague_2026_2027_full_season.sql, vleague_2026_2027_verify.sql, historical_seasons.sql. Nếu artifact còn giá trị nguồn thì giữ dưới dạng bằng chứng ngoài SQL executable, không để một đường setup khác tạo lại cột đã bỏ. users.sql/players.sql/player_code_sequence.sql phải dùng thống nhất với seed mới, không chạy chồng bộ tài khoản cũ.

# 5. Kiểm chứng trước cutover

## Cô lập

- Database/schema riêng; test dữ liệu không ghi vào public. Chạy fresh schema+seed và lặp lại từ sạch với ngày mốc cố định; chạy seed trên bộ đã có phải bị từ chối rõ.
- Catalog so sánh đúng danh sách bảng/cột đã chốt. Năm cột đã bỏ không còn; seasons.name còn; không bảng metadata mới.
- 1 Tournament, 5 Season, 14 Club, 26 Round/182 Match mùa chính; 4 mùa cũ0 vòng/trận. Đúng HOME/AWAY, shirt/player uniqueness và subtype1–1.
- Từng ca treo giò có expected player/match cụ thể, không chỉ count tổng. Audit đội hình lịch sử theo án thực sự tại thời điểm đăng ký.
- Audit actor, assister, victim, substitution; so tỷ số với manifest demo, không tiếp tục tuyên bố42 tỷ số nguồn nếu đã đổi diễn biến.
- Test consumer-visible: tìm mã vòng/trận ổn định khi lọc; tạo cầu thủ sau seed không trùng username; thiếu thủ môn/quota/tuổi/thẻ cấm bị từ chối; FINISHED/kickoff quá hạn/events khóa ghi; stale baseline/concurrency giữ nguyên.
- Build Maven package với PostgreSQL tests không skipped. Không re-pin test source/wiring hoặc wording.

# 6. Cutover runtime và bàn giao

- Chỉ sau schema cô lập đạt: backup runtime (thử restore), dừng web, reset/schema/seed, audit và commit; lỗi phục hồi backup theo hướng dẫn cụ thể đã kiểm tra.
- Build WAR/image đúng source mới, docker compose up -d --build --wait; recreate web để bỏ session cũ.
- Browser Staff: đủ mùa→vòng→trận, tìm R07/R07-T01, mở form; lưu HOME rồi AWAY trên ca demo, reload thấy đúng DB; thay thế giữ ID; lỗi quota/thủ môn/treo giò giữ lựa chọn; trạng thái khóa không lưu được. Manager/Player/User bị403 ở trang và POST; logout/login đúng.
- Không có cảnh báo mô phỏng hoặc truy vấn cột bỏ; không lộ SQL/credentials. CN3 vẫn disabled.
- Cập nhật README/AGENTS và4 báo cáo về schema, tên/mã derived, không miễn thẻ và dataset mới; DESIGN chỉ sửa nếu template thực sự đổi.
- Ghi kết quả thực chạy, backup path, ngày mốc, tài khoản demo, đường walkthrough và cách reset an toàn. Dọn scaffolding/throwaway; không commit trừ khi được yêu cầu.

# Các cổng hoàn thành

- [x] Schema/caller thống nhất, không cột thêm ngoài ngoại lệ seasons.name.
- [x] Nguồn VPF kiểm tra lại; mapping/cách tạo ca demo tái lập ngoài database; timeout hồ sơ được công bố, không giả xác minh.
- [x] Bộ đủ mùa và ca treo giò/đội hình nhất quán, ngày tương lai dùng được khi seed chạy.
- [x] Reset có bảo vệ, backup restore được, seed không nhân đôi hoặc tự xóa ngầm.
- [x] Kiểm chứng cô lập + 32 PostgreSQL tests không skipped + Tomcat/browser đạt; bằng chứng và trạng thái sau smoke trong README.
- [x] Tài liệu/setup sạch một đường, CN3 không bị mở ngoài scope.

# Thứ tự triển khai

P1 kiểm kê/schema → P2 mã nguồn và contract mã derived → P3 lấy nguồn/manifest/demo seed → P4 kiểm chứng cô lập → P5 backup/reset/deploy → P6 smoke/tài liệu. P2 và chuẩn bị nguồn P3 có thể độc lập sau P1; cutover tuyệt đối sau P4. Không yêu cầu lựa chọn sản phẩm mới ngoài ba quyết định đã chốt; chi tiết triển khai chọn cách đơn giản và báo chứng cứ.

# Kết quả thực thi

Reset runtime COMMIT ngày08/11/2026 với ngày mốc hiện tại; image healthy. Backup `pre-demo-reset-20261108.dump` đã restore thử. Lỗi review import JSP rỗng và lỗi audit PL/pgSQL alias đã sửa, kiểm chứng trên Tomcat/PostgreSQL. Runtime sau smoke có hai đội hình11 chính ở trận43 và thay một dự bị trận44;13 đăng ký còn lại giữ ID/registeredAt. CN1 tạo/xóa CT317 thành công. Không commit. Chi tiết bằng chứng/giới hạn nguồn và lệnh phục hồi trong README.
