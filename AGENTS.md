# Hướng dẫn agent — Football Manager

## 1. Mục đích và phạm vi

Đây là hướng dẫn phát triển trong repo. Trước khi sửa code, đọc bốn báo cáo trong `docs/` được liệt kê bên dưới; chỉ dùng `DESIGN.md` làm template/phong cách giao diện hiện đang triển khai, không lấy đề xuất nghiệp vụ hoặc kiến trúc trong file đó làm yêu cầu.

Ứng dụng quản lý giải bóng đá vô địch quốc gia, đồ án Phân tích và thiết kế hệ thống thông tin. Giữ kiến trúc Java/JSP hiện tại và giao diện tiếng Việt. Không tự chuyển sang Spring, React, ORM hoặc bổ sung hệ thống build frontend.

CN2 đăng ký HOME/AWAY và CN3 thêm sự kiện/kết thúc trận đã triển khai cho Staff. CN3 không bao gồm sửa/xóa sự kiện, mở lại trận hoặc action bắt đầu riêng.

### Nguồn yêu cầu và cách xử lý khác biệt

| Tài liệu | Vai trò |
|---|---|
| `docs/Phan-tich-Analysis-Class-Diagram.md` | Mô hình miền đã chốt, quan hệ, lớp trung gian, kế thừa sự kiện, dữ liệu derived và ràng buộc nghiệp vụ |
| `docs/Pha-Thiet-Ke-CN1.md` | Thiết kế thêm cầu thủ và đăng nhập, quy ước JSP–DAO |
| `docs/Pha-Thiet-Ke-CN2.md` | Luồng màn hình, session, DAO và lưu đội hình của Staff |
| `docs/Pha-Thiet-Ke-CN3.md` | Luồng màn hình, session, DAO, thêm sự kiện và kết thúc trận của Staff |
| `DESIGN.md` | Chỉ tham khảo giao diện: màu, typography, layout, form, bảng, dialog và responsive |

- Yêu cầu trực tiếp mới nhất của người dùng quyết định phạm vi; báo cáo quyết định nghiệp vụ/mô hình. Dùng báo cáo pha thiết kế tương ứng để cụ thể hóa lời gọi DAO và màn hình; không dùng phần đề xuất cũ trong `DESIGN.md` để ghi đè.
- Mã nguồn và SQL cho biết điều gì thực sự đã triển khai, không chứng minh báo cáo đã được hiện thực. Báo cáo cũng không chứng minh migration đã chạy.
- Các báo cáo đã đồng bộ Java 17/JSP/PostgreSQL; không dùng nhãn hoặc đề xuất cũ để thay stack hiện tại.
- Kiến trúc báo cáo là JSP–DAO, không bổ sung Controller/Service điều phối nghiệp vụ. `index.jsp` hiện có chỉ là điểm điều hướng/kiểm tra quyền; không tạo kiến trúc MVC/framework mới.
- CN1–CN2 dùng `gdHome.jsp`, session `currentUser`, `UserDAO.checkLogin()` trả subtype. Tái sử dụng đường đăng nhập khi thêm CN3; không tạo session xác thực thứ hai hoặc lưu đối tượng chứa mật khẩu vào session.
- Báo cáo phân tích mô tả User abstract nhưng code hiện khởi tạo User cho đăng nhập và khán giả. Không đổi thành abstract trong scope CN2/CN3 nếu chưa xử lý toàn bộ caller và yêu cầu vai trò.
- Nếu tài liệu còn mâu thuẫn thực chất hoặc thiếu định nghĩa, nêu đúng điểm cần quyết định; không suy diễn luật mới và không hỏi lại điều báo cáo đã chốt.

## 2. Stack và bản đồ mã nguồn

