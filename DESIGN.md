# Thiết kế — Football Manager

## 1. Cách đọc tài liệu

Tài liệu ghi nhận kiến trúc và giao diện hiện có, sau đó đưa ra định hướng cho hai chức năng chưa triển khai. Không phải PRD đã phê duyệt cho toàn bộ luật giải đấu.

- **Hiện có:** đã thấy trong mã nguồn.
- **Có trong migration:** được định nghĩa tại `database/football_manager_migration.sql`, không đồng nghĩa đã triển khai Java/UI hoặc đã chạy trên CSDL.
- **Đề xuất:** hướng nối tiếp phù hợp kiến trúc; cần đối chiếu yêu cầu khi bắt đầu code.

Tại thời điểm kiểm tra 2026-11-04, CSDL Docker chỉ có `users`, `managers`, `staff`, `players`. Không áp dụng migration hay thay dữ liệu khi soạn tài liệu này. Hướng dẫn thao tác cho coding agent nằm ở `AGENTS.md`.

## 2. Kiến trúc hiện có

| Lớp | Trách nhiệm | Điểm vào thực tế |
|---|---|---|
| Điều hướng | Kiểm tra session, whitelist trang và quyền theo subtype | `src/main/webapp/index.jsp` |
| Giao diện | Render HTML tiếng Việt, form, danh sách, dialog | `src/main/webapp/WEB-INF/views/` |
| Hành động | POST, kiểm tra quyền/dữ liệu, gọi DAO, xử lý lỗi/redirect | `src/main/webapp/doLogin.jsp`, `doLogout.jsp`, `doAddPlayer.jsp`, `doEditPlayer.jsp`, `doDeletePlayer.jsp` |
| DAO | SQL, prepared statement, mapping, transaction | `src/main/java/com/footballmanager/dao/` |
| Model | User và các subtype Manager/Staff/Player | `src/main/java/com/footballmanager/model/` |
| Hạ tầng | Kết nối theo biến môi trường; WAR trên Tomcat | `DAO.java`, `pom.xml`, `Dockerfile`, `docker-compose.yaml` |

Luồng đọc hiện tại: browser GET `index.jsp?page=...` → kiểm tra `currentUser` → forward view trong `WEB-INF` → DAO khi cần → HTML.

Luồng ghi hiện tại: form POST tới `do*.jsp` → kiểm tra method/session/quyền/dữ liệu → DAO → transaction nếu nhiều bảng → flash session/redirect. Chi tiết mỗi handler có khác nhau; không giả định đã có lớp validation hoặc middleware chung.

### Route và quyền hiện tại

| `page` | View | Quyền |
|---|---|---|
| `login` | `gdLogin.jsp` | Chưa đăng nhập; người đã đăng nhập được chuyển về home |
| `home` | `gdHome.jsp` | Mọi subtype User đã đăng nhập |
| `player-search` | `gdPlayerSearch.jsp` | Staff |
| `player-add` | `gdPlayerForm.jsp` | Staff |
| `player-edit` | `gdPlayerForm.jsp` | Staff |

Không đăng nhập được chuyển tới login; route không tồn tại trả 404; không đủ quyền trả 403. Các handler POST cũng tự kiểm tra quyền. Trang chủ hiện có hai nút disabled cho đăng ký thi đấu và cập nhật kết quả.

## 3. Mô hình dữ liệu

### 3.1. Schema gốc đang được dùng

- `users`: tài khoản, hash mật khẩu, họ tên, ngày sinh, email, điện thoại.
- `managers`, `staff`, `players`: PK `user_id` đồng thời là FK tới `users`; xóa user cascade xuống subtype.
- `staff.position`; `players.nationality`, `primary_position`, `height_cm`, `weight_kg`.
- Mã `CTxxx` là `users.username`, không phải một khóa cầu thủ riêng.
- Mật khẩu qua PostgreSQL `pgcrypto`; sequence cầu thủ ở `player_code_sequence.sql`.

### 3.2. Schema mở rộng có trong migration

