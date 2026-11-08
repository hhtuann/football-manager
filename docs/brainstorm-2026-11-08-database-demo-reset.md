# Chốt làm mới database phục vụ demo

## Mục tiêu và quyết định

- Demo logic môn Phân tích và thiết kế hệ thống thông tin; giữ Java 17/JSP–DAO/PostgreSQL và các bảng/quan hệ ERD hiện có.
- Người dùng đồng ý: giữ seasons.name là ngoại lệ so với ERD gốc; bỏ users.source_key, rounds.round_number, matches.match_number, match_players.is_simulated, events.is_simulated. provenance đã bỏ. Không thêm bảng/cột thay thế.
- Backup rồi xóa toàn bộ dữ liệu ứng dụng, cấp lại identity và username CTxxx/QLxxx/NVxxx từ đầu. Không tái sử dụng hồ sơ demo cũ.
- Đủ mùa 2026/27: 14 CLB, 26 vòng/182 trận; giữ 4 mùa cũ chỉ có thông tin mùa.
- Dịch lịch tương đối quanh ngày chạy seed: có lịch sử và trận tương lai, không giữ nguyên ngày chính thức. Lưu quy tắc ngày trong script/file, không thêm cấu hình vào bảng.
- Không hiện cảnh báo mô phỏng. Giữ nguồn và giới hạn dataset trong file ngoài database.

## Nguồn và khả thi

Nguồn đã dùng: https://vpf.vn/season/v-league-2027/?action=calendar&pagejs=1 và trang CLB/cầu thủ/trận liên kết. JSON/CSV hiện tại là đầu vào tham khảo; cần kiểm tra lại nguồn khi thực thi. Không khẳng định lần brainstorm này đã thu thập lại web.

Bản phân tích tại Git HEAD không có 6 cột trên; bản docs working tree đã được cập nhật theo các lần triển khai. Không hoàn tác toàn bộ docs về HEAD vì sẽ làm mất các quyết định JSP–DAO, quan hệ một chiều và các yêu cầu đã chốt khác.

Các phụ thuộc đã thấy: DAO tìm mùa dùng seasons.name; SQL/DAO/model/UI/test và seed hiện dùng số vòng/trận, cờ mô phỏng, khóa nguồn. Phải cutover toàn bộ caller cùng schema; không chỉ DROP COLUMN.

## Các phương án đã cân nhắc

| Phương án | Ưu | Nhược | Quyết định |
|---|---|---|---|
| Đúng mọi thuộc tính ERD gốc | Schema tối thiểu | Mất tên mùa riêng | Không chọn; giữ seasons.name |
| Đủ mùa, bố trí ca demo | Đủ ngữ cảnh và ca nghiệp vụ | Cần kiểm tra liên kết, lịch và án treo giò | Chọn |
| Dataset gọn / chỉ dữ liệu xác minh | Ít dữ liệu / sát nguồn | Thiếu độ phủ ca demo | Không chọn |
| Giữ ngày VPF | Đúng lịch nguồn | Các ca đăng ký hết hạn theo thời gian | Không chọn |
| Dịch lịch demo | Có thể chuẩn bị ca trước/sau kickoff khi chạy seed | Không phải lịch chính thức | Chọn |

## Thiết kế triển khai

1. Đối chiếu đầy đủ schema runtime, báo cáo và mọi dependency trước sửa; giữ nguyên bảng, FK, trigger bảo vệ và view tỷ số derived trừ điều chỉnh thật sự cần thiết.
2. Thiết kế một bộ schema/seed chuẩn, không duy trì chuỗi script cũ tạo lại cột đã bỏ. Nguồn map vào ID chỉ trong script hoặc cấu trúc tạm, không thêm bảng bền vững.
3. Số vòng lấy từ Round.name theo quy ước thống nhất; mã trận derived theo thứ tự ổn định trong vòng, không cột mới. ID/FK vẫn là định danh; không coi ID là số vòng toàn hệ thống.
4. Tạo đủ hồ sơ cầu thủ theo luật đang dùng, mỗi CLB đủ đội hình 11 chính, thủ môn, dự bị/ngoại binh hợp lệ. Không suy các thông tin hồ sơ thiếu từ tên; nếu cần dữ liệu bổ sung phục vụ demo phải ghi cách tạo trong file và không mô tả là nguồn xác minh.
5. Các ca: lưu HOME/AWAY độc lập; lỗi số chính/thủ môn/quota; cầu thủ bị treo giò và đã chấp hành án; trận FINISHED/đã có event khóa đăng ký; trận tương lai cho phép lưu; bàn NORMAL/PENALTY/OWN và subtype đúng quan hệ.
6. Bỏ lọc thẻ mô phỏng: mọi thẻ trong seed tham gia tính án đúng cùng một luật. Kiểm tra toàn chuỗi sự kiện và đội hình, không sinh ngẫu nhiên gây mâu thuẫn.
7. Có tài khoản demo từng vai trò, mật khẩu băm, không sao chép credentials thật. Giữ nguyên đăng nhập currentUser, phân quyền và CSRF.
8. Chạy bộ mới trong môi trường cô lập trước. Khi đạt, backup database hiện tại ra ngoài repo; dừng web, reset dữ liệu trong phạm vi ứng dụng, áp dụng schema/seed và deploy WAR đồng bộ; bỏ session cũ.
9. Dọn script/schema/caller lỗi thời, cập nhật README/AGENTS và báo cáo mô hình liên quan. Không đổi stack hoặc triển khai CN3 ngoài phạm vi.

## Rủi ro và tiêu chí đạt

- Reset là phá hủy dữ liệu: backup phải đọc được, không chỉ tạo file rỗng. Không reset volume Docker để tiện thao tác.
- Giữ 5 mùa, 26 vòng/182 trận ở mùa chính; mùa cũ không có vòng/trận.
- Không còn 5 cột bị bỏ hoặc SQL/Java phụ thuộc chúng; seasons.name còn.
- Username đúng vai trò, identity/sequence không trùng khi thêm cầu thủ.
- FK và các quan hệ subtype đúng; tỷ số derived, không bảng/cột tỷ số song song.
- Đội hình/sự kiện/án treo giò nhất quán; có trận tương lai phù hợp khi seed chạy.
- Seed chạy lại không nhân đôi; nếu là reset seed phải ghi rõ và bảo vệ phạm vi phá hủy, không xóa dữ liệu âm thầm khi chạy ứng dụng.
- Build/test PostgreSQL thật; smoke Tomcat/browser đủ luồng Staff, lưu HOME/AWAY/reload, khóa trạng thái, từ chối vai trò và dữ liệu sai.
- CN3 vẫn chưa có route ghi; không hứa demo thao tác thêm sự kiện/kết thúc trận bằng UI.

## Trạng thái và bước tiếp

Chỉ brainstorm; chưa xóa dữ liệu, chưa sửa schema/code hoặc thu thập lại nguồn theo phương án này. Bước tiếp: lập kế hoạch triển khai chi tiết nếu người dùng chọn, sau đó thực thi theo yêu cầu riêng.
