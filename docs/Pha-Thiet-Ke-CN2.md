# PHA THIẾT KẾ — CHỨC NĂNG 2: ĐĂNG KÝ CẦU THỦ THI ĐẤU

Kiến trúc: **JSP – DAO**, không Controller. Java + JSP + PostgreSQL (JDBC).

> Danh tính đăng nhập kế thừa CN1 là session `currentUser: User` qua `UserDAO`, không phải session `staff`. Các tham số ID chỉ chọn đối tượng trong ngữ cảnh đã tải, không tạo đối tượng tin cậy trực tiếp từ request.

---

## 1. THIẾT KẾ TĨNH — FULL VIEW CLASS DIAGRAM

### 1.1. Tầng giao diện gồm các trang .jsp

**Giao diện tìm mùa giải: gdSeasonSearch.jsp**
- Thuộc tính tường minh:
  - searchKey: input
  - btnSearch: submit
  - tblSeason: Table
  - chooseSeason: link
- Thuộc tính ẩn:
  - currentUser: User
  - listSeason: Season[]
  - tournament: Tournament (từ TournamentDAO.getTournament(), duy nhất một bản ghi)
- Submit về chính trang này (action: self): nhận searchKey từ param, gọi SeasonDAO thực hiện searchSeason(tournament, searchKey).
- Hiển thị tên giải từ Tournament.name, tên mùa chính thức từ Season.name; tìm theo tên mùa, năm hoặc trạng thái. Không ghép tên giải + năm làm tên mùa.

**Giao diện tìm vòng đấu: gdRoundSearch.jsp**
- Thuộc tính tường minh:
  - searchKey: input
  - btnSearch: submit
  - tblRound: Table
  - chooseRound: link
- Thuộc tính ẩn:
  - currentUser: User
  - season: Season (đọc session khớp seasonId từ param)
  - listRound: Round[]
- Submit về chính trang này (action: self): nhận searchKey từ param + đọc session lấy season, gọi RoundDAO thực hiện searchRound(season, key).

**Giao diện tìm trận đấu: gdMatchSearch.jsp**
- Thuộc tính tường minh:
  - searchKey: input
  - btnSearch: submit
  - tblMatch: Table
  - chooseMatch: link
- Thuộc tính ẩn:
  - currentUser: User
  - round: Round (đọc session khớp roundId từ param)
  - listMatch: Match[]
- Submit về chính trang này (action: self): nhận searchKey từ param + đọc session lấy round, gọi MatchDAO thực hiện searchMatch(round, key) — hàm tải trọn chuỗi ◆ (MatchPlayer → MatchClub → Match), lưu listMatch vào session.

**Giao diện thông tin trận: gdMatchInfo.jsp**
- Thuộc tính tường minh:
  - lblMatchTime: label
  - lblStadium: label
  - tblHomeLineup: Table
  - tblAwayLineup: Table
  - registerHome: link
  - registerAway: link
- Thuộc tính ẩn:
  - currentUser: User
  - match: Match (đọc session khớp matchId từ param — đã chứa đầy đủ matchClubs + matchPlayers từ searchMatch)
  - homeLineup: MatchPlayer[]
  - awayLineup: MatchPlayer[]
- Đọc session lấy match → hiển thị thông tin trận + 2 bảng đội hình. KHÔNG gọi DAO — đội hình đã có sẵn trong match từ searchMatch.

**Giao diện đăng ký đội hình: gdLineupRegistration.jsp**
- Thuộc tính tường minh:
  - tblPlayer: Table
  - roleSelect: select
  - btnSaveLineup: submit
- Thuộc tính ẩn:
  - currentUser: User
  - matchClub: MatchClub (truy xuất từ match trong session)
  - season: Season (đọc session)
  - listClubPlayer: ClubPlayer[]
- Đọc session lấy season + matchclub (truy xuất từ match), gọi ClubDAO thực hiện getClubPlayerList(club).

### 1.2. Tầng thao tác dữ liệu có các lớp DAO

**TournamentDAO:**
- Phương thức getTournament(): Tournament — CSDL phải có đúng một Tournament; không có hoặc nhiều hơn một là lỗi cấu hình, không chọn tùy ý bản ghi đầu.

**SeasonDAO:**
- Phương thức searchSeason(tournament: Tournament, key: String): Season[]

**RoundDAO:**
- Phương thức searchRound(season: Season, key: String): Round[]

**MatchDAO (DAO của chủ thể Match — thao tác trên trung gian MatchClub/MatchPlayer):**
- Phương thức searchMatch(round: Round, key: String): Match[] — tải trọn chuỗi ◆: MatchPlayer → MatchClub → Match
- Phương thức saveLineup(match: Match, matchClub: MatchClub): boolean — match là đối tượng chủ thể; matchClub xác định bên đang đăng kí (đã chứa MatchPlayer[] mới, ClubPlayer đầy đủ từ session)