| Nhóm | Bảng và liên kết |
|---|---|
| Giải/mùa/luật | `tournaments` → `seasons` → `rounds` → `matches`; mỗi mùa tham chiếu `rules` |
| CLB | `clubs` tham chiếu `stadiums` và `managers.user_id`; `manager_id` UNIQUE |
| Đội tham gia | `match_clubs(match_id, club_id, role)`; role HOME/AWAY |
| Thành viên CLB | `club_players(club_id, player_id, shirt_number)`; player trỏ `players.user_id` |
| Đội hình trận | `match_players(match_club_id, club_player_id, role, registered_at)`; role STARTING/SUBSTITUTE |
| Sự kiện | `events(match_id, match_player_id, minute, extra_minute, note)` |
| Loại sự kiện | `goals`, `fouls`, `yellow_cards`, `red_cards`, `substitutions`; PK mỗi subtype đồng thời FK tới `events.id` |
| Tỷ số | View `v_match_scores(match_id, home_score, away_score)` tính từ sự kiện bàn thắng |

Các ID cần phân biệt: user/player → membership `club_players.id` → đăng ký `match_players.id` → actor `events.match_player_id`. Form nhập bàn thắng dùng ID đăng ký trận, không dùng trực tiếp ID user.

### 3.3. Ràng buộc đã có trong SQL mở rộng

- Một trận tối đa một HOME và một AWAY; không cùng CLB ở hai phía. Chuyển trận sang ONGOING/FINISHED yêu cầu đủ hai phía.
- Một membership không đăng ký trùng cho cùng phía tham gia; cầu thủ đăng ký phải thuộc đúng CLB đó.
- Actor sự kiện phải được đăng ký cho đúng trận.
- Kiến tạo: khác người ghi bàn, cùng đội và cùng trận; chỉ bàn NORMAL được có kiến tạo.
- Nạn nhân phạm lỗi: khác actor, khác đội, cùng trận.
- Thay người: người vào khác người ra, cùng đội/trận, đăng ký vai trò SUBSTITUTE; người vào là UNIQUE theo ID đăng ký trận.
- Trigger bảo vệ thay đổi liên kết đội/membership/đăng ký đã có phụ thuộc; event có subtype không được đổi actor/trận.
- Phần lớn FK mới dùng RESTRICT. Subtype sự kiện cascade khi xóa event. Không thể giả định xóa cầu thủ đã thuộc CLB vẫn hoạt động như CSDL gốc.

### 3.4. Nguồn sự thật của tỷ số

`v_match_scores` cộng bàn NORMAL/PENALTY cho đội của actor, bàn OWN cho đối phương. Không có bàn trả về 0–0; không có cột tỷ số lưu trên `matches`. View không lọc trạng thái trận: 0–0 không có nghĩa đã FINISHED.

**Đề xuất:** cập nhật kết quả theo sự kiện và trạng thái, đọc tỷ số từ view. Không thêm ô nhập tỷ số độc lập rồi lưu lệch với danh sách bàn thắng. Nếu yêu cầu thực tế chỉ nhập tỷ số tổng, phải chốt thay đổi mô hình trước; không tự tạo bàn thắng giả để khớp tỷ số.

### 3.5. Luật chưa được migration thực thi

`rules` chứa số đá chính, dự bị, thay người, ngưỡng thẻ vàng, giới hạn tuổi và ngoại binh nhưng chưa kiểm tra các quota này. SQL cũng chưa đảm bảo:

- Một cầu thủ không đăng ký cho cả hai phía; membership hiện cho phép cùng người thuộc nhiều CLB.
- Hạn đăng ký, khóa đội hình theo kickoff/trạng thái, draft/submitted/approved.
- Trình tự chuyển trạng thái hoặc khóa sửa khi FINISHED.
- Một event có đúng một subtype; event hiện có thể không có hoặc có nhiều subtype.
- Actor đang thực sự trên sân, giới hạn thay người, hai vàng thành đỏ, treo giò.
- Quyền người dùng hoặc ownership CLB; timestamp không âm không chứng minh hợp lệ theo hiệp đấu.

