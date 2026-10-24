# API contract

Base URL local: `http://localhost:8081/api`. Ngoại trừ login, refresh và health, các API yêu cầu `Authorization: Bearer <accessToken>`. Body JSON dùng `Content-Type: application/json`.

## Xác thực

`POST /login`: `{ "username": "admin", "password": "Admin@12345" }` (tài khoản dev).

Response data: `{ token, accessToken, refreshToken, fullName, email, roles }`. JWT chứa `sub` là user ID, `sid`, `roles`, `email`, `fullName`, `iat`, `exp`, `iss: "fbus"`.

`POST /refresh-token`: `{ "refreshToken": "..." }`. Dùng refresh token mới trong phản hồi cho lần tiếp theo.

`POST /session/logout`: body rỗng. `GET /health` trả `data: { "status": "UP" }`.

Ngoại trừ ADMIN, tài khoản phải có email và vai trò tương ứng trong `email_access`. Kiểm tra áp dụng khi đăng nhập, refresh và dùng access token. Migration giữ quyền cho tài khoản hiện có; admin tạo tài khoản mới sẽ đồng thời cấp quyền email. Nhập email qua Excel chỉ cấp quyền, không tạo mật khẩu hoặc xác minh danh tính. Google OAuth chưa được tích hợp; không có endpoint đăng nhập chỉ bằng email.

## Điểm dừng, tuyến, chuyến (ADMIN)

`POST /admin/stops`:

```json
{ "stopCode": "FU-HCM", "name": "FU HCM", "address": "Địa chỉ cơ sở FU HCM" }
```

`GET /admin/stops` trả mảng `{ stopId, stopCode, name, address, offsetMinutes: null }`. Lưu lại ID do server cấp.

`POST /admin/routes`:

```json
{
  "routeCode": "HCM-TU",
  "name": "FU HCM → FU Tân Uyên",
  "stops": [
    { "stopId": 1, "offsetMinutes": 0 },
    { "stopId": 2, "offsetMinutes": 60 }
  ]
}
```

ID ví dụ phải thay bằng ID thật. Ít nhất 2, tối đa 30 điểm dừng không trùng nhau; offset đầu bằng 0, tăng dần, tối đa 1440 phút. Tuyến có hướng; chiều ngược tạo tuyến riêng. `GET /admin/routes` trả mảng `{ routeId, routeCode, name, stops: [{ stopId, stopCode, name, address, offsetMinutes }] }`.

`POST /admin/trips`: `{ routeId, vehicleId: null, vehicle: "51B-123.45", capacity: 45, driver: "Nguyễn Văn A", driverId: null, busAttendant: "", busAttendantId: null, date: "YYYY-MM-DD", time: "HH:mm", slot: 1 }`.

`POST /admin/trips/batch`: cùng thông tin xe/nhân sự như tạo một chuyến, thay `date` bằng `dates: ["YYYY-MM-DD", "YYYY-MM-DD"]`. Từ 1 đến 90 ngày khác nhau. Một transaction cho toàn bộ danh sách: ngày không hợp lệ hoặc trùng lịch sẽ rollback cả lần tạo. Response là mảng Trip đã tạo. Nếu mất kết nối, tải lại lịch để đối chiếu trước khi tạo lại.

`vehicleId` tùy chọn. Web cho nhập thủ công; chọn xe có sẵn sẽ điền biển số, số ghế, tài xế và bus attendant vào các ô có thể sửa. Các giá trị gửi lên được lưu riêng cho chuyến, không thay đổi mặc định của xe. Chỉ gửi `vehicleId` vẫn dùng được các mặc định. Nếu đổi biển số, bỏ `vehicleId`; biển số trùng xe đã đăng ký sẽ tự liên kết lại và kiểm tra trạng thái ACTIVE.

Biển số bắt buộc, chuẩn hóa chữ hoa và bỏ khoảng trắng, 5–30 ký tự chữ/số/dấu chấm/gạch ngang. Số ghế 1–100, tài xế bắt buộc, bus attendant tùy chọn. Có ID nhân sự thì kiểm tra đúng role/ACTIVE và dùng tên tài khoản; chỉ nhập tên trùng duy nhất một tài khoản đúng role thì tự liên kết ID. Tên trùng nhiều tài khoản phải chọn từ gợi ý. Tên nhập mới chưa có tài khoản được lưu với ID null; chuyến đó không xuất hiện trong phân công của một tài khoản nhân sự. Gửi `busAttendant: ""` và `busAttendantId: null` để bỏ attendant mặc định.

Giờ khởi hành phải ở tương lai theo giờ Việt Nam. Slot 1–6. Không trùng khoảng thời gian của xe, tài xế hoặc bus attendant theo ID; khi chưa có ID kiểm tra biển số/tên. Điểm dừng, nhân sự và sức chứa là snapshot tại thời điểm tạo; ghế tự tạo (`1A`, `1B`, ...). Mỗi hàng thường có 4 ghế (2–2, có lối đi); khi sức chứa chia 4 dư 1 và có ít nhất 5 ghế, hàng cuối có 5 ghế liền nhau. Ví dụ 45 ghế = 10 hàng × 4 + hàng cuối `11A`–`11E`; 49 ghế = 11 hàng × 4 + 5. Các sức chứa khác giữ đủ số ghế đã nhập, hàng cuối dùng phần còn lại (1–4). Mã và ID ghế của chuyến đã tạo được giữ nguyên.

