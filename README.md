# Hệ thống quản lý giải bóng đá vô địch quốc gia

Đồ án môn học **Phân tích và thiết kế hệ thống thông tin** — Học viện Công nghệ Bưu chính Viễn thông (PTIT).

**Giảng viên hướng dẫn:** PGS. TS. Nguyễn Mạnh Hùng.

## Phạm vi và công nghệ

- **CN1:** Staff tìm kiếm, thêm, sửa, xóa hồ sơ cầu thủ; đăng nhập/đăng xuất theo vai trò.
- **CN2:** Staff đăng ký độc lập đội hình HOME/AWAY qua mùa → vòng → trận.
- **CN3:** cập nhật kết quả chưa triển khai; nút chức năng vẫn khóa. Có dữ liệu sự kiện không đồng nghĩa có giao diện ghi sự kiện.
- Java 17, JSP–DAO gọi JDBC trực tiếp, Tomcat 10.1 (Jakarta), PostgreSQL 17, Maven Wrapper và Docker Compose. Không có Controller/Service nghiệp vụ hoặc build frontend riêng.

Ứng dụng Docker: <http://localhost:8088>. Nếu database đã được khởi tạo và muốn chạy tiếp dữ liệu hiện có, **không reset/seed lại**:

```powershell
.\mvnw.cmd package -DskipTests
docker compose up -d --build --wait
```

Lệnh build trên bỏ qua test; xem mục Kiểm chứng để chạy integration test PostgreSQL. Cài mới hoặc chủ động thay toàn bộ dữ liệu demo: làm theo quy trình backup và reset bên dưới.

## Cấu trúc chính

| Đường dẫn | Nội dung |
|---|---|
| `database/schema.sql` | Bốn bảng người dùng và subtype |
| `database/football_manager_migration.sql` | Mô hình giải đấu, CLB, đội hình, sự kiện, view tỷ số và invariant |
| `database/demo_reset.sql` | Reset tường minh; tự gọi `demo_seed.sql` và `demo_verify.sql` |
| `database/demo_seed.sql`, `database/demo_verify.sql` | Seed schema rỗng và audit/assertion dữ liệu demo |
| `database/vleague_2026_2027_*` | JSON/CSV nguồn VPF ngoài database |
| `src/main/java/com/footballmanager/` | Model một chiều, DAO và tiện ích |
| `src/main/webapp/` | `index.jsp`, handler POST `do*.jsp`, view trong `WEB-INF/views`, CSS/JS |
| `src/test/java/` | JUnit tích hợp PostgreSQL cô lập |
| `docs/` | Báo cáo phân tích/thiết kế và ảnh walkthrough CN1 |

## Cài đặt: một đường khởi tạo CSDL

Đây là **hướng dẫn chạy**, không phải bằng chứng database runtime đã được reset hoặc bản WAR mới đã được kiểm chứng.

Cần Docker Desktop/Compose; nếu chạy Tomcat ngoài Docker, cần JDK 17, PostgreSQL và Tomcat 10.1. Dockerfile dùng WAR đã build, không tự chạy Maven.

### 1. Cấu hình và khởi động riêng database

Chạy PowerShell từ thư mục gốc:

```powershell
Copy-Item .env.example .env
# Sửa DB_PASSWORD trong .env; không đưa mật khẩu môi trường vào repo/log.
docker compose up -d --wait db
```

PostgreSQL: `127.0.0.1:5433`, database `football_db`, user `football_app`. Container web dùng JDBC `db:5432`, không phải `localhost:5433`.

### 2. Backup và dừng web trước mọi reset

**Reset xóa toàn bộ dữ liệu của 20 bảng ứng dụng, kể cả tài khoản hiện tại, rồi cấp lại ID/sequence.** Không giữ hồ sơ hoặc mật khẩu từ bộ demo cũ. Chỉ chạy khi được phép thay bộ dữ liệu. Không xóa Docker volume.