Các quy tắc mới phải được thực thi phía server; dùng transaction/khóa phù hợp để kiểm tra và ghi atomic, không dựa riêng trigger hoặc JavaScript.

## 4. Thiết kế giao diện hiện tại

### 4.1. Ngôn ngữ thị giác

Nguồn: `assets/css/common.css`, `home.css`, `login.css`, `search.css`, `form.css`. Hiện chưa có hệ CSS custom properties; bảng dưới là giá trị đang dùng, không phải API token đã được khai báo.

| Thành phần | Giá trị / quy ước |
|---|---|
| Font | Arial, sans-serif; body 14px |
| Chữ chính | `#1c2b23` |
| Nền trang | `#edf2ee` |
| Màu chủ đạo | `#0e5a3c`; hover nút xác nhận `#0a3f2c` |
| Topbar | Nền xanh đậm, chữ trắng; tên hệ thống và tên/vai trò người dùng; logout bên phải |
| Khung topbar | max-width 1040px, canh giữa, padding 16px 24px 12px |
| Breadcrumb | Dưới topbar, ngăn bởi viền `#43866a`; chữ phụ `#e0f0e7` |
| Lỗi | `.error-box`: nền `#fff0f0`, chữ `#c62828`, viền `#df4545`, radius 8px |
| Dialog | `.app-dialog`: tối đa 440px, padding 24px, radius 10px, backdrop đen mờ |
| Nút dialog | Padding 10px 28px, radius 8px; nút chính xanh nền đặc |

Tái sử dụng fragment `WEB-INF/views/fragments/topbar.jspf`, `.error-box`, `.app-dialog`, `.dialog-actions`, `.dialog-ok-btn`. Các class ở CSS riêng phải đọc trước khi tái sử dụng vì chưa phải component library thống nhất.

### 4.2. Bố cục và tương tác

- Trang nội dung giữ cấu trúc: topbar/breadcrumb → quay lại → tiêu đề → vùng thao tác/form/bảng.
- Tìm kiếm cầu thủ có ô tìm, nút Tìm, link Thêm; bảng có Sửa và Xóa. Giữ phân biệt hành động chính, phụ và phá hủy.
- Form thêm/sửa chung một view; kết quả và xác nhận dùng native `<dialog>` được điều khiển bằng JS.
- `common.css` có breakpoint 480px để thu padding topbar và font tiêu đề. Không coi toàn bộ màn hình mới đã responsive chỉ vì dùng common.css.
- Giao diện mới phải giữ label rõ ràng, focus bàn phím, nút submit đúng type, lỗi có chữ thay vì chỉ màu; kiểm tra bảng dài trên màn hình nhỏ.

**Đề xuất mở rộng:** CSS/JS theo trang mới, dùng chung phong cách đang có. Không thêm thư viện UI, sidebar, biểu đồ hoặc animation không phục vụ hai luồng nghiệp vụ.

### 4.3. Chuẩn giao diện CN2 và CN3