**ClubDAO (DAO của chủ thể Club — thao tác trên trung gian ClubPlayer):**
- Phương thức getClubPlayerList(club: Club): ClubPlayer[]

> **Quy tắc trung gian:** MatchClub, ClubPlayer, MatchPlayer là lớp trung gian ◆ sinh từ quan hệ n–n — KHÔNG có DAO riêng. Thao tác qua DAO của chủ thể: MatchPlayer/MatchClub → MatchDAO, ClubPlayer → ClubDAO.

#### Quy tắc CN2 đã chốt

- **Ngữ cảnh một chiều:** `Tournament.seasons → Season.rounds → Round.matches → Match.matchClubs → MatchClub.matchPlayers`; Season không giữ Tournament. `Club.clubPlayers` chứa danh sách cầu thủ CLB; `getClubPlayerList(Club)` không nhận Season. Điều lệ lấy từ Season trong ngữ cảnh cha đã chọn, không thêm liên kết ngược vào entity.
- **Mã hiển thị:** giữ `Season.name`/`seasons.name`. Số vòng suy ra từ tên chuẩn `Vòng 1` ... `Vòng 26`, không phải khóa chính; `R07` và `Vòng 7` tìm cùng vòng, tên không có số dùng fallback ổn định. Trận thứ nhất của vòng 7 hiển thị `R07-T01`; số thứ tự trận derived theo ID trong **toàn vòng trước khi lọc từ khóa**, không theo kickoff để mã không đổi khi sửa lịch. Các giá trị hiển thị có thể derived trong Java nhưng không có cột `round_number`/`match_number`. ID nội bộ giữ nguyên cho session/URL/FK; không renumber khóa chính để đổi nhãn.
- **Cấu hình và vai trò:** `Rule.requiredMainPlayers` bắt buộc bằng **11**; giá trị khác là lỗi cấu hình, không đổi số người đá chính theo giá trị đó. Đội hình có đúng 11 `STARTING`, trong đó ít nhất một cầu thủ có `primaryPosition` là **Thủ môn**; số `SUBSTITUTE` không vượt `maxBenchPlayers`. Mỗi cầu thủ chỉ xuất hiện một lần và thuộc đúng CLB của MatchClub đang đăng ký.
- **Tuổi:** tuổi tròn tại `Season.startDate` phải **≤ `Rule.ageLimit`**, áp dụng cho cả đá chính và dự bị; không tính theo ngày lưu hay ngày đá trận.
- **Quốc tịch:** trim trước khi so sánh không phân biệt hoa/thường; **Việt Nam** là nội binh, quốc tịch hợp lệ khác là ngoại binh. Quốc tịch thiếu/rỗng là dữ liệu không hợp lệ, không xem là nội binh. Tổng ngoại binh trên **toàn bộ đội hình** (STARTING + SUBSTITUTE) không vượt `maxForeignPlayers`.
- **Treo giò:** trong cùng mùa, mỗi lần tổng thẻ vàng đạt thêm một ngưỡng `yellowCardsToSuspend` và mỗi **RedCard được ghi tường minh** tạo thêm một trận cấm; các án cộng dồn. Hai YellowCard chỉ được tính là hai thẻ vàng, không tự sinh thêm RedCard. Xét theo thứ tự kickoff, án được chấp hành ở những trận **FINISHED kế tiếp của chính CLB nơi cầu thủ nhận thẻ**, không cần cầu thủ có mặt trong đội hình trận chấp hành án. Trận chưa FINISHED không xóa án; không dùng tổng thẻ suốt mùa để cấm vĩnh viễn sau ngưỡng đầu tiên.
- **Bộ demo và thẻ:** 1 Tournament, 5 Season (mùa chính 2026/27 và bốn mùa cũ chỉ có thông tin mùa), 14 CLB, 26 vòng/182 trận mùa chính. Cặp đấu và hồ sơ CLB/cầu thủ dựa VPF; lịch dịch theo ngày mốc seed, đội hình/diễn biến được chuẩn bị cho ca nghiệp vụ, không khẳng định là lịch sử đã xác minh. **Mọi thẻ đã lưu đều tính treo giò**, kể cả thẻ của ca demo; không cờ `is_simulated`/`simulated`, không miễn thẻ hay nhãn/cảnh báo mô phỏng trên UI. Nguồn và khác biệt ghi trong file repo, không thêm metadata bền vững.
- **Điều kiện lưu:** `saveLineup(Match, MatchClub)` chỉ lưu khi CSDL mới nhất vẫn có `Match.status = SCHEDULED`, kickoff **khác null**, hiện tại trước kickoff và trận có **0 sự kiện**. Ngày chưa công bố hiển thị “Chưa xác định”, không cho đăng ký. DAO kiểm tra lại luật và trạng thái trong transaction; session không thay thế kiểm tra CSDL.
- **Toàn vẹn cập nhật:** so sánh baseline đội hình đã đọc với CSDL, từ chối baseline cũ thay vì ghi đè chỉnh sửa đồng thời. Cầu thủ được giữ lại (kể cả đổi vai trò) giữ nguyên `MatchPlayer.id` và `registeredAt`; chỉ cầu thủ mới có đăng ký mới. Cập nhật nguyên tử đúng MatchClub được chọn, không xóa/tạo lại toàn bộ và không sửa đội hình bên còn lại. Sau khi lưu, cập nhật ngữ cảnh session bằng kết quả đã lưu; nếu lỗi, không công bố thành công hoặc giữ đội hình chưa lưu làm dữ liệu đã xác nhận.

