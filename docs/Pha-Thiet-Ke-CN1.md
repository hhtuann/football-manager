# PHA THIẾT KẾ — CHỨC NĂNG 1: THÊM CẦU THỦ

Kiến trúc: **JSP – DAO**, không Controller. Java + JSP + MySQL.

---

## 1. THIẾT KẾ TĨNH — FULL VIEW CLASS DIAGRAM

### 1.1. Tầng giao diện gồm các trang .jsp

**Giao diện Đăng nhập: gdLogin.jsp**
- Thuộc tính tường minh:
  - username: input
  - password: input
  - btnLogin: submit
- Thuộc tính ẩn: không có
- Submit form tới doLogin.jsp (action) để kiểm tra đăng nhập.

**Giao diện chính nhân viên BTC: gdStaffHome.jsp**
- Thuộc tính tường minh:
  - managePlayer: link
  - registerLineup: link
  - updateResult: link
- Thuộc tính ẩn:
  - staff: Staff
- Không gọi DAO — đọc session lấy đối tượng staff, hiển thị thông tin + menu.

**Giao diện tìm cầu thủ: gdPlayerSearch.jsp**
- Thuộc tính tường minh:
  - searchKey: input
  - btnSearch: submit
  - tblPlayer: Table
  - choosePlayer: link
  - addPlayer: link
- Thuộc tính ẩn:
  - staff: Staff
  - listPlayer: Player[]
- Submit về chính trang này (action: self): nhận searchKey từ param, gọi PlayerDAO thực hiện searchPlayer().

**Giao diện thêm cầu thủ: gdPlayerForm.jsp**
- Thuộc tính tường minh:
  - fullName: input
  - dob: input
  - nationality: input
  - email: input
  - phone: input
  - heightCm: input
  - weightKg: input
  - primaryPosition: select
  - btnSave: submit
- Thuộc tính ẩn:
  - staff: Staff
- Submit form tới doAddPlayer.jsp (action) để lưu cầu thủ mới vào hệ thống.

### 1.2. Tầng thao tác dữ liệu có các lớp DAO

**UserDAO:**
- Phương thức checkLogin(user: User): User

**StaffDAO (kế thừa từ UserDAO):**
- Phương thức checkLogin(user: User): Staff — override, setter đầy đủ thông tin ngay trong checkLogin