- Java 17, Jakarta Servlet 6.0, JSP; Maven Wrapper; WAR `target/football-manager.war`.
- Tomcat 10.1; PostgreSQL 17 trong Docker Compose; JDBC trực tiếp.
- Không có framework frontend; CSS và JavaScript thuần.
- `src/main/java/com/footballmanager/model/`: User và subtype, Tournament/Season/Rule/Round/Match, Club/Stadium và các lớp trung gian một chiều; các model sự kiện giữ đúng kế thừa.
- `src/main/java/com/footballmanager/dao/`: DAO/UserDAO/PlayerDAO và TournamentDAO/SeasonDAO/RoundDAO/MatchDAO/ClubDAO cho CN2. Không có DAO trung gian.
- `src/main/java/com/footballmanager/util/HTMLUtil.java`: escape dữ liệu HTML.
- `src/main/webapp/index.jsp`: điểm điều hướng hiện có, whitelist route và phân quyền trang; không phải lớp Controller nghiệp vụ mới.
- `src/main/webapp/do*.jsp`: xử lý hành động POST, không phải REST API.
- `src/main/webapp/WEB-INF/views/`: trang `gdLogin`, `gdHome`, `gdPlayerSearch`, `gdPlayerForm`.
- CN2: `gdSeasonSearch`, `gdRoundSearch`, `gdMatchSearch`, `gdMatchInfo`, `gdLineupRegistration`; ghi qua `doSaveLineup.jsp`. Route và quyền Staff cùng đăng ký ở `index.jsp`.
- CN3: dùng chung tìm mùa/vòng/trận với `mode=events`; `gdMatchEvent`, `gdEventForm`, POST `doAddEvent.jsp`/`doEndMatch.jsp`; `EventDAO` ghi subtype, `MatchDAO.saveMatch` kết thúc. Không có DAO trung gian mới.
- `src/main/webapp/WEB-INF/views/fragments/topbar.jspf`: topbar/breadcrumb dùng chung.
- `src/main/webapp/assets/`: CSS và JS; xem tokens và quy ước trong `DESIGN.md`.
- `database/`: chỉ `schema.sql` và `dataset.sql`, gồm schema/nâng cấp bảo toàn dữ liệu và dataset kèm audit.
- `README.md`: cài đặt, tài khoản demo và walkthrough chức năng cầu thủ.

## 3. Hiện trạng đã đối chiếu

### Đã triển khai

- Đăng nhập/đăng xuất; session lưu `currentUser`, timeout đăng nhập 30 phút.
- `UserDAO.checkLogin()` trả về subtype từ bảng vai trò; không có cột role chung. Thứ tự chọn subtype hiện tại: Manager, Staff, Player, rồi User.
- Trang quản lý cầu thủ dành cho `Staff`: tìm theo tên/mã, thêm, sửa, xóa.
- Tìm kiếm JDBC dùng prepared statement và `ILIKE`.
- Thêm/sửa cầu thủ dùng transaction. Thêm sinh username `CTxxx` qua `player_code_seq`; mật khẩu băm bằng PostgreSQL `crypt`/`pgcrypto`.
- CN2 và CN3 mở cho Staff, dùng currentUser và CSRF thật. CN2 lưu độc lập HOME/AWAY; CN3 thêm subtype/kết thúc, tỷ số derived và cache chỉ cập nhật sau commit.

### CSDL: phân biệt file SQL với môi trường thực tế

