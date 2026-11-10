# Hệ thống quản lý giải bóng đá vô địch quốc gia

Đồ án môn học **Phân tích và thiết kế hệ thống thông tin** — Học viện Công nghệ Bưu chính Viễn thông (PTIT).

**Giảng viên hướng dẫn:** PGS. TS. Nguyễn Mạnh Hùng.

## Phạm vi và công nghệ

- **CN1:** Staff tìm kiếm, thêm, sửa, xóa hồ sơ cầu thủ; đăng nhập/đăng xuất theo vai trò.
- **CN2:** Staff đăng ký độc lập đội hình HOME/AWAY qua mùa → vòng → trận.
- **CN3:** Staff thêm bàn thắng/phạm lỗi/thẻ/thay người và kết thúc trận; tỷ số derived, khóa ghi sau kết thúc. Không sửa/xóa sự kiện, mở lại hoặc nhập tổng tỷ số thủ công.
- Java 17, JSP–DAO gọi JDBC trực tiếp, Tomcat 10.1 (Jakarta), PostgreSQL 17, Maven Wrapper và Docker Compose. Không có Controller/Service nghiệp vụ hoặc build frontend riêng.

Ứng dụng Docker: <http://localhost:8088>. Nếu database đã được khởi tạo và muốn chạy tiếp dữ liệu hiện có, **không reset/seed lại**:

```powershell
.\mvnw.cmd package -DskipTests
docker compose up -d --build --wait
```

Lệnh build trên bỏ qua test; xem mục Kiểm chứng để chạy integration test PostgreSQL. Cài mới: chạy schema và dataset trên database rỗng theo hướng dẫn bên dưới. Database đang dùng: backup và nâng cấp schema, không import dataset hoặc xóa volume.

## Cấu trúc chính

| Đường dẫn | Nội dung |
|---|---|
| `database/schema.sql` | Toàn bộ schema: 20 bảng, constraint/index, trigger, view tỷ số, pgcrypto và sequence; nâng cấp bảo toàn dữ liệu hiện có |
| `database/dataset.sql` | Dataset và audit trong một transaction, chỉ import vào schema rỗng; không cần file SQL khác |
| `src/main/java/com/footballmanager/` | Model một chiều, DAO và tiện ích |
| `src/main/webapp/` | `index.jsp`, handler POST `do*.jsp`, view trong `WEB-INF/views`, CSS/JS |
| `src/test/java/` | JUnit tích hợp PostgreSQL cô lập |
| `docs/` | Báo cáo phân tích/thiết kế và ảnh walkthrough CN1 |

## Quy ước formatting

- Bộ formatter cho Java, JSP/JSPF, JavaScript, CSS, XML và YAML: **IntelliJ IDEA 2026.2.3**, cấu hình chung `config/code-style.xml`; `.editorconfig` quy định UTF-8, LF và thụt lề 4 spaces. Giữ các file sinh tự động và Maven Wrapper ngoài lượt format.
- JSP: mỗi import là một directive `<%@ page import="..." %>` riêng. Handler POST dùng cùng cách kiểm tra method và header `Allow: POST`; các trang Staff dùng cùng cách lấy `currentSession`/`currentUser`. Formatting không bổ sung CSRF hoặc thay đổi cơ chế khóa/session của CN1.
- SQL trong DAO: khai báo `String` có tên trước khi gọi JDBC; câu nhiều dòng dùng text block. Keyword hoa, mỗi cột SELECT một dòng, FROM/JOIN/WHERE/GROUP BY/ORDER BY tách dòng; các điều kiện AND/OR và danh sách INSERT/UPDATE thụt lề nhất quán. Giữ nguyên `?` và thứ tự bind; không nối dữ liệu người dùng vào SQL.
- Trên PowerShell, đặt `$env:IDEA_HOME` tới thư mục cài IntelliJ IDEA rồi chạy `.\format.cmd`; kiểm tra không ghi file bằng `.\format.cmd -dry`. Cần plugin **Jakarta EE: Server Pages (JSP)** (`javaee-jsp`) trong thư mục plugin người dùng IntelliJIdea2026.2. Script dùng cấu hình/cache formatter riêng và nạp plugin đã cài để không xung đột IDE đang mở.
- SQL có lệnh `psql` (`\if`, `\gset`) giữ nguyên: không dùng formatter SQL tổng quát làm thay đổi cú pháp chạy script.
- Kiểm chứng chuẩn hóa: 43 PostgreSQL regression tests đạt, WAR build đạt, Jasper biên dịch 20 JSP; browser mở CN1/CN2/CN3 và các handler GET trả405 với `Allow: POST`. Chạy formatter lần hai không đổi byte nào. IntelliJ `-dry` vẫn báo riêng `UserDAO.java` cần format dù chạy format không thay nội dung; không dùng exit code `-dry` làm cổng CI (công cụ vẫn trả0 khi phát hiện file cần format).


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