Dừng web để ngăn ghi cạnh tranh; giữ web dừng đến khi audit/restore hoàn tất. Với database đã có dữ liệu, tạo backup custom và phục hồi thử trước khi reset:

```powershell
docker compose stop web
New-Item -ItemType Directory -Force backups | Out-Null
docker compose exec -T db pg_dump -U football_app -d football_db -Fc -f /tmp/demo-before-reset.dump
docker compose cp db:/tmp/demo-before-reset.dump backups/demo-before-reset.dump
docker compose exec -T db pg_restore -l /tmp/demo-before-reset.dump
docker compose exec -T db createdb -U football_app football_restore_check
docker compose exec -T db pg_restore -U football_app --exit-on-error --no-owner -d football_restore_check /tmp/demo-before-reset.dump
```

Chọn tên file backup mới cho mỗi lần chạy, không ghi đè bản cần giữ. Kiểm tra từng lệnh thành công trước khi tiếp tục; chỉ xem danh mục bằng `pg_restore -l` chưa chứng minh phục hồi được. Database `football_restore_check` phải là tên cô lập chưa tồn tại; kiểm tra dữ liệu phục hồi rồi mới xóa database kiểm tra nếu không còn cần. Không restore thử lên `football_db`.

### 3. Khởi tạo schema rồi reset/seed/audit

**Database hoàn toàn mới:** sao chép cả thư mục để các lệnh `\ir` tìm được seed và audit; chạy đúng thứ tự:

```powershell
docker compose cp database db:/tmp/football-database
docker compose exec -T db psql -X -v ON_ERROR_STOP=1 -U football_app -d football_db -f /tmp/football-database/schema.sql
docker compose exec -T db psql -X -v ON_ERROR_STOP=1 -U football_app -d football_db -f /tmp/football-database/football_manager_migration.sql
docker compose exec -T db psql -X -v ON_ERROR_STOP=1 -v confirm_reset=RESET_FOOTBALL_DEMO -U football_app -d football_db -f /tmp/football-database/demo_reset.sql
```

**Database đã có schema ứng dụng:** không chạy lại `schema.sql`. Sau backup/restore thử và dừng web, sao chép thư mục SQL mới, chạy `football_manager_migration.sql` rồi `demo_reset.sql` như trên. Đối chiếu schema hiện tại trước khi áp dụng; file tồn tại không chứng minh migration đã chạy.

`confirm_reset=RESET_FOOTBALL_DEMO` là xác nhận bắt buộc; thiếu/sai thì script dừng trước mutation. Reset dùng danh sách bảng tường minh, không `CASCADE`, không tắt FK/trigger; seed và verify chạy trong transaction reset trước commit. Không chạy seed riêng chồng lên database có dữ liệu: seed yêu cầu rỗng và phải từ chối dữ liệu đã tồn tại. Không mount reset vào startup.

Ngày mốc mặc định là ngày chạy theo `Asia/Ho_Chi_Minh`. Để tái lập một lịch cố định, thay lệnh reset cuối bằng:

```powershell
docker compose exec -T db psql -X -v ON_ERROR_STOP=1 -v confirm_reset=RESET_FOOTBALL_DEMO -v demo_date=2026-11-08 -U football_app -d football_db -f /tmp/football-database/demo_reset.sql
```

Chọn mốc hiện tại cho demo tương tác; mốc cố định cũ có thể khiến kickoff đã qua và CN2 khóa lưu. Không có chuỗi script bổ sung để ghép bộ dữ liệu cũ vào bộ mới.

### 4. Build và mở lại ứng dụng

Chỉ tiếp tục nếu reset/audit không có lỗi:

```powershell
.\mvnw.cmd package
docker compose up -d --build --wait --force-recreate web
```

