# PHA THIẾT KẾ — CHỨC NĂNG 3: CẬP NHẬT KẾT QUẢ TRẬN ĐẤU

Kiến trúc: **JSP – DAO**, không Controller. Java + JSP + MySQL.

> Staff đã đăng nhập ở CN1 — các trang này đều đọc session kiểm tra `staff: Staff`.

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
  - staff: Staff
  - listSeason: Season[]
- Submit về chính trang này (action: self): nhận searchKey từ param, gọi SeasonDAO thực hiện searchSeason().

**Giao diện tìm vòng đấu: gdRoundSearch.jsp**
- Thuộc tính tường minh:
  - searchKey: input
  - btnSearch: submit
  - tblRound: Table
  - chooseRound: link
- Thuộc tính ẩn:
  - staff: Staff
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
  - staff: Staff
  - round: Round (đọc session khớp roundId từ param)
  - listMatch: Match[]
- Submit về chính trang này (action: self): nhận searchKey từ param + đọc session lấy round, gọi MatchDAO thực hiện searchMatch(round, key) — hàm tải trọn chuỗi ◆ (MatchPlayer → MatchClub → Match), lưu listMatch vào session.

**Giao diện sự kiện trận: gdMatchEvent.jsp**
- Thuộc tính tường minh:
  - lblMatchTime: label
  - lblHomeScore: label
  - lblAwayScore: label
  - tblEvent: Table
  - addEvent: link
  - btnEndMatch: submit
- Thuộc tính ẩn:
  - staff: Staff
  - match: Match (đọc session khớp matchId từ param — đã chứa đầy đủ matchClubs + matchPlayers từ searchMatch)
  - listEvent: Event[]
  - listPlayerHome: MatchPlayer[] (truy xuất từ match, lưu vào session cho gdEventForm)
  - listPlayerAway: MatchPlayer[] (truy xuất từ match, lưu vào session cho gdEventForm)
- Đọc session lấy match → gọi EventDAO thực hiện getEventList(matchId) → lấy danh sách cầu thủ 2 CLB từ match lưu vào session → hiển thị tỉ số + danh sách sự kiện. KHÔNG gọi DAO lấy đội hình — đã có sẵn trong match.

**Giao diện thêm sự kiện: gdEventForm.jsp**
- Thuộc tính tường minh:
  - club: select
  - eventType: select
  - matchMinute: input
  - stoppageMinute: input
  - primaryPlayer: select
  - secondaryPlayer: select
  - note: input
  - btnSaveEvent: submit
- Thuộc tính ẩn:
  - staff: Staff
  - match: Match (đọc session)
  - listPlayerHome: MatchPlayer[] (đọc session — đã lưu từ gdMatchEvent)
  - listPlayerAway: MatchPlayer[] (đọc session — đã lưu từ gdMatchEvent)
- Nhận matchClubId từ param khi nhân viên chọn CLB → đọc session lấy danh sách cầu thủ CLB đã chọn (KHÔNG gọi CSDL — dữ liệu đã cached). Submit form tới doAddEvent.jsp (action).

### 1.2. Tầng thao tác dữ liệu có các lớp DAO

**SeasonDAO:**
- Phương thức searchSeason(key: String): Season[]

**RoundDAO:**
- Phương thức searchRound(season: Season, key: String): Round[]

**MatchDAO (DAO của chủ thể Match — thao tác trên trung gian MatchClub/MatchPlayer):**
- Phương thức searchMatch(round: Round, key: String): Match[] — tải trọn chuỗi ◆: MatchPlayer → MatchClub → Match
- Phương thức saveMatch(match: Match): boolean — lưu trạng thái kết thúc trận

**EventDAO (Event là lớp độc lập có DAO riêng — không phải trung gian):**
- Phương thức getEventList(matchId: int): Event[]
- Phương thức addEvent(match: Match, event: Event): boolean — tự cập nhật tỉ số trận