### 2. Tạo schema và import dataset — chỉ hai file

Database mới chỉ cần chạy hai file, đúng thứ tự:

```powershell
docker compose cp database/schema.sql db:/tmp/football-schema.sql
docker compose cp database/dataset.sql db:/tmp/football-dataset.sql
docker compose exec -T db psql -X -v ON_ERROR_STOP=1 -U football_app -d football_db -f /tmp/football-schema.sql
docker compose exec -T db psql -X -v ON_ERROR_STOP=1 -U football_app -d football_db -f /tmp/football-dataset.sql
```

`schema.sql` chứa toàn bộ DDL; `dataset.sql` chứa sẵn seed và audit, không `\ir` hay phụ thuộc JSON/CSV/Python khi cài. Dataset tự mở transaction, từ chối nếu bất kỳ bảng ứng dụng nào có dữ liệu, audit trước commit. Không reset, TRUNCATE hoặc xóa volume. Chạy lại schema được; không import dataset chồng lên dữ liệu đang dùng.

Dataset INSERT ID cố định bằng `OVERRIDING SYSTEM VALUE` để giữ đúng các liên kết fixture, rồi đồng bộ sequence theo ID lớn nhất. Các cột `id` trong bảng subtype sự kiện là khóa ngoại đến `events`, không phải identity. Script vẫn khóa và từ chối nếu bất kỳ bảng ứng dụng nào có dữ liệu; không dùng dataset để ghép thêm vào database đang dùng.

Theo yêu cầu dọn thư mục, chỉ giữ hai file SQL; đã loại script sinh dataset, template audit rời và các file nguồn/bằng chứng JSON/CSV. Audit vẫn nằm trong `dataset.sql`, nhưng repo không còn đầu vào/công cụ tái sinh dataset hay bằng chứng nguồn chi tiết.

Ngày mốc mặc định theo ngày chạy tại `Asia/Ho_Chi_Minh`; để tái lập lịch, thêm `-v demo_date=2026-11-10` vào lệnh dataset. Ngày cũ có thể khiến CN2 khóa lưu vì đã qua kickoff.

### Lỗi thường gặp khi mở SQL trong IDE

- Chọn dialect **PostgreSQL** và datasource đúng `football_db` trên `localhost:5433`, schema `public`; refresh/synchronize metadata sau khi nâng cấp schema.
- `Only DEFAULT may be inserted into generated column`: dataset nhập ID cố định bằng cú pháp PostgreSQL `OVERRIDING SYSTEM VALUE`. Chạy nguyên file qua `psql`, không tách riêng phần `VALUES` hoặc bỏ mệnh đề override. File có lệnh `psql` nên không chạy như SQL thuần trong JDBC console.
- Không nhận diện `r.number` trong DAO: kiểm tra datasource đã có `public.rounds.number`; nếu chỉ IDE gạch đỏ, refresh metadata cũ. Nếu database thiếu cột, nâng cấp bằng `schema.sql` theo mục bên dưới, không đổi DAO về suy số vòng từ tên.
- `Dataset requires empty application tables`: giữ nguyên database có dữ liệu; tạo database mới để demo hoặc chỉ nâng cấp schema. Dataset không hỗ trợ trộn dữ liệu có sẵn.


### 3. Nâng cấp database đang có dữ liệu

Không chạy dataset. Dừng web, backup custom và phục hồi thử vào database cô lập trước khi nâng cấp:

```powershell
docker compose stop web
New-Item -ItemType Directory -Force backups | Out-Null
docker compose exec -T db pg_dump -U football_app -d football_db -Fc -f /tmp/before-schema-upgrade.dump
docker compose cp db:/tmp/before-schema-upgrade.dump backups/before-schema-upgrade.dump
docker compose exec -T db pg_restore -l /tmp/before-schema-upgrade.dump
docker compose exec -T db createdb -U football_app football_restore_check
docker compose exec -T db pg_restore -U football_app --exit-on-error --no-owner -d football_restore_check /tmp/before-schema-upgrade.dump
docker compose cp database/schema.sql db:/tmp/football-schema.sql
docker compose exec -T db psql -X -v ON_ERROR_STOP=1 -U football_app -d football_db -f /tmp/football-schema.sql
```

Chọn tên backup/database phục hồi chưa dùng; không ghi đè bản cần giữ. Schema bổ sung `rounds.number`, chuyển tên chuẩn `Vòng N` thành số **một lần khi nâng cấp**, rồi ràng buộc số dương và duy nhất trong mùa. Tên không xác định hoặc trùng số khiến nâng cấp rollback; cần xác định số đúng cho các vòng đó, không tự suy từ ID hay thứ tự ngày. Sau nâng cấp, đổi tên vòng không đổi mã vòng/trận.

Nếu nâng cấp thất bại, giữ web dừng, kiểm tra lỗi và trạng thái transaction; dùng backup đã phục hồi thử nếu cần khôi phục. Không chạy dataset để sửa lỗi migration. Các backup chứa tài khoản, không commit lên GitHub.

### 4. Build và chạy ứng dụng

```powershell
.\mvnw.cmd package
docker compose up -d --build --wait --force-recreate web
```

Linux/macOS dùng `./mvnw package`. Truy cập <http://localhost:8088>, đăng nhập lại để bỏ session cũ. Với Tomcat ngoài Docker, chạy cùng hai file qua `psql`, cấu hình `JDBC_URL`, `DB_USER`, `DB_PASSWORD` rồi deploy WAR. `pgcrypto` cần quyền tạo extension thích hợp.