CN2 đọc lịch sử sự kiện để kiểm tra treo giò; điều này **không đồng nghĩa CN3 đã được hiện thực**. Luồng tạo sự kiện/kết thúc trận vẫn là thiết kế trong `Pha-Thiet-Ke-CN3.md`.


### 1.3. Tầng thực thể

Gồm tất cả các thực thể liên quan (xem biểu đồ).

### 1.4. Biểu đồ Full View

```mermaid
classDiagram
    direction LR

    class StaffActor["Staff"]
    class gdLogin {
        <<jsp gd>>
        +username: input
        +password: input
        +btnLogin: submit
        +action: doLogin.jsp
    }
    class doLogin {
        <<jsp do>>
        +user: User
        +call UserDAO: checkLogin
    }
    class gdHome {
        <<jsp gd>>
        +currentUser: User
        +managePlayer: link
        +registerLineup: link
        +updateResult: link
    }
    class gdSeasonSearch {
        <<jsp gd>>
        +currentUser: User
        +searchKey: input
        +btnSearch: submit
        +action: gdSeasonSearch.jsp
        +listSeason: Season[]
        +tournament: Tournament
        +tblSeason: Table
        +chooseSeason: link
    }
    class gdRoundSearch {
        <<jsp gd>>
        +currentUser: User
        +season: Season
        +searchKey: input
        +btnSearch: submit
        +action: gdRoundSearch.jsp
        +listRound: Round[]
        +tblRound: Table
        +chooseRound: link
    }
    class gdMatchSearch {
        <<jsp gd>>
        +currentUser: User
        +round: Round
        +searchKey: input
        +btnSearch: submit
        +action: gdMatchSearch.jsp
        +listMatch: Match[]
        +tblMatch: Table
        +chooseMatch: link
    }
    class gdMatchInfo {
        <<jsp gd>>
        +currentUser: User
        +match: Match
        +lblMatchTime: label
        +lblStadium: label
        +homeLineup: MatchPlayer[]
        +tblHomeLineup: Table
        +awayLineup: MatchPlayer[]
        +tblAwayLineup: Table
        +registerHome: link
        +registerAway: link
    }
    class gdLineupRegistration {
        <<jsp gd>>
        +currentUser: User
        +matchClub: MatchClub
        +season: Season
        +listClubPlayer: ClubPlayer[]
        +tblPlayer: Table
        +roleSelect: select
        +btnSaveLineup: submit
        +action: doSaveLineup.jsp
    }
    class doSaveLineup {
        <<jsp do>>
        +listMatchPlayer: MatchPlayer[]
        +matchClub: MatchClub
        +match: Match
        +call MatchDAO: saveLineup
    }

    class DAO {
        <<abstract>>
        +DAO()
        #con: Connection
    }
    class UserDAO {
        +UserDAO()
        +checkLogin(user: User): User
    }
    class TournamentDAO {
        +TournamentDAO()
        +getTournament(): Tournament
    }
    DAO <|-- TournamentDAO
    class SeasonDAO {
        +SeasonDAO()
        +searchSeason(tournament: Tournament, key: String): Season[]
    }
    class RoundDAO {
        +RoundDAO()
        +searchRound(season: Season, key: String): Round[]
    }
    class MatchDAO {
        +MatchDAO()
        +searchMatch(round: Round, key: String): Match[]
        +saveLineup(match: Match, matchClub: MatchClub): boolean
    }
    class ClubDAO {
        +ClubDAO()
        +getClubPlayerList(club: Club): ClubPlayer[]
    }
    DAO <|-- UserDAO
    DAO <|-- SeasonDAO
    DAO <|-- RoundDAO
    DAO <|-- MatchDAO
    DAO <|-- ClubDAO

    class User {
        +id: int
        +username: String
        +password: String
        +fullName: String
        +dob: LocalDate
        +email: String
        +phone: String
    }
    class Staff {
        +position: String
    }
    class Manager {
    }
    class Player {
        +nationality: String
        +primaryPosition: String
        +heightCm: double
        +weightKg: double
    }
    class Tournament {
        +id: int
        +name: String
        +seasons: Season[]
    }
    class Season {
        +id: int
        +year: int
        +name: String
        +startDate: LocalDate
        +endDate: LocalDate
        +status: String
        +rule: Rule
        +rounds: Round[]
    }
    class Rule {
        +id: int
        +requiredMainPlayers: int
        +maxBenchPlayers: int
        +maxSubstitutions: int
        +yellowCardsToSuspend: int
        +ageLimit: int
        +maxForeignPlayers: int
    }
    class Round {
        +id: int
        +name: String
        +startTime: LocalDateTime
        +endTime: LocalDateTime
        +status: String
        +matches: Match[]
    }
    class Match {
        +id: int
        +kickoffTime: LocalDateTime
        +status: String
        +matchClubs: MatchClub[]
        +events: Event[]
    }
    class MatchClub {
        +id: int
        +role: String
        +score: int
        +club: Club
        +matchPlayers: MatchPlayer[]
    }
    class ClubPlayer {
        +id: int
        +shirtNumber: int
        +player: Player
    }
    class MatchPlayer {
        +id: int
        +registeredAt: LocalDateTime
        +role: String
        +clubPlayer: ClubPlayer
    }
    class Event {
        <<abstract>>
        +id: int
        +minute: int
        +extraMinute: int
        +note: String
        +primaryPlayer: MatchPlayer
    }
    class Goal {
        +type: String
        +assister: MatchPlayer
    }
    class Foul {
        +victim: MatchPlayer
    }
    class YellowCard {
    }
    class RedCard {
    }
    class Substitution {
        +playerIn: MatchPlayer
    }
    class Club {
        +id: int
        +fullName: String
        +shortName: String
        +foundingYear: int
        +coachName: String
        +manager: Manager
        +homeStadium: Stadium
        +clubPlayers: ClubPlayer[]
    }
    class Stadium {
        +id: int
        +name: String
        +capacity: int
        +address: String
    }

    User <|-- Staff
    User <|-- Manager
    User <|-- Player
    Event <|-- Goal
    Event <|-- Foul
    Event <|-- YellowCard
    Event <|-- RedCard
    Event <|-- Substitution
    Tournament "1" *-- "1..*" Season
    Season "0..*" o-- "1" Rule
    Season "1" *-- "1..*" Round
    Round "1" *-- "1..*" Match
    Match "1" *-- "0..*" Event
    Club "1" o-- "1" Manager : managed by
    Club "0..*" o-- "1" Stadium : home stadium
    Match "1" *-- "2" MatchClub
    MatchClub "0..*" o-- "1" Club
    Club "1" *-- "0..*" ClubPlayer
    ClubPlayer "0..*" o-- "1" Player
    MatchClub "1" *-- "0..*" MatchPlayer
    MatchPlayer "0..*" o-- "1" ClubPlayer
    Event "0..*" o-- "1" MatchPlayer : primary
    Goal "0..*" o-- "0..1" MatchPlayer : assister
    Foul "0..*" o-- "1" MatchPlayer : victim
    Substitution "0..1" o-- "1" MatchPlayer : player in

    StaffActor --> gdLogin
    gdLogin --> doLogin : submit [btnLogin]
    doLogin --> gdHome : redirect
    gdHome --> gdSeasonSearch : link [registerLineup]
    gdSeasonSearch --> gdSeasonSearch : submit [btnSearch]
    gdSeasonSearch --> gdRoundSearch : link [chooseSeason]
    gdRoundSearch --> gdRoundSearch : submit [btnSearch]
    gdRoundSearch --> gdMatchSearch : link [chooseRound]
    gdMatchSearch --> gdMatchSearch : submit [btnSearch]
    gdMatchSearch --> gdMatchInfo : link [chooseMatch]
    gdMatchInfo --> gdLineupRegistration : link [registerHome / registerAway]
    gdLineupRegistration --> doSaveLineup : submit [btnSaveLineup]
    doSaveLineup --> gdMatchInfo : redirect

    doLogin ..> UserDAO : checkLogin
    gdSeasonSearch ..> TournamentDAO : getTournament()
    gdSeasonSearch ..> SeasonDAO : searchSeason
    gdRoundSearch ..> RoundDAO : searchRound
    gdMatchSearch ..> MatchDAO : searchMatch
    gdLineupRegistration ..> ClubDAO : getClubPlayerList
    doSaveLineup ..> MatchDAO : saveLineup
```