Linux/macOS dùng `./mvnw package`. Truy cập <http://localhost:8088>, đăng nhập lại để bỏ session/ngữ cảnh cũ. Với Tomcat ngoài Docker, dùng cùng ba bước SQL `schema.sql` → `football_manager_migration.sql` → `demo_reset.sql` qua `psql`, cấu hình `JDBC_URL`, `DB_USER`, `DB_PASSWORD`, rồi deploy `target/football-manager.war` vào Tomcat 10.1; URL thường là `http://localhost:8080/football-manager`. `pgcrypto` cần được cài bởi tài khoản có quyền phù hợp.

### Phục hồi nếu reset hoặc triển khai thất bại

Giữ web dừng. Nếu transaction reset báo lỗi, không coi đó là bộ demo đã commit; sequence vẫn cần được kiểm tra trước khi dùng lại. Phục hồi bản backup đã thử thành công để quay lại trạng thái trước reset:

```powershell
docker compose stop web
docker compose cp backups/demo-before-reset.dump db:/tmp/demo-before-reset.dump
docker compose exec -T db pg_restore -U football_app --exit-on-error --clean --if-exists --no-owner -d football_db /tmp/demo-before-reset.dump
```

Lệnh restore trên **thay thế các đối tượng có trong backup**, chỉ chạy lên database đích đã xác nhận. Nếu restore báo lỗi, giữ web dừng và xử lý lỗi; không mở lại với dữ liệu phục hồi dở dang. Sau restore, dùng WAR/image tương thích với schema trong backup rồi mới `docker compose up -d --wait --force-recreate web`. Backup chỉ khôi phục database, không tự khôi phục source/WAR/image.

## Tài khoản demo sau reset

| Tài khoản | Mật khẩu | Vai trò |
|---|---|---|
| `NV001` | `staff@123` | Staff — quản lý cầu thủ và lưu cả HOME/AWAY |
| `QL001` | `manager@123` | Manager kỹ thuật đại diện quan hệ CLB |
| `CT001` | `player@123` | Cầu thủ |
| `khangia` | `spectator@123` | Khán giả (User) |

Mật khẩu được băm bằng `pgcrypto`/BCrypt, không lưu dạng rõ trong database. Đây là tài khoản demo, không dùng mật khẩu này cho môi trường thật. Cầu thủ mới được cấp mã `CTxxx` (ít nhất ba chữ số, không giới hạn ở 999) và mật khẩu mặc định `player@123`; seed đồng bộ `player_code_seq` để tránh trùng.

## Bộ dữ liệu và contract schema