## Tài khoản demo sau import dataset

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
- Tên CLB, hồ sơ và cặp đấu dựa trên [VPF](https://vpf.vn/season/v-league-2027/?action=calendar&pagejs=1). Từ 387 dòng nguồn đã chọn 316 hồ sơ đủ trường, loại alias trùng nhận dạng/số áo; không suy quốc tịch từ tên hoặc đổi năm sinh thành ngày sinh chính xác. Lần refresh chỉ có 4/387 request HTTP 200; không tuyên bố toàn bộ hồ sơ đã được xác minh lại trực tiếp. Manager kỹ thuật không phải xác nhận danh tính người thật.
- Dataset thiết kế 1.190 đăng ký và 14 sự kiện ban đầu. Đội hình/diễn biến phục vụ demo không phải toàn bộ biên bản lịch sử VPF; không tuyên bố tỷ số demo đối chiếu toàn bộ tỷ số nguồn. CN3 dùng lại đội hình CN2, không cần reset để mở chức năng.
- Giữ `seasons.name`; số vòng lưu riêng ở `rounds.number`. Không khôi phục metadata nguồn/cờ mô phỏng hoặc cột số thứ tự trận; tỷ số và thứ tự trận vẫn derived.
- **Mọi thẻ đều tính án treo giò**, không phân biệt nguồn hay cách tạo dữ liệu. Tỷ số derived từ Goal qua `v_match_scores`, không lưu score/result riêng.
- `rounds.number` là số dương duy nhất trong từng mùa, độc lập `rounds.name`. Tìm theo tên hiện tại hoặc mã `R07`; đổi tên không đổi mã. `R07-T01` derived theo số vòng và thứ tự ID trận trong toàn bộ vòng **trước** lọc tìm kiếm, không theo kickoff. URL/session/FK dùng ID, không coi ID là số vòng.
- Ngày chưa xác định vẫn có thể NULL trong schema. CN2 khóa lưu nếu kickoff NULL; bộ demo bố trí ngày cụ thể cho các ca cần thao tác.

## Walkthrough CN2 — đăng ký đội hình

1. Đăng nhập `NV001`, chọn **Đăng ký cầu thủ thi đấu cho trận đấu**.
2. Tìm mùa bằng `LPBank`, năm hoặc trạng thái; chọn mùa 2026/27 → tìm `R07`/`Vòng 7` → tìm `R07-T01`.
3. Sau **reset sạch**, mở `R07-T01` (ID 43, Thành Phố Đồng Nai – SHB Đà Nẵng), HOME/AWAY đều chưa đăng ký. Runtime sau smoke đã có đội hình như ghi ở mục bằng chứng; không reset chỉ để tiếp tục sử dụng. Chọn HOME với đủ 11 STARTING có ít nhất một `Thủ môn`, dự bị và ngoại binh trong quota; lưu.
4. Đăng ký AWAY độc lập; mở lại từ tìm mùa để đọc dữ liệu đã commit, không chỉ nhìn cache thành công.
5. Mở `R07-T02` (ID 44, Hồng Lĩnh Hà Tĩnh – Thép Xanh Nam Định): HOME có sẵn 11 chính + 3 dự bị, AWAY trống. Thay một đăng ký HOME hợp lệ: cầu thủ còn lại phải giữ ID/`registeredAt`. Thử thiếu thủ môn, quá quota hoặc cầu thủ đang treo giò: bị từ chối và giữ lựa chọn để sửa.
6. Trận FINISHED, đã qua kickoff, kickoff chưa xác định hoặc có event phải khóa ghi. Manager/Player/User không được mở trang hay POST CN2.

Các ID trên là contract sau reset sạch, không phải khóa nghiệp vụ để hardcode trong ứng dụng. `R01-T01` (ID1) có tỷ số derived3–1 và các subtype mẫu; `R01-T02` (ID2) FINISHED0–0. Hai trận đã kết thúc chỉ xem được. Chọn trận SCHEDULED/ONGOING khác để thử CN3; việc chốt kết quả là ghi dữ liệu thật, không có action mở lại.

Ca treo giò tại `R07-T01` trong dataset:

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


## Walkthrough CN3 — cập nhật kết quả thi đấu

1. Đăng nhập Staff → **Cập nhật kết quả thi đấu** → tìm mùa → vòng → trận. Luồng tìm kiếm dùng chung CN2 nhưng giữ `mode=events`; đổi chức năng/ngữ cảnh làm tab cũ hết hiệu lực.
2. Trang **Sự kiện trận đấu** hiển thị HOME/AWAY, tỷ số derived, trạng thái và diễn biến. Cả hai CLB cần đội hình hợp lệ trước khi thêm sự kiện hoặc kết thúc. Nếu còn thiếu, quay về CN2 đăng ký khi điều kiện CN2 cho phép.
3. **Thêm sự kiện**: chọn CLB, loại, phút/bù giờ không âm, cầu thủ chính và ghi chú ≤255 ký tự. NORMAL có kiến tạo tùy chọn cùng đội; PENALTY/OWN không có kiến tạo; Foul chọn nạn nhân đối phương; Substitution chọn dự bị cùng đội vào sân.
4. Lưu → redirect về diễn biến. NORMAL/PENALTY cộng đội ghi bàn, OWN cộng đối phương. Actor/kiến tạo/nạn nhân phải đang trên sân tại thời điểm đó; thay người theo Rule.maxSubstitutions và không vào lại. Thêm lùi phút bị từ chối nếu làm sai sự kiện phía sau. Lỗi giữ lựa chọn để sửa.
5. **Kết thúc trận đấu** chốt FINISHED, kể cả0–0; trang khóa thêm sự kiện và DAO cũng từ chối POST sau chốt. Không có sửa/xóa/mở lại trận. Chỉ thao tác trên trận thực sự muốn chốt.

CN3 nhận SCHEDULED/ONGOING, sự kiện đầu tiên chuyển ONGOING; không thêm quy trình bắt đầu trận hoặc cổng kickoff ngoài báo cáo. `EventDAO.addEvent()`/`MatchDAO.saveMatch()` khóa cùng dòng trận với CN2, kiểm tra DB hiện tại và cập nhật cache sau commit. `refreshMatch()` chỉ tải status/tỷ số; đội hình cho form lấy từ Match trong session. POST có kiểm tra Staff, CSRF và flow/ID; các vai trò khác bị403.

## Walkthrough CN1 — hồ sơ cầu thủ

1. Staff vào **Quản lý thông tin cầu thủ**, tìm theo tên/mã → **Thêm cầu thủ mới**.
2. Khai báo họ tên, ngày sinh, quốc tịch, vị trí, email/SĐT, chiều cao/cân nặng → xác nhận lưu.
3. `PlayerDAO.addPlayer()` transaction lấy `player_code_seq`, thêm `users` với mật khẩu băm và `players`, commit rồi bàn giao tài khoản; lỗi rollback cả hai.
4. Tìm lại mã mới → **Sửa** → lưu. `updatePlayer()` cập nhật hai bảng trong cùng transaction.
5. Xóa cầu thủ vừa tạo nếu chưa có quan hệ CLB/lịch sử: xác nhận → POST → thông báo kết quả. Cầu thủ có quan hệ phải bị từ chối bằng thông báo nghiệp vụ, không xóa dây chuyền lịch sử.

[Ảnh đăng nhập](docs/screenshots/01-login.png), [form thêm](docs/screenshots/04-add-player-form.png), [form sửa](docs/screenshots/08-edit-player-form.png), [xác nhận xóa](docs/screenshots/12-delete-player-confirm.png) minh họa luồng CN1 cũ; tên/mã trong ảnh không phải bằng chứng bộ demo mới hoặc số sequence tiếp theo.

## Kiểm chứng

`dataset.sql` chứa audit snapshot ngay trước COMMIT: kiểm tra cardinality, số vòng, fixture, tỷ số và các ca treo giò. Audit lỗi thì import không commit. Đây là audit **snapshot ban đầu**, không chạy lại sau khi Staff đã thay đội hình; không còn file seed/audit/reset riêng cần ghép bằng tay.

JUnit dùng PostgreSQL thật, schema UUID riêng, không ghi `public` hay fallback sang biến DB ứng dụng. Dùng database kiểm thử riêng và tài khoản có quyền tạo schema:

```powershell
$env:CN2_TEST_JDBC_URL = 'jdbc:postgresql://127.0.0.1:5433/football_test'
$env:CN2_TEST_DB_USER = 'football_app'
# Nạp CN2_TEST_DB_PASSWORD từ cấu hình bí mật của môi trường.
.\mvnw.cmd "-Dtest=Cn2LineupIntegrationTest,Cn2TournamentIntegrationTest,Cn3EventIntegrationTest" test
```

Thiếu `CN2_TEST_*` thì integration test **skipped**, không phải pass nghiệp vụ. Build thành công không chứng minh JSP/Tomcat/browser thành công. Cần ghi riêng bằng chứng schema cô lập, reset/restore, suite PostgreSQL và browser trên đúng bộ dữ liệu/source mới; các số test/snapshot triển khai cũ không được dùng thay thế.

### Bằng chứng triển khai ngày 08/11/2026

- Backup trước reset: `pre-demo-reset-20261108.dump` tại thư mục repo và `/tmp/pre-demo-reset-20261108.dump` trong container db; đã phục hồi thử vào `football_restore_20261108` (385 users, 182 matches, 1.482 registrations, 367 events). Backup chứa dữ liệu tài khoản, không đưa vào Git.
- Seed/audit đã đạt trong `football_demo_stage_20261108`; seed chạy chồng bị từ chối. Reset runtime đã audit và COMMIT, không reset volume; bỏ đúng năm cột, giữ `seasons.name`. Ngày mốc 08/11/2026, vòng 7 bắt đầu 15/11/2026.
- Maven: 32 kiểm thử PostgreSQL đạt, 0 lỗi, 0 skipped; WAR được đóng gói lại sau sửa import JSP, image Tomcat rebuild/recreate và healthy.
- Browser: Staff đi đủ mùa → vòng → trận; lưu R07-T01 HOME/AWAY mỗi bên 11 chính, tìm lại từ đầu đọc đúng DB. Thiếu 11 chính/thiếu thủ môn và CT242/CT243 đang treo giò bị từ chối; CT241 đã chấp hành án được lưu. POST lỗi render lại JSP, không lỗi compile.
- R07-T02 thay dự bị ClubPlayer150 bằng152: 13 đăng ký còn lại giữ nguyên ID/registeredAt. Manager/Player/User đăng nhập được nhưng trang CN2 và POST đều403; GET handler ghi405, POST thiếu CSRF403; logout quay về login. CN1 thêm được CT317 không trùng mã rồi xóa chính hồ sơ smoke đó.
- Runtime sau smoke **không còn snapshot seed nguyên trạng**: R07-T01 đã có hai đội hình, R07-T02 đã thay một dự bị; sequence đã cấp CT317. Muốn bắt đầu lại walkthrough trống cần reset tường minh theo quy trình backup ở trên, không chạy seed chồng.
- Lịch VPF đã đọc lại được 182 cặp; chỉ 4/387 lần refresh hồ sơ trả HTTP 200. Dữ liệu hồ sơ còn lại dựa snapshot cũ, không tuyên bố xác minh lại toàn bộ. Nguồn có mâu thuẫn BN–ND vòng 15/16 và nhãn lịch đá bù; dataset giữ các cặp đấu đã chọn.

### Bằng chứng CN3 ngày 08/11/2026

- `mvnw.cmd package` đạt: 43 PostgreSQL tests, 0 failures/errors/skipped (32 CN2 + 11 CN3), schema UUID cô lập trong `football_cn3_smoke`. Có kiểm tra subtype/tỷ số OWN, rollback, timeline/thay người/thẻ đỏ, cầu thủ liên quan chưa vào hoặc đã rời sân, đội hình DB thiếu, kết thúc0–0 và addEvent đồng thời với kết thúc.
- Hai lượt review độc lập backend/UI không phát hiện lỗi chặn; bổ sung regression cho cầu thủ liên quan ngoài sân và cổng đội hình addEvent. Chưa có test đồng thời riêng cho addEvent-vs-saveLineup; các writer dùng cùng khóa dòng trận.
- Browser trên Tomcat cô lập cổng8089, database `football_cn3_smoke`: Staff đi đủ ba cấp; thêm NORMAL có kiến tạo, OWN, PENALTY, Foul nạn nhân đối phương, YellowCard, RedCard, Substitution. Tỷ số2–1 giữ đúng sau reload/kết thúc; chốt trận khác0–0 thành công. Cầu thủ đã thay ra bị từ chối và giữ input; ghi chú chứa script hiển thị dạng văn bản, không thực thi. Đã quan sát desktop và form390px.
- Endpoint GET trả405, POST thiếu CSRF403; Manager/Player/User bị403 ở cả view/form và hai POST. ID trận giả trả400. POST cũ sau kết thúc bị400 vì flow đã đổi; cổng FINISHED độc lập ở DAO được regression với Match cũ.
- Đã rebuild/recreate `football-web` bằng Docker Compose, healthy tại8088. Browser đăng nhập Staff, đi mùa → vòng → trận, đọc R01-T01 tỷ số3–1 và khóa FINISHED, logout thành công. Không ghi sự kiện/kết thúc trận trên `football_db`; các ghi CN3 smoke nằm trong database cô lập, không thay snapshot nghiệp vụ8088.

### Bằng chứng nâng cấp ngày 10/11/2026

- Cài sạch `schema.sql` → `dataset.sql` thành công trong `football_two_file_check`; audit snapshot đạt (316 cầu thủ, 26 vòng, 182 trận). Import dataset lần hai bị từ chối. Đổi tên vòng rồi chạy lại schema vẫn giữ số đã lưu.
- Backup custom `/tmp/football-before-round-number.dump` đọc được danh mục và phục hồi thử vào `football_round_upgrade_check`. Nâng cấp bản phục hồi và `football_db` giữ nguyên 332 users, 182 trận, 1.212 đăng ký, 14 sự kiện; 26 vòng có số 1–26. Không chạy dataset trên runtime, không reset dữ liệu.
- Hai ca migration tên vòng không xác định/trùng số đều bị từ chối và rollback trong database cô lập.
- Lượt `mvnw.cmd package` chạy 46 tests: 32 CN2 lineup và 11 CN3 đạt, một ca Tournament gặp lỗi socket Windows `BindException: Address already in use` khi tạo fixture. Chạy riêng cả lớp Tournament sau đó đạt 3/3, không skipped. `package -DskipTests` đóng gói WAR thành công; không mô tả lượt package đầu là xanh hoàn toàn.
- Đã rebuild/recreate web healthy tại 8088. Browser Staff đi mùa → vòng, tìm `R07`, mở `R07-T01`; hai bên vẫn có 11 cầu thủ và reload giữ mã/đội hình. Smoke này chỉ đọc dữ liệu, không thay đăng ký hoặc sự kiện. Regression kiểm tra đổi tên không đổi mã, thứ tự số vòng và ràng buộc số dương/duy nhất theo mùa chạy trên schema UUID riêng.


## Giấy phép

Dự án phục vụ mục đích học tập.