> **Quy tắc trung gian:** MatchClub, MatchPlayer là lớp trung gian ◆ sinh từ quan hệ n–n — KHÔNG có DAO riêng, thao tác qua MatchDAO. Event là lớp độc lập (con của Match nhưng không phải trung gian n–n) → có DAO riêng bình thường. Đội hình cầu thủ tải 1 lần duy nhất trong searchMatch, các trang sau đọc từ match trong session.

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
        +call StaffDAO: checkLogin
    }
    class gdStaffHome {
        <<jsp gd>>
        +staff: Staff
        +managePlayer: link
        +registerLineup: link
        +updateResult: link
    }
    class gdSeasonSearch {
        <<jsp gd>>
        +staff: Staff
        +searchKey: input
        +btnSearch: submit
        +action: gdSeasonSearch.jsp
        +listSeason: Season[]
        +tblSeason: Table
        +chooseSeason: link
    }
    class gdRoundSearch {
        <<jsp gd>>
        +staff: Staff
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
        +staff: Staff
        +round: Round
        +searchKey: input
        +btnSearch: submit
        +action: gdMatchSearch.jsp
        +listMatch: Match[]
        +tblMatch: Table
        +chooseMatch: link
    }
    class gdMatchEvent {
        <<jsp gd>>
        +staff: Staff
        +match: Match
        +lblMatchTime: label
        +lblHomeScore: label
        +lblAwayScore: label
        +listEvent: Event[]
        +tblEvent: Table
        +addEvent: link
        +btnEndMatch: submit
        +action: doEndMatch.jsp
    }
    class gdEventForm {
        <<jsp gd>>
        +staff: Staff
        +match: Match
        +club: select
        +eventType: select
        +matchMinute: input
        +stoppageMinute: input
        +primaryPlayer: select
        +secondaryPlayer: select
        +note: input
        +btnSaveEvent: submit
        +action: doAddEvent.jsp
        +listPlayerHome: MatchPlayer[]
        +listPlayerAway: MatchPlayer[]
    }
    class doAddEvent {
        <<jsp do>>
        +event: Event
        +match: Match
        +call EventDAO: addEvent
    }
    class doEndMatch {
        <<jsp do>>
        +match: Match
        +call MatchDAO: saveMatch
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
    class StaffDAO {
        +StaffDAO()
        +checkLogin(user: User): Staff
    }
    class SeasonDAO {
        +SeasonDAO()
        +searchSeason(key: String): Season[]
    }
    class RoundDAO {
        +RoundDAO()
        +searchRound(season: Season, key: String): Round[]
    }
    class MatchDAO {
        +MatchDAO()
        +searchMatch(round: Round, key: String): Match[]
        +saveMatch(match: Match): boolean
    }
    class EventDAO {
        +EventDAO()
        +getEventList(matchId: int): Event[]
        +addEvent(match: Match, event: Event): boolean
    }
    DAO <|-- UserDAO
    UserDAO <|-- StaffDAO
    DAO <|-- SeasonDAO
    DAO <|-- RoundDAO
    DAO <|-- MatchDAO
    DAO <|-- EventDAO

    class User {
        <<abstract>>
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
        +startDate: LocalDate
        +endDate: LocalDate
        +status: String
        +tournament: Tournament
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
    doLogin --> gdStaffHome : redirect
    gdStaffHome --> gdSeasonSearch : link [updateResult]
    gdSeasonSearch --> gdSeasonSearch : submit [btnSearch]
    gdSeasonSearch --> gdRoundSearch : link [chooseSeason]
    gdRoundSearch --> gdRoundSearch : submit [btnSearch]
    gdRoundSearch --> gdMatchSearch : link [chooseRound]
    gdMatchSearch --> gdMatchSearch : submit [btnSearch]
    gdMatchSearch --> gdMatchEvent : link [chooseMatch]
    gdMatchEvent --> gdEventForm : link [addEvent]
    gdEventForm --> doAddEvent : submit [btnSaveEvent]
    doAddEvent --> gdMatchEvent : redirect
    gdMatchEvent --> doEndMatch : submit [btnEndMatch]
    doEndMatch --> gdMatchEvent : redirect

    doLogin ..> StaffDAO : checkLogin
    gdSeasonSearch ..> SeasonDAO : searchSeason
    gdRoundSearch ..> RoundDAO : searchRound
    gdMatchSearch ..> MatchDAO : searchMatch
    gdMatchEvent ..> EventDAO : getEventList
    doAddEvent ..> EventDAO : addEvent
    doEndMatch ..> MatchDAO : saveMatch
```

---

## 2. MÔ TẢ ACTIVITY DIAGRAM

**Các activity đại diện cho các lớp giao diện, kèm các action của giao diện**

**gdStaffHome.jsp**
- Nhận thông tin staff từ session
- Hiện giao diện (menu)
- Nhân viên click nút updateResult → chuyển gdSeasonSearch.jsp

**gdSeasonSearch.jsp / gdRoundSearch.jsp / gdMatchSearch.jsp**
- (giống Chức năng 2)

**gdMatchEvent.jsp**
- Đọc session lấy đối tượng match khớp matchId từ param
- Gọi EventDAO thực hiện getEventList() — lấy danh sách sự kiện của trận đấu
- Hiện tỉ số và danh sách sự kiện cho nhân viên
- Nhân viên click nút addEvent → chuyển gdEventForm.jsp
- Nhân viên click nút btnEndMatch → chuyển doEndMatch.jsp
- Nhận lại từ doAddEvent.jsp / doEndMatch.jsp → hiện sự kiện mới + tỉ số cập nhật / kết quả cuối cùng

**gdEventForm.jsp**
- Hiện giao diện (form sự kiện: loại sự kiện, câu lạc bộ, phút)
- Nhận matchClubId từ param khi nhân viên chọn câu lạc bộ
- Đọc session lấy danh sách cầu thủ của CLB đã chọn
- Hiện danh sách cầu thủ cho nhân viên
- Nhận loại sự kiện, cầu thủ, phút nhân viên nhập vào, click nút btnSaveEvent → chuyển doAddEvent.jsp

**doAddEvent.jsp**
- Nhận thông tin sự kiện từ request
- Gọi lớp Event đóng gói thành đối tượng Event
- Gọi EventDAO thực hiện addEvent() — tự cập nhật tỉ số trận
- Chuyển về gdMatchEvent.jsp
- Hiển thị sự kiện mới và tỉ số cập nhật nếu thành công / thông báo lỗi nếu không thành công

**doEndMatch.jsp**
- Đọc session lấy đối tượng match khớp matchId từ param
- Gọi MatchDAO thực hiện saveMatch() — lưu trạng thái kết thúc trận
- Chuyển về gdMatchEvent.jsp (khóa khung, không cho thêm sự kiện nữa)
- Hiển thị kết quả cuối cùng của trận đấu

---

---

## 3. THIẾT KẾ ĐỘNG

### 3.1. Kịch bản

| Bước | Nội dung thao tác |
|---|---|
| 1 | Nhân viên BTC click nút updateResult |
| 2 | gdStaffHome.jsp gọi gdSeasonSearch.jsp |
| 3 | gdSeasonSearch.jsp hiển thị giao diện cho nhân viên |
| 4 | Nhân viên BTC nhập "2026", click nút btnSearch |
| 5 | gdSeasonSearch.jsp nhận searchKey từ request |
| 6 | gdSeasonSearch.jsp gọi lớp SeasonDAO |
| 7 | SeasonDAO thực hiện hàm searchSeason() |
| 8 | Hàm searchSeason() thực hiện và gọi lớp Season |
| 9 | Lớp Season khởi tạo đối tượng season và thực hiện hàm setter() |
| 10 | Lớp Season trả danh sách seasons[] cho hàm searchSeason() |
| 11 | Hàm searchSeason() trả danh sách seasons[] cho gdSeasonSearch.jsp |
| 12 | gdSeasonSearch.jsp lưu danh sách seasons[] vào session |
| 13 | gdSeasonSearch.jsp hiển thị danh sách kết quả cho nhân viên. |
| 14 | Nhân viên BTC click vào dòng mùa giải |
| 15 | gdSeasonSearch.jsp gọi gdRoundSearch.jsp |
| 16 | gdRoundSearch.jsp hiển thị giao diện cho nhân viên |
| 17 | Nhân viên BTC nhập "5", click nút btnSearch |
| 18 | gdRoundSearch.jsp nhận searchKey từ request, đọc session lấy đối tượng season khớp seasonId từ param |
| 19 | gdRoundSearch.jsp gọi lớp RoundDAO |
| 20 | RoundDAO thực hiện hàm searchRound() |
| 21 | Hàm searchRound() thực hiện và gọi lớp Round |
| 22 | Lớp Round khởi tạo đối tượng round và thực hiện hàm setter() |
| 23 | Lớp Round trả danh sách rounds[] cho hàm searchRound() |
| 24 | Hàm searchRound() trả danh sách rounds[] cho gdRoundSearch.jsp |
| 25 | gdRoundSearch.jsp lưu danh sách rounds[] vào session |
| 26 | gdRoundSearch.jsp hiển thị danh sách kết quả cho nhân viên |
| 27 | Nhân viên BTC click vào dòng vòng đấu |
| 28 | gdRoundSearch.jsp gọi gdMatchSearch.jsp |
| 29 | gdMatchSearch.jsp hiển thị giao diện cho nhân viên |
| 30 | Nhân viên BTC nhập "Hà Nội FC", click nút btnSearch |
| 31 | gdMatchSearch.jsp nhận searchKey từ request, đọc session lấy đối tượng round khớp roundId từ param |
| 32 | gdMatchSearch.jsp gọi lớp MatchDAO |
| 33 | MatchDAO thực hiện hàm searchMatch() |
| 34 | Hàm searchMatch() thực hiện và gọi lớp Match |
| 35 | Lớp Match khởi tạo đối tượng match |
| 36 | Lớp Match gọi lớp MatchClub |
| 37 | Lớp MatchClub khởi tạo đối tượng matchClub |
| 38 | Lớp MatchClub gọi lớp MatchPlayer |
| 39 | Lớp MatchPlayer khởi tạo đối tượng matchPlayer và thực hiện hàm setter() |
| 40 | Lớp MatchPlayer trả danh sách matchPlayers[] cho lớp MatchClub |
| 41 | Lớp MatchClub đóng gói danh sách matchPlayers[] vào đối tượng matchClub |
| 42 | Lớp MatchClub trả về danh sách matchClubs[] cho lớp Match |
| 43 | Lớp Match đóng gói danh sách matchClubs[] vào đối tượng match |
| 44 | Lớp Match trả danh sách matchs[] cho hàm searchMatch() |
| 45 | Hàm searchMatch() trả danh sách matchs[] cho gdMatchSearch.jsp |
| 46 | gdMatchSearch.jsp lưu danh sách matchs[] vào session |
| 47 | gdMatchSearch.jsp hiển thị danh sách kết quả cho nhân viên |
| 48 | Nhân viên BTC click vào dòng trận đấu |
| 49 | gdMatchSearch.jsp gọi gdMatchEvent.jsp |
| 50 | gdMatchEvent.jsp đọc session lấy đối tượng match khớp matchId từ param |
| 51 | gdMatchEvent.jsp gọi lớp EventDAO |
| 52 | EventDAO thực hiện hàm getEventList() |
| 53 | Hàm getEventList() thực hiện và gọi lớp Event |
| 54 | Lớp Event thực hiện hàm setter() |
| 55 | Lớp Event trả danh sách events[] cho hàm getEventList() |
| 56 | Hàm getEventList() trả danh sách Event[] cho gdMatchEvent.jsp |
| 57 | gdMatchEvent.jsp gọi lớp Match |
| 58 | Lớp Match đóng gói danh sách events[] vào đối tượng match |
| 59 | Lớp Match trả đối tượng match cho gdMatchEvent.jsp |
| 60 | gdMatchEvent.jsp hiển thị danh sách sự kiện cho nhân viên |
| 61 | Nhân viên BTC click nút addEvent |
| 62 | gdMatchEvent.jsp gọi gdEventForm.jsp |
| 63 | gdEventForm.jsp hiển thị giao diện cho nhân viên |
| 64 | Nhân viên BTC chọn câu lạc bộ của cầu thủ chính của sự kiện |
| 65 | gdEventForm.jsp đọc session lấy matchPlayers[] (truy xuất từ match) |
| 66 | gdEventForm.jsp hiển thị danh sách cầu thủ cho nhân viên |
| 67 | Nhân viên BTC chọn loại sự kiện, cầu thủ, phút, click nút btnSaveEvent |
| 68 | gdEventForm.jsp gọi doAddEvent.jsp |
| 69 | doAddEvent.jsp nhận thông tin sự kiện từ request |
| 70 | doAddEvent.jsp gọi lớp Event |
| 71 | Lớp Event đóng gói thông tin thành đối tượng event |
| 72 | Lớp Event trả đối tượng event cho doAddEvent.jsp |
| 73 | doAddEvent.jsp gọi lớp EventDAO |
| 74 | EventDAO thực hiện hàm addEvent() |
| 75 | Hàm addEvent() trả kết quả cho doAddEvent.jsp |
| 76 | doAddEvent.jsp gọi gdMatchEvent.jsp |
| 77 | gdMatchEvent.jsp hiển thị sự kiện mới và tỉ số cập nhật cho nhân viên |
| 78 | Nhân viên BTC click nút btnEndMatch |
| 79 | gdMatchEvent.jsp gọi doEndMatch.jsp |
| 80 | doEndMatch.jsp đọc session lấy đối tượng match khớp matchId từ param |
| 81 | doEndMatch.jsp gọi lớp MatchDAO |
| 82 | MatchDAO thực hiện hàm saveMatch() |
| 83 | Hàm saveMatch() trả kết quả cho doEndMatch.jsp |
| 84 | doEndMatch.jsp gọi gdMatchEvent.jsp |
| 85 | gdMatchEvent.jsp hiển thị kết quả cuối cùng cho nhân viên. |

### 3.2. Sequence Diagram

```mermaid
sequenceDiagram
    autonumber
    actor NV as Staff
    participant gdH as gdStaffHome.jsp
    participant gdSS as gdSeasonSearch.jsp
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
    participant gdE as gdMatchEvent.jsp
    participant eDAO as EventDAO
    participant e as event: Event
    participant gdF as gdEventForm.jsp
    participant doAE as doAddEvent.jsp
    participant doEM as doEndMatch.jsp

    NV->>gdH: Click nút updateResult
    gdH->>gdSS: Gọi gdSeasonSearch.jsp
    gdSS-->>NV: Hiển thị giao diện cho nhân viên
    NV->>gdSS: Nhập "2026" → click nút btnSearch
    gdSS->>gdSS: Nhận searchKey từ request
    gdSS->>seDAO: Gọi lớp SeasonDAO
    seDAO->>seDAO: Thực hiện hàm searchSeason()
    seDAO->>se: Hàm searchSeason() gọi lớp Season
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
    rDAO->>rDAO: Thực hiện hàm searchRound()
    rDAO->>r: Hàm searchRound() gọi lớp Round
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
    mDAO->>mDAO: Thực hiện hàm searchMatch()
    mDAO->>m: Hàm searchMatch() gọi lớp Match
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
    gdMS->>gdE: Gọi gdMatchEvent.jsp
    gdE->>gdE: Đọc session lấy match khớp matchId từ param
    gdE->>eDAO: Gọi lớp EventDAO
    eDAO->>eDAO: Thực hiện hàm getEventList()
    eDAO->>e: Hàm getEventList() gọi lớp Event
    e->>e: Thực hiện hàm setter()
    e-->>eDAO: Trả danh sách events[]
    eDAO-->>gdE: Trả danh sách Event[]
    gdE->>m: Gọi lớp Match
    m->>m: Đóng gói danh sách events[] vào đối tượng match
    m-->>gdE: Trả đối tượng match
    gdE-->>NV: Hiển thị danh sách sự kiện cho nhân viên
    NV->>gdE: Click nút addEvent
    gdE->>gdF: Gọi gdEventForm.jsp
    gdF-->>NV: Hiển thị giao diện cho nhân viên
    NV->>gdF: Chọn câu lạc bộ của cầu thủ chính
    gdF->>gdF: Đọc session lấy matchPlayers[] (truy xuất từ match)
    gdF-->>NV: Hiển thị danh sách cầu thủ cho nhân viên
    NV->>gdF: Chọn loại sự kiện, cầu thủ, phút → click nút btnSaveEvent
    gdF->>doAE: Gọi doAddEvent.jsp
    doAE->>doAE: Nhận thông tin sự kiện từ request
    doAE->>e: Gọi lớp Event
    e->>e: Đóng gói thông tin thành đối tượng event
    e-->>doAE: Trả đối tượng event
    doAE->>eDAO: Gọi lớp EventDAO
    eDAO->>eDAO: Thực hiện hàm addEvent()
    eDAO-->>doAE: Trả kết quả
    doAE->>gdE: Gọi gdMatchEvent.jsp
    gdE-->>NV: Hiển thị sự kiện mới và tỉ số cập nhật cho nhân viên
    NV->>gdE: Click nút btnEndMatch
    gdE->>doEM: Gọi doEndMatch.jsp
    doEM->>doEM: Đọc session lấy match khớp matchId từ param
    doEM->>mDAO: Gọi lớp MatchDAO
    mDAO->>mDAO: Thực hiện hàm saveMatch()
    mDAO-->>doEM: Trả kết quả
    doEM->>gdE: Gọi gdMatchEvent.jsp
    gdE-->>NV: Hiển thị kết quả cuối cùng cho nhân viên
```