- Setup chỉ hai file độc lập: `database/schema.sql` (đủ 20 bảng, trigger/view/sequence/pgcrypto và nâng cấp bảo toàn dữ liệu) → `database/dataset.sql` (seed+audit tự mở transaction, chỉ schema rỗng). Không có external include; script sinh dữ liệu, template audit rời và JSON/CSV nguồn đã xóa theo yêu cầu dọn repo.
- `dataset.sql` từ chối nếu bất kỳ bảng ứng dụng có dữ liệu, không reset/TRUNCATE/xóa volume; ngày mốc tùy chọn `-v demo_date=YYYY-MM-DD`, mặc định theo Asia/Ho_Chi_Minh. Không mount import vào startup.
- Database hiện có: dừng web, backup custom, xem danh mục và restore thử database cô lập, chỉ chạy `schema.sql`, không dataset. Migration `rounds.number` lấy số từ tên chuẩn một lần khi thiếu số; số không xác định/trùng khiến rollback để sửa mapping rõ ràng, không suy từ ID.
- Sau nâng cấp/redeploy phải recreate web và đăng nhập lại để bỏ context cũ. Không coi file schema tồn tại là đã nâng cấp runtime. Backup chứa tài khoản không được commit.
- Bộ mới: 1 Tournament, 5 Season (LPBank V.League 1 – 2026/27 và bốn mùa cũ chỉ có thông tin), 14 CLB, 316 cầu thủ nguồn đủ trường, 26 vòng/182 trận mùa chính. Mặc định ngày mốc theo `Asia/Ho_Chi_Minh`; vòng 1–6 FINISHED, vòng 7 sau mốc ít nhất bảy ngày, các vòng sau theo tuần. Ngày là lịch demo dịch, tên/năm mùa là nhãn nguồn.
- Manager là tài khoản kỹ thuật; đội hình/sự kiện phục vụ demo không phải biên bản VPF đã xác minh. Repo không còn file nguồn/bằng chứng chi tiết hoặc công cụ tái sinh dataset. Sau import sạch, R07-T01 (ID43) trống cả hai bên; R07-T02 (ID44) có HOME 11 chính + 3 dự bị, AWAY trống; không hardcode các ID này vào ứng dụng.
- Lần refresh trước chỉ có 4/387 request HTTP 200, còn lại timeout; không tuyên bố toàn bộ 316 hồ sơ đã live-reverify. Dataset thiết kế 1.190 đăng ký/14 sự kiện; CT241 đã chấp hành án cộng dồn trước trận43, CT242/CT243 bị cấm trận43. Đây là contract fixture, không phải kết quả kiểm chứng runtime.
- Giữ `seasons.name`; `rounds.number` là cột nghiệp vụ mới theo yêu cầu10/11/2026, dương và duy nhất theo mùa. Không khôi phục `round_number`, `matches.match_number` hoặc metadata nguồn/cờ mô phỏng. Mọi thẻ đều tính treo giò.
- `Round.number` độc lập tên; DAO tìm/sắp xếp theo số lưu, không regex tên. Mã `R07-T01` dùng số vòng và thứ tự ID trận trong toàn vòng trước lọc, không theo kickoff. URL/session/FK dùng ID; đổi tên không đổi mã. Giữ transaction/baseline/cache.
- Ngày vòng/trận chưa xác định vẫn được NULL trong schema, hiển thị “Chưa xác định” và khóa CN2 khi kickoff NULL. Bộ demo bố trí ngày cụ thể cho ca cần lưu.
- Username: Player `CTxxx`, Manager `QLxxx`, Staff `NVxxx`, ít nhất ba chữ số và không cắt số >999. Reset đồng bộ `player_code_seq`; định danh VPF chỉ dùng mapping tạm hoặc file nguồn, không dùng username làm khóa nguồn.
- Không coi reset/migration/deploy đã chạy vì file tồn tại. Hướng dẫn setup khác kết quả thực chạy; snapshot bộ cũ không chứng minh bộ mới. Không đọc/in mật khẩu `.env` ra báo cáo.
- Đã thực thi reset ngày08/11/2026 sau backup và restore thử vào database cô lập; audit seed đạt, runtime COMMIT, WAR/image rebuild healthy.32 PostgreSQL tests đạt không skipped. Backup repo `pre-demo-reset-20261108.dump` chứa dữ liệu tài khoản, không commit. Sau browser smoke R07-T01 đã có HOME/AWAY11 chính, R07-T02 đã thay một dự bị; không coi runtime còn snapshot seed trống. CT317 tạo/xóa khi smoke, sequence tiếp theo đã tăng. Xem bằng chứng và giới hạn refresh nguồn trong README.

## 4. Quy ước phát triển

### Kiến trúc và dữ liệu

- Giữ pattern: route GET qua `index.jsp` → view trong `WEB-INF/views`; hành động ghi POST → validation/phân quyền → DAO → redirect.
- Đăng ký route mới đồng thời ở whitelist view, whitelist quyền và liên kết trên trang chủ. Không chỉ ẩn/hiện nút để bảo vệ quyền.
- Model chứa dữ liệu; DAO chứa SQL/mapping và transaction. Không đưa SQL mới trực tiếp vào JSP.
- Dùng `PreparedStatement`, try-with-resources; lấy cấu hình từ `DAO.getConnection()` qua `JDBC_URL`, `DB_USER`, `DB_PASSWORD`.
- Giữ tên bảng/cột snake_case như SQL hiện có. Không tạo bảng score/result song song khi đã có `v_match_scores` mà chưa giải quyết nguồn sự thật.
- Các thao tác nhiều bảng phải atomic; rollback khi lỗi. Kiểm tra điều kiện nghiệp vụ trong cùng transaction với ghi dữ liệu để tránh request đồng thời vượt luật.
- Migration thường phải bảo toàn dữ liệu; không drop/recreate schema hoặc vô hiệu hóa FK để tránh lỗi. Ngoại lệ reset demo được yêu cầu phải theo backup/dừng web/xác nhận/audit, không gọi ngầm từ startup.
- ID của `Manager`, `Staff`, `Player` là ID `users`; đừng nhầm với ID `club_players` hay `match_players`.