- **Nguồn chuẩn:** giao diện đang chạy của `gdPlayerSearch.jsp` và `search.css`. `docs/quan-ly-giai-bong-da.html` chỉ tham khảo bố cục/tương tác; không chép luật nghiệp vụ hoặc CSS đè lên CN1.
- Mọi trang tìm kiếm dùng `common.css` → `search.css` → CSS bổ sung của chức năng. Tái sử dụng `.search-content`, `.back-link`, `.search-toolbar`, `.search-results`, `.table-scroll`; không tạo bảng màu hoặc bộ kích thước thứ hai.
- Khung nội dung rộng tối đa 1040px, padding desktop 36px 24px; link **« Quay lại** cách tiêu đề 24px; h2 24px màu `#0a3f2c`. Font Arial, chữ chính `#1c2b23`, nền trang `#edf2ee` giữ nguyên CN1.
- Thanh tìm kiếm chỉ có ô nhập và nút **Tìm**, gap 12px, không nền/viền/padding dạng card và không có nhãn hiển thị riêng. Nhãn input vẫn có cho trình đọc màn hình. Ô nhập padding 12px, font 16px, radius 10px, viền `#b3cbbd`; nút dùng màu và kích thước từ `search.css`.
- Bảng tìm CN2 có vùng cuộn cao cố định 552px, header sticky; cuộn trong bảng thay vì kéo dài trang. Header `#e4f0e9`, chữ `#0a3f2c`, cell padding 10px 12px, font 16px và viền `#d7e2da` theo CN1; không nền trắng riêng cho tbody.
- Ba trang tìm mùa/vòng/trận **không có cột Thao tác hoặc thẻ a trong ô tên**. Toàn dòng giữ URL trong `data-row-link`, click hoặc Tab/Enter để chọn; hover/focus làm nổi dòng. JS điều hướng tới URL đã escape, server vẫn kiểm tra ID/session.
- Cột theo mẫu HTML: mùa = Mã giải đấu / Tên giải đấu / Trạng thái; vòng = Mã vòng đấu / Tên vòng đấu / Thời gian bắt đầu / Thời gian kết thúc / Trạng thái; trận = Mã trận đấu / Đội nhà / Đội khách / Ngày thi đấu / Giờ thi đấu / Trạng thái đăng kí. Mã hiển thị derived VLE+năm, R+ID, TR+ID, không phải cột mã nghiệp vụ mới trong DB. Trạng thái đăng kí derived từ hai đội có đủ 11 STARTING, không thay trạng thái trận.
- Breadcrumb dùng fragment chung, hiển thị đủ **Trang chủ › Tìm mùa giải › Tìm vòng đấu › Tìm trận đấu › Thông tin trận đấu › Đăng ký cầu thủ thi đấu** đến cấp hiện tại. Các cấp trước là link mang đúng ngữ cảnh session, không ghép HTML động vào chuỗi breadcrumb. Giữ tên **Tìm mùa giải**, không tạo màn hình chọn Tournament.
- Không lặp dòng phụ tên giải/năm/vòng bên dưới tiêu đề. Nội dung trận vẫn có thời gian, sân và trạng thái cần thiết; CLB và vai trò HOME/AWAY phải rõ ràng.
- Thông tin trận bố trí **hai bảng đội nhà/đội khách trên hai cột desktop**, mỗi cột `minmax(0, 1fr)`, gap 18px. Mỗi bảng cuộn dọc độc lập, cao 320px; cột Mã cầu thủ / Họ tên / Số áo / Vị trí / Vai trò. Bỏ cột thời điểm đăng ký khỏi UI này; dữ liệu vẫn giữ trong model/DB. Table min-width 0, layout fixed, chữ xuống dòng, font 13.5px và padding 7px 10px theo panel mẫu để đủ năm cột mà không cuộn ngang. Dưới 700px chuyển một cột.
- Khung/quay lại/h2/toolbar giữ nguyên class và CSS CN1; khi kiểm chứng so cả tọa độ, không chỉ computed styles. Asset CSS/JS CN2 có version URL để browser không dùng bản cũ sau cập nhật.
- Trang chọn cầu thủ giữ select Không đăng ký / Đá chính / Dự bị, bộ đếm, thông báo điều lệ và lỗi server; không nhân đôi form hoặc thay luồng lưu một bên thành lưu hai bên.
- Giữ trạng thái rỗng, lỗi/thành công, lựa chọn khi validation thất bại, trạng thái bị khóa; JS chỉ hỗ trợ tương tác, không thay kiểm tra quyền/ngữ cảnh/luật ở server.
- **Áp dụng cho CN3:** tái sử dụng ba trang tìm kiếm và cùng quy ước khung/breadcrumb/bảng/nút; không thêm sidebar, dashboard hoặc hệ thống CSS mới. Phần 5–8 bên dưới là đề xuất lịch sử, không phải yêu cầu nghiệp vụ đã duyệt; xem báo cáo CN2/CN3 và AGENTS.

## 5. Đề xuất chức năng: đăng ký cầu thủ thi đấu