`GET /admin/trips` trả mảng Trip (bên dưới). `booked` là số chỗ đang giữ cao nhất trên các chặng của chuyến, không phải tổng lịch sử booking. `PUT /admin/trips/{id}/status`: `{ "status": "SCHEDULED" }` hoặc `PAUSED`. Tạm dừng chặn đặt mới, không tự hủy các chỗ đang giữ. Xe và tài xế vẫn được giữ lịch khi chuyến tạm dừng.

## Tìm chuyến (STUDENT, LECTURER)

`GET /student/routes` và `GET /student/stops?routeId=...` dùng cùng dữ liệu tuyến/điểm dừng của admin.

`GET /student/trips?date=YYYY-MM-DD&routeId=...&boardingStopId=...&alightingStopId=...&slot=...`

Chỉ `date` bắt buộc. Các bộ lọc khác tùy chọn. Điểm đón/trả trong tìm kiếm dùng **stopId của tuyến**. Không có bộ lọc điểm thì dùng hai đầu tuyến. Ngày theo ngày khởi hành đầu tuyến. Chỉ trả chuyến đang mở, đúng thứ tự điểm dừng và còn trước giờ đón; vẫn trả chuyến hết ghế để UI hiển thị trạng thái.

Trip response:

```text
{ tripId, tripCode, routeId, routeName, date, time, arrivalTime, slot,
  vehicle, driver, capacity, booked, status, availableSeats,
  vehicleId, driverId, busAttendantId, busAttendant,
  stops: [{ tripStopId, stopId, name, position, arrivalAt }] }
```

`availableSeats` là số ghế trống đúng chặng đang tìm. `arrivalAt` và timestamp booking dạng `YYYY-MM-DDTHH:mm:ss`, giờ Việt Nam, không hậu tố UTC. `position` bắt đầu từ 0. Kết quả tìm kiếm sắp theo giờ đầu tuyến. `GET /student/trips/{id}/stops` trả mảng `stops` của chuyến.

`GET /student/trips/{id}/seats?boardingTripStopId=...&alightingTripStopId=...` trả `[{ seatId, seatCode, status: "AVAILABLE" | "BOOKED" }]`. Hai query bắt buộc và phải thuộc cùng chuyến, đúng chiều. Ghế trống ở lần đọc có thể được người khác đặt trước khi xác nhận.

## Đặt và hủy chỗ (STUDENT, LECTURER)

`POST /student/bookings`:

```text
{ tripId, boardingTripStopId, alightingTripStopId, seatId, idempotencyKey }
```

Lấy `tripStopId` từ `trip.stops`, không dùng `stopId` thay thế. Lấy `seatId` từ API ghế theo đúng chặng. Key gồm 16–100 ký tự chữ/số/`_`/`-`, giữ nguyên khi thử lại cùng yêu cầu. Mỗi học sinh chỉ có một booking đang hoạt động trên một chuyến. Ghế chỉ bị chiếm trong khoảng `[boardingPosition, alightingPosition)`.

Response data booking và `GET /student/bookings/my`:

```text
{ bookingId, bookingCode, tripId, tripCode, boardingStopId, boardingStop,
  alightingStopId, alightingStop, seatId, seatCode, status,
  reservedAt, checkedInAt, cancelledAt, departureAt,
  boardingTripStopId, alightingTripStopId }
```

`departureAt` là giờ đón tại điểm của học sinh. `checkedInAt`, `cancelledAt` có thể null. Booking mới có trạng thái `RESERVED`. Lịch sử gồm cả `CANCELLED`. `PUT /student/bookings/{id}/cancel` (body `{}` hoặc rỗng) chỉ cho chủ booking, trước giờ đón và chưa check-in; trả booking sau khi hủy. Gọi lại đã hủy vẫn trả thành công.

`PUT /student/profile/preferred-stop`: `{ "stopId": ... }` lưu lựa chọn và trả ID điểm dừng.

## Tài khoản, xe và nhân sự

`POST /admin/users`: `{ username, email, fullName, password, role }`. Admin tạo các role `STUDENT`, `LECTURER`, `DRIVER`, `BUS_ATTENDANT`. Username 3–100 ký tự chữ/số/`_.-`; password ít nhất 8 ký tự, tối đa 72 byte UTF-8. Mật khẩu lưu BCrypt. Username/email được chuẩn hóa chữ thường và kiểm tra trùng. Response không chứa password hoặc hash.

`GET /admin/staff` trả danh sách tài khoản đang hoạt động thuộc DRIVER/BUS_ATTENDANT: `[{ userId, fullName, email, role }]`.

`GET /admin/vehicles` trả `[{ vehicleId, licensePlate, capacity, driverId, driverName, busAttendantId, busAttendantName, status }]`.