---

## 2. MÔ TẢ ACTIVITY DIAGRAM

**Các activity đại diện cho các lớp giao diện, kèm các action của giao diện**

**gdHome.jsp**
- Nhận currentUser từ session
- Hiện giao diện (menu)
- Nhân viên click nút registerLineup → chuyển gdSeasonSearch.jsp

**gdSeasonSearch.jsp**
- Hiện giao diện (form tìm kiếm)
- Nhận searchKey từ param khi nhân viên click nút btnSearch
- Gọi TournamentDAO.getTournament() lấy đúng một giải đấu trước khi tìm mùa; thiếu/nhiều bản ghi báo lỗi cấu hình.
- Gọi SeasonDAO thực hiện searchSeason(tournament, searchKey) — lấy danh sách mùa giải khớp từ khoá
- Lưu danh sách Season[] vào session
- Hiện danh sách mùa giải cho nhân viên
- Nhân viên click vào dòng mùa giải → chuyển gdRoundSearch.jsp

**gdRoundSearch.jsp**
- Hiện giao diện (form tìm kiếm)
- Nhận searchKey từ param, đọc session lấy đối tượng season khớp seasonId từ param
- Gọi RoundDAO thực hiện searchRound(season, searchKey) — lấy danh sách vòng đấu khớp từ khoá
- Lưu danh sách Round[] vào session
- Hiện danh sách vòng đấu cho nhân viên
- Nhân viên click vào dòng vòng đấu → chuyển gdMatchSearch.jsp