### Vai trò và luồng

Manager đăng nhập → xem trận có CLB mình tham dự → chọn trận → xem thông tin trận/luật/danh sách thành viên → chọn đá chính và dự bị → kiểm tra → lưu → tải lại thấy đội hình đã lưu.

Không có CLB gắn manager hoặc chưa có lịch thi đấu: hiển thị trạng thái rỗng có giải thích, không lỗi 500 và không giả dữ liệu.

### Màn hình đề xuất

1. **Danh sách trận:** mùa/vòng, kickoff, chủ nhà–khách, trạng thái; thao tác “Đăng ký đội hình” hoặc “Xem đội hình”.
2. **Đội hình trận:** thông tin trận và CLB hiện tại; số đá chính/dự bị đã chọn so với luật; bảng cầu thủ gồm mã, tên, số áo, vị trí, lựa chọn không đăng ký/đá chính/dự bị; lỗi theo trường/nhóm; nút Lưu và Quay lại.
3. **Kết quả:** thông báo thành công chỉ sau commit; khi không được sửa thì trang chỉ đọc và nêu lý do.

Tên route/file sẽ chốt khi triển khai, không phải endpoint đang tồn tại.

### Invariant và acceptance đề xuất

- Manager chỉ đọc/ghi đội hình thuộc CLB mình quản lý; đổi ID trên URL/form không vượt quyền.
- Cầu thủ thuộc CLB tham gia và không xuất hiện ở hai phía của cùng trận.
- Một cầu thủ không đồng thời đá chính và dự bị. Khi lưu đội hình hoàn chỉnh: đủ `required_main_players`, dự bị không vượt `max_bench_players`.
- Đề xuất chỉ sửa khi SCHEDULED; cần chốt thêm có khóa theo kickoff hay không. Trạng thái phải được kiểm tra lại trong transaction, kể cả request cũ đang mở.
- Lưu toàn bộ thay đổi atomic; request đồng thời không phá quota hoặc ghi đè đội hình đã khóa. Không xóa hàng loạt đăng ký đã có sự kiện để “lưu lại”.
- Tuổi/ngoại binh/treo giò chỉ bật khi đã xác định rõ cách tính, dữ liệu và phạm vi; không hardcode 11/7 hoặc suy đoán quốc tịch từ chuỗi tự do.

## 6. Đề xuất chức năng: cập nhật kết quả

### Vai trò và luồng

Staff đăng nhập → danh sách trận → chi tiết trận và hai đội hình → chọn loại sự kiện, thời điểm, cầu thủ liên quan → lưu → xem tỷ số/sự kiện đã cập nhật → xác nhận kết thúc trận.

Đăng ký đội hình là tiền điều kiện để nhập actor sự kiện. Nếu chưa đủ dữ liệu, màn hình nêu thiếu đội hình/đội tham gia; không cho chọn tùy ý tất cả cầu thủ trong hệ thống.

### Màn hình đề xuất

1. **Danh sách trận:** bộ lọc phù hợp dữ liệu hiện có; hiển thị hai CLB, kickoff, trạng thái, tỷ số và thao tác.
2. **Chi tiết/cập nhật:** tên trận, trạng thái, tỷ số chỉ đọc, đội hình hai phía; bảng sự kiện theo thời gian; form nhập/sửa với trường thay đổi theo loại sự kiện.
3. **Chốt kết quả:** dialog xác nhận hai đội, tỷ số và ảnh hưởng khóa sửa. Không thông báo chốt thành công trước commit.

### Invariant và acceptance đề xuất