`POST /admin/vehicles` và `PUT /admin/vehicles/{id}` dùng body `{ licensePlate, capacity, driverId, busAttendantId: null, status: "ACTIVE" }`. Biển số là tên hiển thị mặc định, chuẩn hóa chữ hoa và bỏ khoảng trắng; phải duy nhất. Số ghế 1–100; driver bắt buộc, attendant tùy chọn; kiểm tra đúng role và tài khoản đang hoạt động. Status nhận `ACTIVE` hoặc `INACTIVE`.

Sửa mặc định của xe chỉ ảnh hưởng chuyến được tạo sau đó; các chuyến cũ giữ snapshot nhân sự và ghế. API không xóa xe/tài khoản đang được tham chiếu. Xe ngừng sử dụng không tạo được chuyến mới, các chuyến cũ không tự tạm dừng.

`GET /staff/trips` yêu cầu DRIVER hoặc BUS_ATTENDANT, chỉ trả các chuyến từ hôm nay có `driverId` hoặc `busAttendantId` của người đang đăng nhập. Payload dùng Trip response. Nhân sự không có quyền tạo chuyến, xem toàn bộ đội xe hoặc tạo đặt chỗ qua API student. LECTURER sử dụng các API `/student/**` để đặt chỗ như STUDENT; web quản trị vẫn chỉ dành cho ADMIN.

## Danh sách email được phép đăng nhập (ADMIN)

- `GET /admin/email-access?page=0&size=20`: `{ content: [{ email, fullName, role, createdAt, userId }], page: { number, size, totalElements, totalPages } }`. `userId: null` nghĩa là email chưa có tài khoản.
- `GET /admin/email-access/template`: tải `fbus-email-template.xlsx` (binary, không có envelope), cần Bearer token. Sheet `Emails` có tiêu đề ở dòng 1: `Email`, `Họ tên`, `Vai trò`; sheet `Hướng dẫn` nêu quy định. File trống, không chứa email ví dụ để tránh nhập nhầm; Vai trò có danh sách chọn.
- `POST /admin/email-access/import`: multipart/form-data, trường `file`; không tự đặt boundary trong client. Nhận `.xlsx` và `.xls`, tối đa 2 MB / 2.000 dòng. Đọc sheet `Emails`, hoặc sheet đầu nếu không có tên này. Header bắt buộc ở dòng 1; hỗ trợ tên cột `Email`, `Họ tên`/`fullName`, `Vai trò`/`role`. Không nhận công thức, ô không phải văn bản, cột lạ hoặc dữ liệu ngoài cột khai báo. Giữ cơ chế bảo vệ ZIP và giới hạn kích thước entry giải nén 32 MB của POI.

Email bắt buộc, tối đa 254 ký tự, chuẩn hóa chữ thường và bỏ khoảng trắng hai đầu. Họ tên tùy chọn, tối đa 150 ký tự. Vai trò tùy chọn, mặc định `STUDENT`; nhận `STUDENT`, `LECTURER`, `DRIVER`, `BUS_ATTENDANT`, không cấp ADMIN qua Excel.

Response envelope data: `{ totalRows, imported, skipped, failed, errors: [{ row, email, message }] }`. `row` là số dòng Excel (bắt đầu 2); bỏ qua dòng trống. Các dòng hợp lệ được thêm, dòng lỗi trả trong `errors`, kể cả khi không thêm được dòng nào. Email trùng (trong tệp hoặc database) cùng vai trò bị bỏ qua; khác vai trò bị báo lỗi. Không cập nhật tên/vai trò/trạng thái của tài khoản hoặc email đã có. Import nối thêm, không xóa email vắng mặt trong tệp. Gửi lại sau lỗi mạng không tạo email trùng.

Thao tác lưu được khóa chung với tạo tài khoản; nếu lỗi database, rollback toàn bộ các dòng thêm mới của lần nhập. Email mới lưu `imported_by` (admin hiện tại), `created_at`. Danh sách chỉ dành cho ADMIN. Nhân sự có tên trong Excel chưa tự trở thành tài khoản đăng nhập hoặc nhân sự phân công được.

## Lỗi

`400`: dữ liệu không hợp lệ, điểm sai chuyến/chiều, ngày quá khứ, Excel không đọc được. `401`: chưa đăng nhập/token hết hạn/session thu hồi. `403`: sai role hoặc email chưa được cấp quyền khi login/refresh. `404`: không có tài nguyên hoặc không phải chủ booking. `409`: ghế vừa được đặt, đã có booking, key bị dùng với payload khác, chuyến không nhận đặt chỗ, lịch xe/tài xế bị trùng hoặc mã bị trùng. `413`: tệp vượt giới hạn upload.

## Khác với API Render cũ

Backend mới trả HTTP 200 cho thao tác thành công (client nhận cả 200/201); search dùng `boardingStopId`/`alightingStopId`, không dùng trip-stop ID để lọc tuyến. API ghế, tạo/hủy booking và tạo tuyến/chuyến đã có hợp đồng rõ ở trên. Các file hợp đồng Render cũ trong mobile được giữ như tài liệu lịch sử.