### Bảo mật và xử lý request

- Mọi endpoint ghi kiểm tra POST (sai method → 405), session và subtype quyền (không được phép → 403), ID/dữ liệu đầu vào phía server.
- CN2/CN3 yêu cầu `currentUser instanceof Staff` ở cả trang và endpoint ghi; không thay bằng quyền Manager hoặc giới hạn Staff chỉ được thao tác CLB của mình. Kiểm tra ID trận/đội/cầu thủ thuộc đúng ngữ cảnh đã chọn; không tin ID gửi từ form.
- Escape dữ liệu động bằng `HTMLUtil` trong HTML; không ghép dữ liệu người dùng vào JavaScript hoặc SQL.
- Giữ UTF-8, URL theo `request.getContextPath()`; không hardcode `/` vì WAR cũng có thể deploy dưới context `/football-manager`.
- SQL trong DAO khai báo biến `String` riêng trước khi gọi JDBC; text block cho câu nhiều dòng. Dùng chung quy ước keyword hoa, mỗi cột SELECT một dòng, từng JOIN/clause/predicate xuống dòng, không thay placeholder hoặc thứ tự bind để format.
- Đăng nhập hiện hủy session cũ và tạo session mới; giữ hành vi này.
- Ghi lỗi kỹ thuật qua `application.log`, hiển thị lỗi tiếng Việt có thể xử lý được; không lộ stack trace/SQL/credentials ra trang.
- Sau ghi thành công dùng Post/Redirect/Get và flash message; không để refresh gửi lại thao tác.
- Lưu CN2 có CSRF token đồng bộ form/handler. Các handler CN1 chưa có cơ chế CSRF chung; không mô tả POST-only là chống CSRF.

### Giao diện và scope

- Tái sử dụng topbar, breadcrumb, màu xanh, bảng, form, dialog hiện tại; không thêm dashboard/sidebar hoặc thay bộ nhận diện ngoài yêu cầu.
- JS chỉ hỗ trợ tương tác, không phải nơi thực thi duy nhất của luật đăng ký/tỷ số.
- Màn hình phải có trạng thái rỗng, validation lỗi, lỗi CSDL, thành công và thao tác bị khóa theo trạng thái trận.
- Không mở các chức năng chưa có end-to-end bằng nút dẫn tới placeholder.
- Tránh refactor toàn bộ chức năng cầu thủ trong lúc thêm chức năng trận đấu; xử lý các tác động thật của FK mới, nhất là xóa cầu thủ đã có lịch sử.

## 5. Build và chạy

Từ thư mục gốc, trên Windows:

```powershell
.\mvnw.cmd package -DskipTests
docker compose up -d --build --wait
docker compose ps
```

Nếu shell không nhận `.\mvnw.cmd`, dùng `cmd.exe /c mvnw.cmd package -DskipTests`. Linux/macOS dùng `./mvnw`.

- Web: `http://localhost:8088`.
- PostgreSQL: `127.0.0.1:5433`, database `football_db`, user `football_app`; mật khẩu lấy từ `.env`, không hardcode.
- Trong container web, JDBC trỏ `db:5432`; `localhost:5433` chỉ dành cho client trên máy host.
- Dockerfile dùng WAR đã build, không tự chạy Maven. Thay đổi Java/dependency cần build lại WAR và image để kiểm chứng.
- Compose mount webapp tại `/opt/dev/webapp`; `META-INF/context.xml` khai báo `PreResources` overlay thư mục này lên `/`. JSP/CSS/JS có đường nạp từ source khi chạy cấu hình Docker này; không suy ra Java đã hot reload. Build lại WAR/image khi cần kiểm chứng bản deploy đầy đủ hoặc thay cấu hình resource.
- `docker compose stop` dừng dịch vụ; `docker compose up -d` chạy lại image hiện có. Không dùng `down -v` trên dữ liệu người dùng.
- Log: `docker compose logs --tail=100 web` và `docker compose logs --tail=100 db`.