- Chỉ Staff được ghi; Manager/Player/User không truy cập được endpoint ghi dù tự gửi POST.
- Hỗ trợ dữ liệu đúng mô hình: bàn thắng NORMAL/PENALTY/OWN, thẻ vàng, thẻ đỏ, phạm lỗi, thay người. Phạm vi UI và luật diễn biến chi tiết cần được người dùng chốt trước code; không coi có bảng SQL là đã hoàn thành tính năng.
- Ghi `events` và subtype trong cùng transaction, đúng một subtype cho mỗi event do ứng dụng tạo. Không cho đổi PK để chuyển subtype sang event khác.
- Validate phút/bù giờ, actor/kiến tạo/nạn nhân/người vào sân theo đúng trận và đội; lỗi không để lại event mồ côi.
- Bàn phản lưới cộng cho đối phương; sửa/xóa bàn làm view đổi tương ứng; trận kết thúc không bàn là 0–0 hợp lệ.
- Đề xuất luồng trạng thái SCHEDULED → ONGOING → FINISHED; chuyển trạng thái phải được server bảo vệ, không chỉ disable nút.
- Đề xuất FINISHED chỉ đọc. Nếu cho sửa sau chốt, cần chốt quyền, lý do và lịch sử trước khi mở chức năng; không âm thầm đưa trận về SCHEDULED để né trigger.
- Trạng thái nhập sự kiện, xử lý hai vàng, thay người/đang trên sân, treo giò cần hợp đồng nghiệp vụ rõ ràng; chưa có engine thực thi trong repo.

## 7. Quyết định cần chốt trước khi triển khai

| Vấn đề | Dữ kiện hiện có | Cần xác định |
|---|---|---|
| Kết quả tổng hay sự kiện | SQL tính tỷ số từ goals | Giữ mô hình sự kiện hay đổi contract có chủ đích |
| Khóa đăng ký | Chưa có cutoff nghiệp vụ | Chỉ theo trạng thái hay thêm kickoff/deadline |
| Nháp và duyệt | Chưa có cột trạng thái đội hình | Lưu đội hình hoàn chỉnh hay cần draft/approval |
| Tuổi và ngoại binh | Có giới hạn, thiếu định nghĩa | Mốc tính tuổi, ngày sinh thiếu, quốc tịch nội, phạm vi quota |
| Treo giò | Có thẻ và ngưỡng luật | Theo mùa/vòng, cách cộng/xóa án, hai vàng |
| Sự kiện và chốt | Có 5 subtype, chưa có audit | Phạm vi nhập/sửa/xóa, luật diễn biến, mở lại kết quả |
| Thời gian | SQL dùng TIMESTAMP WITHOUT TIME ZONE | Múi giờ hiển thị/nhập và diễn giải phút bù giờ |
| Dữ liệu vận hành | Chưa có UI quản trị giải/CLB/lịch | Seed demo có thể chạy lại hay yêu cầu thêm màn hình quản trị riêng |

Không tự mở rộng scope sang chuyển nhượng, bảng xếp hạng, lịch tự sinh, quản lý tài khoản hay quản trị giải đấu nếu người dùng chỉ yêu cầu hai chức năng trên.

## 8. Kế hoạch nối tiếp và kiểm chứng

1. Kiểm tra schema runtime; áp dụng migration trên dữ liệu đã bảo vệ sau khi đọc constraint. Script có `IF NOT EXISTS` không tự nâng cấp cấu trúc bảng đã tồn tại lệch phiên bản.
2. Chuẩn bị seed demo riêng, không reset dữ liệu thật: giải/mùa/luật/vòng, sân, hai CLB, manager, membership, trận HOME/AWAY.
3. Bổ sung model/DAO/view/POST handler cho đăng ký; mở route và nút theo Manager khi luồng chạy được.
4. Bổ sung luồng Staff cập nhật sự kiện/trạng thái và đọc `v_match_scores`; chỉ mở nút khi hoàn thiện.
5. Build WAR/image; truy cập Tomcat thực. Kiểm tra role đúng/sai, ownership, quota, rollback, reload sau lưu, bàn phản lưới, trận 0–0, khóa sau chốt và request đồng thời.
6. Hồi quy đăng nhập/đăng xuất và quản lý cầu thủ, đặc biệt xóa cầu thủ đã có membership. Cập nhật hướng dẫn setup và bằng chứng thực chạy.

Chỉ tài liệu được tạo trong lần này; không migration, seed hoặc chức năng mới nào được triển khai bởi nội dung đề xuất trên.