**gdMatchSearch.jsp**
- Hiện giao diện (form tìm kiếm)
- Nhận searchKey từ param, đọc session lấy đối tượng round khớp roundId từ param
- Gọi MatchDAO thực hiện searchMatch(round, searchKey) — lấy danh sách trận đấu khớp từ khoá
- Lưu danh sách Match[] vào session
- Hiện danh sách trận đấu cho nhân viên
- Nhân viên click vào dòng trận đấu → chuyển gdMatchInfo.jsp

**gdMatchInfo.jsp**
- Đọc session lấy đối tượng match khớp matchId từ param
- Hiện thông tin trận và đội hình cho nhân viên
- Nhân viên click nút register home/away lineup → chuyển gdLineupRegistration.jsp
- Nhận lại từ doSaveLineup.jsp → hiện đội hình vừa đăng ký

**gdLineupRegistration.jsp**
- Đọc session lấy matchClub (truy xuất từ match)
- Gọi ClubDAO thực hiện getClubPlayerList(club) — lấy danh sách cầu thủ của CLB
- Hiện danh sách cầu thủ cho nhân viên
- Nhận vai trò nhân viên chọn cho các cầu thủ, click nút btnSaveLineup → chuyển doSaveLineup.jsp

**doSaveLineup.jsp**
- Nhận danh sách cầu thủ + vai trò từ request
- Đóng gói danh sách đề xuất tách khỏi baseline session; tái sử dụng id/registeredAt của cầu thủ được giữ, không sửa baseline trước khi DAO xác nhận lưu thành công.
- Gọi MatchDAO thực hiện saveLineup(match, matchClub) — lưu danh sách đăng ký thi đấu của CLB vào CSDL
- DAO kiểm tra CSDL trong giao dịch (baseline, trạng thái, thời gian theo đồng hồ CSDL, chưa có sự kiện, điều lệ và treo giò); lỗi thì không ghi thay đổi, thành công mới cập nhật ngữ cảnh session.
- Chuyển về gdMatchInfo.jsp
- Hiển thị đội hình vừa đăng ký nếu lưu thành công / thông báo lỗi nếu không thành công

---

---

## 3. THIẾT KẾ ĐỘNG

### 3.1. Kịch bản