Tài khoản sau reset theo README: `NV001`/`staff@123`, `QL001`/`manager@123`, `CT001`/`player@123`, `khangia`/`spectator@123`. Mật khẩu băm pgcrypto, chỉ dùng cho demo. Không suy từ tài liệu rằng runtime đã reset hoặc đăng nhập đã được kiểm chứng.

## 6. Kiểm chứng và điều kiện hoàn thành

- `src/test/java` có JUnit CN2/CN3 trên PostgreSQL với schema UUID cô lập; cùng dùng biến CN2_TEST_* theo README. Các chương trình main() cũ không phải JUnit.
- `package -DskipTests` chỉ chứng minh build. JSP cần được truy cập thực trên Tomcat để phát hiện lỗi compile/runtime.
- Khi thêm nghiệp vụ, giữ regression test cho invariant quan trọng theo công cụ thực sự được bổ sung; không viết test kiểm tra chuỗi source hoặc mock trả lại đúng dữ liệu vừa truyền.
- Smoke qua browser: đăng nhập từng vai trò liên quan, mở trang, thao tác thành công, reload kiểm tra dữ liệu đã lưu; thử ID trái quyền, dữ liệu sai và trạng thái bị khóa.
- Dữ liệu demo CN1/CN2 phải đủ mùa/luật/vòng/trận, sân, hai CLB và Manager tương ứng, cầu thủ thuộc CLB. Tái lập qua reset có xác nhận; seed riêng từ chối database không rỗng. Không ghi đè dữ liệu thật để smoke.
- Kiểm tra hồi quy chức năng cầu thủ và đăng xuất. Không xóa hoặc sửa dữ liệu thật chỉ để chứng minh smoke.
- Cập nhật README/AGENTS.md khi triển khai thực tế làm thay đổi route, schema hoặc hướng dẫn chạy; chỉ cập nhật DESIGN.md cho thay đổi template giao diện. Thay đổi yêu cầu/mô hình phải đối chiếu báo cáo trong docs, không hợp thức hóa bằng đề xuất trong DESIGN.md. Báo đúng những gì đã chạy và các quyết định còn thiếu.

## 7. Trình tự cho lần triển khai tiếp theo

1. Đọc báo cáo phân tích và pha thiết kế CN tương ứng, SQL migration và handler gần nhất; kiểm tra schema runtime. Đọc DESIGN.md chỉ để giữ giao diện nhất quán.
2. Đối chiếu contract CN2/CN3 ở mục dưới; chỉ làm rõ phần chưa được báo cáo xác định, không hỏi lại actor Staff, chuỗi tìm kiếm, kế thừa sự kiện hoặc khóa thêm sự kiện sau kết thúc.
3. Dùng schema hiện có làm nền; bổ sung migration bảo toàn dữ liệu và demo cần thiết, không tạo quan hệ Club–Season hoặc lớp phiếu đã bị loại khỏi mô hình.
4. Tái sử dụng luồng mùa/vòng/trận và đội hình CN2 đã triển khai; không tạo lại luồng tìm kiếm phẳng cho CN3.
5. Giữ EventDAO và MatchDAO cùng khóa match row khi thêm/kết thúc để tương thích CN2; không mutate cache trước commit.
6. CN3 đã mở; mọi thay đổi tiếp theo phải giữ subtype, tỷ số derived, khóa FINISHED và smoke hồi quy CN2.

## 8. Contract CN2/CN3 theo báo cáo hệ thống

CN2 và CN3 bên dưới đều đã có triển khai. Đối chiếu chi tiết báo cáo tương ứng; nguồn code và kiểm chứng runtime quyết định bằng chứng thực chạy, không chỉ nội dung thiết kế.

### Luồng chung và session