**PlayerDAO (kế thừa từ UserDAO):**
- Phương thức searchPlayer(key: String): Player[]
- Phương thức addPlayer(player: Player): boolean

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
    class gdPlayerSearch {
        <<jsp gd>>
        +staff: Staff
        +searchKey: input
        +btnSearch: submit
        +action: gdPlayerSearch.jsp
        +listPlayer: Player[]
        +tblPlayer: Table
        +choosePlayer: link
        +addPlayer: link
    }
    class gdPlayerForm {
        <<jsp gd>>
        +staff: Staff
        +fullName: input
        +dob: input
        +nationality: input
        +email: input
        +phone: input
        +heightCm: input
        +weightKg: input
        +primaryPosition: select
        +btnSave: submit
        +action: doAddPlayer.jsp
    }
    class doAddPlayer {
        <<jsp do>>
        +player: Player
        +call PlayerDAO: addPlayer
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
    class PlayerDAO {
        +PlayerDAO()
        +checkLogin(user: User): Player
        +searchPlayer(key: String): Player[]
        +addPlayer(player: Player): boolean
    }
    class ManagerDAO {
        +ManagerDAO()
        +checkLogin(user: User): Manager
    }
    DAO <|-- UserDAO
    UserDAO <|-- StaffDAO
    UserDAO <|-- PlayerDAO
    UserDAO <|-- ManagerDAO

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
    gdStaffHome --> gdPlayerSearch : link [managePlayer]
    gdPlayerSearch --> gdPlayerSearch : submit [btnSearch]
    gdPlayerSearch --> gdPlayerForm : link [addPlayer]
    gdPlayerForm --> doAddPlayer : submit [btnSave]
    doAddPlayer --> gdPlayerSearch : redirect

    doLogin ..> StaffDAO : checkLogin
    gdPlayerSearch ..> PlayerDAO : searchPlayer
    doAddPlayer ..> PlayerDAO : addPlayer
```

---

## 2. MÔ TẢ ACTIVITY DIAGRAM

**Các activity đại diện cho các lớp giao diện, kèm các action của giao diện**

**gdLogin.jsp**
- Hiện giao diện đăng nhập

**doLogin.jsp**
- Nhận thông tin từ param username / password
- Tạo đối tượng User (đóng gói username, password) và lưu vào session
- Gọi StaffDAO thực hiện checkLogin() — kiểm tra đăng nhập
- Lưu đối tượng staff hoàn chỉnh vào session
- Hiển thị thông báo lỗi từ param nếu đăng nhập sai
- Chuyển tới gdStaffHome.jsp nếu đăng nhập đúng, quay lại gdLogin.jsp nếu sai

**gdStaffHome.jsp**
- Nhận thông tin staff từ session
- Hiện giao diện (thông tin nhân viên + menu)
- Nhân viên click nút managePlayer → chuyển gdPlayerSearch.jsp

**gdPlayerSearch.jsp**
- Hiện giao diện (form tìm kiếm)
- Nhận searchKey từ param khi nhân viên click nút btnSearch
- Gọi PlayerDAO thực hiện searchPlayer() — lấy danh sách cầu thủ khớp từ khoá
- Hiện danh sách cầu thủ cho nhân viên

**gdPlayerForm.jsp**
- Hiện giao diện (form nhập cầu thủ)
- Lấy thông tin cầu thủ nhập vào (họ tên, ngày sinh, quốc tịch, email, phone, chiều cao, cân nặng, vị trí)

**doAddPlayer.jsp**
- Nhận thông tin cầu thủ từ param
- Gọi lớp Player đóng gói thành đối tượng Player
- Gọi PlayerDAO thực hiện addPlayer() — lưu thông tin cầu thủ vào CSDL
- Chuyển về gdPlayerSearch.jsp
- Hiển thị thông báo thành công nếu thêm thành công / thông báo lỗi nếu thêm không thành công

---

---

## 3. THIẾT KẾ ĐỘNG

### 3.1. Kịch bản

| Bước | Nội dung thao tác |
|---|---|
| 1 | Nhân viên BTC thực hiện đăng nhập vào hệ thống |
| 2 | gdLogin.jsp gọi trang doLogin.jsp |
| 3 | doLogin.jsp nhận username, password từ request |
| 4 | doLogin.jsp gọi lớp User |
| 5 | Lớp User đóng gói username, password thành đối tượng user |
| 6 | Lớp User trả đối tượng user cho doLogin.jsp |
| 7 | doLogin.jsp lưu đối tượng user vào session |
| 8 | doLogin.jsp gọi lớp StaffDAO |
| 9 | StaffDAO thực hiện hàm checkLogin() |
| 10 | Hàm checkLogin() thực hiện và gọi lớp Staff |
| 11 | Lớp Staff khởi tạo đối tượng staff và thực hiện hàm setter() |
| 12 | Lớp Staff trả kết quả cho hàm checkLogin() |
| 13 | Hàm checkLogin() trả kết quả cho doLogin.jsp |
| 14 | doLogin.jsp lưu đối tượng staff vào session |
| 15 | doLogin.jsp gọi gdStaffHome.jsp |
| 16 | gdStaffHome.jsp đọc session lấy đối tượng staff |
| 17 | gdStaffHome.jsp hiển thị thông tin cho nhân viên |
| 18 | Nhân viên BTC click nút managePlayer |
| 19 | gdStaffHome.jsp gọi gdPlayerSearch.jsp |
| 20 | gdPlayerSearch.jsp hiển thị giao diện cho nhân viên |
| 21 | Nhân viên BTC nhập "Hải", click nút btnSearch |
| 22 | gdPlayerSearch.jsp nhận searchKey từ request |
| 23 | gdPlayerSearch.jsp gọi lớp PlayerDAO |
| 24 | PlayerDAO thực hiện hàm searchPlayer() |
| 25 | Hàm searchPlayer() thực hiện và gọi lớp Player |
| 26 | Lớp Player khởi tạo đối tượng player và thực hiện hàm setter() |
| 27 | Lớp Player trả danh sách players[] cho hàm searchPlayer() |
| 28 | Hàm searchPlayer() trả danh sách players[] cho gdPlayerSearch.jsp |
| 29 | gdPlayerSearch.jsp lưu danh sách players[] vào session |
| 30 | gdPlayerSearch.jsp hiển thị danh sách kết quả cho nhân viên |
| 31 | Nhân viên BTC click nút addPlayer |
| 32 | gdPlayerSearch.jsp gọi gdPlayerForm.jsp |
| 33 | gdPlayerForm.jsp hiển thị giao diện cho nhân viên |
| 34 | Nhân viên BTC nhập form, click nút btnSave |
| 35 | gdPlayerForm.jsp gọi doAddPlayer.jsp |
| 36 | doAddPlayer.jsp nhận thông tin cầu thủ từ request |
| 37 | doAddPlayer.jsp gọi lớp Player |
| 38 | Lớp Player đóng gói thông tin thành đối tượng player |
| 39 | Lớp Player trả đối tượng player cho doAddPlayer.jsp |
| 40 | doAddPlayer.jsp gọi lớp PlayerDAO |
| 41 | PlayerDAO thực hiện hàm addPlayer() |
| 42 | Hàm addPlayer() thực hiện và trả kết quả cho doAddPlayer.jsp |
| 43 | doAddPlayer.jsp gọi gdPlayerSearch.jsp |
| 44 | gdPlayerSearch.jsp hiển thị danh sách kết quả được cập nhật + thông báo thành công |

### 3.2. Sequence Diagram

```mermaid
sequenceDiagram
    autonumber
    actor NV as Staff
    participant gdL as gdLogin.jsp
    participant doL as doLogin.jsp
    participant u as user: User
    participant st as staff: Staff
    participant sDAO as StaffDAO
    participant gdH as gdStaffHome.jsp
    participant gdS as gdPlayerSearch.jsp
    participant pDAO as PlayerDAO
    participant p as player: Player
    participant gdF as gdPlayerForm.jsp
    participant doA as doAddPlayer.jsp

    NV->>gdL: Đăng nhập vào hệ thống
    gdL->>doL: Gọi trang doLogin.jsp
    doL->>doL: Nhận username, password từ request
    doL->>u: Gọi lớp User
    u->>u: Đóng gói username, password thành đối tượng user
    u-->>doL: Trả đối tượng user
    doL->>doL: Lưu đối tượng user vào session
    doL->>sDAO: Gọi lớp StaffDAO
    sDAO->>sDAO: Thực hiện hàm checkLogin()
    sDAO->>st: Hàm checkLogin() gọi lớp Staff
    st->>st: Khởi tạo đối tượng staff và thực hiện hàm setter()
    st-->>sDAO: Trả kết quả cho hàm checkLogin()
    sDAO-->>doL: Hàm checkLogin() trả kết quả
    doL->>doL: Lưu đối tượng staff vào session
    doL->>gdH: Gọi gdStaffHome.jsp
    gdH->>gdH: Đọc session lấy đối tượng staff
    gdH-->>NV: Hiển thị thông tin cho nhân viên
    NV->>gdH: Click nút managePlayer
    gdH->>gdS: Gọi gdPlayerSearch.jsp
    gdS-->>NV: Hiển thị giao diện cho nhân viên
    NV->>gdS: Nhập "Hải" → click nút btnSearch
    gdS->>gdS: Nhận searchKey từ request
    gdS->>pDAO: Gọi lớp PlayerDAO
    pDAO->>pDAO: Thực hiện hàm searchPlayer()
    pDAO->>p: Hàm searchPlayer() gọi lớp Player
    p->>p: Khởi tạo đối tượng player và thực hiện hàm setter()
    p-->>pDAO: Trả danh sách players[] cho hàm searchPlayer()
    pDAO-->>gdS: Trả danh sách players[]
    gdS->>gdS: Lưu danh sách players[] vào session
    gdS-->>NV: Hiển thị danh sách kết quả cho nhân viên
    NV->>gdS: Click nút addPlayer
    gdS->>gdF: Gọi gdPlayerForm.jsp
    gdF-->>NV: Hiển thị giao diện cho nhân viên
    NV->>gdF: Nhập form → click nút btnSave
    gdF->>doA: Gọi doAddPlayer.jsp
    doA->>doA: Nhận thông tin cầu thủ từ request
    doA->>p: Gọi lớp Player
    p->>p: Đóng gói thông tin thành đối tượng player
    p-->>doA: Trả đối tượng player
    doA->>pDAO: Gọi lớp PlayerDAO
    pDAO->>pDAO: Thực hiện hàm addPlayer()
    pDAO-->>doA: Hàm addPlayer() trả kết quả
    doA->>gdS: Gọi gdPlayerSearch.jsp
    gdS-->>NV: Hiển thị danh sách kết quả được cập nhật + thông báo thành công
```