- Một giải **Giải Bóng đá Vô địch Quốc gia Việt Nam**, năm mùa: mùa chính **LPBank V.League 1 – 2026/27** và bốn mùa cũ chỉ có thông tin mùa, không có vòng/trận.
- Mùa chính có 14 CLB, 316 hồ sơ cầu thủ nguồn đủ trường, 26 vòng, 182 trận. Vòng 1–6 FINISHED; vòng 7 nằm sau ngày mốc ít nhất bảy ngày, các vòng sau tiếp tục theo tuần. Ngày là lịch demo đã dịch, không phải lịch thi đấu VPF đã xác minh; năm/tên mùa giữ nhãn nguồn.
- Tên CLB, hồ sơ và cặp đấu dựa trên [VPF](https://vpf.vn/season/v-league-2027/?action=calendar&pagejs=1); contract ca demo ở `database/demo_manifest.json`, ghi chú nguồn ở `database/demo_source_refresh.json`, bằng chứng lấy lại nguồn ở `database/demo_vpf_calendar_refresh.json` và `database/demo_vpf_profiles_refresh.json`. JSON/CSV nguồn nằm ngoài database. Từ 387 dòng nguồn, chọn 316 hồ sơ đủ trường và loại alias trùng nhận dạng/số áo; không suy quốc tịch từ tên hoặc đổi năm sinh thành ngày sinh chính xác. Lần refresh hồ sơ chỉ có 4/387 request HTTP 200, các request còn lại timeout được ghi rõ; không tuyên bố toàn bộ 316 hồ sơ đã được xác minh lại trực tiếp. Manager kỹ thuật không phải xác nhận danh tính người thật.
- Manifest thiết kế 1.190 đăng ký và 14 sự kiện. Đội hình/diễn biến phục vụ demo không phải toàn bộ biên bản lịch sử VPF; không tuyên bố tỷ số demo đối chiếu toàn bộ tỷ số nguồn. CN3 vẫn khóa.
- Giữ `seasons.name`. Bỏ đúng năm cột: `users.source_key`, `rounds.round_number`, `matches.match_number`, `match_players.is_simulated`, `events.is_simulated`; không thay bằng cột/bảng metadata bền vững khác. Không có nhãn miễn thẻ hoặc cảnh báo mô phỏng trên UI.
- **Mọi thẻ đều tính án treo giò**, không phân biệt nguồn hay cách tạo dữ liệu. Tỷ số derived từ Goal qua `v_match_scores`, không lưu score/result riêng.
- Vòng lưu tên `Vòng 1`…`Vòng 26`; số vòng derived từ tên, có fallback ổn định nếu tên không chứa số. `R07` và `Vòng 7` cùng tìm vòng 7. `R07-T01` derived theo thứ tự ID trận trong toàn bộ vòng **trước** lọc tìm kiếm, không theo kickoff. URL/session/FK dùng ID; không giả định ID vòng luôn bằng số vòng ở mọi mùa.
- Ngày chưa xác định vẫn có thể NULL trong schema. CN2 khóa lưu nếu kickoff NULL; bộ demo bố trí ngày cụ thể cho các ca cần thao tác.

## Walkthrough CN2 — đăng ký đội hình

1. Đăng nhập `NV001`, chọn **Đăng ký cầu thủ thi đấu cho trận đấu**.
2. Tìm mùa bằng `LPBank`, năm hoặc trạng thái; chọn mùa 2026/27 → tìm `R07`/`Vòng 7` → tìm `R07-T01`.
3. Sau **reset sạch**, mở `R07-T01` (ID 43, Thành Phố Đồng Nai – SHB Đà Nẵng), HOME/AWAY đều chưa đăng ký. Runtime sau smoke đã có đội hình như ghi ở mục bằng chứng; không reset chỉ để tiếp tục sử dụng. Chọn HOME với đủ 11 STARTING có ít nhất một `Thủ môn`, dự bị và ngoại binh trong quota; lưu.
4. Đăng ký AWAY độc lập; mở lại từ tìm mùa để đọc dữ liệu đã commit, không chỉ nhìn cache thành công.
5. Mở `R07-T02` (ID 44, Hồng Lĩnh Hà Tĩnh – Thép Xanh Nam Định): HOME có sẵn 11 chính + 3 dự bị, AWAY trống. Thay một đăng ký HOME hợp lệ: cầu thủ còn lại phải giữ ID/`registeredAt`. Thử thiếu thủ môn, quá quota hoặc cầu thủ đang treo giò: bị từ chối và giữ lựa chọn để sửa.
6. Trận FINISHED, đã qua kickoff, kickoff chưa xác định hoặc có event phải khóa ghi. Manager/Player/User không được mở trang hay POST CN2.

Các ID trên là contract sau reset sạch, không phải khóa nghiệp vụ để hardcode trong ứng dụng. `R01-T01` (ID 1) có tỷ số derived 3–1 và các ca bàn thường/có hoặc không kiến tạo, penalty, phản lưới, phạm lỗi, thẻ, thay người; `R01-T02` (ID 2) là FINISHED 0–0. Tra manifest để chọn đúng cầu thủ/ca treo giò; không thêm sự kiện qua CN3 để diễn thử khi chức năng này còn khóa.

Ca treo giò tại `R07-T01` theo manifest:

| Cầu thủ | Diễn biến | Kỳ vọng |
|---|---|---|
| `CT241` — Dương Văn Khoa (Player ID 257) | Đủ vàng + đỏ vòng 3, vắng vòng 4/5, đã đăng ký vòng 6 | Đã chấp hành hết án trước trận 43 |
| `CT242` — Huỳnh Tấn Sinh (Player ID 258) | Vàng ở vòng 4/5/6 | Bị cấm trận 43 |
| `CT243` — Nguyễn Thành Lộc (Player ID 259) | Đỏ vòng 6 | Bị cấm trận 43 |

### Luật và bảo mật cần giữ

- `Rule.requiredMainPlayers` phải là 11; khác 11 là lỗi cấu hình. Dự bị ≤ `maxBenchPlayers`, ngoại binh tính trên cả đá chính và dự bị. Quốc tịch trim, không phân biệt hoa/thường: `Việt Nam` là nội binh, khác là ngoại binh, thiếu bị từ chối.
- Tuổi tròn tại `Season.startDate` ≤ `ageLimit`; ngày sinh thiếu/sau khai mạc không hợp lệ. Rule của bộ demo, không phải toàn bộ điều lệ VPF: 11 chính, tối đa 9 dự bị, 5 lượt thay người, 3 ngoại binh toàn danh sách, tuổi tối đa 45, ngưỡng vàng 3. Giới hạn được đọc từ Rule, không hardcode theo phần mô tả này.
- Mỗi ngưỡng vàng tích lũy và mỗi RedCard rõ ràng tạo một trận cấm, cộng dồn cùng mùa. Chấp hành ở trận FINISHED tiếp theo của CLB nhận thẻ theo kickoff; hai vàng vẫn là hai vàng, không tự suy RedCard.
- Chỉ ghi khi SCHEDULED trước kickoff và chưa có event. Transaction kiểm tra lại DB, khóa cạnh tranh, giữ baseline/cache trước commit; form cũ khác DB bị từ chối, phải quay lại tìm trận.
- Dùng session `currentUser`, kiểm tra `Staff` ở trang và handler; handler ghi chỉ nhận POST, CN2 có CSRF token. Không mô tả POST-only CN1 là chống CSRF chung.
- Prepared statement cho SQL, `HTMLUtil` escape HTML; không lộ SQL/stack trace/credentials. Giữ URL theo contextPath và UTF-8.

## Walkthrough CN1 — hồ sơ cầu thủ

1. Staff vào **Quản lý thông tin cầu thủ**, tìm theo tên/mã → **Thêm cầu thủ mới**.
2. Khai báo họ tên, ngày sinh, quốc tịch, vị trí, email/SĐT, chiều cao/cân nặng → xác nhận lưu.
3. `PlayerDAO.addPlayer()` transaction lấy `player_code_seq`, thêm `users` với mật khẩu băm và `players`, commit rồi bàn giao tài khoản; lỗi rollback cả hai.
4. Tìm lại mã mới → **Sửa** → lưu. `updatePlayer()` cập nhật hai bảng trong cùng transaction.
5. Xóa cầu thủ vừa tạo nếu chưa có quan hệ CLB/lịch sử: xác nhận → POST → thông báo kết quả. Cầu thủ có quan hệ phải bị từ chối bằng thông báo nghiệp vụ, không xóa dây chuyền lịch sử.

[Ảnh đăng nhập](docs/screenshots/01-login.png), [form thêm](docs/screenshots/04-add-player-form.png), [form sửa](docs/screenshots/08-edit-player-form.png), [xác nhận xóa](docs/screenshots/12-delete-player-confirm.png) minh họa luồng CN1 cũ; tên/mã trong ảnh không phải bằng chứng bộ demo mới hoặc số sequence tiếp theo.

## Kiểm chứng

`demo_reset.sql` tự chạy `demo_verify.sql` trước commit. Audit này kiểm tra **snapshot ban đầu**, không dùng sau khi Staff đã sửa đội hình vì các ca trống/cardinality sẽ thay đổi. Có thể chạy lại ngay sau seed bằng:

```powershell
docker compose exec -T db psql -X -v ON_ERROR_STOP=1 -U football_app -d football_db -f /tmp/football-database/demo_verify.sql
```

JUnit dùng PostgreSQL thật, schema UUID riêng, không ghi `public` hay fallback sang biến DB ứng dụng. Dùng database kiểm thử riêng và tài khoản có quyền tạo schema:

```powershell
$env:CN2_TEST_JDBC_URL = 'jdbc:postgresql://127.0.0.1:5433/football_test'
$env:CN2_TEST_DB_USER = 'football_app'
# Nạp CN2_TEST_DB_PASSWORD từ cấu hình bí mật của môi trường.
.\mvnw.cmd "-Dtest=Cn2LineupIntegrationTest,Cn2TournamentIntegrationTest" test
```

Thiếu `CN2_TEST_*` thì integration test **skipped**, không phải pass nghiệp vụ. Build thành công không chứng minh JSP/Tomcat/browser thành công. Cần ghi riêng bằng chứng schema cô lập, reset/restore, suite PostgreSQL và browser trên đúng bộ dữ liệu/source mới; các số test/snapshot triển khai cũ không được dùng thay thế.

### Bằng chứng triển khai ngày 08/11/2026

- Backup trước reset: `pre-demo-reset-20261108.dump` tại thư mục repo và `/tmp/pre-demo-reset-20261108.dump` trong container db; đã phục hồi thử vào `football_restore_20261108` (385 users, 182 matches, 1.482 registrations, 367 events). Backup chứa dữ liệu tài khoản, không đưa vào Git.
- Seed/audit đã đạt trong `football_demo_stage_20261108`; seed chạy chồng bị từ chối. Reset runtime đã audit và COMMIT, không reset volume; bỏ đúng năm cột, giữ `seasons.name`. Ngày mốc 08/11/2026, vòng 7 bắt đầu 15/11/2026.
- Maven: 32 kiểm thử PostgreSQL đạt, 0 lỗi, 0 skipped; WAR được đóng gói lại sau sửa import JSP, image Tomcat rebuild/recreate và healthy.
- Browser: Staff đi đủ mùa → vòng → trận; lưu R07-T01 HOME/AWAY mỗi bên 11 chính, tìm lại từ đầu đọc đúng DB. Thiếu 11 chính/thiếu thủ môn và CT242/CT243 đang treo giò bị từ chối; CT241 đã chấp hành án được lưu. POST lỗi render lại JSP, không lỗi compile.
- R07-T02 thay dự bị ClubPlayer150 bằng152: 13 đăng ký còn lại giữ nguyên ID/registeredAt. Manager/Player/User đăng nhập được nhưng trang CN2 và POST đều403; GET handler ghi405, POST thiếu CSRF403; logout quay về login. CN1 thêm được CT317 không trùng mã rồi xóa chính hồ sơ smoke đó.
- Runtime sau smoke **không còn snapshot seed nguyên trạng**: R07-T01 đã có hai đội hình, R07-T02 đã thay một dự bị; sequence đã cấp CT317. Muốn bắt đầu lại walkthrough trống cần reset tường minh theo quy trình backup ở trên, không chạy seed chồng.
- Lịch VPF đọc lại được182 cặp; chỉ4/387 lần refresh hồ sơ trả HTTP200, còn lại timeout được ghi trong JSON nguồn. Dữ liệu hồ sơ còn lại dựa snapshot đã có; không tuyên bố xác minh lại toàn bộ. Mâu thuẫn BN–ND và nhãn lịch đá bù được ghi trong manifest.

## Giấy phép

Dự án phục vụ mục đích học tập.