- Cả hai chức năng bắt đầu từ menu Staff → `gdSeasonSearch.jsp` → `gdRoundSearch.jsp` → `gdMatchSearch.jsp`. Giữ đủ ba cấp tìm kiếm, không thay bằng một danh sách trận phẳng chỉ vì template cũ đề xuất như vậy.
- `SeasonDAO.searchSeason(tournament, key)`, `RoundDAO.searchRound(season, key)`, `MatchDAO.searchMatch(round, key)` trả `List<T>`. `TournamentDAO.getTournament()` yêu cầu đúng một dòng, không hardcode ID hoặc tùy tiện lấy dòng đầu.
- Trang tìm kiếm submit về chính luồng trang đó; lưu danh sách kết quả vào session. Chọn seasonId/roundId/matchId phải khớp đối tượng trong ngữ cảnh session, không nhận đối tượng tùy ý chỉ từ hidden input.
- `searchMatch()` tải đầy đủ `Match.matchClubs → MatchClub.matchPlayers → MatchPlayer.clubPlayer` và các tham chiếu cần hiển thị. Trang sau lấy đội hình từ Match trong session, không thêm DAO lấy lại đội hình cho mỗi trang/chọn CLB.
- Dùng `currentUser` hiện có làm nguồn xác thực Staff; không tạo thêm một nguồn xác thực song song.
- Khi đổi mùa/vòng/trận, không để selection/list cầu thủ của ngữ cảnh cũ bị dùng nhầm. Session là cache cho luồng màn hình, không thay kiểm tra quyền, trạng thái và ràng buộc hiện thời trong transaction ghi. Thiếu/hết session phải có đường quay lại tìm kiếm an toàn.

### CN2 — Staff đăng ký đội hình hai phía

- Sau chọn trận: `gdMatchInfo.jsp` hiển thị thời gian, sân derived từ CLB HOME, hai bảng đội hình và hai link đăng ký HOME/AWAY. Trang này đọc Match đã có trong session, không gọi DAO tải đội hình.
- `gdLineupRegistration.jsp` lấy Season và MatchClub từ ngữ cảnh đó, gọi `ClubDAO.getClubPlayerList(club)` để hiển thị thành viên CLB và lựa chọn vai trò.
- POST `doSaveLineup.jsp` đóng gói các cặp ClubPlayer–role thành MatchPlayer trong MatchClub ứng viên riêng rồi gọi `MatchDAO.saveLineup(matchBaseline, candidate): boolean`. Giữ nguyên Match baseline/cache trước commit.
- Sau lưu quay về thông tin trận và hiển thị đội hình mới hoặc lỗi. Chỉ cập nhật cache thành công sau commit; lỗi không được để session hiển thị đội hình chưa lưu như đã thành công.
- Điều kiện đội hình theo báo cáo phân tích: đủ 11 STARTING, ít nhất một thủ môn, không vượt dự bị/ngoại binh theo Rule; treo giò derived từ thẻ theo mùa và ngưỡng luật. Không bỏ các điều kiện đã được báo cáo nêu vì SQL migration chưa thực thi chúng.
- Quyết định đã chốt: `requiredMainPlayers` khác 11 là lỗi cấu hình; ít nhất một STARTING có vị trí `Thủ môn`. Quốc tịch trim không phân biệt hoa/thường: `Việt Nam` là nội binh, khác là ngoại binh, thiếu không hợp lệ; quota tính toàn danh sách. Tuổi tối đa `ageLimit` ở ngày khai mạc mùa, thiếu/ngày sinh sau khai mạc bị từ chối.
- Mọi thẻ đều tính: mỗi ngưỡng vàng tích lũy và mỗi thẻ đỏ rõ ràng phát sinh một trận cấm, cộng dồn trong mùa; chấp hành ở trận FINISHED tiếp theo của CLB nhận thẻ theo kickoff. Hai vàng tính hai vàng, chỉ bản ghi RedCard phát sinh án đỏ; không miễn vì nguồn/cách tạo ca demo.
- Chỉ lưu khi SCHEDULED trước kickoff và chưa có event; thay thế giữ ID/registeredAt của đăng ký còn lại, từ chối baseline cũ khác DB; không thêm draft/approval.

### CN3 — Staff thêm sự kiện và kết thúc trận