| Bước | Nội dung thao tác |
|---|---|
| 1 | Nhân viên BTC click nút registerLineup |
| 2 | gdHome.jsp gọi gdSeasonSearch.jsp |
| 3 | gdSeasonSearch.jsp hiển thị giao diện cho nhân viên |
| 4 | Nhân viên BTC nhập "2026", click nút btnSearch |
| 5 | gdSeasonSearch.jsp nhận searchKey từ request |
| 6 | gdSeasonSearch.jsp gọi TournamentDAO.getTournament(), giữ tournament trong ngữ cảnh rồi gọi SeasonDAO |
| 7 | SeasonDAO thực hiện hàm searchSeason(tournament, searchKey) |
| 8 | Hàm searchSeason(tournament, searchKey) thực hiện và gọi lớp Season |
| 9 | Lớp Season khởi tạo đối tượng season và thực hiện hàm setter() |
| 10 | Lớp Season trả danh sách seasons[] cho hàm searchSeason(tournament, searchKey) |
| 11 | Hàm searchSeason(tournament, searchKey) trả danh sách seasons[] cho gdSeasonSearch.jsp |
| 12 | gdSeasonSearch.jsp lưu danh sách seasons[] vào session |
| 13 | gdSeasonSearch.jsp hiển thị danh sách kết quả cho nhân viên. |
| 14 | Nhân viên BTC click vào dòng mùa giải |
| 15 | gdSeasonSearch.jsp gọi gdRoundSearch.jsp |
| 16 | gdRoundSearch.jsp hiển thị giao diện cho nhân viên |
| 17 | Nhân viên BTC nhập "5", click nút btnSearch |
| 18 | gdRoundSearch.jsp nhận searchKey từ request, đọc session lấy đối tượng season khớp seasonId từ param |
| 19 | gdRoundSearch.jsp gọi lớp RoundDAO |
| 20 | RoundDAO thực hiện hàm searchRound(season, searchKey) |
| 21 | Hàm searchRound(season, searchKey) thực hiện và gọi lớp Round |
| 22 | Lớp Round khởi tạo đối tượng round và thực hiện hàm setter() |
| 23 | Lớp Round trả danh sách rounds[] cho hàm searchRound(season, searchKey) |
| 24 | Hàm searchRound(season, searchKey) trả danh sách rounds[] cho gdRoundSearch.jsp |
| 25 | gdRoundSearch.jsp lưu danh sách rounds[] vào session |
| 26 | gdRoundSearch.jsp hiển thị danh sách kết quả cho nhân viên |
| 27 | Nhân viên BTC click vào dòng vòng đấu |
| 28 | gdRoundSearch.jsp gọi gdMatchSearch.jsp |
| 29 | gdMatchSearch.jsp hiển thị giao diện cho nhân viên |
| 30 | Nhân viên BTC nhập "Hà Nội FC", click nút btnSearch |
| 31 | gdMatchSearch.jsp nhận searchKey từ request, đọc session lấy đối tượng round khớp roundId từ param |
| 32 | gdMatchSearch.jsp gọi lớp MatchDAO |
| 33 | MatchDAO thực hiện hàm searchMatch(round, searchKey) |
| 34 | Hàm searchMatch(round, searchKey) thực hiện và gọi lớp Match |
| 35 | Lớp Match khởi tạo đối tượng match |
| 36 | Lớp Match gọi lớp MatchClub |
| 37 | Lớp MatchClub khởi tạo đối tượng matchClub |
| 38 | Lớp MatchClub gọi lớp MatchPlayer |
| 39 | Lớp MatchPlayer khởi tạo đối tượng matchPlayer và thực hiện hàm setter() |
| 40 | Lớp MatchPlayer trả danh sách matchPlayers[] cho lớp MatchClub |
| 41 | Lớp MatchClub đóng gói danh sách matchPlayers[] vào đối tượng matchClub |
| 42 | Lớp MatchClub trả về danh sách matchClubs[] cho lớp Match |
| 43 | Lớp Match đóng gói danh sách matchClubs[] vào đối tượng match |
| 44 | Lớp Match trả danh sách matchs[] cho hàm searchMatch(round, searchKey) |
| 45 | Hàm searchMatch(round, searchKey) trả danh sách matchs[] cho gdMatchSearch.jsp |
| 46 | gdMatchSearch.jsp lưu danh sách matchs[] vào session |
| 47 | gdMatchSearch.jsp hiển thị danh sách kết quả cho nhân viên |
| 48 | Nhân viên BTC click vào dòng trận đấu |
| 49 | gdMatchSearch.jsp gọi gdMatchInfo.jsp |
| 50 | gdMatchInfo.jsp đọc session lấy đối tượng match khớp matchId từ param |
| 51 | gdMatchInfo.jsp hiển thị thông tin trận và đội hình cho nhân viên |
| 52 | Staff click nút register home/away lineup |
| 53 | gdMatchInfo.jsp gọi gdLineupRegistration.jsp |
| 54 | gdLineupRegistration.jsp đọc session lấy matchClub (truy xuất từ match) |
| 55 | gdLineupRegistration.jsp gọi lớp ClubDAO |
| 56 | ClubDAO thực hiện hàm getClubPlayerList(club) |
| 57 | Hàm getClubPlayerList(club) thực hiện và gọi lớp ClubPlayer |
| 58 | Lớp ClubPlayer thực hiện hàm setter() |
| 59 | Lớp ClubPlayer trả danh sách clubPlayers[] cho hàm getClubPlayerList(club) |
| 60 | Hàm getClubPlayerList(club) trả danh sách clubPlayers[] cho gdLineupRegistration.jsp |
| 61 | gdLineupRegistration.jsp hiển thị danh sách cầu thủ của CLB cho nhân viên |
| 62 | Nhân viên BTC chọn vai trò cho các cầu thủ, click nút btnSaveLineup |
| 63 | gdLineupRegistration.jsp gọi doSaveLineup.jsp |
| 64 | doSaveLineup.jsp nhận danh sách cầu thủ + vai trò từ request |
| 65 | doSaveLineup.jsp gọi lớp MatchPlayer |
| 66 | Lớp MatchPlayer đóng gói đội hình đề xuất, giữ id/registeredAt của cầu thủ còn lại; không sửa baseline session |
| 67 | Lớp MatchPlayer trả danh sách matchPlayers[] cho doSaveLineup.jsp |
| 68 | doSaveLineup.jsp gọi lớp MatchClub |
| 69 | Lớp MatchClub đóng gói danh sách matchPlayers[] vào matchClub đề xuất riêng, chưa thay dữ liệu đã xác nhận |
| 70 | Lớp MatchClub trả đối tượng matchClub cho doSaveLineup.jsp |
| 71 | doSaveLineup.jsp gọi lớp Match |
| 72 | Lớp Match giữ nguyên baseline đã đọc; matchClub đề xuất được truyền riêng cho saveLineup(match, matchClub) |
| 73 | Lớp Match trả đối tượng match cho doSaveLineup.jsp |
| 74 | doSaveLineup.jsp gọi lớp MatchDAO |
| 75 | MatchDAO thực hiện hàm saveLineup(match, matchClub) |
| 76 | Hàm saveLineup(match, matchClub) trả kết quả giao dịch; chỉ sau commit thành công mới cập nhật ngữ cảnh session, lỗi giữ nguyên baseline và báo lỗi |
| 77 | doSaveLineup.jsp gọi gdMatchInfo.jsp |
| 78 | gdMatchInfo.jsp hiển thị đội hình vừa đăng ký. |