- Sau chọn trận: `gdMatchEvent.jsp` gọi `EventDAO.getEventList(matchId): Event[]`, dùng hai đội hình trong Match để lưu `listPlayerHome`/`listPlayerAway` vào session; hiển thị tỷ số và sự kiện.
- `gdEventForm.jsp` có CLB, loại sự kiện, phút thi đấu, phút bù giờ, cầu thủ chính, cầu thủ liên quan và ghi chú. Khi đổi CLB, lấy danh sách đăng ký từ session, không truy vấn lại đội hình. Lựa chọn cầu thủ liên quan phải đúng quan hệ của subtype (kiến tạo, nạn nhân, người vào sân), không mặc định mọi trường hợp đều cùng CLB với actor.
- POST `doAddEvent.jsp` đóng gói đúng subtype và gọi `EventDAO.addEvent(match, event): boolean`; sau commit quay về sự kiện trận, hiển thị tỷ số mới và event đã lưu hoặc thông báo lỗi.
- POST `doEndMatch.jsp` gọi `MatchDAO.saveMatch(match): boolean` để lưu kết thúc; quay về `gdMatchEvent.jsp` hiển thị kết quả cuối cùng và khóa thêm sự kiện. Endpoint ghi phải kiểm tra lại FINISHED, không chỉ khóa UI.
- Luồng được báo cáo mô tả là **thêm sự kiện + kết thúc trận**, không mặc nhiên yêu cầu sửa/xóa sự kiện hay mở lại trận sau chốt. Không tự thêm các tính năng đó từ đề xuất cũ trong DESIGN.md.
- Tỷ số là derived trên từng MatchClub: NORMAL/PENALTY cộng bên ghi bàn, OWN cộng bên kia. `addEvent()` “tự cập nhật tỷ số” phải tương thích nguồn dữ liệu derived (`v_match_scores`) và refresh giá trị trên model/session, không lưu thêm một tỷ số độc lập hoặc cho nhập tổng thủ công.
- Report xác định tập status SCHEDULED/ONGOING/FINISHED và khóa sau kết thúc; chưa mô tả action bắt đầu trận hay đầy đủ luật chuyển trạng thái. Không tự thêm màn hình bắt đầu hoặc quy trình duyệt ngoài kịch bản.

### Mô hình và DAO không được làm lệch

- Chủ thể `Match` chứa `MatchClub`, MatchClub giữ Club và chứa MatchPlayer, MatchPlayer giữ ClubPlayer; `Club` chứa ClubPlayer, ClubPlayer giữ Player. Không tạo `MatchClubDAO`, `MatchPlayerDAO`, `ClubPlayerDAO`; thao tác qua MatchDAO/ClubDAO.
- Không nối trực tiếp Club–Season, không khôi phục RegisteredClub/RegisteredPlayer, phiếu LineupRegistration/LineupDetail hoặc EventDetail. `getClubPlayerList` chỉ nhận Club. Tournament chứa Season; Season không giữ tham chiếu ngược Tournament; chuỗi model khác cũng một chiều theo chủ thể chứa.
- `Event` là lớp độc lập có EventDAO và các subtype Goal/Foul/YellowCard/RedCard/Substitution; không thay bằng một model generic chỉ chứa eventType và hai ID cầu thủ. UI vẫn có select loại sự kiện; Goal có type NORMAL/PENALTY/OWN.
- Event có primaryPlayer; Goal có assister nullable chỉ cho NORMAL; Foul có victim; Substitution có playerIn, người ra là primaryPlayer. Người vào chỉ vào sân một lần trong trận; cầu thủ sự kiện phải thuộc đội hình cùng trận.
- Lớp/entity đặt tên số ít; quan hệ nhiều biểu diễn bằng List<T>, không tạo lớp “DanhSach...”. Tỷ số, sân trận và treo giò là derived. Giữ FK vật lý để truy vấn không đồng nghĩa phải thêm quan hệ đối tượng ngược hoặc bảng dư thừa vào mô hình chốt.

### Kiểm chứng bổ sung theo báo cáo

- Staff đi được đủ mùa → vòng → trận ở cả hai chức năng; Manager/Player/User bị từ chối ở trang lẫn POST.
- Staff lưu được đội hình HOME và AWAY, xem lại đúng bên; ID cầu thủ/đội/trận sai ngữ cảnh bị từ chối; không truy vấn lại đội hình chỉ vì chuyển view/chọn CLB.
- Đội hình sai quota/thủ môn hoặc điều kiện luật đã xác định bị từ chối; lỗi lưu không tạo dữ liệu hoặc cache thành công giả.
- Thêm từng subtype, kiểm tra cầu thủ liên quan và bàn phản lưới, tỷ số/diễn biến sau reload; kết thúc trận 0–0 hợp lệ; thêm sự kiện bằng POST sau kết thúc bị chặn.