### 3.2. Sequence Diagram

```mermaid
sequenceDiagram
    autonumber
    actor NV as Staff
    participant gdH as gdHome.jsp
    participant gdSS as gdSeasonSearch.jsp
    participant tDAO as TournamentDAO
    participant seDAO as SeasonDAO
    participant se as season: Season
    participant gdRS as gdRoundSearch.jsp
    participant rDAO as RoundDAO
    participant r as round: Round
    participant gdMS as gdMatchSearch.jsp
    participant mDAO as MatchDAO
    participant m as match: Match
    participant mc as matchclub: MatchClub
    participant mp as matchplayer: MatchPlayer
    participant gdI as gdMatchInfo.jsp
    participant gdLR as gdLineupRegistration.jsp
    participant cDAO as ClubDAO
    participant cp as clubplayer: ClubPlayer
    participant doSL as doSaveLineup.jsp

    NV->>gdH: Click nút registerLineup
    gdH->>gdSS: Gọi gdSeasonSearch.jsp
    gdSS-->>NV: Hiển thị giao diện cho nhân viên
    NV->>gdSS: Nhập "2026" → click nút btnSearch
    gdSS->>gdSS: Nhận searchKey từ request
    gdSS->>tDAO: getTournament()
    tDAO-->>gdSS: tournament (đúng một bản ghi)
    gdSS->>seDAO: Gọi lớp SeasonDAO
    seDAO->>seDAO: Thực hiện hàm searchSeason(tournament, searchKey)
    seDAO->>se: Hàm searchSeason(tournament, searchKey) gọi lớp Season
    se->>se: Khởi tạo đối tượng season và thực hiện hàm setter()
    se-->>seDAO: Trả danh sách seasons[]
    seDAO-->>gdSS: Trả danh sách seasons[]
    gdSS->>gdSS: Lưu danh sách seasons[] vào session
    gdSS-->>NV: Hiển thị danh sách kết quả cho nhân viên
    NV->>gdSS: Click vào dòng mùa giải
    gdSS->>gdRS: Gọi gdRoundSearch.jsp
    gdRS-->>NV: Hiển thị giao diện cho nhân viên
    NV->>gdRS: Nhập "5" → click nút btnSearch
    gdRS->>gdRS: Nhận searchKey từ request, đọc session lấy season khớp seasonId từ param
    gdRS->>rDAO: Gọi lớp RoundDAO
    rDAO->>rDAO: Thực hiện hàm searchRound(season, searchKey)
    rDAO->>r: Hàm searchRound(season, searchKey) gọi lớp Round
    r->>r: Khởi tạo đối tượng round và thực hiện hàm setter()
    r-->>rDAO: Trả danh sách rounds[]
    rDAO-->>gdRS: Trả danh sách rounds[]
    gdRS->>gdRS: Lưu danh sách rounds[] vào session
    gdRS-->>NV: Hiển thị danh sách kết quả cho nhân viên
    NV->>gdRS: Click vào dòng vòng đấu
    gdRS->>gdMS: Gọi gdMatchSearch.jsp
    gdMS-->>NV: Hiển thị giao diện cho nhân viên
    NV->>gdMS: Nhập "Hà Nội FC" → click nút btnSearch
    gdMS->>gdMS: Nhận searchKey từ request, đọc session lấy round khớp roundId từ param
    gdMS->>mDAO: Gọi lớp MatchDAO
    mDAO->>mDAO: Thực hiện hàm searchMatch(round, searchKey)
    mDAO->>m: Hàm searchMatch(round, searchKey) gọi lớp Match
    m->>m: Khởi tạo đối tượng match
    m->>mc: Gọi lớp MatchClub
    mc->>mc: Khởi tạo đối tượng matchClub
    mc->>mp: Gọi lớp MatchPlayer
    mp->>mp: Khởi tạo đối tượng matchPlayer và thực hiện hàm setter()
    mp-->>mc: Trả danh sách matchPlayers[]
    mc->>mc: Đóng gói danh sách matchPlayers[] vào đối tượng matchClub
    mc-->>m: Trả về danh sách matchClubs[]
    m->>m: Đóng gói danh sách matchClubs[] vào đối tượng match
    m-->>mDAO: Trả danh sách matchs[]
    mDAO-->>gdMS: Trả danh sách matchs[]
    gdMS->>gdMS: Lưu danh sách matchs[] vào session
    gdMS-->>NV: Hiển thị danh sách kết quả cho nhân viên
    NV->>gdMS: Click vào dòng trận đấu
    gdMS->>gdI: Gọi gdMatchInfo.jsp
    gdI->>gdI: Đọc session lấy match khớp matchId từ param
    gdI-->>NV: Hiển thị thông tin trận và đội hình cho nhân viên
    NV->>gdI: Click nút register home/away lineup
    gdI->>gdLR: Gọi gdLineupRegistration.jsp
    gdLR->>gdLR: Đọc session lấy matchClub (truy xuất từ match)
    gdLR->>cDAO: Gọi lớp ClubDAO
    cDAO->>cDAO: Thực hiện hàm getClubPlayerList(club)
    cDAO->>cp: Hàm getClubPlayerList(club) gọi lớp ClubPlayer
    cp->>cp: Thực hiện hàm setter()
    cp-->>cDAO: Trả danh sách clubPlayers[]
    cDAO-->>gdLR: Trả danh sách clubPlayers[]
    gdLR-->>NV: Hiển thị danh sách cầu thủ của CLB cho nhân viên
    NV->>gdLR: Chọn vai trò → click nút btnSaveLineup
    gdLR->>doSL: Gọi doSaveLineup.jsp
    doSL->>doSL: Nhận danh sách cầu thủ + vai trò từ request
    doSL->>mp: Gọi lớp MatchPlayer
    mp->>mp: Tạo đề xuất, giữ id/registeredAt của người còn lại
    mp-->>doSL: Trả danh sách matchPlayers[]
    doSL->>mc: Gọi lớp MatchClub
    mc->>mc: Đóng gói danh sách matchPlayers[] vào đối tượng matchClub
    mc-->>doSL: Trả đối tượng matchClub
    doSL->>m: Gọi lớp Match
    m->>m: Giữ baseline gốc; truyền matchClub đề xuất riêng
    m-->>doSL: Trả đối tượng match
    doSL->>mDAO: Gọi lớp MatchDAO
    mDAO->>mDAO: Thực hiện hàm saveLineup(match, matchClub)
    Note over mDAO: Giao dịch: đọc lại baseline, SCHEDULED, trước kickoff theo CSDL, 0 sự kiện; kiểm tra Rule/treo giò; giữ id/registeredAt
    mDAO-->>doSL: Kết quả giao dịch
    alt Lưu thành công
        doSL->>doSL: Cập nhật session từ đội hình đã lưu
        doSL->>gdI: Redirect thông tin trận
        gdI-->>NV: Hiển thị đội hình đã xác nhận
    else Lỗi hoặc baseline cũ
        doSL-->>NV: Hiển thị lỗi; không ghi đè đội hình trong CSDL
    end
```

